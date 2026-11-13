(function(App){
 'use strict';
 const fs=require('fs'),path=require('path'),child=require('child_process');
 const policy=require('./update-policy'),downloads=require('./update-download');
 let running=false,applying=false,ready=null,version='',panel=null;
 const root=path.join(require('./butter-provider/streamnest-standalone').profileRoot(),'Updates');
 function playing(){return !!document.querySelector('#video_player, .player video, .loading')||Array.from(document.querySelectorAll('video')).some(v=>!v.paused);}
 function show(text,install){
  if(!panel){panel=document.createElement('div');panel.style.cssText='position:fixed;z-index:99999;right:20px;top:50px;max-width:400px;padding:20px;background:#20232b;color:white;font:15px sans-serif;border:1px solid #526078;border-radius:10px';document.body.appendChild(panel);}
  panel.textContent='';const label=document.createElement('p');label.textContent=text;label.style.cssText='margin:0 0 16px;line-height:1.45;color:#f3f4f6';panel.appendChild(label);
  function action(text,primary,callback){
   const button=document.createElement('button');button.type='button';button.textContent=text;
   button.style.cssText='appearance:none;display:inline-block;position:static;float:none;width:auto;height:auto;min-height:40px;margin:0 10px 0 0;padding:10px 16px;border:1px solid '+(primary?'#99c5ff':'#8794a8')+';border-radius:6px;background:'+(primary?'#99c5ff':'#303b4c')+';color:'+(primary?'#101827':'#ffffff')+';font:600 14px/20px sans-serif;text-align:center;text-shadow:none;opacity:1;cursor:pointer;box-shadow:none';
   button.onfocus=()=>{button.style.outline='2px solid #ffffff';button.style.outlineOffset='3px';};
   button.onblur=()=>{button.style.outline='';};button.onclick=callback;panel.appendChild(button);
  }
  if(install)action('Install and restart',true,apply);
  action(install?'Later':'Close',false,()=>{panel.remove();panel=null;});
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
  if(applying)return;
  if(!ready){show('Check for updates before installing.',false);return;}
  applying=true;show('Preparing the update installer...',false);
  try{
   // Re-read the trusted feed and verify the cached executable immediately before launching it.
   const doc=accept(await downloads.manifest(fs.readFileSync(path.join(path.dirname(process.execPath),'src/app/update-public-key.pem'))));
   if(doc.windows.version!==version||doc.windows.artifact.sha256!==ready.item.sha256)throw Error('Update changed. Check again.');
   const file=await downloads.download(doc.windows.artifact,root);
   const installDir=path.dirname(process.execPath),exe=process.execPath;
   const userData=(nw.App.fullArgv||[]).find(arg=>arg.indexOf('--user-data-dir=')===0);
   const restartArgs=userData&&!/["\r\n]/.test(userData)?' -ArgumentList '+quote('"'+userData+'"'):'';
   if(/[\r\n]/.test(installDir))throw Error('Unsupported installation path');
   const script=path.join(root,'install-update.ps1'),ack=path.join(root,'helper-'+require('crypto').randomBytes(12).toString('hex')+'.ready'),log=path.join(root,'install-update.log');
   const source="$ErrorActionPreference='Stop'\ntry {\n"+
    'Add-Content -LiteralPath '+quote(log)+' -Value ((Get-Date -Format o)+" Starting update helper")\n'+
    '[IO.File]::WriteAllText('+quote(ack)+',"ready")\n'+
    '$until=(Get-Date).AddSeconds(90)\nwhile(Get-Process -Id '+process.pid+' -ErrorAction SilentlyContinue){if((Get-Date) -gt $until){throw "App did not exit in time"};Start-Sleep -Milliseconds 200}\n'+
    '$until=(Get-Date).AddSeconds(90)\nwhile(Get-CimInstance Win32_Process -Filter '+quote("name='StreamNest.exe'")+' | Where-Object {$_.ExecutablePath -eq '+quote(exe)+'}){if((Get-Date) -gt $until){throw "App processes did not exit in time"};Start-Sleep -Milliseconds 500}\n'+
    '$installer='+quote(file)+'\n$destination='+quote(installDir)+'\n'+
    '$result=Start-Process -FilePath $installer -ArgumentList (\'/S /UPDATE /D=\'+$destination) -Wait -PassThru -WindowStyle Hidden\n'+
    'if($result.ExitCode -ne 0){throw ("Installer exit code: "+$result.ExitCode)}\n'+
    'Add-Content -LiteralPath '+quote(log)+' -Value ((Get-Date -Format o)+" Installation completed; restarting")\n'+
    'Start-Process -FilePath '+quote(exe)+' -WorkingDirectory $destination'+restartArgs+' -WindowStyle Hidden\n'+
    '} catch { Add-Content -LiteralPath '+quote(log)+' -Value ((Get-Date -Format o)+" ERROR: "+$_.Exception.Message);exit 1 }\n';
   fs.writeFileSync(script,source,'utf8');
   const output=fs.openSync(log,'a'),shell=path.join(process.env.SystemRoot||'C:\\Windows','System32','WindowsPowerShell','v1.0','powershell.exe');
   const command='Start-Process -FilePath '+quote(shell)+' -ArgumentList '+quote('-NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"'+script+'\"')+' -WindowStyle Hidden';
   let worker;try{worker=child.spawn(shell,['-NoProfile','-NonInteractive','-Command',command],{detached:false,stdio:['ignore',output,output],windowsHide:true});}finally{fs.closeSync(output);}
   await new Promise((resolve,reject)=>{
    const deadline=Date.now()+15000;let timer;
    function failed(error){clearInterval(timer);reject(error);}
    worker.once('error',failed);
    timer=setInterval(()=>{
     if(fs.existsSync(ack)){clearInterval(timer);try{fs.unlinkSync(ack);}catch(ignore){}resolve();}
     else if(Date.now()>deadline)failed(Error('Updater did not become ready. Please retry or use the latest installer.'));
    },100);
   });
   worker.unref();nw.App.quit();
  }catch(error){require('./butter-provider/streamnest-diagnostics').failure('update.failed','update','install',error);show('Update not installed: '+error.message,false);}
  finally{applying=false;}
 }
 async function check(manual){
  if(running)return;running=true;
  try{
   if(manual)show('Checking for updates...',false);
   const doc=accept(await downloads.manifest(fs.readFileSync(path.join(path.dirname(process.execPath),'src/app/update-public-key.pem'))));
   const item=doc.windows;policy.artifact(item.artifact);
   if(!policy.newer(item.version,nw.App.manifest.version)){try{for(const name of fs.readdirSync(root))if(/^[a-f0-9]{64}\.exe$/.test(name))fs.unlinkSync(path.join(root,name));}catch(ignore){}if(manual)show('StreamNest is up to date.',false);return;}
   version=item.version;
   if(manual)show('Downloading StreamNest '+version+'...',false);
   const file=await downloads.download(item.artifact,root,(done,total)=>{if(panel)show('Downloading update - '+Math.floor(done*100/total)+'%',false);});
   ready={file,item:item.artifact};
   if(!playing())show('StreamNest '+version+' is ready. Install and restart when convenient.',true);
  }catch(error){require('./butter-provider/streamnest-diagnostics').failure('update.failed','update','download',error);if(manual)show('Could not update: '+error.message,false);}
  finally{running=false;}
 }
 App.Updater={onlyNotification:event=>check(!!event),check:()=>check(true),updateDHT:()=>Promise.resolve(),updateDHTOld:()=>Promise.resolve(),updateDHTSettings:()=>Promise.resolve()};
 if(!process.env.STREAMNEST_SMOKE_ROOT)setTimeout(()=>check(false),10000);
})(window.App);
