"""Copy verified desktop preview assets to a matching draft; never overwrite a public release."""
import hashlib,json,os,re,subprocess,tempfile,zipfile
from pathlib import Path
version=os.environ['VERSION']
assert re.fullmatch(r'\d+\.\d+\.\d+',version),'Invalid version'
def gh(*args):return subprocess.check_output(['gh',*args],text=True)
ids={r['tag_name']:r['id'] for r in json.loads(gh('api','repos/'+os.environ['GH_REPO']+'/releases?per_page=100'))}
def release(tag):
 assert tag in ids,'Expected release is missing'
 return json.loads(gh('api','repos/'+os.environ['GH_REPO']+'/releases/'+str(ids[tag])))
preview='desktop-v'+version;target='v'+version
source=release(preview);draft=release(target)
assert not source['draft'] and source['prerelease'],'Expected published desktop preview'
assert draft['draft'],'Refusing to overwrite a published release'
ref=json.loads(gh('api','repos/'+os.environ['GH_REPO']+'/git/ref/tags/'+preview))['object']
if ref['type']=='tag':ref=json.loads(gh('api','repos/'+os.environ['GH_REPO']+'/git/tags/'+ref['sha']))['object']
assert ref['type']=='commit' and draft['target_commitish']==ref['sha'],'Release source mismatch'
names={f'StreamNest-Desktop-{version}-win-x64-Setup.exe',f'StreamNest-Desktop-{version}-win-x64-portable.zip',f'StreamNest-Desktop-{version}-application-source.zip',f'SHA256SUMS-Desktop-{version}.txt'}
assets={a['name']:a for a in source['assets']};assert set(assets)==names
with tempfile.TemporaryDirectory() as temp:
 root=Path(temp);subprocess.run(['gh','release','download',preview,'--dir',temp],check=True)
 for name,a in assets.items():
  data=(root/name).read_bytes();assert len(data)==a['size'] and a['digest']=='sha256:'+hashlib.sha256(data).hexdigest(),name
 checks={}
 for line in (root/f'SHA256SUMS-Desktop-{version}.txt').read_text().splitlines():
  digest,name=line.split('  ',1);assert Path(name).name==name and name in names;checks[name]=digest
 assert set(checks)==names-{f'SHA256SUMS-Desktop-{version}.txt'}
 for name,digest in checks.items():assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest,name
 with zipfile.ZipFile(root/f'StreamNest-Desktop-{version}-application-source.zip') as z:
  paths=['streamer.js','app.js']+['butter-provider/'+n for n in ['streamnest-recovery.js','streamnest-startup-window.js','streamnest-preload.js','streamnest-bounded-read.js','streamnest-transfer-budget.js','streamnest-progress.js','streamnest-home-ui.js','streamnest-late-subtitles.js','streamnest-playback.js','streamnest-diagnostics.js','streamnest-source-policy.js','streamnest-episodes.js','streamnest-media-decoder.js','streamnest-decoded-audio.js','streamnest-embedded-subtitles.js','streamnest-media-tracks.js','streamnest-buffered-recovery.js']]+['lib/views/player.js','lib/providers/watchlist.js','vendor/videojshooks.js']
  for path in paths:
   expected=(Path('desktop/overlay')/path).read_bytes().decode('utf8',errors='replace').replace('\r\n','\n')
   for prefix in ['desktop/overlay/','generated-app/src/app/']:
    entries=[n for n in z.namelist() if n.endswith('/'+prefix+path)];assert len(entries)==1,(path,entries)
    actual=z.read(entries[0]).decode('utf8',errors='replace').replace('\r\n','\n')
    wanted=expected if prefix.startswith('desktop/') else expected.replace('Popcorn-Time','StreamNest').replace('Popcorn Time','StreamNest')
    assert actual==wanted,entries[0]
 assert release(target)['draft'],'Draft became public during verification'
 subprocess.run(['gh','release','upload',target,*[str(root/n) for n in sorted(names)],'--clobber'],check=True)
 copied={a['name']:a for a in release(target)['assets']}
 for name,a in assets.items():assert copied[name]['digest']==a['digest'] and copied[name]['size']==a['size'],name
 print(json.dumps({'version':version,'source':ref['sha'],'verifiedAndCopied':len(names),'targetStillDraft':release(target)['draft']}))
