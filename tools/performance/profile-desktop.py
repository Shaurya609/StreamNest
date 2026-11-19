from pathlib import Path
import argparse,json,os,subprocess,time
import psutil

parser=argparse.ArgumentParser(description='Profile an isolated, extracted StreamNest Windows package. Requires psutil.')
parser.add_argument('--app',required=True,type=Path)
parser.add_argument('--output',required=True,type=Path)
a=parser.parse_args();workspace=Path(__file__).resolve().parents[2];reference=(workspace/'reference').resolve();app=a.app.resolve();out=a.output.resolve()
if not app.is_relative_to(reference) or not out.is_relative_to(reference):parser.error('Use an extracted test package and output folder under this checkout\'s reference directory.')
if not (app/'StreamNest.exe').is_file() or not (app/'src/app/smoke.js').is_file():parser.error('Expected an extracted standalone package.')
if out.exists():parser.error('Choose a new output folder; existing profiles are never erased.')
out.mkdir(parents=True)
for n in ['Local','Roaming','Temp','Chromium']:(out/n).mkdir()
env={**os.environ,'LOCALAPPDATA':str(out/'Local'),'APPDATA':str(out/'Roaming'),'TEMP':str(out/'Temp'),'TMP':str(out/'Temp'),'STREAMNEST_SMOKE_ROOT':str(out)}
target=app/'src/app/smoke.js';original=target.read_bytes();target.write_bytes(Path(__file__).with_name('startup-probe.js').read_bytes());proc=None;parent=None
try:
 for label in ['fresh-profile','warm-profile']:
  samples=[];cpu={};begin=time.time()
  with (out/(label+'.log')).open('w',encoding='utf8')as log:
   proc=subprocess.Popen([str(app/'StreamNest.exe'),'--streamnest-smoke','--user-data-dir='+str(out/'Chromium'),'--disable-background-timer-throttling'],cwd=app,env=env,stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW);parent=psutil.Process(proc.pid)
   while proc.poll() is None and time.time()-begin<70:
    private=rss=0;count=0
    try:processes=[parent]+parent.children(recursive=True)
    except psutil.Error:processes=[]
    for p in processes:
     try:m=p.memory_info();private+=m.private;rss+=m.rss;t=p.cpu_times();cpu[p.pid]=t.user+t.system;count+=1
     except psutil.Error:pass
    samples.append({'seconds':time.time()-begin,'privateBytes':private,'rssBytes':rss,'cpuSeconds':sum(cpu.values()),'processes':count});time.sleep(1)
   if proc.poll() is None:raise RuntimeError('Profile timed out; result is not acceptance.')
  probe=json.loads((out/'probe.json').read_text());result={'run':label,'startupMs':probe.get('homeReadyAt',0)-begin*1000 if probe.get('homeReadyAt') else None,'probe':probe,'samples':samples};(out/(label+'.json')).write_text(json.dumps(result,indent=2),encoding='utf8')
  if probe.get('errors') or not probe.get('homeReadyAt'):raise RuntimeError('Home did not load cleanly; result is not acceptance.')
  print(json.dumps({'run':label,'startupMs':result['startupMs'],'peakPrivateBytes':max(s['privateBytes']for s in samples),'cpuSeconds':sum(cpu.values())}),flush=True)
finally:
 if proc is not None and proc.poll()is None:
  try:
   for p in parent.children(recursive=True):
    try:p.kill()
    except psutil.Error:pass
   proc.kill();proc.wait(5)
  except (psutil.Error,subprocess.TimeoutExpired):pass
 target.write_bytes(original)
