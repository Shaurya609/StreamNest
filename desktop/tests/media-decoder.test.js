"use strict";
const {test} = require('node:test'), assert = require('node:assert/strict'), {EventEmitter} = require('node:events'), {PassThrough} = require('node:stream');
const D = require('../overlay/butter-provider/streamnest-media-decoder');
const {cues, EmbeddedSubtitles} = require('../overlay/butter-provider/streamnest-embedded-subtitles');
const {DecodedAudio} = require('../overlay/butter-provider/streamnest-decoded-audio');
test('decoder accepts only local selected media and separate validated arguments', () => {
  for (const src of ['https://example.com/movie', 'file:///tmp/movie', 'http://user:pass@localhost:123/movie', 'http://localhost/movie']) assert.throws(() => D.localSource(src));
  const args = D.audioArgs('http://127.0.0.1:1543/movie', 2, 13.5);
  assert.equal(args[args.indexOf('-map') + 1], '0:2');
  assert.equal(args[args.indexOf('-ss') + 1], '13.5');
  assert.ok(args.includes('-format_whitelist')); assert.ok(!args.includes('file'));
  assert.throws(() => D.audioArgs('http://127.0.0.1:123/movie', '0;cmd', 0));
  assert.throws(() => D.audioArgs('http://127.0.0.1:123/movie', 0, Infinity));
});
test('decoder abort and oversized metadata kill the owned helper', async () => {
  function child() { const c = new EventEmitter(); c.stdout = new PassThrough(); c.stderr = new PassThrough(); c.killed = false; c.kill = () => c.killed = true; return c; }
  const a = child(), controller = new AbortController();
  const pending = D.collect('ffprobe.exe', [], {spawn: () => a, signal: controller.signal}); controller.abort();
  await assert.rejects(pending, /cancelled/); assert.equal(a.killed, true);
  const b = child(), oversized = D.collect('ffprobe.exe', [], {spawn: () => b, limit: 3}); b.stdout.write('1234');
  await assert.rejects(oversized, /too large/); assert.equal(b.killed, true);
});
test('subtitle seeks preserve original timestamps and a bounded reading window', () => {
  const args = D.subtitleArgs('http://127.0.0.1:123/movie', 4, 6000);
  assert.ok(args.includes('-copyts')); assert.equal(args[args.indexOf('-ss') + 1], '5985');
  assert.equal(args[args.indexOf('-to') + 1], '6135');
  assert.deepEqual(cues('WEBVTT\n\n01:00:03.250 --> 01:00:05.000\nEnglish dialogue'), [{start: 3603.25, end: 3605, text: 'English dialogue'}]);
});
class Media extends EventTarget {
  constructor() { super(); this.paused = false; this.currentTime = 70; this.volume = 0.7; this.muted = true; this.playbackRate = 1; this.currentSrc = 'http://127.0.0.1:123/movie'; this.textTracks = []; this.buffered = {length: 0}; }
  pause() { this.paused = true; this.dispatchEvent(new Event('pause')); }
  async play() { this.paused = false; this.dispatchEvent(new Event('play')); }
  load() { if (this.src) queueMicrotask(() => this.dispatchEvent(new Event('canplay'))); }
  removeAttribute(name) { this[name] = ''; }
  addTextTrack(kind, label, language) { const t = {kind, label, language, cues: [], addCue(c) { this.cues.push(c); }, removeCue(c) { this.cues.splice(this.cues.indexOf(c), 1); }}; this.textTracks.push(t); return t; }
}
test('embedded cues use native tracks without overwriting legacy Video.js methods', async t => {
  t.mock.method(D, 'subtitles', async () => 'WEBVTT\n\n01:04.000 --> 01:08.000\nEnglish dialogue');
  const video = new Media(), oldTrack = {mode() { return 0; }, language() { return 'en'; }}, player = {textTracks: () => [oldTrack], showTextTrack() {}};
  const bridge = new EmbeddedSubtitles(player, video, {VTTCue: class {constructor(startTime, endTime, text) { Object.assign(this, {startTime, endTime, text}); }}});
  await bridge.select({index: 4, language: 'eng'});
  assert.equal(typeof oldTrack.mode, 'function'); assert.equal(video.textTracks[0].mode, 'showing'); assert.equal(video.textTracks[0].cues[0].text, 'English dialogue');
  bridge.off(); assert.equal(video.textTracks[0].mode, 'disabled'); assert.equal(video.textTracks[0].cues.length, 0); bridge.dispose();
});
test('compatible audio follows native pause, volume, seeking and disposal', async t => {
  const requests = []; let closed = false;
  t.mock.method(D, 'audioServer', async () => ({url(index, at) { requests.push({index, at}); return 'http://127.0.0.1:2/audio'; }, close() { closed = true; }}));
  const video = new Media(), audio = new Media(), bridge = new DecodedAudio(video, {document: {createElement: () => audio}});
  await bridge.start({index: 1}); assert.equal(bridge.ready, true); assert.equal(audio.paused, false); assert.equal(audio.muted, true); assert.equal(requests[0].at, 70);
  video.pause(); assert.equal(audio.paused, true); await video.play(); assert.equal(audio.paused, false);
  video.volume = 0.3; video.dispatchEvent(new Event('volumechange')); assert.equal(audio.volume, 0.3);
  video.currentTime = 300; await bridge.restart(); assert.equal(requests.at(-1).at, 300);
  bridge.dispose(); assert.equal(closed, true); assert.equal(audio.paused, true);
});
