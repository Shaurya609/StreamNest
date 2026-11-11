(function(){
 'use strict';const fs=require('fs'),path=require('path'),root=process.env.STREAMNEST_SMOKE_ROOT;if(!root)return;
 const result={errors:[]};let done=false;function finish(ok){if(done)return;done=true;result.ok=ok;fs.writeFileSync(path.join(root,'result.json'),JSON.stringify(result,null,2));nw.App.quit();}
 const wait=ms=>new Promise(r=>setTimeout(r,ms)),assert=(v,m)=>{if(!v)throw Error(m);};
 window.addEventListener('error',e=>result.errors.push(e.message));window.addEventListener('unhandledrejection',e=>result.errors.push(String(e.reason)));
 (async()=>{try{
  for(let n=0;n<200&&!document.querySelector('#filterbar-home');n++)await wait(250);
  assert(document.querySelector('#filterbar-home'),'Home navigation missing');
  App.vent.trigger('home:list');await wait(1000);
  const home=document.querySelector('.sn-home');result.currentview=App.currentview;result.startScreen=Settings.startScreen;result.documentText=document.body.innerText.slice(0,600);assert(home,'Home did not render');assert(home.querySelectorAll('.sn-card').length>20,'Home cards missing');assert(home.getBoundingClientRect().height>200,'Home rail clipped');
  result.rows=[...home.querySelectorAll('h2')].map(e=>e.textContent);assert(result.rows.includes('Anime')&&result.rows.includes('Movies')&&result.rows.includes('Series'),'Mixed catalog rows missing');
  const P=require('./butter-provider/streamnest-progress');P.merge({'episode:tt11280740:1:1':{positionMs:125000,durationMs:3435000,updatedAt:Date.now()}});await wait(3500);
  const resume=[...document.querySelectorAll('.sn-card')].find(e=>e.getAttribute('aria-label').includes('Severance')&&e.querySelector('progress'));
  assert(resume,'Synced Continue Watching card missing');result.resume=resume.textContent;assert(result.resume.includes('S1 · E1'),'Episode caption wrong');resume.focus();assert(document.activeElement===resume,'Card is not keyboard focusable');
  result.posterCount=document.querySelectorAll('.sn-card img[src]').length;assert(result.posterCount>20,'Poster URLs missing');
  // Use the actual public detail provider and existing title dialog, never start a public torrent.
  resume.click();for(let n=0;n<160&&!document.querySelector('#movie-detail .show-detail');n++)await wait(250);
  result.detailText=document.querySelector('#movie-detail').innerText.slice(0,700);assert(result.detailText.includes('Severance'),'Normal series detail did not open');
  App.vent.trigger('show:closeDetail');P.merge({'episode:tt11280740:1:1':{positionMs:0,durationMs:3435000,updatedAt:Date.now()+1,completed:true}});await wait(4500);
  const next=[...document.querySelectorAll('.sn-card')].find(e=>e.getAttribute('aria-label').includes('Severance')&&e.textContent.includes('Up next · S1 · E2'));assert(next,'Completed episode did not offer next episode');result.upNext=next.textContent;next.click();
  for(let n=0;n<100;n++){const selected=document.querySelector('#movie-detail .tab-episode.active');if(selected&&selected.getAttribute('data-season')==='1'&&selected.getAttribute('data-episode')==='2')break;await wait(150);}
  const selected=document.querySelector('#movie-detail .tab-episode.active');assert(selected&&selected.getAttribute('data-episode')==='2','Up next card did not select episode2');result.upNextSelected=true;
  App.vent.trigger('show:closeDetail');App.vent.trigger('movies:list');await wait(500);assert(!document.querySelector('.sn-home'),'Movies navigation did not leave Home');let homeEvents=0;App.vent.on('home:list',()=>homeEvents++);result.homeButtonCount=document.querySelectorAll('#filterbar-home').length;document.querySelector('#filterbar-home').click();await wait(1500);result.returnState={homeEvents,currentview:App.currentview,text:document.body.innerText.slice(0,250),home:!!document.querySelector('.sn-home')};assert(document.querySelector('.sn-home'),'Return Home failed');
  App.vent.trigger('settings:show');await wait(500);const start=document.querySelector('select[name=start_screen]');assert(start&&[...start.options].some(o=>o.value==='Home'),'Home startup setting missing');result.startupSetting=start.value;App.vent.trigger('settings:close');
  finish(!result.errors.length);
 }catch(e){result.errors.push(String(e.stack||e));finish(false);}})();setTimeout(()=>finish(false),105000);
})();
