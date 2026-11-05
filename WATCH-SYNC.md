# Private resume sync

StreamNest saves movie and episode positions locally. Open Settings → Resume & sync devices to create a private group on one device, then connect the other devices using its code or QR. No StreamNest account is required. Install version 0.13.0 or newer on every device.

Keep the pairing code private: it grants access to the group. Disconnect removes that device's pairing. Subtitle credentials and downloaded video are never synced.

Positions sync in the background while playing and on launch. Offline playback stays local until connectivity returns. Newer changes win, including deliberate rewinds. Completed videos restart from the beginning. Source and quality changes retain the same title/episode position.

The free Cloudflare Worker stores encrypted progress snapshots in D1. Encryption happens on the device; the pairing secret and plaintext titles never reach the database. Inactive groups are removed after 180 days. Free service quotas and outages can delay synchronization; local resume continues working. Devices must have reasonably correct clocks.

Deployment source is in `sync/`. Run `npm ci`, create a D1 database, update `wrangler.toml`, apply `schema.sql`, then deploy with Wrangler. Both embedded `watch-sync.json` files must use the same HTTPS endpoint. The deployed endpoint is https://streamnest-watch-sync.streamnest-shaurya609.workers.dev.

Intro/credits skip remains independent of resume sync. Anime and series show controls only when validated timestamps are available for the episode and fit its runtime; missing data does not produce guessed skips.

## Automatic episode selection (0.14.0)

Opening a series or anime selects the season and episode from its newest saved playback record. Press Play to resume its timestamp. A newly recorded completion selects the next aired canonical episode, including season boundaries; Specials stay in their own sequence. Metadata-only episodes are retained rather than silently skipped.

Watch history refreshes before selection. Offline/slow service keeps local history usable; a later sync result can update the selection only while you have not manually chosen an episode or started playback. New records retain an encrypted completion flag. Update paired devices to 0.14.0 for completion-driven selection; older unfinished-position records remain usable, and ambiguous older zero-position records are not guessed to be completed.
