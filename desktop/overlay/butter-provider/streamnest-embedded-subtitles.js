"use strict";
const Decoder = require('./streamnest-media-decoder');
function cues(vtt) {
  const time = raw => raw.split(':').map(Number).reduce((total, n) => total * 60 + n, 0);
  return vtt.replace(/\r/g, '').split(/\n\n+/).flatMap(block => {
    const lines = block.split('\n'), at = lines.findIndex(line => /^((?:\d+:)?\d{2}:\d{2}\.\d{3}) --> /.test(line));
    if (at < 0) return [];
    const match = lines[at].match(/^((?:\d+:)?\d{2}:\d{2}\.\d{3}) --> ((?:\d+:)?\d{2}:\d{2}\.\d{3})/);
    if (!match) return [];
    const start = time(match[1]), end = time(match[2]);
    return end > start ? [{start, end, text: lines.slice(at + 1).join('\n')}] : [];
  });
}
class EmbeddedSubtitles {
  constructor(player, video, win, report = () => {}) {
    this.player = player; this.video = video; this.win = win; this.report = report; this.sequence = 0; this.disposed = false; this.tracks = new Map();
    const display = video.parentElement?.querySelector('.vjs-text-track-display');
    if (display && win.document) {
      this.overlay = win.document.createElement('div'); this.overlay.className = 'vjs-subtitles vjs-text-track streamnest-embedded-captions';
      this.overlay.style.cssText = 'display:block;white-space:pre-line;pointer-events:none';
      const settings=win.Settings||{};display.style.fontSize=String(settings.subtitle_size||'38px');
      if(settings.subtitle_color)this.overlay.style.color=settings.subtitle_color;if(settings.subtitle_font)this.overlay.style.fontFamily=settings.subtitle_font;
      display.append(this.overlay);
    }
    this.render = () => {
      if (!this.overlay) return;
      this.overlay.replaceChildren();
      for (const track of this.tracks.values()) if (track.mode !== 'disabled') for (const cue of Array.from(track.activeCues || [])) {
        const line = win.document.createElement('span'); line.className = 'vjs-tt-cue';
        if (typeof cue.getCueAsHTML === 'function') line.append(cue.getCueAsHTML()); else line.textContent = cue.text;
        this.overlay.append(line);
      }
    };
    this.tick = () => { if (this.selected && !this.loading && Date.now() - (this.lastAttempt || 0) > 15000 && (video.currentTime < this.start || video.currentTime > this.start + 110)) this.refresh(); };
    video.addEventListener('timeupdate', this.tick); video.addEventListener('seeked', this.tick);
  }
  async select(track) { this.selected = track; this.start = -1000; await this.refresh(); }
  async refresh() {
    if (this.disposed || !this.selected) return;
    this.abort?.abort(); this.abort = new AbortController(); const seq = ++this.sequence;
    this.loading = true; this.lastAttempt = Date.now(); const at = this.video.currentTime, selected = this.selected;
    try {
      const vtt = await Decoder.subtitles(this.video.currentSrc || this.video.src, selected.index, at, {signal: this.abort.signal});
      if (this.disposed || seq !== this.sequence) return;
      // Video.js 4 uses its own legacy track API. Feed native WebVTT cues directly
      // to Chromium; never assign properties over Video.js mode()/language().
      let track = this.tracks.get(selected.index);
      if (!track) { track = this.video.addTextTrack('subtitles', selected.label || selected.language || 'Embedded subtitles', selected.language); track.streamnestDecoded=true; track.addEventListener?.('cuechange',this.render); this.tracks.set(selected.index, track); }
      track.mode = 'hidden';
      for (const cue of Array.from(track.cues || [])) track.removeCue(cue);
      for (const cue of cues(vtt)) track.addCue(new this.win.VTTCue(cue.start, cue.end, cue.text));
      this.player.showTextTrack?.('', 'subtitles'); this.player.showTextTrack?.('', 'captions');
      for (const t of Array.from(this.video.textTracks || [])) t.mode = t === track ? (this.overlay?'hidden':'showing') : 'disabled';
      this.render();
      this.start = Math.max(0, at - 15); this.loading = false; this.report('');
    } catch (error) { if (!this.disposed && seq === this.sequence) { this.loading = false; this.report('Embedded subtitles could not load. Online and local subtitles remain available.'); } }
  }
  off() { ++this.sequence; this.abort?.abort(); this.selected = null; this.loading = false; this.overlay?.replaceChildren(); for (const track of this.tracks.values()) { track.mode = 'disabled'; for (const cue of Array.from(track.cues || [])) track.removeCue(cue); } }
  dispose() { this.disposed = true; this.off(); this.video.removeEventListener('timeupdate', this.tick); this.video.removeEventListener('seeked', this.tick); for(const track of this.tracks.values())track.removeEventListener?.('cuechange',this.render); this.overlay?.remove(); }
}
module.exports = {cues, EmbeddedSubtitles};
