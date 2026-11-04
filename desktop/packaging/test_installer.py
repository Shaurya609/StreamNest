"""Silent install/uninstall in a generated folder; never launches the app's UI."""
from pathlib import Path
import argparse, json, os, subprocess, time, uuid, winreg

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--installer',type=Path,required=True);args=parser.parse_args()
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
subprocess.run([str(args.installer.resolve()),'/S','/D='+str(target)],check=True)
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
print(json.dumps({'ok':True,'perUserInstall':True,'executableInstalled':True,'uninstallPreservedUserFiles':True,'testFolder':str(target)}))
