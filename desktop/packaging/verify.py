from pathlib import Path
import argparse, json, re, struct

parser=argparse.ArgumentParser();parser.add_argument('--app',type=Path,required=True);args=parser.parse_args()
app=args.app.resolve();manifest=json.loads((app/'package.json').read_text(encoding='utf8'))
assert manifest['name']=='StreamNest'
assert manifest['window']['title']=='StreamNest'
assert manifest['homepage']=='https://github.com/Shaurya609/StreamNest'
assert (app/manifest['main']).is_file()
for name in ('User Data','Profile','Cache','data','settings.db','logs.txt','debug.log','Popcorn-Time.exe'):
    assert not (app/name).exists(),'State or upstream executable leaked into payload: '+name
binary=(app/'StreamNest.exe').read_bytes();offset=struct.unpack_from('<I',binary,0x3c)[0]
assert binary[:2]==b'MZ' and binary[offset:offset+4]==b'PE\0\0'
assert struct.unpack_from('<H',binary,offset+4)[0]==0x8664,'Expected x64 executable'
icon=(app/'src/app/images/streamnest.ico').read_bytes()
assert struct.unpack_from('<HHH',icon)==(0,1,7),'Expected multiresolution ICO'
settings=(app/'src/app/settings.js').read_text(encoding='utf8')
assert not re.search(r"(?:client_id|client_secret|api_key):\s*'[^']+'",settings),'Vendor application key remained in payload'
assert (app/'node_modules/butter-settings-popcorntime.app/index.js').read_text(encoding='utf8').strip()=='module.exports = {};'
assert 'updateDHTOld: function() { return Promise.resolve(); }' in (app/'src/app/updater.js').read_text(encoding='utf8')
assert (app/'LICENSE.txt').is_file() and (app/'third-party/npm-inventory.json').is_file()
assert (app/'node_modules/webtorrent/package.json').is_file()
assert (app/'src/app/themes/Official_-_Dark_theme.css').is_file()
print(json.dumps({'ok':True,'version':manifest['version'],'executable':'x64','iconSizes':7,'userStateBundled':False,'sharedVendorKeysBundled':False}))
