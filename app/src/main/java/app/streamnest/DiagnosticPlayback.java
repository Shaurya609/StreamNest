package app.streamnest;
import org.json.*;import org.videolan.libvlc.*;import org.videolan.libvlc.interfaces.IMedia;
/** Called at most every 30 seconds from the live player's existing poll. No media positions are recorded. */
final class DiagnosticPlayback {
 static void sample(android.content.Context c,MediaPlayer player,TorrentEngine engine,boolean software,float buffering){
  if(!Diagnostics.enabled(c))return;IMedia media=null;
  JSONObject d=new JSONObject();try{d=new JSONObject().put("decoder",software?"software":"auto").put("bufferPercent",Math.max(0,Math.min(100,Math.round(buffering)))).put("audioTracks",Math.max(0,player.getAudioTracksCount())).put("volumePercent",Math.max(0,Math.min(200,player.getVolume()))).put("audioState",player.getAudioTrack()<0?(player.isPlaying()?"disabled":"unknown"):player.getVolume()==0?"muted":"unmuted");
   if(engine!=null&&!engine.closed)d.put("peers",engine.diagnosticPeers).put("downloadKbps",engine.diagnosticKbps).put("percent",engine.diagnosticPercent).put("nativeCode",Math.abs((long)engine.diagnosticError));
   IMedia.VideoTrack video=player.getCurrentVideoTrack();if(video!=null)d.put("width",video.width).put("height",video.height).put("videoCodec",DiagnosticData.codec(video.codec,false)).put("bitrateKbps",Math.max(0,video.bitrate/1000));
   media=player.getMedia();if(media!=null){IMedia.Stats stats=media.getStats();if(stats!=null)d.put("totalFrames",Math.max(0,stats.displayedPictures)).put("droppedFrames",Math.max(0,stats.lostPictures)).put("audioDecodedBuffers",Math.max(0,stats.decodedAudio)).put("audioPlayedBuffers",Math.max(0,stats.playedAbuffers)).put("audioLostBuffers",Math.max(0,stats.lostAbuffers));for(int i=0;i<media.getTrackCount();i++){IMedia.Track t=media.getTrack(i);if(t instanceof IMedia.AudioTrack&&t.id==player.getAudioTrack()){IMedia.AudioTrack a=(IMedia.AudioTrack)t;d.put("audioCodec",DiagnosticData.codec(a.codec,true)).put("audioChannels",Math.max(0,a.channels)).put("sampleRate",Math.max(0,a.rate));break;}}}
   android.app.ActivityManager am=(android.app.ActivityManager)c.getSystemService(android.content.Context.ACTIVITY_SERVICE);android.app.ActivityManager.MemoryInfo m=new android.app.ActivityManager.MemoryInfo();am.getMemoryInfo(m);d.put("freeRamMb",m.availMem/1048576).put("processMemoryMb",android.os.Debug.getPss()/1024);
   String selected=CacheLocation.current(c).getAbsolutePath();d.put("storage",selected.startsWith(c.getFilesDir().getAbsolutePath()+java.io.File.separator)?"internal":selected.startsWith("/storage/")?"external":"custom");
   d.put("playState",buffering<100?"buffering":player.isPlaying()?"playing":"paused");
   android.media.AudioManager audio=(android.media.AudioManager)c.getSystemService(android.content.Context.AUDIO_SERVICE);int max=audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);if(max>0)d.put("systemVolumePercent",Math.max(0,Math.min(100,Math.round(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)*100f/max))));d.put("systemAudioState",(audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC)||audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)==0)?"muted":"unmuted");
  }catch(Exception error){Diagnostics.failure("app.error","player","playback",error);}finally{if(media!=null)media.release();}
  Diagnostics.event("player.sample",d);
 }
}
