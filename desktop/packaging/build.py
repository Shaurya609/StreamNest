"""Clean Windows standalone assembly. Never reads an installed app or user profile."""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess, urllib.request, zipfile

ROOT = Path(__file__).resolve().parents[2]
DESKTOP = ROOT / 'desktop'
VERSION = json.loads((DESKTOP / 'packaging/package.json').read_text(encoding='utf8'))['version']
LOCK = json.loads((DESKTOP / 'packaging/toolchain.json').read_text(encoding='utf8'))
BUILD = ROOT / 'reference/standalone-build' / VERSION
CACHE = ROOT / 'reference/desktop-toolchain'
DIST = ROOT / 'dist/standalone'
UPSTREAM = DESKTOP / 'upstream'
YARN = DESKTOP / 'packaging/node_modules/yarn/bin/yarn.js'

def run(args, cwd=ROOT, capture=False):
    env = dict(os.environ, GIT_CONFIG_COUNT='2',
               GIT_CONFIG_KEY_0='url.https://github.com/.insteadOf',
               GIT_CONFIG_VALUE_0='ssh://git@github.com/',
               GIT_CONFIG_KEY_1='url.https://github.com/.insteadOf',
               GIT_CONFIG_VALUE_1='git@github.com:')
    result = subprocess.run([str(x) for x in args], cwd=cwd, env=env,
                            text=True, capture_output=capture, check=True)
    return result.stdout if capture else None

def sha(file):
    digest = hashlib.sha256()
    with open(file, 'rb') as stream:
        while block := stream.read(1024 * 1024): digest.update(block)
    return digest.hexdigest()

def download(name):
    item = LOCK['assets'][name]
    target = CACHE / item['file']
    CACHE.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        temporary = target.with_suffix('.pending')
        with urllib.request.urlopen(urllib.request.Request(item['url'], headers={'User-Agent':'StreamNest-Build/1'}), timeout=120) as source, temporary.open('wb') as out:
            shutil.copyfileobj(source, out)
        if sha(temporary) != item['sha256']: raise ValueError(f'{name} checksum mismatch')
        temporary.replace(target)
    if sha(target) != item['sha256']: raise ValueError(f'{name} checksum mismatch')
    return target

def safe_extract(archive, destination):
    destination = destination.resolve()
    with zipfile.ZipFile(archive) as source:
        for item in source.infolist():
            if not (destination / item.filename).resolve().is_relative_to(destination): raise ValueError('Unsafe archive path')
        source.extractall(destination)

def generated_clean(folder):
    target = folder.resolve()
    allowed = (ROOT / 'reference/standalone-build').resolve()
    if target == allowed or not target.is_relative_to(allowed): raise ValueError('Refusing cleanup outside generated build workspace')
    if target.exists(): shutil.rmtree(target)
    target.mkdir(parents=True)

def replace(file, old, new):
    text = file.read_text(encoding='utf8')
    if old not in text: raise ValueError(f'Expected source marker missing in {file.name}')
    file.write_text(text.replace(old, new), encoding='utf8')

