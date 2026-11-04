(function() {
  'use strict';
  if (!!!process.env.STREAMNEST_SMOKE_ROOT) return;
  const fs = require('fs'), path = require('path');
  const root = process.env.STREAMNEST_SMOKE_ROOT;
  if (!root) { nw.App.quit(); return; }
  const result = {errors:[], version:nw.App.manifest.version};
  const started = Date.now();
  let finished = false;
  let seedClient, downloadClient, streamServer;
  function finish(ok) {
    if (finished) return;
    finished = true;
    result.ok = ok;
    result.elapsedMs = Date.now()-started;
    fs.writeFileSync(path.join(root, 'result.json'), JSON.stringify(result,null,2));
    try { if(streamServer)streamServer.close(); } catch(error) {}
    try { if(seedClient)seedClient.destroy(); } catch(error) {}
    try { if(downloadClient)downloadClient.destroy(); } catch(error) {}
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
        video.ontimeupdate=function(){if(video.currentTime>2.5){
          result.decodedVideo=video.videoWidth>0&&video.videoHeight>0;result.videoDimensions=[video.videoWidth,video.videoHeight];
          result.audioDecodedBytes=video.webkitAudioDecodedByteCount||0;
          const canvas=document.createElement('canvas');canvas.width=64;canvas.height=36;const context=canvas.getContext('2d');context.drawImage(video,0,0,64,36);
          try{const pixels=context.getImageData(0,0,64,36).data;let bright=0;for(let i=0;i<pixels.length;i+=4)if(pixels[i]+pixels[i+1]+pixels[i+2]>90)bright++;result.visibleFrame=bright>50;}catch(error){result.visibleFrame=null;}
          video.pause();finish(result.profileIsolated&&result.cachesIsolated&&result.navigation&&result.decodedVideo&&result.loopbackTorrentVerified&&!result.errors.length);
        }};
        video.onerror=function(){result.errors.push('Fixture video error '+(video.error&&video.error.code));finish(false);};
        // A private loopback-only torrent verifies the packaged dependency closure;
        // the fixture never enters public DHT/tracker discovery or external peers.
        const WebTorrent=require('webtorrent'),options={dht:false,tracker:false,lsd:false,utp:false};
        seedClient=new WebTorrent(options);downloadClient=new WebTorrent(options);
        for(const client of [seedClient,downloadClient])client.on('error',error=>{result.errors.push(String(error));finish(false);});
        seedClient.seed(fixture,{announce:[],private:true},function(seed){
          const torrent=downloadClient.add(seed.torrentFile,{path:path.join(root,'LoopbackCache')});
          torrent.on('error',error=>{result.errors.push(String(error));finish(false);});
          torrent.on('ready',()=>torrent.addPeer('127.0.0.1:'+seedClient.torrentPort));
          torrent.on('done',function(){
            torrent.files[0].getBuffer(function(error,bytes){
              if(error){result.errors.push(String(error));finish(false);return;}
              const crypto=require('crypto'),hash=b=>crypto.createHash('sha256').update(b).digest('hex');
              result.loopbackTorrentVerified=hash(bytes)===hash(fs.readFileSync(fixture));result.loopbackTorrentBytes=bytes.length;
              streamServer=torrent.createServer({origin:'*'});streamServer.listen(0,'127.0.0.1',()=>{video.src='http://127.0.0.1:'+streamServer.address().port+'/0';});
            });
          });
        });
      },10000);
    } catch(error) { result.errors.push(String(error.stack||error)); finish(false); }
  },250);
  setTimeout(()=>finish(false),90000);
})();
