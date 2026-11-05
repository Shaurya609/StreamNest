# Application updates

Android and Windows check the signed `updates/stable.json` feed at launch. The embedded update public key is separate from the catalog signing key. RSA/SHA256 signatures cover platform versions, artifact URLs, byte counts, checksums and expiry. Clients reject expired/tampered feeds, older trusted feed timestamps, foreign download URLs and non-newer app versions. GitHub HTTPS serves the feed and release artifacts.

Android downloads the universal APK to private application storage, verifies SHA256, package identity, versionCode and the installed certificate, and opens the system installer only after Install update is selected. Android installation approval and Allow from this source may be required. TV/projector behavior follows the device package installer. Failed checks leave the installed application intact. Manual checks are in Settings.

Windows downloads the signed feed's installer to the separate profile Updates folder. Install and restart is deferred while the player/loading view exists. A detached helper waits for the application process to exit, invokes the per-user NSIS installer with `/S /UPDATE /D=<existing folder>`, and relaunches only when the installer exits successfully. `/UPDATE` preserves whether a desktop shortcut already exists. Installation does not remove the profile, subtitle credentials or cache. A portable copy updated this way becomes a registered per-user installation in its existing folder.

Checks/downloads are automatic; installation uses the Android system confirmation or Windows Install and restart button. The updater-enabled release must be installed manually once. The original Popcorn Time installation/overlay has no StreamNest application updater.

## Publishing

`tools/updates/publish.py` reads public release metadata, uses independent Android `vX.Y.Z` and Windows `desktop-vX.Y.Z` versions, reads the Android versionCode from the exact release source and pins release asset SHA256 digests. It signs with `UPDATE_SIGNING_KEY` (repository Actions secret). `.github/workflows/app-update-feed.yml` refreshes metadata after published releases, on manual dispatch and weekly before the 90-day expiry. The private key is stored outside the repository; only public keys may be committed.

When another workflow creates releases using GITHUB_TOKEN, dispatch the feed workflow explicitly because those release events do not trigger another workflow. Manual releases using the maintainer token trigger it normally. For a local release, run the publisher with a secure local key and commit the signed output after both platform assets are available. Do not replace the Android signing key: existing devices will reject that update. Signing-key recovery/rotation requires a separately verified bootstrap release.

See VALIDATION.md for measured upgrade results and remaining device/UI limitations.