def assemble(skip_install=False):
    if not skip_install:
        run(['node', YARN, 'install', '--frozen-lockfile', '--ignore-scripts', '--non-interactive'], UPSTREAM)
    run(['node', UPSTREAM / 'node_modules/patch-package/index.js', '--error-on-fail'], UPSTREAM)
    run(['node', DESKTOP / 'packaging/styles.cjs', UPSTREAM])
    generated_clean(BUILD)
    app = BUILD / 'StreamNest'
    app.mkdir()
    runtime_dir = BUILD / 'runtime'
    safe_extract(download('runtime'), runtime_dir)
    runtime = next(p.parent for p in runtime_dir.rglob('nw.exe'))
    allowed = {'.dll', '.pak', '.bin', '.dat', '.nexe'}
    for file in runtime.iterdir():
        if file.is_file() and (file.suffix in allowed or file.name == 'nw.exe'):
            shutil.copyfile(file, app / ('StreamNest.exe' if file.name == 'nw.exe' else file.name))
        elif file.is_dir() and file.name in ('locales', 'swiftshader', 'pnacl'):
            shutil.copytree(file, app / file.name)
    shutil.copytree(UPSTREAM / 'src', app / 'src', ignore=shutil.ignore_patterns('styl', '.jshintrc'))
    for file in ('LICENSE.txt', 'CHANGELOG.md'): shutil.copyfile(UPSTREAM / file, app / file)
    inventory = json.loads((DESKTOP / 'manifest.json').read_text(encoding='utf8'))
    for item in inventory['files']:
        source = DESKTOP / 'overlay' / item['path']
        if sha(source) != item['sha256']: raise ValueError('Overlay checksum mismatch: ' + item['path'])
        target = app / 'src/app' / item['path']
        if not target.resolve().is_relative_to((app / 'src/app').resolve()): raise ValueError('Unsafe overlay path')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    # Copy only the production closure from the frozen lockfile, including nested dependencies.
    lines = run(['node', YARN, 'list', '--prod', '--json'], UPSTREAM, capture=True).splitlines()
    tree = next(json.loads(line)['data']['trees'] for line in lines if json.loads(line).get('type') == 'tree')
    production = app / 'node_modules'
    production.mkdir()
    for item in tree:
        name = item['name'].rsplit('@', 1)[0]
        source = UPSTREAM / 'node_modules' / name
        if source.is_dir(): shutil.copytree(source, production / name, dirs_exist_ok=True)
    for source in (DESKTOP / 'standalone').rglob('*'):
        if source.suffix in ('.js', '.tpl', '.pem'):
            target = app / 'src/app' / source.relative_to(DESKTOP / 'standalone')
            if source.name == 'trakttv.js': target = app / 'src/app/lib/providers/trakttv.js'
            if source.name == 'disclaimer.tpl': target = app / 'src/app/templates/disclaimer.tpl'
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(source, target)
    settings_file = app / 'src/app/settings.js'
    settings_text = settings_file.read_text(encoding='utf8')
    # Preserve original source in upstream; the runnable product has no vendor app keys.
    settings_text = re.sub(r"(client_id|client_secret|api_key):\s*'[^']*'", r"\1: ''", settings_text)
    settings_file.write_text(settings_text, encoding='utf8')
    (production / 'butter-settings-popcorntime.app/index.js').write_text('module.exports = {};\n', encoding='utf8')
    for file in (app / 'src/app').rglob('*'):
        if file.is_file() and file.suffix in ('.js', '.html', '.tpl', '.css'):
            text = file.read_text(encoding='utf8')
            text = text.replace('Popcorn-Time', 'StreamNest').replace('Popcorn Time', 'StreamNest')
            # Protocol client identifiers belong to the inherited subtitle integration, not UI branding.
            if file.name in ('streamnest-org.js','opensubtitles.js'):
                text = text.replace('StreamNest NodeJS','Popcorn Time NodeJS')
            file.write_text(text, encoding='utf8')
    # Defaults are overridden after the npm settings package has loaded, before providers start.
    replace(app / 'src/app/bootstrap.js', '.then(loadProviders)', ".then(function(){require('./butter-provider/streamnest-standalone').configure(Settings);})\n    .then(loadProviders)")
    # Require paths in NW resolve relative to the app entry, matching the existing provider modules.
    shutil.move(app / 'src/app/streamnest-standalone.js', app / 'src/app/butter-provider/streamnest-standalone.js')
    replace(app / 'src/app/global.js', 'data_path = nw.App.dataPath,', "data_path = require('./butter-provider/streamnest-standalone').profileRoot(),")
    # The opt-in smoke mode never shows or activates a native window.
    replace(app / 'src/app/global.js', 'nw.Window.get().show();', "if(!process.env.STREAMNEST_SMOKE_ROOT)nw.Window.get().show();")
    replace(app / 'src/app/global.js', 'dayjs.extend(', "if(process.env.STREAMNEST_SMOKE_ROOT){win.show=function(){};}\n\ndayjs.extend(")
    if (DESKTOP / 'standalone/smoke.js').exists():
        replace(app / 'src/app/index.html', '    <script src="bootstrap.js"></script>', '    <script src="bootstrap.js"></script>\n    <script src="smoke.js"></script>')
    # No remote DHT document may change standalone endpoints, branding or account keys.
    replace(app / 'src/app/database.js', 'applyDhtSettings: function (dhtInfo) {', 'applyDhtSettings: function (dhtInfo) { return; /* Legacy config is disabled. */')
    package = json.loads((UPSTREAM / 'package.json').read_text(encoding='utf8'))
    package.update(name='StreamNest', companyName='StreamNest contributors', version=VERSION,
                   releaseName='Standalone preview', homepage='https://github.com/Shaurya609/StreamNest',
                   repository={'type':'git','url':'https://github.com/Shaurya609/StreamNest.git'},
                   bugs='https://github.com/Shaurya609/StreamNest/issues',
                   installIcon='./src/app/images/streamnest.ico', unInstallIcon='./src/app/images/streamnest.ico')
    package['window'].update(title='StreamNest', icon='src/app/images/icon.png', show=False)
    package['scripts'] = {}
    package.pop('devDependencies', None)
    (app / 'package.json').write_text(json.dumps(package, indent=2)+'\n', encoding='utf8')
    run(['node', DESKTOP / 'packaging/icons.cjs', ROOT / 'branding/streamnest-icon.svg', app / 'src/app/images'])
    for name in ('popcorn-time-logo.svg', 'streamnest-logo.svg'):
        shutil.copyfile(ROOT / 'branding/streamnest-icon.svg', app / 'src/app/images' / name)
    replace(app / 'src/app/templates/about.tpl', '<div class="text-about">', '<div class="text-about"><p>Independent StreamNest fork, based on Popcorn Time 0.5.1 and Butter Project. GPL v3. Complete application sources and build instructions accompany this preview.</p>')
    # Replace executable resources, not just the shortcut icon.
    run([download('rcedit'), app / 'StreamNest.exe', '--set-icon', app / 'src/app/images/streamnest.ico',
         '--set-version-string','ProductName','StreamNest', '--set-version-string','FileDescription','StreamNest Desktop',
         '--set-version-string','CompanyName','StreamNest contributors', '--set-version-string','OriginalFilename','StreamNest.exe',
         '--set-version-string','LegalCopyright','StreamNest contributors; Popcorn Time and Butter Project contributors; NW.js authors',
         '--set-file-version',VERSION, '--set-product-version',VERSION])
    notices = app / 'third-party'
    notices.mkdir()
    for name in ('credits.html', 'LICENSE'):
        if (runtime / name).exists(): shutil.copyfile(runtime / name, notices / ('NWJS-' + name))
    (notices / 'SOURCE-ORIGINS.json').write_text(json.dumps(LOCK, indent=2)+'\n', encoding='utf8')
    packages=[]
    for file in production.rglob('package.json'):
        try:
            data=json.loads(file.read_text(encoding='utf8'))
            if data.get('name'): packages.append({key:data[key] for key in ('name','version','license','repository') if key in data})
        except (ValueError, UnicodeError): pass
    (notices / 'npm-inventory.json').write_text(json.dumps(packages, indent=2)+'\n', encoding='utf8')
    (app / 'BUILD-INFO.json').write_text(json.dumps({'version':VERSION,'upstream':LOCK['upstream'],'overlayVersion':inventory['version'],'runtimeArchiveSha256':LOCK['assets']['runtime']['sha256']},indent=2)+'\n',encoding='utf8')
    (app / 'README.txt').write_text('StreamNest Desktop '+VERSION+' preview\n\nRun StreamNest.exe. No Popcorn Time installation is required.\nSettings and video cache are separate under %LOCALAPPDATA%\\StreamNest.\nNo accounts or saved settings are imported. Updates are manual from the StreamNest GitHub releases page.\nThis build retains the legacy NW.js 0.44.5 runtime for codec compatibility. Runtime modernization and broader device testing remain pending.\nThe preview is unsigned.\nApplication sources and build provenance are provided separately. See desktop/STANDALONE.md.\n',encoding='utf8')
    return app

