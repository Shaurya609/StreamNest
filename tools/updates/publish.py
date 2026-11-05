import argparse,base64,hashlib,json,os,re,subprocess,time
from pathlib import Path
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding
p=argparse.ArgumentParser();p.add_argument('--key',required=True);p.add_argument('--output',default='updates/stable.json');args=p.parse_args()
def api(endpoint):return json.loads(subprocess.check_output(['gh','api',endpoint]))
repo='Shaurya609/StreamNest'
releases=api('repos/'+repo+'/releases?per_page=100')
def choose(pattern):
 rows=[r for r in releases if not r['draft'] and re.fullmatch(pattern,r['tag_name'])]
 if not rows:raise ValueError('No matching release')
 return max(rows,key=lambda r:tuple(map(int,re.search(r'(\d+\.\d+\.\d+)$',r['tag_name']).group(1).split('.'))))
def asset(release,name):
 a=next(a for a in release['assets'] if a['name']==name)
 assert a['digest'].startswith('sha256:')
 return {'url':a['browser_download_url'],'bytes':a['size'],'sha256':a['digest'][7:]}
a=choose(r'v\d+\.\d+\.\d+');w=choose(r'desktop-v\d+\.\d+\.\d+')
av=a['tag_name'][1:];wv=w['tag_name'][9:]
source=api('repos/'+repo+'/contents/app/build.gradle?ref='+a['target_commitish'])
text=base64.b64decode(source['content']).decode();code=int(re.search(r'versionCode\s+(\d+)',text).group(1))
assert re.search(r"versionName\s+'"+re.escape(av)+"'",text),'Release source version mismatch'
now=int(time.time()*1000)
doc={'schema':1,'product':'StreamNest','generatedAt':now,'expiresAt':now+90*86400000,
 'android':{'version':av,'versionCode':code,'minSdk':int(re.search(r'minSdk\s+(\d+)',text).group(1)),'artifacts':{'universal':asset(a,'StreamNest-'+av+'-universal.apk')}},
 'windows':{'version':wv,'artifact':asset(w,'StreamNest-Desktop-'+wv+'-win-x64-Setup.exe')}}
payload=json.dumps(doc,separators=(',',':')).encode();key=serialization.load_pem_private_key(Path(args.key).read_bytes(),password=None)
signature=key.sign(payload,padding.PKCS1v15(),hashes.SHA256())
Path(args.output).parent.mkdir(parents=True,exist_ok=True)
Path(args.output).write_text(json.dumps({'payload':base64.b64encode(payload).decode(),'signature':base64.b64encode(signature).decode()},indent=2)+'\n')
print(json.dumps({'android':av,'androidVersionCode':code,'windows':wv,'signed':True}))
