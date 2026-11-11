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
    element.id='sprint1-video';element.className='video-js vjs-popcorn-skin';
    element.style.cssText='position:fixed;top:50px;left:20px;z-index:999999;width:960px;height:540px';element.muted=true;
    document.body.appendChild(element);
    player=videojs(element,{controls:true,width:960,height:540});
    player.src({type:'video/mp4',src:require('url').pathToFileURL(process.env.STREAMNEST_SMOKE_VIDEO).href});
    player.play();
    for(let i=0;i<100&&!(player.duration()>0);i++)await wait(100);
    assert(player.duration()>20,'Fixture did not load');
    const header=document.createElement('div');header.className='player-header-background vjs-control-bar';player.el().appendChild(header);
    const attrs={type:'episode',season:1,episode:1,auto_play_data:[{season:1,episode:1},{season:1,episode:2}]};
    view={player,video:player,model:{get:()=>({attributes:attrs,get:key=>attrs[key]})},isDestroyed:()=>false,_streamnestSkipRequested:true};
    Settings.streamnestAutoSkip=true;
    App.View.Player.prototype.setupEpisodeControls.call(view);
    view._streamnestSegments=[{type:'intro',start:3,end:18}];
    player.currentTime(4);
    await wait(1200);
    assert(view._streamnestSkip.text().startsWith('Skip Intro'),'Skip label missing');
    const icon=getComputedStyle(view._streamnestSkip[0],'::before');
    assert(icon.content!=='none'&&icon.fontFamily.includes('Font Awesome 6 Free')&&icon.fontWeight==='900','Skip icon missing');
    result.iconAndText=true;
    const next=player.el().querySelector('[aria-label="Next episode"]');
    const previous=player.el().querySelector('[aria-label="Previous episode"]');
    assert(next&&previous,'Episode navigation missing');
    assert(player.el().querySelectorAll('[aria-label="Next episode"]').length===1,'Duplicate next control');
    assert(!header.querySelector('.streamnest-episode-nav'),'Episode controls leaked into title header');
    const bar=player.el().querySelector('.vjs-control-bar:not(.player-header-background)'),pause=bar.querySelector('.vjs-play-control');
    assert(next.parentElement===bar&&previous.parentElement===bar,'Episode controls not in playback toolbar');
    assert(pause.nextElementSibling===previous&&previous.nextElementSibling===next,'Episode controls not beside Pause');
    assert(!next.textContent.trim()&&!previous.textContent.trim(),'Navigation should use icons and hover labels');
    for(const size of [[960,540],[640,360],[2560,1440]]){
      player.dimensions(size[0],size[1]);player.el().style.width=size[0]+'px';player.el().style.height=size[1]+'px';
      player.el().classList.toggle('vjs-fullscreen',size[0]>2000);player.userActive(true);await wait(100);
      const n=next.getBoundingClientRect(),p=pause.getBoundingClientRect(),seek=bar.querySelector('.vjs-progress-control').getBoundingClientRect();
      assert(Math.abs((n.top+n.height/2)-(p.top+p.height/2))<3,'Navigation baseline differs from Pause');
      assert(n.right<seek.left&&n.width<=32&&n.height<=32,'Navigation overlaps seek bar or has oversized styling');
      assert(getComputedStyle(next).backgroundColor==='rgba(0, 0, 0, 0)','Navigation has a floating button background');
    }
    player.el().classList.remove('vjs-fullscreen');player.dimensions(960,540);player.el().style.width='960px';player.el().style.height='540px';
    // Native hidden Chromium suspends CSS transitions. Check the actual inactive target
    // without claiming a foreground animation-timing measurement.
    bar.style.setProperty('transition','none','important');
    player.userActive(false);await wait(1200);result.hiddenControls={visibility:getComputedStyle(bar).visibility,opacity:getComputedStyle(bar).opacity,classes:player.el().className,paused:player.paused(),active:player.userActive(),inline:bar.getAttribute("style"),rules:[]};for(const sheet of document.styleSheets)try{for(const rule of sheet.cssRules||[])if(rule.selectorText&&bar.matches(rule.selectorText)&&(rule.style.visibility||rule.style.opacity))result.hiddenControls.rules.push({selector:rule.selectorText,visibility:rule.style.visibility,opacity:rule.style.opacity});}catch(e){}assert(getComputedStyle(bar).visibility==='hidden','Controls do not hide together');player.userActive(true);
    result.navigation={singleToolbar:true,iconsOnly:true,besidePause:true,noSeekOverlap:true,windowedAndFullscreen:true,autoHide:true};
    Settings.streamnestAutoSkip=false;
    await wait(5000);
    assert(player.currentTime()<17,'Settings disable failed');
    result.settingsDisabledSkip=true;
    player.pause();player.currentTime(4);await wait(500);
    view._skipKey='';Settings.streamnestAutoSkip=true;player.play();
    await wait(6500);
    assert(player.currentTime()>=18,'Automatic skip failed');
    result.autoSkipPosition=player.currentTime();
    player.pause();const creditsStart=player.duration()*.55,creditsEnd=Math.min(player.duration()-5,creditsStart+15);
    view._streamnestSegments=[{type:'outro',start:creditsStart,end:creditsEnd}];view._skipKey='';player.currentTime(creditsStart+.2);player.play();await wait(1000);
    assert(view._streamnestSkip.text().startsWith('Skip Credits'),'Credits label missing');await wait(5200);
    assert(player.currentTime()>=creditsEnd-.5&&player.currentTime()<player.duration()-1,'Credits auto-skip did not preserve following content');result.autoCreditsPosition=player.currentTime();
    const video=player.el().querySelector('video');
    assert(video.videoWidth>0,'Decoded video missing');
    result.decodedVideo=true;
    finish(true);
   }catch(error){result.errors.push(String(error.stack||error));finish(false);}
  })();
 },250);
 setTimeout(()=>{result.errors.push('Probe timeout');finish(false);},60000);
})();
