(function() {
  'use strict';
  if (!!!process.env.STREAMNEST_SMOKE_ROOT) return;
  const fs = require('fs'), path = require('path');
  const root = process.env.STREAMNEST_SMOKE_ROOT;
  if (!root) { nw.App.quit(); return; }
  const result = {errors:[], version:nw.App.manifest.version};
  const started = Date.now();
  let finished = false;
  function finish(ok) {
    if (finished) return;
    finished = true;
    result.ok = ok;
    result.elapsedMs = Date.now()-started;
    fs.writeFileSync(path.join(root, 'result.json'), JSON.stringify(result,null,2));
    nw.App.quit();
  }
  window.addEventListener('error', event => { result.errors.push(String(event.message)+' at '+event.filename+':'+event.lineno); });
  window.addEventListener('unhandledrejection', event => { result.errors.push(String(event.reason)); });
  const timer = setInterval(function() {
    try {
      if (!window.App || !App.settings || !App.Trakt || !Settings || Settings.version !== nw.App.manifest.version) {
        if (Date.now()-started>60000) finish(false);
        return;
      }
      clearInterval(timer);
      result.project = Settings.projectName;
      result.profile = data_path;
      result.cache = Settings.tmpLocation;
      result.profileIsolated = data_path.indexOf(process.env.LOCALAPPDATA) === 0 && data_path.indexOf('Popcorn') < 0;
      result.cachesIsolated = Settings.tmpLocation.indexOf(process.env.LOCALAPPDATA) === 0;
      result.appManifest = nw.App.manifest.name;
      result.legacyDhtDisabled = !Settings.dht && !Settings.dhtEnable;
      const catalog = require('./butter-provider/streamnest-resilience');
      result.bundles = {};
      for (const kind of ['movies','shows','animes','latest']) {
        const doc=catalog.catalog(kind); result.bundles[kind]=(doc.movies||doc.series||doc.releases).length;
      }
      const playback=require('./butter-provider/streamnest-playback');
      result.navigation = !!playback.neighbour({season:1,episode:1,auto_play_data:[{season:1,episode:1},{season:1,episode:2}]},1);
      setTimeout(async function(){
        try {
          const org=require('./butter-provider/streamnest-org');
          const rows=await org.search({imdbid:'tt12324366',season:1,episode:1});
          const english=rows.en||rows.eng||[];
          if(!english.length)throw Error('No English episode subtitles');
          const bytes=await require('./butter-provider/streamnest-subtitles').read(english[0].url);
          result.subtitleResults=english.length;result.subtitleBytes=bytes.length;
          const file=path.join(root,'episode.srt');fs.writeFileSync(file,bytes);
          const element=document.createElement('video');element.id='subtitle-probe-video';element.className='video-js';
          element.style.cssText='width:640px;height:360px';document.body.appendChild(element);
          const player=videojs(element,{controls:false});
          player.ready(function(){
            const track=player.addTextTrack('subtitles','English','en',{src:english[0].url,srclang:'en'});
            track.on('loaded',function(){
              result.subtitleCues=track.cues_.length;
              if(track.cues_.length<5){result.errors.push('No parsed subtitle cues');finish(false);return;}
              const cue=track.cues_[0];
              // Exercise the real video.js display with the first downloaded cue active.
              const time=player.currentTime;player.currentTime=function(){return cue.startTime+0.05;};
              track.update();
              setTimeout(function(){
                result.subtitleRendered=!!track.el().textContent.trim();
                result.subtitleDisplayAttached=document.body.contains(track.el());
                player.currentTime=time;
                finish(result.subtitleRendered&&result.subtitleDisplayAttached&&!result.errors.length);
              },250);
            });
            track.show();
          });
        }catch(error){result.errors.push(String(error.stack||error));finish(false);}
      },10000);
    } catch(error) { result.errors.push(String(error.stack||error)); finish(false); }
  },250);
  setTimeout(()=>finish(false),90000);
})();
