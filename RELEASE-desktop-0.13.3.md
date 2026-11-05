# Windows 0.13.3 · Android / Google TV 0.13.0

## Downloads

- **Windows:** `StreamNest-Desktop-0.13.3-win-x64-Setup.exe` is the standalone installer; the portable ZIP is also available. Popcorn Time is not required.
- **Android / Google TV / projector:** `StreamNest-0.13.0-universal.apk` remains current. The APK's sync screen passed device verification and did not require a production change.

## Resume & sync devices

Fixes the Windows Settings button appearing to do nothing. The sync dialog was positioned below the window because the bundled browser does not support CSS inset. Explicit compatible positioning now keeps it within the viewport. Sync and Settings controls have readable colors and keyboard focus indicators; empty/disconnected pairing no longer shows a blank QR block.

Verified actual Settings-button navigation, invalid-code errors, live group creation, visible QR, Sync now, disconnect/QR clearing, close/reopen and encrypted desktop-to-Android/Android-to-desktop resume. Disposable test groups were deleted. Android verification clicked its real Settings row, checked the visible input and QR/error handling, without changing the saved pairing or account.

Existing Windows 0.13.2 installations can use Check for updates → Install and restart. Windows 0.12.0–0.13.1 requires this installer once to repair the earlier update handoff. Settings and accounts remain in their existing profile.

Pair devices under Settings → Resume & sync devices. Intro/credits controls still depend on validated episode timestamps.
