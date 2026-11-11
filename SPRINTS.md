# StreamNest sprints

## Sprint 1 — released in 0.17.0

Implemented: prominent desktop player Previous/Next controls; icon/text intro and credits controls with five-second progress fill; auto-skip disable in Settings; clipboard normalization for desktop pairing; ten-second progress uploads and forced pause/seek/exit updates, including a queued final update during another upload.

Passed: Android decoded-video fixture, hidden native Windows player and real Settings/pairing dialog, encrypted desktop-to-Android 125-second resume and 45-second rewind back to desktop. Tests used disposable groups/profiles; the owner's existing group was not replaced.

Android production auto-update to 0.18.0/code26 is confirmed. Laptop production update to 0.18.0 is confirmed by the owner. The owner confirmed the MSI short-code and timestamp-sync issue resolved on 11 November 2026. All Windows checks continue in the background while the owner works.

## Sprint 2 — released in 0.18.0

Implemented: existing-group naming without re-pairing; per-device names and registered-device lists; connected-group status and hidden Create action while connected; a separate protected owner dashboard with creation/last-sync times. Existing apps keep progress-sync compatibility, and device registration appears as apps update. Metadata service failures do not block progress uploads.

Cloudflare schema migration and service deployment are complete. Owner authorization is separate from app pairing; the encrypted local owner key is excluded from Git and app packages. See WATCH-SYNC.md for owner access and metadata privacy.

Passed: 72 desktop/catalog/backend checks, Android build/unit/lint, live isolated Android group migration/rename/device list and hidden native Windows named-group/pairing/disconnect flow. Production packaging and installed-app update acceptance are tracked in VALIDATION.md.

## Sprint 3 — released in 0.19.0

Optional next-episode preloading is disabled by default on Android and Windows. It starts only once the current selected video is fully downloaded. One speculative episode is selected with existing canonical episode/source/quality rules. Low-storage checks reserve 256 MiB; cancellation closes the speculative engine before removing its files. Android adopts the preloaded engine; Windows reuses its files after the preload client closes. Active playback remains independent.

Previous episode data is retained until the next video has decoded frames, and its previous downloader has closed. Desktop exit waits for speculative clients before deleting cache. Failed source discovery does not skip over the missing episode.

Passed: 79 desktop/catalog/backend checks; hidden Windows real local torrent transfer, next-cache reuse, decoded next video, previous-cache purge and cancellation preserving active video; Android native hash-verified fixture pieces, engine adoption, decoded video before purge and cancellation preserving active cache. Android local HTTP/peer fixture transport stalled and remains unverified; no claim of a successful native network transfer is made for that harness. Tablet production auto-update to 0.19.0/code27 is confirmed; preloading stays disabled by default and its previous unpaired state is unchanged. Windows update is available and left to the owner while working. Validation is in VALIDATION.md.

## Sprint 4 — 0.21.0 release acceptance

Implemented Home on Android and standalone Windows: Continue Watching, timestamp/episode cards, local genre affinity for returning users, diverse discovery for new users, recently added and language/type rows. Home opens the existing title/quality flow. Android bounds row/image work in Low Memory mode and refreshes progress after sync; Windows uses the existing browser region and navigation, with a Home startup option. Owner dashboard work was already delivered in Sprint 2.

## Broader coverage, playback verification and Windows maintenance — 0.21.0

Independent EZTV episode discovery supplements the indexed provider for regular series. Trusted anime batches and existing movie mirrors remain. The bundled signed catalog now contains 866 movies, 10,029 series and 2,681 anime; 1,158 series are marked Korean. Counts describe metadata coverage only. Generic verified anime mappings retain canonical later seasons rather than truncating a title to its first MAL season. Optional mapped filler/recap labels are cached and fetched in the background.

Physical tablet Home/progress refresh/navigation and native intro/credits/settings-return checks passed in isolated verification apps. Live series and anime timestamp fallback checks passed. New Windows runtime 0.119.2 with matching pinned codecs passed hidden startup/video, Home, subtitle rendering, local fixture preload/cache handover and native updater checks. Precise release runs and validation limits are in VALIDATION.md.

Remaining external dependencies: live Jikan filler-page verification (timed out). Windows signing was subsequently declined by the owner for this personal project. The Android local HTTP/peer preload harness remains an open verification item; its hash-verified cache/video handover checks passed. These are not re-opened TV/projector or MSI user-reported bugs.

## Source availability scope

0.20.0 delivered the initial independent anime/batch discovery work. Broader movie/series/anime coverage remains an ongoing availability goal, with 0.21.0 adding independent series lookup and broader verified metadata. Finding a matching source does not guarantee reachable peers, working playback or a particular audio language.


## Shared recommendations follow-up — 0.22.0

Home recommendation rows now belong to the existing sync group. Android/Windows share encrypted ordering and lean metadata with backwards-compatible progress sync. Real disposable cross-platform sync, native Home rendering, Windows Home rendering, watched-title removal and group isolation passed. Low Memory shows the same row prefix. Windows signing is intentionally not planned by owner decision. Publication acceptance is recorded in VALIDATION.md.


## Home layout follow-up — 0.22.1

Fixed Windows renderer-owned styling, navigation clearance and visible/preserved keyboard focus. Added real geometry and screenshot checks across desktop themes/sizes. Android phone/tablet/TV-sized/Low Memory layouts and remote navigation passed on a disposable tablet app; physical TV/projector retesting remains distinct. Recommendation data and pairing are unchanged.


## Artwork and resume progress follow-up — 0.22.2

Home artwork now repairs missing metadata through IMDb and bounded identity-checked fallbacks on Windows/Android. Desktop has an explicit watched-progress fill; Android progress is unchanged. Shared recommendation identities and order are retained. Validation includes real poster decoding and actual desktop fill geometry.

## Unified Home search — 0.23.0

Desktop Home has a persistent search field. Android Home search opens directly without choosing a content category. Both search saved catalogs immediately and query movie/series/anime services independently, with grouped results, bounded result pages, partial-failure status and stale-query guards. Search is local to the device and does not replace shared Home recommendations. The owner confirmed TV universal-APK preload and physical TV/projector Home layout passed.

## Manual watched preferences and Android startup resilience — 0.24.0

Manual Watched preferences use their own domain-derived encrypted room with bounded metadata and timestamped undo tombstones. Recommendations consume them separately from playback history; old progress clients cannot erase them. Both Android title toggles and desktop Watched actions publish changes. Native startup now gets low-memory settings before session creation, staged monotonic watchdog status and busy-start suppression. A thread-specific guard handles only the pinned torrent library's known pop_alerts null-session exception during intentional shutdown. The projector-specific symptom is not claimed reproduced on the tablet.
