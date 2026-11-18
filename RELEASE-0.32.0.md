StreamNest 0.32.0 improves anime discovery and subtitle/audio handling on Android, Google TV and Windows.

- Trusted batch search when numbered search finds no verified episode; exact torrent/member checks remain required.
- Independently mapped AnimeFillerList fallback for Black Clover and One Piece when Jikan fails. Later pages supported; mixed canon is not pure filler and unknown recap remains unknown.
- Next episode retains mapped anime identity for Windows intro/credits lookup.
- Prefer exposed English dialogue tracks for Korean titles. Dub availability depends on the actual source.

Validation:210 Android unit tests, lint/build;165 desktop/catalog/backend tests; disposable tablet track selection, visible ASS, anonymous anime/Korean subtitle download and live later-episode filler; Windows native MKV/audio/subtitle checks. Live skip data returned for Black Clover, Severance and Squid Game. One Piece1000 returned no validated segments. Peer availability and release-specific subtitle timing vary. Windows preview remains unsigned.

Android universal, ARM32 and ARM64 APKs. Windows standalone installer and portable archive. Signed auto-update publication follows asset verification.
