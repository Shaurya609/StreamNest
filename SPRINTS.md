# StreamNest sprints

## Sprint 1 — released in 0.17.0

Implemented: prominent desktop player Previous/Next controls; icon/text intro and credits controls with five-second progress fill; auto-skip disable in Settings; clipboard normalization for desktop pairing; ten-second progress uploads and forced pause/seek/exit updates, including a queued final update during another upload.

Passed: Android decoded-video fixture, hidden native Windows player and real Settings/pairing dialog, encrypted desktop-to-Android 125-second resume and 45-second rewind back to desktop. Tests used disposable groups/profiles; the owner's existing group was not replaced.

Remaining acceptance checks: upgrade the actual installed apps using the auto-update flow, and retry the original MSI short-code case. Clipboard variants pass, but the original exact input is unknown. Windows installation/restart stays deferred while the owner is working.

## Sprint 2 — 0.18.0

Implemented: existing-group naming without re-pairing; per-device names and registered-device lists; connected-group status and hidden Create action while connected; a separate protected owner dashboard with creation/last-sync times. Existing apps keep progress-sync compatibility, and device registration appears as apps update. Metadata service failures do not block progress uploads.

Cloudflare schema migration and service deployment are complete. Owner authorization is separate from app pairing; the encrypted local owner key is excluded from Git and app packages. See WATCH-SYNC.md for owner access and metadata privacy.

Passed: 72 desktop/catalog/backend checks, Android build/unit/lint, live isolated Android group migration/rename/device list and hidden native Windows named-group/pairing/disconnect flow. Production packaging and installed-app update acceptance are tracked in VALIDATION.md.

## Later work

Additional source discovery (including unavailable anime), the home page and recommendations, and optional next-episode preloading with previous-episode cache removal remain separate future work.
