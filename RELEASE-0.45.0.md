# StreamNest 0.45.0 — peer discovery and bounded final-source startup

Android / Google TV / projector update. Windows remains at [0.44.0](https://github.com/Shaurya609/StreamNest/releases/tag/desktop-v0.44.0).

- Preserve bounded, validated tracker hints supplied by the original public magnet when HTTP/cached torrent metadata is used. Retain existing trackers, deduplicate and request an announce after restoring missing hints. Do not supplement private torrents or introduce a new global tracker list.
- Apply the existing startup timeout policy to the final source even if video has not opened. Show a clear exhausted-source message instead of waiting indefinitely; preserve a real startup error rather than relabelling it as a peer failure. Worker cleanup stays off the UI thread with cache leases protected. Deliberate pause and active/blocked cleanup are excluded.
- Distinguish waiting for peers from buffering headers. Diagnostics now include source/restored tracker counts and bounded tracker-error counts/code, without tracker URLs or credentials. Existing sharing/retention controls apply.

Evidence: the latest physical-TV0.44.0 report concerns The Last of Us S1E5 on20November2026 around09:50–09:56IST, not the earlier S1E3 attempt. Four sampled sources had zero connected peers, zero verified startup pieces and no video-range requests. The last source remained there for over four minutes before the owner closed it. There was also a distinct first-source file-selection rejection. These observations do not establish a decoder defect or prove why peers were unavailable.

Validation:248 Android unit tests, lint/build and instrumentation compilation;252 desktop/catalog/backend checks. Isolated native tablet checks passed tracker preservation/deduplication/private-policy handling and final-source UI timeout/pause behaviour, without downloading video. Physical-TV playback after this update remains pending. Preserving source trackers fixes a verified code-path omission; it is not proof that a public torrent will acquire peers or that every historical TV stall is fixed.

Install using the normal in-app update workflow and confirm Android's installation prompt.
