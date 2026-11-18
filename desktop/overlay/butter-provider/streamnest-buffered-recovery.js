"use strict";
function headroom(media) {
  let seconds = 0;
  for (let i = 0; i < (media.buffered?.length || 0); i++) if (media.buffered.start(i) <= media.currentTime && media.buffered.end(i) >= media.currentTime) seconds = Math.max(seconds, media.buffered.end(i) - media.currentTime);
  return seconds;
}
class BufferedRecovery {
  constructor() { this.reset(); }
  reset() { this.lastPosition = -1; this.lastFrames = -1; this.since = 0; this.reported = false; }
  sample(now, video, eligible) {
    const frames = video.getVideoPlaybackQuality?.()?.totalVideoFrames ?? video.webkitDecodedFrameCount;
    const moving = this.lastPosition < 0 || video.currentTime > this.lastPosition + 0.1 || (Number.isFinite(frames) && frames > this.lastFrames);
    if (!eligible || video.paused || video.seeking || video.ended || !video.videoWidth || !(video.duration > 0) || video.duration - video.currentTime < 5 || headroom(video) < 5 || moving) {
      this.since = now; this.reported = false;
    }
    this.lastPosition = video.currentTime; this.lastFrames = Number.isFinite(frames) ? frames : -1;
    if (eligible && !video.paused && !video.seeking && !video.ended && video.videoWidth && headroom(video) >= 5 && !this.reported && now - this.since >= 15000) { this.reported = true; return true; }
    return false;
  }
}
function sameSource(attrs, position) {
  const torrent = attrs.torrent, url = typeof torrent === 'string' ? torrent : attrs.streamnestSourceUri || torrent?.magnetURI;
  if (!/^magnet:\?|^https?:\/\//i.test(url || '') || !Number.isFinite(position) || position < 0) return null;
  const member = attrs.video_file;
  // Preserve the original index and URI: both participate in the episode cache key.
  const fileIndex = attrs.fileIndex;
  const file = attrs.file_name || member?.name || member?.path || '';
  const name = String(file).replace(/\\/g, '/').split('/').pop();
  const sourceId = torrent?.infoHash || url.match(/urn:btih:([a-z0-9]{32,40})/i)?.[1]?.toLowerCase() || url;
  const key = [sourceId, attrs.imdb_id, attrs.season, attrs.episode, fileIndex, name].join('\n');
  const attempts = attrs.streamnestBufferedRecoveryKey === key ? Number(attrs.streamnestBufferedRecoveries) || 0 : 0;
  if (attempts >= 1) return null;
  return {...attrs, torrent: url, file_name: file, fileIndex, streamnestPrepared: true, streamnestFromBeginning: false, streamnestRecoveryPosition: position, streamnestBufferedRecoveryKey: key, streamnestBufferedRecoveries: attempts + 1};
}
module.exports = {headroom, BufferedRecovery, sameSource};
