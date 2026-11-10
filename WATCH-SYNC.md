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

## Temporary pairing codes

Choose Show pairing code on a connected device, then enter its eight characters on the other device. Codes expire after five minutes and can be redeemed once. Existing recovery keys remain supported through Show recovery key.

Use the normal phone camera for QR pairing. The HTTPS page offers Open StreamNest, followed by in-app confirmation. A built-in camera scanner is not provided. The fragment carries the temporary code and is not sent in the landing-page request.

The pairing broker stores an AES-GCM encrypted ticket and a separately derived lookup digest. The ticket contains the existing group recovery key, encrypted using a key derived from the temporary code. Short codes have 40 bits of randomness and rely on short expiry and server rate limits. Progress continues using the existing 128-bit recovery key and encrypted sync protocol.

## Named groups and owner dashboard (0.18.0)

Updating keeps the existing recovery key and encrypted watch history. Updated devices register automatically with their existing group; older versions continue syncing progress but do not appear in the new device list until updated. Choose Save group name once, and all updated paired devices see that name after syncing. The Create a sync group action disappears while connected. Device names can be changed independently on each device.

The list shows registered devices and their last synchronization time, not a real-time online indicator. Disconnect removes that device from the list without deleting the group's shared history. Inactive device entries expire after 90 days; groups expire after 180 days without metadata updates.

Group/device names and activity timestamps are administrative metadata visible to the service owner. Playback titles/positions remain encrypted. Do not put sensitive information into names. Names are limited to 64 characters and a group supports up to 32 registered devices.

The private owner dashboard is at `/admin` on the sync endpoint. `/admin/data` requires the separate ADMIN_TOKEN Worker secret; no administration key is bundled in an app or committed to GitHub. On the deploying Windows account, manually run `tools/open-sync-dashboard.ps1` to copy the locally encrypted owner key and open the dashboard. Paste the key to load groups. The page keeps it only in memory; it is not placed in a URL or browser storage. The local key file is protected with Windows user encryption and is excluded from Git.

Apply the additive schema.sql migration before deploying this service update. Metadata failure does not block encrypted progress sync.
