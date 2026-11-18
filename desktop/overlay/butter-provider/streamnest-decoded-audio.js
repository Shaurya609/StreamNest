"use strict";
const Decoder = require('./streamnest-media-decoder');
const {headroom} = require('./streamnest-buffered-recovery');
// Native video remains the clock. Owned pauses must never overwrite user intent.
class DecodedAudio {
  constructor(video, win, report = () => {}, options = {}) {
    this.video = video; this.win = win; this.report = report; this.options = options;
    this.audio = win.document.createElement('audio'); this.audio.preload = 'auto';
    this.disposed = false; this.sequence = 0; this.epoch = 0; this.ready = false; this.freezing = false;
    this.phase = 'starting'; this.restarts = []; this.ignorePause = false;
    video.streamnestCompatibilityAudio = this;
    this.handlers = {
      pause: () => { if (this.ignorePause) { this.ignorePause = false; return; } this.wantPlaying = false; this.audio.pause(); this.phase = 'paused'; },
      play: () => { this.wantPlaying = true; if (this.freezing) this.holdVideo(); else if (this.ready) this.playAudio(); else this.restart('user'); },
      seeking: () => { this.audio.pause(); clearTimeout(this.seekTimer); this.seekTimer = setTimeout(() => this.restart('seek'), 180); },
      volumechange: () => { this.audio.volume = video.volume; this.audio.muted = video.muted; },
      ratechange: () => { this.audio.playbackRate = video.playbackRate; },
      ended: () => { this.wantPlaying = false; this.audio.pause(); },
      waiting: () => { this.videoWaiting = true; this.audio.pause(); },
      playing: () => { this.videoWaiting = false; if (this.ready && this.wantPlaying && !this.freezing) this.playAudio(); }
    };
    this.audioHandlers = {
      ended: () => { if (this.ready && this.wantPlaying && !this.video.ended && this.video.duration - this.video.currentTime > 5) this.fail(Error('Compatible audio ended before video')); },
      waiting: () => { if (this.ready && this.wantPlaying && !this.freezing) { this.phase = 'buffering'; this.bufferingAt = performance.now(); this.holdVideo(); } },
      canplay: () => { if (this.ready && this.wantPlaying && !this.freezing && this.phase === 'buffering') this.resume(); },
      error: () => { if (this.ready && !this.freezing) this.fail(Error('Compatible audio stream failed')); }
    };
    for (const [name, fn] of Object.entries(this.handlers)) video.addEventListener(name, fn);
    for (const [name, fn] of Object.entries(this.audioHandlers)) this.audio.addEventListener(name, fn);
    this.timer = setInterval(() => this.correct(), 500); this.timer.unref?.();
  }
  holdVideo() { if (!this.video.paused) { this.ignorePause = true; this.video.pause(); } }
  async playAudio() {
    try { await this.audio.play(); if (!this.wantPlaying || this.disposed) this.audio.pause(); else if (!this.video.paused && !this.videoWaiting && !this.freezing) { this.phase = 'playing'; this.report(''); } }
    catch (error) { if (this.wantPlaying && !this.disposed && !this.freezing) this.fail(error); }
  }
  async resume() {
    if (this.disposed || !this.wantPlaying || !this.ready) return;
    try {
      await this.audio.play();
      if (this.disposed || !this.wantPlaying) { this.audio.pause(); return; }
      await this.video.play();
      if (this.wantPlaying) { this.phase = 'playing'; this.report(''); }
    } catch (error) { if (!this.disposed && this.wantPlaying) this.fail(error); }
  }
  fail(error) {
    if (this.disposed) return;
    this.ready = false; this.freezing = false; this.phase = 'failed'; this.audio.pause();
    if (this.wantPlaying) this.holdVideo();
    this.report('Compatible audio stopped. Use Play to retry or choose another source.');
    try { this.options.onFailure?.(error, this.wantPlaying); } catch (_) { /* Keep a failed UI callback from recursively restarting the decoder. */ }
  }
  async start(track) {
    this.track = track; this.wantPlaying = !this.video.paused;
    this.server = await Decoder.audioServer(this.video.currentSrc || this.video.src);
    if (this.disposed) { this.server.close(); return; }
    await this.restart('start');
  }
  async restart(reason = 'user') {
    if (this.disposed || !this.server || this.freezing && reason !== 'seek') return;
    if (['sync', 'buffer'].includes(reason)) {
      const now = performance.now(); this.restarts = this.restarts.filter(at => now - at < 60000);
      if (this.restarts.length >= 2) { this.fail(Error('Compatible audio recovery limit reached')); return; }
      this.restarts.push(now);
    }
    const seq = ++this.sequence; this.pendingCancel?.();
    this.ready = false; this.freezing = true; this.phase = 'starting'; this.holdVideo(); this.audio.pause(); this.epoch = this.video.currentTime;
    this.report('Preparing compatible audio...');
    try {
      await new Promise((resolve, reject) => {
        const cleanup = () => { clearTimeout(timer); this.audio.removeEventListener('canplay', ready); this.audio.removeEventListener('error', fail); if (this.pendingCancel === cancel) this.pendingCancel = null; };
        const cancel = () => { cleanup(); reject(Error('Audio preparation cancelled')); }; this.pendingCancel = cancel;
        const ready = () => { cleanup(); resolve(); }, fail = () => { cleanup(); reject(Error('Audio decoding failed')); };
        const timer = setTimeout(() => { cleanup(); reject(Error('Audio decoding timed out')); }, 25000);
        this.audio.addEventListener('canplay', ready, {once: true}); this.audio.addEventListener('error', fail, {once: true});
        this.audio.src = this.server.url(this.track.index, this.epoch); this.audio.load();
      });
      if (this.disposed || seq !== this.sequence) return;
      this.audio.volume = this.video.volume; this.audio.muted = this.video.muted; this.audio.playbackRate = this.video.playbackRate;
      this.ready = true; this.freezing = false; this.startedAt = performance.now(); this.phase = this.wantPlaying ? 'playing' : 'paused';
      if (this.wantPlaying) await this.resume(); else this.report('');
    } catch (error) { if (!this.disposed && seq === this.sequence) this.fail(error); }
  }
  correct() {
    if (this.disposed || !this.ready || this.freezing || !this.wantPlaying || this.video.seeking) return;
    if (this.phase === 'buffering') {
      if (performance.now() - this.bufferingAt >= 15000 && headroom(this.video) >= 5) this.restart('buffer');
      return;
    }
    // A paused/waiting audio clock is not evidence of drift. Let data arrive first.
    if (this.video.paused || this.videoWaiting || this.audio.paused || this.video.readyState < 3 || this.audio.readyState < 3 || performance.now() - this.startedAt < 3000) return;
    const drift = this.epoch + this.audio.currentTime - this.video.currentTime;
    if (Math.abs(drift) > 1.5) { this.restart('sync'); return; }
    const desired = this.video.currentTime - this.epoch;
    if (Math.abs(drift) > 0.25 && performance.now() - (this.correctedAt || 0) > 3000) {
      for (let n = 0; n < this.audio.buffered.length; n++) if (desired >= this.audio.buffered.start(n) && desired < this.audio.buffered.end(n) - 0.25) {
        try { this.audio.currentTime = desired; this.correctedAt = performance.now(); } catch (_) { this.restart('sync'); } break;
      }
    }
    this.audio.playbackRate = this.video.playbackRate;
  }
  diagnostic() {
    return {audioPath: 'compatibility', audioHelperState: this.phase, audioReadyState: this.audio.readyState || 0,
      audioBufferedMs: Math.round(headroom(this.audio) * 1000), audioDriftMs: Math.round(Math.abs(this.epoch + this.audio.currentTime - this.video.currentTime) * 1000),
      nativeAudioTracks: this.video.audioTracks?.length || 0, audioTracks: this.track ? 1 : 0,
      audioDecodedBytes: this.audio.webkitAudioDecodedByteCount || 0, audioState: this.audio.muted || this.audio.volume === 0 ? 'muted' : 'unmuted'};
  }
  dispose() {
    this.disposed = true; ++this.sequence; this.pendingCancel?.(); clearInterval(this.timer); clearTimeout(this.seekTimer);
    for (const [name, fn] of Object.entries(this.handlers)) this.video.removeEventListener(name, fn);
    for (const [name, fn] of Object.entries(this.audioHandlers)) this.audio.removeEventListener(name, fn);
    if (this.video.streamnestCompatibilityAudio === this) delete this.video.streamnestCompatibilityAudio;
    this.audio.pause(); this.audio.removeAttribute('src'); this.audio.load(); this.server?.close();
  }
}
module.exports = {DecodedAudio};
