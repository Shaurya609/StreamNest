# StreamNest Desktop 0.10.0 preview

A standalone Windows x64 app: install the Setup executable, or extract the portable ZIP and run StreamNest.exe. Popcorn Time does not need to be installed. The existing Android release and legacy desktop patch are separate downloads.

This preview includes StreamNest branding and the approved vector-derived icon, its own executable/Start-menu identity, separate settings/cache, saved catalogs, Hindi filters, anime/series episode navigation, natural-end autoplay and timestamp-based intro/credits skipping where data is available. Subtitle accounts are entered inside this app; existing Popcorn Time accounts and libraries are not imported.

The per-user installer needs no administrator access. Uninstall preserves settings/cache and unrelated files added to the program folder. Legacy upstream updates and DHT configuration are disabled; app updates are manual through StreamNest releases, while signed catalog refreshes remain automatic.

The portable ZIP, installer and matching application-source ZIP come from clean Windows CI build 41827321956, source commit b984fae. SHA-256 checksums are supplied. The pinned full legacy application source, dependencies/build instructions and third-party notices accompany the release.

Verified: clean build/package inspection, fresh-profile renderer startup, silent install/uninstall in CI and locally, plus a private local-torrent transfer of 11,053,871 bytes followed by decoded video/audio in the native player. Forty desktop/publisher test groups passed.

This is an unsigned development preview retaining the compatible legacy NW.js 0.44.5 runtime. Runtime modernization, broader hands-on playback/subtitle/binge testing and non-Windows packages remain pending. Trakt sync and extra account-based metadata features are not configured; shared upstream application keys are not reused. The local library, catalog metadata and subtitle integrations remain available.

See desktop/STANDALONE.md in the repository for locations, build instructions, verification details and limits. The normal user profile is %LOCALAPPDATA%\StreamNest\Profile and default video cache is %LOCALAPPDATA%\StreamNest\Cache.
