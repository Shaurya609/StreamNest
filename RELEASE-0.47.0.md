# StreamNest 0.47.0 — Faster Home and USB-cache recovery

Android / Google TV / projector update (code 61). Windows remains 0.44.0.

- Show saved Continue Watching first, together with saved recommendations when available. Full catalog loading and online enrichment run separately. Saved title metadata never restores old progress: current history, removals and watched choices are applied each time.
- Avoid clearing Home while refreshing it, avoid repeated full catalog parsing on ordinary tab changes, and preserve remote focus against background refreshes.
- Reserve free internal space even when video is stored on USB. Check internal and selected-cache space before starting and while downloading; stop with an actionable storage message instead of cycling through sources on a disk error.
- When switching to removable storage under internal-space pressure, recover only recorded, stopped StreamNest video cache on built-in storage. Active/protected cache, watch history, sync groups and accounts remain intact. Playback & storage also provides a confirmed Clear stopped video cache action and shows both free-space figures.
- Request native torrent status asynchronously instead of blocking the engine worker in a synchronous piece-status call. Status queries do not overlap, and an uncompleted query has a bounded timeout. Native file errors are surfaced as storage failures.
- Use consistent internal/removable storage classification in player/worker diagnostics. The owner dashboard accepts separate internal/cache free-space measurements and explains low internal space even when USB has capacity.

Validation: 255 Android unit tests, lint and build; 32 backend tests. Isolated tablet checks passed for saved Home/current progress/removal/tab return, asynchronous native status, protected-cache cleanup and stopping source cycling after storage failure. Home preparation measured 12–36 ms from saved titles versus 2.2–3.0 seconds for full catalog rescans in the first comparison; a later run measured 14–32 ms versus 2.2–5.3 seconds. These are preparation timings, not projector launch guarantees. Real The Last of Us S1E3 played on the first source in tablet Low Memory mode, continued downloading while paused, resumed and advanced through the 60-second sustained sample.

The physical projector and its USB filesystem were not attached for this test. Retest the reported Remarried Empress S1E1 USB attempt after updating; do not treat the earlier one-byte USB probe as a full-drive acceptance test. Peer/source availability and slow external storage can still affect playback. No claim is made that this fixes the separately reported TV download-speed drop.

Use StreamNest's normal verified updater and confirm Android's installation prompt. Universal APK includes ARM32 and ARM64. No uninstall is needed.
