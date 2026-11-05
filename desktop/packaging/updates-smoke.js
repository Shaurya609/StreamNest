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
        try{
          await App.Updater.check();
          const text=document.body.innerText;
          result.updateCheckPassed=text.includes('StreamNest is up to date.');
          const net=require('https'),original=net.get,bytes=Buffer.from('native runtime updater fixture'),hash=require('crypto').createHash('sha256').update(bytes).digest('hex');
          const artifact={url:'https://github.com/Shaurya609/StreamNest/releases/download/test/update-fixture.exe',bytes:bytes.length,sha256:hash};
          net.get=function(address,options,callback){
            if(address!==artifact.url)return original.apply(this,arguments);
            const req=new (require('events'))();req.setTimeout=function(){};req.destroy=function(error){req.emit('error',error);};
            Promise.resolve().then(function(){const response=require('stream').Readable.from([bytes]);response.statusCode=200;callback(response);});return req;
          };
          try{
            const file=await require('./update-download').download(artifact,path.join(root,'download-probe'));
            result.nativeDownloadVerified=fs.readFileSync(file).equals(bytes);
          }finally{net.get=original;}

          finish(result.updateCheckPassed&&result.nativeDownloadVerified&&!result.errors.length);
        }catch(error){result.errors.push(String(error.stack||error));finish(false);}
      },1000);
    } catch(error) { result.errors.push(String(error.stack||error)); finish(false); }
  },250);
  setTimeout(()=>finish(false),90000);
})();
