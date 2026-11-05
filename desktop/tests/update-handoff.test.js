const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),os=require('os'),vm=require('vm'),{EventEmitter}=require('events');
test('Windows updater defers playback, generates installer handoff and quits only after helper spawn',async()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),"streamnest update's test-")),app=path.join(root,'Application'),profile=path.join(root,'Profile');fs.mkdirSync(path.join(app,'src/app'),{recursive:true});fs.writeFileSync(path.join(app,'src/app/update-public-key.pem'),'test public key');
 const buttons=[];let playing=false,spawned=0,quit=0;
 const doc={generatedAt:Date.now(),windows:{version:'0.12.0',artifact:{url:'https://github.com/Shaurya609/StreamNest/releases/download/desktop-v0.12.0/Setup.exe',bytes:100,sha256:'a'.repeat(64)}}};
 const document={querySelector:()=>playing?{}:null,querySelectorAll:()=>[],body:{appendChild:()=>{}},createElement:tag=>{const node={style:{},appendChild:()=>{},remove:()=>{},textContent:''};if(tag==='button')buttons.push(node);return node;}};
 const child={spawn:(file,args,options)=>{spawned++;assert.equal(file,'powershell.exe');assert.equal(options.windowsHide,true);const e=new EventEmitter();e.unref=()=>{};queueMicrotask(()=>e.emit('spawn'));return e;}};
 const App={};
 vm.runInNewContext(fs.readFileSync(path.resolve('desktop/standalone/updater.js'),'utf8'),{window:{App},document,process:{pid:2147483640,execPath:path.join(app,'StreamNest.exe'),env:{STREAMNEST_SMOKE_ROOT:'test'}},nw:{App:{manifest:{version:'0.11.1'},quit:()=>quit++}},require:n=>n==='child_process'?child:n==='./update-policy'?require('../standalone/update-policy'):n==='./update-download'?{manifest:async()=>doc,download:async()=>path.join(root,'installer.exe')}:n.includes('streamnest-standalone')?{profileRoot:()=>profile}:require(n)});
 try{
  await App.Updater.check();let install=buttons.find(b=>b.textContent==='Install and restart');assert(install);
  playing=true;await install.onclick();assert.equal(spawned,0);assert.equal(quit,0);
  playing=false;await install.onclick();await Promise.resolve();assert.equal(spawned,1);assert.equal(quit,1);
  const script=fs.readFileSync(path.join(profile,'Updates/install-update.ps1'),'utf8');assert(script.includes('/S /UPDATE /D='));assert(script.includes('Wait-Process'));assert(script.includes("update''s test-"));
  if(process.platform==='win32'){
   const result=require('child_process').spawnSync('powershell.exe',['-NoProfile','-Command',"$parseErrors=$null;$tokens=$null;[void][System.Management.Automation.Language.Parser]::ParseFile('"+path.join(profile,'Updates/install-update.ps1').replace(/'/g,"''")+"',[ref]$tokens,[ref]$parseErrors);if($parseErrors.Count){$parseErrors|Out-String|Write-Output;exit 1}"],{encoding:'utf8',windowsHide:true});assert.equal(result.status,0,result.stdout+result.stderr);
  }
 }finally{fs.rmSync(root,{recursive:true,force:true});}
});
