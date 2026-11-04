# Standalone desktop packaging

The standalone Windows x64 preview is implemented in `desktop/packaging`, using pinned full legacy sources in `desktop/upstream`, the existing overlay and separate standalone branding/defaults. It produces a portable ZIP, per-user installer, application-source ZIP and checksums without reading an installed app or user profile.

See [standalone setup/build/validation](desktop/STANDALONE.md). The earlier desktop overlay remains available for installed Popcorn Time 0.5.1; it is separate from the standalone product.

The app has a distinct executable/manifest name, approved vector-derived icons, profile/cache directories and a manual StreamNest release link. Legacy upstream configuration/update feeds and shared integration keys are disabled in the runnable package. Signed catalog distribution remains active.

Remaining work: broader clean-Windows hardware testing, full hands-on playback/subtitle/binge checks, runtime modernization, optional StreamNest-owned Trakt metadata registration, code signing and non-Windows packages. The preview's complete application sources, build inputs, dependency inventory and runtime notices/provenance accompany the binary; Chromium/native runtime components are not claimed rebuilt from source.
