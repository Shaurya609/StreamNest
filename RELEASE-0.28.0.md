# StreamNest 0.28.0

Owner dashboard group deletion and current/recent watching activity on Android / Google TV / projector and Windows.

- Owner-only Delete sync group action with exact-name confirmation, transactional cleanup and permanent primary-code revocation. Local device history remains available.
- Separate default-on watching-activity setting with a persistent per-installation off choice and first-use notice. Shares title, type, episode and playback state; encrypted resume history remains separate.
- Dashboard activity freshness labels, automatic 30-second refresh and a 24-hour latest-report limit. Turning sharing off requests deletion; offline reports expire automatically.
- Clients recognize deleted groups and disconnect safely instead of silently recreating them.

Validation: 146 Node checks, native Android settings/live report acknowledgement, hidden Windows settings/live report acknowledgement, dashboard confirmation/display checks and live disposable-group deletion/revocation. Full build and release acceptance is recorded in VALIDATION.md. No existing user group was deleted. Windows remains unsigned.

Universal/ARM32/ARM64 APKs and standalone Windows installer/portable downloads. [Sharing and deletion guide](https://github.com/Shaurya609/StreamNest/blob/main/WATCH-SYNC.md#owner-controls-and-watching-activity-0280).
