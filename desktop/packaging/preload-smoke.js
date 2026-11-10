(function(){
 const fs=require('fs'),path=require('path'),root=process.env.STREAMNEST_SMOKE_ROOT;
 if(!root)return;let finished=false;const result={errors:[],version:nw.App.manifest.version};
 const delay=ms=>new Promise(r=>setTimeout(r,ms));
 async function wait(check,message){for(let i=0;i<900;i++){if(check())return;await delay(100);}throw Error(message);}
 function assert(ok,message){if(!ok)throw Error(message);}
 function view(){return App.PlayerView;}
 function decoded(){const v=view(),e=v&&v.player&&v.player.el().querySelector('video');return e&&e.videoWidth>0&&e.webkitDecodedFrameCount>0;}
 async function finish(ok){if(finished)return;finished=true;try{await require('./butter-provider/streamnest-preload').cancel();if(view()&&!view().isDestroyed())view().closePlayer();}catch(e){result.errors.push(e.message);}result.ok=ok&&!result.errors.length;fs.writeFileSync(path.join(root,'result.json'),JSON.stringify(result,null,2));nw.App.quit();}
 window.addEventListener('error',e=>result.errors.push(e.message));
 const ready=setInterval(()=>{if(!window.App||!App.settings||!App.WebTorrent||!App.Trakt||!window.Settings)return;clearInterval(ready);
 (async()=>{try{
  const E=require('./butter-provider/streamnest-episodes'),M=require('./butter-provider/streamnest-preload'),base=process.env.STREAMNEST_PRELOAD_BASE||'http://127.0.0.1:18767';
  E.alternatives=async()=>[];Settings.streamnestPreloadNext=false;Settings.streamnestSkipButtons=false;Settings.playNextEpisodeAuto=false;
  App.vent.trigger('settings:show');await delay(500);
  const checkbox=document.getElementById('streamnestPreloadNext');assert(checkbox&&!checkbox.checked,'Preload checkbox missing or enabled by default');
  checkbox.click();await wait(()=>Settings.streamnestPreloadNext===true,'Preload checkbox did not save enabled setting');
  checkbox.click();await wait(()=>Settings.streamnestPreloadNext===false,'Preload checkbox did not save disabled setting');
  App.vent.trigger('settings:close');result.settingsCheckbox=true;
  const rows=[1,2,3].map(n=>({season:1,episode:n,title:'Fixture '+n,streamnestSources:[{url:base+'/episode'+n+'.torrent',quality:'720p',seed:10,fileIndex:0,file:'S01E0'+n+'.mp4'}]}));
  const attrs={...rows[0],type:'episode',imdb_id:'tt90000001',torrent:base+'/episode1.torrent',streamnestQuality:'Auto',fileIndex:0,file_name:'S01E01.mp4',auto_play_data:rows,auto_play:false,defaultSubtitle:'none'};
  App.vent.trigger('stream:start',new Backbone.Model(attrs));
  await wait(()=>decoded()&&view().model.get('torrentModel').get('torrent').files[0].progress>=1,'First fixture did not download and render');
  assert(!M.state,'Preloading not opt-in');result.firstDecoded=true;const first=view().model.get('torrentModel').get('torrent').path;
  Settings.streamnestPreloadNext=true;await wait(()=>M.state&&M.state.file&&M.state.file.progress>=1,'Next episode did not preload');
  const next=M.state.dir;assert(fs.existsSync(first),'Preload deleted active cache');result.preloadDownloaded=true;
  view().navigateEpisode(1);
  await wait(()=>view()&&view().model.get('torrentModel').get('episode')===2&&decoded(),'Second episode did not render');
  await wait(()=>!fs.existsSync(first),'Previous cache not purged after playback');
  assert(view().model.get('torrentModel').get('torrent').path===next,'Preload cache not reused');result.reusedCache=true;result.previousPurgedAfterPlayback=true;
  await wait(()=>M.state&&M.state.file,'Third episode did not start preload');const third=M.state.dir;
  Settings.streamnestPreloadNext=false;await M.cancel();assert(!fs.existsSync(third),'Cancelled preload cache retained');assert(fs.existsSync(next)&&decoded(),'Cancellation damaged active playback');result.cancellationPreservedActive=true;
  await finish(true);
 }catch(e){result.errors.push(e.stack||String(e));await finish(false);}})();
 },250);setTimeout(()=>finish(false),280000);
})();
