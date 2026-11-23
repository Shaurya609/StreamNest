# StreamNest Desktop 0.45.0

- Opens the lightweight startup window before loading media-engine and provider dependencies. Normal launches show “Starting StreamNest”; the updating message is reserved for an active installation.
- Shows saved Continue Watching and recommendations before processing full catalogs. Live progress, completion, watched selections and removal records remain authoritative; no playback timestamps are stored in the Home preview.
- Loads bundled series, anime and release metadata only when requested, rather than eagerly during provider construction.
- Preserves the installer guard and the existing update, account, history and cache locations.

Validation: 219 desktop tests; packaged Home navigation, search, resume bars, removals, manual Watched actions, episode continuation and title details; installer launch guard. Performance comparisons use hidden disposable profiles and are not a guaranteed cold-start speedup on every PC.

Windows x64 standalone installer and portable package. Unsigned personal-project preview. Android remains 0.47.0.
