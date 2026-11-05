# Private resume sync

StreamNest saves movie and episode positions locally. Open Settings → Resume & sync devices to create a private group on one device, then connect the other devices using its code or QR. No StreamNest account is required. Install version 0.13.0 or newer on every device.

Keep the pairing code private: it grants access to the group. Disconnect removes that device's pairing. Subtitle credentials and downloaded video are never synced.

Positions sync in the background while playing and on launch. Offline playback stays local until connectivity returns. Newer changes win, including deliberate rewinds. Completed videos restart from the beginning. Source and quality changes retain the same title/episode position.

The free Cloudflare Worker stores encrypted progress snapshots in D1. Encryption happens on the device; the pairing secret and plaintext titles never reach the database. Inactive groups are removed after 180 days. Free service quotas and outages can delay synchronization; local resume continues working. Devices must have reasonably correct clocks.

Deployment source is in `sync/`. Run `npm ci`, create a D1 database, update `wrangler.toml`, apply `schema.sql`, then deploy with Wrangler. Both embedded `watch-sync.json` files must use the same HTTPS endpoint. The deployed endpoint is https://streamnest-watch-sync.streamnest-shaurya609.workers.dev.

Intro/credits skip remains independent of resume sync. Anime and series show controls only when validated timestamps are available for the episode and fit its runtime; missing data does not produce guessed skips.
