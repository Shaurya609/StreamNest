(function(App){
 'use strict';
 const fs=require('fs'),path=require('path'),child=require('child_process');
 const policy=require('./update-policy'),downloads=require('./update-download');
 let running=false,ready=null,version='',panel=null;
 const root=path.join(require('./butter-provider/streamnest-standalone').profileRoot(),'Updates');
 function playing(){return !!document.querySelector('#video_player, .player video, .loading')||Array.from(document.querySelectorAll('video')).some(v=>!v.paused);}
 function show(text,install){
  if(!panel){panel=document.createElement('div');panel.style.cssText='position:fixed;z-index:99999;right:20px;top:50px;max-width:400px;padding:20px;background:#20232b;color:white;font:15px sans-serif;border:1px solid #526078;border-radius:10px';document.body.appendChild(panel);}
  panel.textContent='';const label=document.createElement('p');label.textContent=text;panel.appendChild(label);
  if(install){const button=document.createElement('button');button.textContent='Install and restart';button.onclick=apply;panel.appendChild(button);}
  const close=document.createElement('button');close.textContent=install?'Later':'Close';close.onclick=()=>{panel.remove();panel=null;};panel.appendChild(close);
 }
 function accept(doc){
  fs.mkdirSync(root,{recursive:true});const file=path.join(root,'trusted-feed-time');let last=0;
  try{last=Number(fs.readFileSync(file,'utf8'));}catch(ignore){}
  if(doc.generatedAt<last)throw Error('Older update feed rejected');
  fs.writeFileSync(file,String(doc.generatedAt));return doc;
 }
 function quote(value){return "'"+String(value).replace(/'/g,"''")+"'";}
 async function apply(){
  if(playing()){show('Finish playback before installing the update.',true);return;}
  if(!ready)return;
  try{
   // Re-read the trusted feed and verify the cached executable immediately before launching it.
   const doc=accept(await downloads.manifest(fs.readFileSync(path.join(path.dirname(process.execPath),'src/app/update-public-key.pem'))));
   if(doc.windows.version!==version||doc.windows.artifact.sha256!==ready.item.sha256)throw Error('Update changed. Check again.');
   const file=await downloads.download(doc.windows.artifact,root);
   const installDir=path.dirname(process.execPath),exe=process.execPath;
   if(/[\r\n]/.test(installDir))throw Error('Unsupported installation path');
   const script=path.join(root,'install-update.ps1');
   const source="$ErrorActionPreference='Stop'\n"+
    'Wait-Process -Id '+process.pid+' -Timeout 90 -ErrorAction SilentlyContinue\n'+
    'if(Get-Process -Id '+process.pid+' -ErrorAction SilentlyContinue){exit 1}\nStart-Sleep -Seconds 2\n'+
    '$installer='+quote(file)+'\n$destination='+quote(installDir)+'\n'+
    '$result=Start-Process -FilePath $installer -ArgumentList (\'/S /UPDATE /D=\'+$destination) -Wait -PassThru -WindowStyle Hidden\n'+
    'if($result.ExitCode -eq 0){Start-Process -FilePath '+quote(exe)+' -WorkingDirectory $destination}\n';
   fs.writeFileSync(script,source,'utf8');
   const worker=child.spawn('powershell.exe',['-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',script],{detached:true,stdio:'ignore',windowsHide:true});
   worker.once('error',error=>show('Could not start update: '+error.message,true));
   worker.once('spawn',()=>{worker.unref();nw.App.quit();});
  }catch(error){show('Update not installed: '+error.message,false);}
 }
 async function check(manual){
  if(running)return;running=true;
  try{
   if(manual)show('Checking for updates…',false);
   const doc=accept(await downloads.manifest(fs.readFileSync(path.join(path.dirname(process.execPath),'src/app/update-public-key.pem'))));
   const item=doc.windows;policy.artifact(item.artifact);
   if(!policy.newer(item.version,nw.App.manifest.version)){try{for(const name of fs.readdirSync(root))if(/^[a-f0-9]{64}\.exe$/.test(name))fs.unlinkSync(path.join(root,name));}catch(ignore){}if(manual)show('StreamNest is up to date.',false);return;}
   version=item.version;
   if(manual)show('Downloading StreamNest '+version+'…',false);
   const file=await downloads.download(item.artifact,root,(done,total)=>{if(panel)show('Downloading update · '+Math.floor(done*100/total)+'%',false);});
   ready={file,item:item.artifact};
   if(!playing())show('StreamNest '+version+' is ready. Install and restart when convenient.',true);
  }catch(error){if(manual)show('Could not update: '+error.message,false);}
  finally{running=false;}
 }
 App.Updater={onlyNotification:event=>check(!!event),check:()=>check(true),updateDHT:()=>Promise.resolve(),updateDHTOld:()=>Promise.resolve(),updateDHTSettings:()=>Promise.resolve()};
 if(!process.env.STREAMNEST_SMOKE_ROOT)setTimeout(()=>check(false),10000);
})(window.App);
