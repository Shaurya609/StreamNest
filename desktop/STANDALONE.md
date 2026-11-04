# StreamNest Desktop 0.10.0 preview

This is a standalone Windows x64 application. Popcorn Time does not need to be installed. Windows 10/11 x64 are the initial targets; Linux, macOS and ARM desktop packages are not provided.

## Install or run portable

Use the per-user `StreamNest-Desktop-0.10.0-win-x64-Setup.exe`, or extract the portable ZIP and run `StreamNest.exe`. The installer needs no administrator access and creates a Start-menu entry. It does not launch the app automatically. Both packages share StreamNest's own profile; portable means no installation, not that accounts/settings travel with the ZIP.

Default locations:

- Program: `%LOCALAPPDATA%\Programs\StreamNest` (installer only).
- App settings, history and bookmarks: `%LOCALAPPDATA%\StreamNest\Profile`.
- Video cache: `%LOCALAPPDATA%\StreamNest\Cache`, changeable in Settings.
- Chromium storage is separate from Popcorn Time because the app manifest has its own name.

No Popcorn Time profile, subtitle credentials, watch history or cached media is imported. Connect subtitles inside StreamNest. Anonymous OpenSubtitles.org access remains available. The existing mobile APK is a separate product and is unchanged by this release.

The standalone app includes the 0.9.0 catalog/playback overlay: saved catalogs, signed snapshot refreshes, Hindi filters, anime/series navigation, next-episode autoplay and community intro/credits timestamps where available. Fresh-profile filter-model and view changes are included explicitly.

## Updates, uninstall and rollback

Legacy upstream updater/DHT configuration is disabled. Check for updates opens StreamNest's own releases page; no downloaded binary is automatically installed. Signed catalog refreshes remain separate and automatic. To roll back the app, close it and use the earlier standalone ZIP or installer; settings are retained. Settings schema migration beyond the preview version is not yet promised.

Uninstall removes the build's explicit file inventory and registration, retaining profile/cache and any unrelated files added to the install directory. It does not recursively erase a custom directory. Close StreamNest before updating. The installer rejects a folder containing `Popcorn-Time.exe`.

## Build from source

Requires Windows x64, Node 22 and Python 3.12 or later:

```powershell
npm ci --prefix desktop/packaging --ignore-scripts
python desktop/packaging/build.py
python desktop/packaging/verify.py --app reference/standalone-build/0.10.0/StreamNest
python desktop/packaging/smoke.py --app reference/standalone-build/0.10.0/StreamNest
python desktop/packaging/test_installer.py --installer dist/standalone/StreamNest-Desktop-0.10.0-win-x64-Setup.exe
```

The build never reads an installed app or a real profile. `desktop/upstream` preserves legacy Popcorn Time source commit `e484724a821a681bdd2a4c0a3de01f50dfbad60a`, recovered from the matching archived fork. Dependencies use its frozen Yarn lockfile. Install scripts are disabled; two audited upstream dependency patches are applied explicitly. Themes compile directly from Stylus. The shared overlay and standalone changes are applied to a generated staging folder, then branding and executable resources are generated from the approved SVG.

`toolchain.json` pins download URLs and SHA-256 checksums for the compatible NW.js runtime, NSIS compiler and rcedit. Source archive provenance and npm license/repository inventory accompany the product. Generated files stay under `reference/` and `dist/`, excluded from Git. A Windows GitHub Actions workflow repeats the clean build, package inspection, isolated startup and silent install/uninstall checks.

The application-source ZIP contains the original upstream application/build sources, StreamNest changes/build tools, generated runnable application source and production dependency source trees. Runtime origin/version/hashes and Chromium/NW.js notices are recorded separately under `third-party`; this is not a claim to have rebuilt Chromium or every native dependency from source. Desktop application changes retain upstream GPL v3 notices; Android licensing scope is separate.

## Preview limits and validation

This preview retains NW.js 0.44.5 for compatibility with the existing codec libraries; runtime modernization is pending. It is unsigned. It is a development preview, not a claim of support for every Windows installation, codec, casting device or long binge session.

Trakt synchronization is not configured: no upstream application's shared Trakt/TMDB/Fanart/TVDB keys are reused in the runnable package. The local library and catalog metadata continue to work. Additional external-account metadata features need a StreamNest application registration before enabling them.

Local tests have started the native standalone renderer with an isolated throwaway profile, rendered the movie catalog/language filters and decoded an 854x480 video fixture. No desktop mouse/keyboard input or personal account/profile data was used for this check. This is stronger than a module-only test but does not replace a hands-on TV/series/subtitle and long-playback check. See VALIDATION.md for completed results.

## Release build and extended check

The preview binaries and matching application-source ZIP were produced by the clean Windows [CI build](https://github.com/Shaurya609/StreamNest/actions/runs/41827321956) at commit `b984fae`. That job passed package inspection, native fresh-profile startup and silent per-user installation/uninstallation. The downloaded artifacts were checked against their SHA-256 file and rechecked locally.

A private local-torrent diagnostic verified all 11,053,871 fixture bytes, served the downloaded file through the packaged torrent HTTP server, and decoded video/audio in the native renderer. The video was 854x480, a non-black decoded frame was observed and decoded audio bytes advanced. DHT/tracker discovery was disabled for these diagnostic clients and only a local peer was supplied. This validates the packaged engine without relying on a live public torrent.

To repeat that extended check on a generated build with a local media fixture:

```powershell
python desktop/packaging/smoke.py --app reference/standalone-build/0.10.0/StreamNest --video path/to/local-test.mp4 --loopback
```

The loopback diagnostic temporarily substitutes only the generated app's opt-in smoke helper, restores it afterward and refuses to modify a real installed application. Normal product files and user profiles are not used for test state.
