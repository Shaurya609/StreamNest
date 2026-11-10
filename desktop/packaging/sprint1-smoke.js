(function(){
 'use strict';
 const fs=require('fs'),path=require('path'),root=process.env.STREAMNEST_SMOKE_ROOT;
 if(!root)return;
 const result={version:nw.App.manifest.version,errors:[]};
 let done=false,view,player;
 function finish(ok){
  if(done)return;done=true;
  if(view&&view._StreamNestTimer)clearInterval(view._StreamNestTimer);
  if(player)player.dispose();
  result.ok=ok;
  fs.writeFileSync(path.join(root,'result.json'),JSON.stringify(result,null,2));nw.App.quit();
 }
 const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
 const assert=(value,message)=>{if(!value)throw Error(message);};
 const timer=setInterval(()=>{
  if(!window.App||!App.View||!App.View.Player||!window.Settings)return;
  clearInterval(timer);
  (async()=>{
   try{
    const element=document.createElement('video');
    element.id='sprint1-video';element.className='video-js';
    element.style.cssText='position:fixed;top:50px;left:20px;z-index:999999;width:960px;height:540px';element.muted=true;
    document.body.appendChild(element);
    player=videojs(element,{controls:true});
    player.src({type:'video/mp4',src:require('url').pathToFileURL(process.env.STREAMNEST_SMOKE_VIDEO).href});
    player.play();
    for(let i=0;i<100&&!(player.duration()>0);i++)await wait(100);
    assert(player.duration()>20,'Fixture did not load');
    const attrs={type:'episode',season:1,episode:1,auto_play_data:[{season:1,episode:1},{season:1,episode:2}]};
    view={player,video:player,model:{get:()=>({attributes:attrs,get:key=>attrs[key]})},isDestroyed:()=>false,_streamnestSkipRequested:true};
    Settings.streamnestAutoSkip=true;
    App.View.Player.prototype.setupEpisodeControls.call(view);
    view._streamnestSegments=[{type:'intro',start:3,end:18}];
    player.currentTime(4);
    await wait(1200);
    assert(view._streamnestSkip.text().startsWith('Skip Intro'),'Skip label missing');
    const icon=getComputedStyle(view._streamnestSkip[0],'::before');
    assert(icon.content!=='none'&&icon.fontFamily.includes('FontAwesome'),'Skip icon missing');
    result.iconAndText=true;
    const next=player.el().querySelector('[aria-label="Next episode"]');
    const previous=player.el().querySelector('[aria-label="Previous episode"]');
    assert(next&&previous,'Episode navigation missing');
    assert(next.getBoundingClientRect().width>=120&&next.getBoundingClientRect().height>=40,'Episode control too small');
    const box=player.el().getBoundingClientRect(),buttonBox=next.getBoundingClientRect();
    assert(buttonBox.bottom<=box.bottom+1&&buttonBox.right<=box.right+1,'Episode control clipped by player');
    result.navigationLabels=[previous.textContent.trim(),next.textContent.trim()];
    Settings.streamnestAutoSkip=false;
    await wait(5000);
    assert(player.currentTime()<17,'Settings disable failed');
    result.settingsDisabledSkip=true;
    player.pause();player.currentTime(4);await wait(500);
    view._skipKey='';Settings.streamnestAutoSkip=true;player.play();
    await wait(6500);
    assert(player.currentTime()>=18,'Automatic skip failed');
    result.autoSkipPosition=player.currentTime();
    const video=player.el().querySelector('video');
    assert(video.videoWidth>0,'Decoded video missing');
    result.decodedVideo=true;
    finish(true);
   }catch(error){result.errors.push(String(error.stack||error));finish(false);}
  })();
 },250);
 setTimeout(()=>{result.errors.push('Probe timeout');finish(false);},60000);
})();
