"use strict";
const Decoder = require('./streamnest-media-decoder');
// Keep the native video element and its controls/resume clock. Decode only missing audio.
class DecodedAudio {
  constructor(video, win, report = () => {}) {
    this.video = video; this.win = win; this.report = report; this.audio = win.document.createElement('audio');
    this.audio.preload = 'auto'; this.disposed = false; this.sequence = 0; this.epoch = 0; this.ready = false; this.freezing = false;
    this.handlers = {
      pause: () => { if (!this.freezing) { this.wantPlaying = false; this.audio.pause(); } },
      play: () => { if (!this.freezing) { this.wantPlaying = true; if (this.ready) this.audio.play().catch(() => this.restart()); } },
      seeking: () => { if (!this.freezing) { this.audio.pause(); clearTimeout(this.seekTimer); this.seekTimer = setTimeout(() => this.restart(), 180); } },
      volumechange: () => { this.audio.volume = video.volume; this.audio.muted = video.muted; },
      ratechange: () => { this.audio.playbackRate = video.playbackRate; },
      ended: () => this.audio.pause(),
      waiting: () => this.audio.pause(),
      playing: () => { if (this.ready && !this.freezing) this.audio.play().catch(() => {}); }
    };
    for (const [name, fn] of Object.entries(this.handlers)) video.addEventListener(name, fn);
    this.timer = setInterval(() => this.correct(), 500); this.timer.unref?.();
  }
  async start(track) {
    this.track = track; this.wantPlaying = !this.video.paused;
    this.server = await Decoder.audioServer(this.video.currentSrc || this.video.src);
    if (this.disposed) { this.server.close(); return; }
    await this.restart();
  }
  async restart() {
    if (this.disposed || !this.server) return;
    const seq = ++this.sequence; this.pendingCancel?.(); const wasPlaying = this.wantPlaying || !this.video.paused;
    this.ready = false; this.freezing = true; this.video.pause(); this.audio.pause(); this.epoch = this.video.currentTime;
    this.report('Preparing compatible audio…');
    try {
      await new Promise((resolve, reject) => {
        const cleanup = () => { clearTimeout(timer); this.audio.removeEventListener('canplay', ready); this.audio.removeEventListener('error', fail); if(this.pendingCancel===cancel)this.pendingCancel=null; };
        const cancel=()=>{cleanup();reject(Error('Audio preparation cancelled'));};this.pendingCancel=cancel;
        const ready = () => { cleanup(); resolve(); }, fail = () => { cleanup(); reject(Error('Audio decoding failed')); };
        const timer = setTimeout(() => { cleanup(); reject(Error('Audio decoding timed out')); }, 25000);
        this.audio.addEventListener('canplay', ready, {once: true}); this.audio.addEventListener('error', fail, {once: true});
        this.audio.src = this.server.url(this.track.index, this.epoch); this.audio.load();
      });
      if (this.disposed || seq !== this.sequence) return;
      this.audio.volume = this.video.volume; this.audio.muted = this.video.muted; this.audio.playbackRate = this.video.playbackRate;
      this.ready = true; this.freezing = false; this.startedAt = performance.now(); this.wantPlaying = wasPlaying;
      if (wasPlaying) { await this.audio.play(); await this.video.play(); }
      this.report('');
    } catch (error) {
      if (this.disposed || seq !== this.sequence) return;
      this.freezing = false; this.report('Compatible audio could not start. Try another source.');
      if (wasPlaying) this.video.play().catch(() => {});
    }
  }
  correct() {
    if (!this.ready || this.disposed || this.video.paused || this.video.seeking || this.freezing || performance.now() - this.startedAt < 3000) return;
    const drift = this.epoch + this.audio.currentTime - this.video.currentTime;
    if (Math.abs(drift) > 1.5) { this.restart(); return; }
    const desired = this.video.currentTime - this.epoch;
    if (Math.abs(drift) > 0.25 && performance.now() - (this.correctedAt || 0) > 3000) {
      for (let n = 0; n < this.audio.buffered.length; n++) if (desired >= this.audio.buffered.start(n) && desired < this.audio.buffered.end(n) - 0.25) { this.audio.currentTime = desired; this.correctedAt = performance.now(); break; }
    }
    this.audio.playbackRate = this.video.playbackRate;
  }
  dispose() {
    this.disposed = true; ++this.sequence; this.pendingCancel?.(); clearInterval(this.timer); clearTimeout(this.seekTimer);
    for (const [name, fn] of Object.entries(this.handlers)) this.video.removeEventListener(name, fn);
    this.audio.pause(); this.audio.removeAttribute('src'); this.audio.load(); this.server?.close();
  }
}
module.exports = {DecodedAudio};
