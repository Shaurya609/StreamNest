# StreamNest sprints

## Sprint 1 — released in 0.17.0

Implemented: prominent desktop player Previous/Next controls; icon/text intro and credits controls with five-second progress fill; auto-skip disable in Settings; clipboard normalization for desktop pairing; ten-second progress uploads and forced pause/seek/exit updates, including a queued final update during another upload.

Passed: Android decoded-video fixture, hidden native Windows player and real Settings/pairing dialog, encrypted desktop-to-Android 125-second resume and 45-second rewind back to desktop. Tests used disposable groups/profiles; the owner's existing group was not replaced.

Android production auto-update to 0.18.0/code26 is confirmed. Laptop production update to 0.18.0 is confirmed by the owner. Remaining acceptance check: retry the original MSI short-code case. Clipboard variants pass, but the original exact input is unknown. All Windows checks continue in the background while the owner works.

## Sprint 2 — released in 0.18.0

Implemented: existing-group naming without re-pairing; per-device names and registered-device lists; connected-group status and hidden Create action while connected; a separate protected owner dashboard with creation/last-sync times. Existing apps keep progress-sync compatibility, and device registration appears as apps update. Metadata service failures do not block progress uploads.

Cloudflare schema migration and service deployment are complete. Owner authorization is separate from app pairing; the encrypted local owner key is excluded from Git and app packages. See WATCH-SYNC.md for owner access and metadata privacy.

Passed: 72 desktop/catalog/backend checks, Android build/unit/lint, live isolated Android group migration/rename/device list and hidden native Windows named-group/pairing/disconnect flow. Production packaging and installed-app update acceptance are tracked in VALIDATION.md.

## Sprint 3 — released in 0.19.0

Optional next-episode preloading is disabled by default on Android and Windows. It starts only once the current selected video is fully downloaded. One speculative episode is selected with existing canonical episode/source/quality rules. Low-storage checks reserve 256 MiB; cancellation closes the speculative engine before removing its files. Android adopts the preloaded engine; Windows reuses its files after the preload client closes. Active playback remains independent.

Previous episode data is retained until the next video has decoded frames, and its previous downloader has closed. Desktop exit waits for speculative clients before deleting cache. Failed source discovery does not skip over the missing episode.

Passed: 79 desktop/catalog/backend checks; hidden Windows real local torrent transfer, next-cache reuse, decoded next video, previous-cache purge and cancellation preserving active video; Android native hash-verified fixture pieces, engine adoption, decoded video before purge and cancellation preserving active cache. Android local HTTP/peer fixture transport stalled and remains unverified; no claim of a successful native network transfer is made for that harness. Tablet production auto-update to 0.19.0/code27 is confirmed; preloading stays disabled by default and its previous unpaired state is unchanged. Windows update is available and left to the owner while working. Validation is in VALIDATION.md.

## Later work

Source discovery implementation is complete for indexed episode lookup plus independent trusted anime releases and verified numbered batches; release acceptance is recorded in VALIDATION.md. External peer availability remains outside discovery guarantees. Sprint 4 is the home page, Continue Watching and recommendations; its owner dashboard was delivered early in Sprint 2.
