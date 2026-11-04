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
      // Wait for the actual browse view to render before checking an independent video element.
      setTimeout(function() {
        result.documentText = document.body.innerText.slice(0,1800);
        result.renderedCards = document.querySelectorAll('.item, .movie-item, .item-container, .tt-item').length;
        const fixture=process.env.STREAMNEST_SMOKE_VIDEO;
        if (!fixture) { finish(result.profileIsolated && result.cachesIsolated && result.navigation && !result.errors.length); return; }
        const video=document.createElement('video');
        video.muted=true;video.style.cssText='position:absolute;width:64px;height:36px;left:0;top:0';
        document.body.appendChild(video);
        video.onloadeddata=function(){
          try {
            video.currentTime=2;
            const playback=video.play();if(playback&&playback.catch)playback.catch(e=>{result.errors.push(String(e));finish(false);});
          }catch(error){result.errors.push(String(error));finish(false);}
        };
        video.ontimeupdate=function(){if(video.currentTime>2.5){result.decodedVideo=video.videoWidth>0&&video.videoHeight>0;result.videoDimensions=[video.videoWidth,video.videoHeight];video.pause();finish(result.profileIsolated&&result.cachesIsolated&&result.navigation&&result.decodedVideo&&!result.errors.length);}};
        video.onerror=function(){result.errors.push('Fixture video error '+(video.error&&video.error.code));finish(false);};
        video.src='file:///'+fixture.replace(/\\/g,'/');
      },10000);
    } catch(error) { result.errors.push(String(error.stack||error)); finish(false); }
  },250);
  setTimeout(()=>finish(false),90000);
})();
