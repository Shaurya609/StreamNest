Unicode true
!include "MUI2.nsh"
!include "x64.nsh"
!include "nsDialogs.nsh"
!include "FileFunc.nsh"
Var UpdateParameters
Var UpdateMode
Var ShortcutChoice
Var ShortcutCheckbox
Var InstallMarker
Var InstallPid
Var InstallMutex
Name "StreamNest"
OutFile "${OUTPUT}"
InstallDir "$LOCALAPPDATA\Programs\StreamNest"
RequestExecutionLevel user
SetCompressor /SOLID lzma
!define MUI_ICON "${APPDIR}\src\app\images\streamnest.ico"
!define MUI_UNICON "${APPDIR}\src\app\images\streamnest.ico"
!define MUI_ABORTWARNING
!define MUI_CUSTOMFUNCTION_ABORT StreamNestAbortCleanup
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${APPDIR}\LICENSE.txt"
!insertmacro MUI_PAGE_DIRECTORY
Page custom ShortcutOptions ShortcutOptionsLeave
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
VIProductVersion "${VERSION}.0"
VIAddVersionKey /LANG=1033 "ProductName" "StreamNest"
VIAddVersionKey /LANG=1033 "FileDescription" "StreamNest per-user installer"
VIAddVersionKey /LANG=1033 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "LegalCopyright" "StreamNest contributors; Popcorn Time and Butter Project contributors"
Function ShortcutOptions
  !insertmacro MUI_HEADER_TEXT "Installation options" "Choose your shortcuts."
  nsDialogs::Create 1018
  Pop $0
  ${NSD_CreateCheckbox} 0 10u 100% 12u "Create desktop shortcut"
  Pop $ShortcutCheckbox
  ${NSD_SetState} $ShortcutCheckbox $ShortcutChoice
  nsDialogs::Show
FunctionEnd
Function ShortcutOptionsLeave
  ${NSD_GetState} $ShortcutCheckbox $ShortcutChoice
FunctionEnd
Function .onInit
  System::Call 'kernel32::CreateMutexW(p 0, i 0, w "Local\StreamNestInstaller") p .r0 ?e'
  Pop $1
  StrCpy $InstallMutex $0
  ${If} $1 == 183
    MessageBox MB_OK "Another StreamNest installation is already running. Wait for it to finish."
    Abort
  ${EndIf}
  SetShellVarContext current
  StrCpy $ShortcutChoice ${BST_CHECKED}
  ${GetParameters} $UpdateParameters
  ClearErrors
  ${GetOptions} $UpdateParameters "/UPDATE" $UpdateMode
  ${IfNot} ${Errors}
    StrCpy $UpdateMode "yes"
    StrCpy $ShortcutChoice ${BST_UNCHECKED}
    IfFileExists "$DESKTOP\StreamNest.lnk" 0 +2
      StrCpy $ShortcutChoice ${BST_CHECKED}
  ${EndIf}
  ${IfNot} ${RunningX64}
    MessageBox MB_OK "StreamNest requires 64-bit Windows."
    Abort
  ${EndIf}
  SetShellVarContext current
FunctionEnd
Function .onVerifyInstDir
  IfFileExists "$INSTDIR\Popcorn-Time.exe" 0 +3
    MessageBox MB_OK "Choose a separate StreamNest folder. The Popcorn Time installation is preserved."
    Abort
FunctionEnd
Section "StreamNest"
  IfFileExists "$INSTDIR\StreamNest.exe" 0 installFiles
  ClearErrors
  FileOpen $0 "$INSTDIR\StreamNest.exe" a
  IfErrors 0 unlocked
    MessageBox MB_OK "Close StreamNest before updating this installation."
    Abort
  unlocked:
  FileClose $0
  installFiles:
  SetOutPath "$INSTDIR"
  System::Call 'kernel32::GetCurrentProcessId() i .r0'
  StrCpy $InstallPid $0
  StrCpy $InstallMarker "$INSTDIR\.streamnest-installing"
  ClearErrors
  FileOpen $0 "$InstallMarker" w
  IfErrors markerFailed
  FileWrite $0 "$InstallPid"
  FileClose $0
  Goto markerReady
  markerFailed:
    MessageBox MB_OK "Cannot prepare installation. Check folder permissions and retry."
    Abort
  markerReady:
  File /r "${APPDIR}\*"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateDirectory "$SMPROGRAMS\StreamNest"
  CreateShortcut "$SMPROGRAMS\StreamNest\StreamNest.lnk" "$INSTDIR\StreamNest.exe" "" "$INSTDIR\src\app\images\streamnest.ico"
  ${If} $ShortcutChoice == ${BST_CHECKED}
    CreateShortcut "$DESKTOP\StreamNest.lnk" "$INSTDIR\StreamNest.exe" "" "$INSTDIR\src\app\images\streamnest.ico"
    IfErrors 0 +3
      MessageBox MB_OK "Windows could not create the desktop shortcut. StreamNest is available from the Start menu."
      ClearErrors
  ${EndIf}
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest" "DisplayName" "StreamNest"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest" "DisplayIcon" '$\"$INSTDIR\src\app\images\streamnest.ico$\",0' 
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest" "Publisher" "StreamNest contributors"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest" "NoRepair" 1
  Delete "$InstallMarker"
SectionEnd
Function .onInstFailed
  Delete "$InstallMarker"
FunctionEnd
Function StreamNestAbortCleanup
  Delete "$InstallMarker"
FunctionEnd
Section "Uninstall"
  SetShellVarContext current
  ; Delete the build's explicit inventory only. Preserve user-added files and profile/cache.
  !include "${DELETE_LIST}"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  Delete "$DESKTOP\StreamNest.lnk"
  Delete "$SMPROGRAMS\StreamNest\StreamNest.lnk"
  RMDir "$SMPROGRAMS\StreamNest"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\StreamNest"
SectionEnd
