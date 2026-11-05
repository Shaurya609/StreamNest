"""Exercise real NW.js shutdown, hidden PowerShell helper, installer arguments and restart.

Uses a tiny local installer fixture; no real registration or user profile is touched.
"""
from pathlib import Path
import subprocess,json,os,time,uuid,argparse
root=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description='Hidden real-runtime updater handoff using a controlled installer fixture')
parser.add_argument('--app',type=Path,required=True);args=parser.parse_args()
app=args.app.resolve();assert app.is_relative_to((root/'reference/standalone-build').resolve()),'Only generated builds may be probed'
probe=root/'reference'/('native-handoff-'+str(uuid.uuid4()));probe.mkdir()
manifest=app/'package.json';original=manifest.read_bytes()
html=app/'handoff-regression.html';flag=probe/'installed.txt';result=probe/'result.json';stub=probe/'installer-fixture.exe'
cs=probe/'fixture.cs';cs.write_text('using System.IO; class Fixture { static int Main(string[] args) {File.WriteAllLines(@"'+str(flag).replace('"','""')+'",args);return 0;} }')
compiler=Path(os.environ['WINDIR'])/'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
subprocess.run([str(compiler),'/nologo','/target:exe','/out:'+str(stub),str(cs)],check=True)
config=json.loads(original);config['main']='handoff-regression.html';config['window']['show']=False
settings={'probe':str(probe),'flag':str(flag),'result':str(result),'stub':str(stub),'source':str(app/'src/app/updater.js')}
script='const cfg='+json.dumps(settings)+';'+r'''
const fs=require('fs'),path=require('path');
if(fs.existsSync(cfg.flag)){fs.writeFileSync(cfg.result,JSON.stringify({ok:true,nativeRuntime:process.versions.node,helperAcknowledged:true,installerArguments:fs.readFileSync(cfg.flag,'utf8').trim().split(/\r?\n/),applicationRestarted:true}));nw.App.quit();}
else {(async()=>{try{
window.App={};let trace={};const cp=require('child_process'),child={spawn:(...args)=>{const p=cp.spawn(...args);trace={command:args[0],args:args[1],pid:p.pid};fs.writeFileSync(path.join(cfg.probe,'spawn.json'),JSON.stringify(trace));p.on('exit',(code,signal)=>{trace.exit={code,signal};fs.writeFileSync(path.join(cfg.probe,'spawn.json'),JSON.stringify(trace));});p.on('error',e=>{trace.error=String(e);fs.writeFileSync(path.join(cfg.probe,'spawn.json'),JSON.stringify(trace));});return p;}};const doc={generatedAt:Date.now(),windows:{version:'99.0.0',artifact:{sha256:'a'.repeat(64)}}};
const localRequire=n=>n==='child_process'?child:n==='./update-download'?{manifest:async()=>doc,download:async()=>cfg.stub}:n==='./update-policy'?{newer:()=>true,artifact:()=>{}}:n.includes('streamnest-standalone')?{profileRoot:()=>path.join(cfg.probe,'Profile')}:require(n);
new Function('require',fs.readFileSync(cfg.source,'utf8'))(localRequire);await App.Updater.check();
const install=Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Install and restart');if(!install)throw Error('Install action missing');await install.onclick();if(document.body.innerText.includes('Update not installed:'))fs.writeFileSync(cfg.result,JSON.stringify({ok:false,error:document.body.innerText}));
}catch(error){fs.writeFileSync(cfg.result,JSON.stringify({ok:false,error:String(error.stack||error)}));nw.App.quit();}})();}
'''
html.write_text('<html><body><script>'+script+'</script></body></html>')
env={**os.environ,'STREAMNEST_SMOKE_ROOT':str(probe),'LOCALAPPDATA':str(probe/'Local'),'APPDATA':str(probe/'Roaming')}
try:
 manifest.write_text(json.dumps(config))
 process=subprocess.Popen([str(app/'StreamNest.exe'),'--user-data-dir='+str(probe/'Chromium')],cwd=app,env=env,creationflags=subprocess.CREATE_NO_WINDOW)
 deadline=time.monotonic()+100
 while time.monotonic()<deadline and not result.exists():
  log=probe/'Profile/Updates/install-update.log'
  if log.exists() and ' ERROR: ' in log.read_text(errors='replace'):raise RuntimeError(log.read_text(errors='replace'))
  time.sleep(.2)
 if not result.exists():raise RuntimeError('Handoff timed out: '+str(probe))
 report=json.loads(result.read_text());assert report['ok'],report
 assert report['installerArguments']==['/S','/UPDATE','/D='+str(app)],report
 print(json.dumps(report,indent=2));process.wait(timeout=10)
finally:
 # Terminate only processes from this generated test application / unique helper.
 literal=lambda value: "'"+str(value).replace("'","''")+"'"
 cleanup="Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne $PID -and ($_.ExecutablePath -eq "+literal(app/'StreamNest.exe')+" -or ($_.Name -eq 'powershell.exe' -and $_.CommandLine -and $_.CommandLine.Contains("+literal(probe/'Profile/Updates/install-update.ps1')+"))) } | ForEach-Object { Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue }"
 subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command',cleanup],creationflags=subprocess.CREATE_NO_WINDOW,check=False)
 manifest.write_bytes(original)
 html.unlink(missing_ok=True)
