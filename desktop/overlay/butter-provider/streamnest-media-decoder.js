"use strict";
// This helper only decodes the selected file served by StreamNest's local torrent server.
const cp = require('child_process'), fs = require('fs'), path = require('path'), http = require('http'), crypto = require('crypto');
function localSource(src) {
  const u = new URL(src);
  if (u.protocol !== 'http:' || !['127.0.0.1', 'localhost', '[::1]'].includes(u.hostname) || !u.port || u.username || u.password) throw Error('Decoder requires the local StreamNest media stream');
  return u.href;
}
function binaries(root = path.join(path.dirname(process.execPath), 'media-decoder')) {
  const ffmpeg = path.join(root, 'ffmpeg.exe'), ffprobe = path.join(root, 'ffprobe.exe');
  return fs.existsSync(ffmpeg) && fs.existsSync(ffprobe) ? {ffmpeg, ffprobe} : null;
}
function index(n) { if (!Number.isInteger(n) || n < 0 || n > 4096) throw Error('Invalid media track'); return n; }
function position(n) { if (!Number.isFinite(n) || n < 0) throw Error('Invalid media position'); return n; }
function input(src) { return ['-format_whitelist', 'matroska,webm,mov,mp4,m4a,3gp,3g2,mj2,avi,mpegts,mpeg', '-protocol_whitelist', 'http,tcp', '-rw_timeout', '15000000', '-analyzeduration', '3000000', '-probesize', '3000000', '-i', localSource(src)]; }
function collect(exe, args, {timeout = 25000, limit = 2097152, signal, spawn = cp.spawn} = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(exe, args, {windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']});
    let output = [], size = 0, done = false;
    const finish = (error, value) => { if (done) return; done = true; clearTimeout(timer); signal?.removeEventListener('abort', abort); child.kill(); error ? reject(error) : resolve(value); };
    const abort = () => finish(Error('Media decoding cancelled'));
    const timer = setTimeout(() => finish(Error('Media decoder timed out')), timeout);
    child.stdout.on('data', data => { size += data.length; if (size > limit) finish(Error('Media decoder response too large')); else output.push(data); });
    child.stderr.resume();
    child.on('error', error => finish(error));
    child.on('close', code => finish(code ? Error('Media decoding failed') : null, Buffer.concat(output).toString('utf8')));
    signal?.addEventListener('abort', abort, {once: true});
    if (signal?.aborted) abort();
  });
}
async function probe(src, options = {}) {
  const tools = options.tools || binaries(); if (!tools) throw Error('Media decoder is unavailable');
  const raw = await collect(tools.ffprobe, ['-v', 'error', ...input(src), '-show_streams', '-show_format', '-of', 'json'], options);
  const data = JSON.parse(raw);
  return {duration: Number(data.format?.duration) || 0, tracks: (data.streams || []).filter(s => ['audio', 'subtitle'].includes(s.codec_type)).map(s => ({index: index(s.index), type: s.codec_type, codec: s.codec_name, language: s.tags?.language || '', label: s.tags?.title || s.tags?.language || '', default: !!s.disposition?.default, forced: !!s.disposition?.forced}))};
}
function audioArgs(src, track, at) {
  return ['-hide_banner', '-loglevel', 'error', '-nostdin', '-readrate', '1', '-readrate_initial_burst', '5', '-ss', String(position(at)), ...input(src), '-map', '0:' + index(track), '-vn', '-sn', '-threads', '1', '-c:a', 'libmp3lame', '-ac', '2', '-ar', '48000', '-b:a', '192k', '-f', 'mp3', 'pipe:1'];
}
async function audioServer(src, options = {}) {
  src = localSource(src); const tools = options.tools || binaries(); if (!tools) throw Error('Media decoder is unavailable');
  const token = crypto.randomBytes(24).toString('hex'), children = new Set(), sockets = new Set(); let disposed = false;
  const server = http.createServer((request, response) => {
    let url, track, at;
    try { url = new URL(request.url, 'http://127.0.0.1'); if (request.method !== 'GET' || url.pathname !== '/' + token) throw Error(); track = index(Number(url.searchParams.get('track'))); at = position(Number(url.searchParams.get('at'))); } catch (_) { response.writeHead(404).end(); return; }
    if (disposed) { response.writeHead(410).end(); return; }
    const child = cp.spawn(tools.ffmpeg, audioArgs(src, track, at), {windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']}); children.add(child);
    child.stderr.resume(); response.writeHead(200, {'Content-Type': 'audio/mpeg', 'Cache-Control': 'no-store', 'Connection': 'close'});
    child.stdout.pipe(response); const stop = () => { child.kill(); children.delete(child); };
    response.on('close', stop); child.on('error', () => response.destroy()); child.on('close', () => { children.delete(child); response.end(); });
  });
  server.on('connection', socket => { sockets.add(socket); socket.on('close', () => sockets.delete(socket)); });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  return {url(track, at) { return 'http://127.0.0.1:' + server.address().port + '/' + token + '?track=' + index(track) + '&at=' + position(at); }, close() { disposed = true; for (const child of children) child.kill(); children.clear(); for (const socket of sockets) socket.destroy(); server.close(); }};
}
function subtitleArgs(src, track, at) {
  const start = Math.max(0, position(at) - 15);
  // Keep source PTS, and bound the absolute output clock rather than shifting cues.
  return ['-hide_banner', '-loglevel', 'error', '-nostdin', '-copyts', '-ss', String(start), ...input(src), '-map', '0:' + index(track), '-vn', '-an', '-to', String(start + 150), '-c:s', 'webvtt', '-f', 'webvtt', 'pipe:1'];
}
async function subtitles(src, track, at, options = {}) {
  const tools = options.tools || binaries(); if (!tools) throw Error('Media decoder is unavailable');
  return collect(tools.ffmpeg, subtitleArgs(src, track, at), options);
}
module.exports = {collect, localSource, binaries, probe, audioArgs, audioServer, subtitleArgs, subtitles};
