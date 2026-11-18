# StreamNest 0.30.0

Android and standalone Windows maintenance release.

- Reduce obsolete Android poster downloads/decoding and thumbnail-cache scans. Defer off-screen Windows Home artwork while preserving fallback posters and resume bars.
- Windows waits for installation to finish before opening the library. Update helper handles instances opened during extraction and verifies that restart stays running. Installer version metadata now follows the actual release.
- Repeatable live content checks validate anonymous English subtitles for Korean titles and anime/series timestamp selection.

Validation: Android unit/build/lint, 157 desktop/catalog/backend tests, native tablet image/content checks, packaged Windows Home and real update-helper/launch-gate probes. Release/installer acceptance is recorded in VALIDATION.md.

Known open work: the RK3562 projector native torrent-engine startup hang; whole-app RAM/CPU benchmarks; live Jikan later-page filler checks (service timed out). English audio depends on the actual source; this release does not claim every Korean title has an English dub.