def source_archive(app):
    """Include build inputs and the generated application's readable dependency sources."""
    DIST.mkdir(parents=True,exist_ok=True)
    target=DIST/f'StreamNest-Desktop-{VERSION}-application-source.zip'
    prefix=f'StreamNest-Desktop-{VERSION}-source/'
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=1) as out:
        for file in sorted(DESKTOP.rglob('*')):
            if not file.is_file():continue
            relative=file.relative_to(DESKTOP)
            if any(part in ('node_modules','__pycache__','.git','themes') for part in relative.parts):continue
            out.write(file,prefix+'desktop/'+relative.as_posix())
        for file in sorted(app.rglob('*')):
            if file.is_file() and (file.relative_to(app).parts[0] in ('src','node_modules','third-party') or file.name in ('package.json','BUILD-INFO.json','LICENSE.txt','README.txt')):
                out.write(file,prefix+'generated-app/'+file.relative_to(app).as_posix())
        out.write(ROOT/'branding/streamnest-icon.svg',prefix+'branding/streamnest-icon.svg')
    return target

def archive(app):
    DIST.mkdir(parents=True,exist_ok=True)
    portable=DIST/f'StreamNest-Desktop-{VERSION}-win-x64-portable.zip'
    with zipfile.ZipFile(portable,'w',zipfile.ZIP_DEFLATED,compresslevel=1) as out:
        for file in sorted(app.rglob('*')):
            if file.is_file(): out.write(file,'StreamNest/'+file.relative_to(app).as_posix())
    nsis_dir=BUILD/'nsis'
    safe_extract(download('nsis'),nsis_dir)
    compiler=next(nsis_dir.rglob('makensis.exe'))
    delete_list=BUILD/'uninstall-files.nsh'
    entries=['Delete "$INSTDIR\\'+f.relative_to(app).as_posix().replace('/','\\').replace('$','$$')+'"' for f in sorted(app.rglob('*')) if f.is_file()]
    entries+=['RMDir "$INSTDIR\\'+p.relative_to(app).as_posix().replace('/','\\').replace('$','$$')+'"' for p in sorted((p for p in app.rglob('*') if p.is_dir()),key=lambda p:len(p.parts),reverse=True)]
    delete_list.write_text('\n'.join(entries)+'\n',encoding='utf8')
    installer=DIST/f'StreamNest-Desktop-{VERSION}-win-x64-Setup.exe'
    run([compiler,'/V2',f'/DAPPDIR={app}',f'/DOUTPUT={installer}',f'/DDELETE_LIST={delete_list}',DESKTOP/'packaging/installer.nsi'])
    files=[portable,installer,source_archive(app)]
    (DIST/f'SHA256SUMS-Desktop-{VERSION}.txt').write_text(''.join(sha(file)+'  '+file.name+'\n' for file in files),encoding='utf8')
    print(json.dumps({'app':str(app),'artifacts':[str(p) for p in files]}))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--skip-install',action='store_true');parser.add_argument('--assemble-only',action='store_true');args=parser.parse_args()
    selected=assemble(args.skip_install)
    if not args.assemble_only: archive(selected)
    else: print(selected)
