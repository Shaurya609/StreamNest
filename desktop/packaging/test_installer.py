"""Silent install/uninstall in a generated folder; never launches the app's UI."""
from pathlib import Path
import argparse, json, os, subprocess, time, uuid, winreg

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--installer',type=Path,required=True);parser.add_argument('--upgrade-from',type=Path);args=parser.parse_args()
key=r'Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest'
for view in (winreg.KEY_WOW64_32KEY,winreg.KEY_WOW64_64KEY):
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER,key,0,winreg.KEY_READ|view):
            raise RuntimeError('A real standalone StreamNest installation is registered; test will not replace its registration')
    except FileNotFoundError:pass
target=(ROOT/'reference/standalone-install-tests'/str(uuid.uuid4())/'StreamNest').resolve()
assert target.is_relative_to((ROOT/'reference/standalone-install-tests').resolve())
desktop=Path(subprocess.check_output(['powershell','-NoProfile','-Command',"[Environment]::GetFolderPath('Desktop')"],text=True).strip())
shortcut=desktop/'StreamNest.lnk'
assert not shortcut.exists(),'Existing desktop shortcut must be preserved; use a clean test profile'
target.parent.mkdir(parents=True)
test_env=dict(os.environ,LOCALAPPDATA=str(target.parent/'Local'),APPDATA=str(target.parent/'Roaming'))
profile=Path(test_env['LOCALAPPDATA'])/'StreamNest/Profile'
profile.mkdir(parents=True)
preferences=profile/'upgrade-settings-sentinel.json'
preferences.write_text('{"subtitleMode":"anonymous","cacheFolder":"custom","autoNext":false}',encoding='utf8')
account=profile/'encrypted-account-sentinel.bin';account.write_bytes(os.urandom(128))
account_before=account.read_bytes();preferences_before=preferences.read_bytes()
if args.upgrade_from:
    subprocess.run([str(args.upgrade_from.resolve()),'/S','/D='+str(target)],check=True,env=test_env)
subprocess.run([str(args.installer.resolve()),'/S',*(['/UPDATE'] if args.upgrade_from else []),'/D='+str(target)],check=True,env=test_env)
assert account.read_bytes()==account_before and preferences.read_bytes()==preferences_before,'Upgrade changed profile/account files'
assert (target/'StreamNest.exe').is_file(),'Installer did not produce the executable'
assert json.loads((target/'package.json').read_text(encoding='utf8'))['name']=='StreamNest'
assert shortcut.is_file(),'Installer did not create the desktop shortcut'
# An uninstaller must not recursively erase user-added files.
sentinel=target/'user-added-keep.txt';sentinel.write_text('preserve',encoding='utf8')
subprocess.run([str(target/'Uninstall.exe'),'/S','_?='+str(target)],check=True)
for i in range(80):
    if not (target/'StreamNest.exe').exists():break
    time.sleep(0.25)
assert not (target/'StreamNest.exe').exists(),'Uninstall did not remove the app'
assert not shortcut.exists(),'Uninstaller left the desktop shortcut behind'
assert sentinel.read_text(encoding='utf8')=='preserve','Uninstall removed a user-added file'
print(json.dumps({'ok':True,'perUserInstall':True,'executableInstalled':True,'uninstallPreservedUserFiles':True,'upgradeProfilePreserved':True,'upgradeFromPrevious':bool(args.upgrade_from),'testFolder':str(target)}))
