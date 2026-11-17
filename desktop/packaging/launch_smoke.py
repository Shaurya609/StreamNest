"""Exercise the packaged entry while an installer process owns the launch marker."""
from pathlib import Path
import argparse,json,os,subprocess,time,uuid
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--app',type=Path,required=True);args=p.parse_args();app=args.app.resolve()
assert app.is_relative_to((root/'reference/standalone-build').resolve())
probe=root/'reference'/('launch-guard-'+str(uuid.uuid4()));probe.mkdir()
index=app/'src/app/index.html';original=index.read_bytes();marker=app/'.streamnest-installing';result=probe/'opened.json'
assert not marker.exists(),'Another installation is active'
process=None
try:
 index.write_text('<html><script>require("fs").writeFileSync('+json.dumps(str(result))+',JSON.stringify({opened:true}));nw.App.quit();</script></html>',encoding='utf8')
 marker.write_text(str(os.getpid()),encoding='utf8')
 env={**os.environ,'STREAMNEST_SMOKE_ROOT':str(probe),'LOCALAPPDATA':str(probe/'Local'),'APPDATA':str(probe/'Roaming')}
 process=subprocess.Popen([str(app/'StreamNest.exe'),'--user-data-dir='+str(probe/'Chromium')],cwd=app,env=env,creationflags=subprocess.CREATE_NO_WINDOW)
 time.sleep(4)
 assert process.poll() is None and not result.exists(),'Application loaded before installation completed'
 marker.unlink()
 process.wait(timeout=20)
 assert result.exists() and json.loads(result.read_text())['opened'],'Application failed to continue after installation'
 print(json.dumps({'ok':True,'blockedDuringInstall':True,'continuedAfterInstall':True,'profileIsolated':True}))
finally:
 marker.unlink(missing_ok=True)
 if process is not None and process.poll() is None:process.terminate();process.wait(timeout=10)
 index.write_bytes(original)
