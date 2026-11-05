# StreamNest 0.14.0 — Automatic episode selection

Android / Google TV / projector and Windows standalone downloads are included below.

- Opening a series or anime automatically selects the season and episode from your newest saved playback record. Play resumes its timestamp.
- Completing an episode selects the next aired canonical episode, including season boundaries; Specials remain separate.
- Uses encrypted synced progress after pairing, with automatic launch/title refresh and local fallback when offline. Late sync does not override an episode you manually chose.
- Completion information and observed history ordering survive encrypted synchronization. Older unfinished-position history remains usable.
- Verified Android signed-feed access, newer-package validation, rejection of invalid/wrong-certificate/non-newer APKs and readable installer handoff.

Android still needs its normal install-source permission and system Update confirmation. Update all paired devices for the new episode-selection behavior. Windows 0.13.2 and newer can use Check for updates → Install and restart; Windows 0.12.0–0.13.1 needs a one-time direct installer to repair its old handoff.

Windows builds retain the readable update buttons and visible Resume & sync devices dialog fixes. See WATCH-SYNC.md and VALIDATION.md for behavior and verification.
