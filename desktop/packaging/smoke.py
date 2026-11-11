"""Run an opt-in hidden renderer probe with no real user profile or UI input."""
from pathlib import Path
import argparse, contextlib, json, os, subprocess, time, uuid

ROOT = Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--app',type=Path,required=True)
parser.add_argument('--video',type=Path)
parser.add_argument('--trailers',action='store_true',help='Verify trailer buttons and external handoff without starting playback')
parser.add_argument('--home',action='store_true',help='Verify home rows, synced progress, standard title detail and navigation')
parser.add_argument('--group-home-input',type=Path,help='Disposable live group fixture for cross-platform Home verification')
parser.add_argument('--sync',action='store_true',help='Exercise actual Settings button, visible dialog, live pairing and QR controls in the legacy runtime')
parser.add_argument('--updates',action='store_true',help='Exercise the real updater and live signed feed in the hidden legacy runtime')
parser.add_argument('--subtitles',action='store_true',help='Check live anonymous subtitle download and actual video.js cue display')
parser.add_argument('--loopback',action='store_true',help='Use the external local-torrent/video probe in a generated build folder')
parser.add_argument('--preload',action='store_true',help='Exercise local fixture next-episode preload and cache handover')
parser.add_argument('--sprint1',action='store_true',help='Exercise episode controls and auto-skip during fixture playback')
args=parser.parse_args()
app=args.app.resolve()
probe=ROOT/'reference/standalone-smoke'/str(uuid.uuid4())
probe.mkdir(parents=True)
for name in ('Local','Roaming','Temp','Chromium'):(probe/name).mkdir()
env=dict(os.environ,LOCALAPPDATA=str(probe/'Local'),APPDATA=str(probe/'Roaming'),
         TEMP=str(probe/'Temp'),TMP=str(probe/'Temp'),STREAMNEST_SMOKE_ROOT=str(probe))
if args.video:env['STREAMNEST_SMOKE_VIDEO']=str(args.video.resolve())
if args.group_home_input:env['STREAMNEST_GROUP_HOME_INPUT']=str(args.group_home_input.resolve())
@contextlib.contextmanager
def diagnostic_script():
    if not args.trailers and not args.group_home_input and not args.home and not args.loopback and not args.subtitles and not args.updates and not args.sync and not args.sprint1 and not args.preload:
        yield
        return
    if args.loopback: assert args.video,'A local video fixture is required for the loopback check'
    assert app.is_relative_to((ROOT/'reference/standalone-build').resolve()),'Diagnostic overrides are restricted to generated build folders'
    target=app/'src/app/smoke.js'
    original=target.read_bytes()
    temporary=target.with_suffix('.diagnostic')
    temporary.write_bytes((ROOT/('desktop/packaging/trailers-smoke.js' if args.trailers else 'desktop/packaging/group-home-smoke.js' if args.group_home_input else 'desktop/packaging/home-smoke.js' if args.home else 'desktop/packaging/preload-smoke.js' if args.preload else 'desktop/packaging/sprint1-smoke.js' if args.sprint1 else 'desktop/packaging/sync-smoke.js' if args.sync else 'desktop/packaging/updates-smoke.js' if args.updates else 'desktop/packaging/subtitles-smoke.js' if args.subtitles else 'desktop/packaging/loopback-smoke.js')).read_bytes())
    temporary.replace(target)
    try:yield
    finally:
        temporary.write_bytes(original)
        temporary.replace(target)
with diagnostic_script(), (probe/'process.log').open('w',encoding='utf8') as output:
    process=subprocess.Popen([str(app/'StreamNest.exe'),'--streamnest-smoke',
                              '--user-data-dir='+str(probe/'Chromium'),
                              '--disable-background-timer-throttling','--autoplay-policy=no-user-gesture-required'],
                             cwd=app,env=env,stdout=output,stderr=output,
                             creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    deadline=time.monotonic()+(300 if args.preload else 110)
    while time.monotonic()<deadline and not (probe/'result.json').exists():
        if process.poll() is not None:break
        time.sleep(0.25)
    if (probe/'result.json').exists():
        result=json.loads((probe/'result.json').read_text(encoding='utf8'))
    else:
        result={'ok':False,'error':'Renderer exited or timed out before producing a report','exitCode':process.poll()}
    if process.poll() is None:
        try:process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            # Only this isolated, agent-launched smoke process is terminated.
            process.terminate()
    result['probeFolder']=str(probe)
    print(json.dumps(result,indent=2))
    if not result['ok']:raise SystemExit(1)
