# StreamNest 0.36.2

Windows startup and player-close reliability.

- Start the previously disconnected torrent-startup watchdog before metadata lookup. A source that makes no download progress for45 seconds moves to the next available episode source; overall startup is bounded at180 seconds per attempt. Successful playback stops the watchdog. Report metadata, local-server and video-buffer stages separately.
- Reused torrents wait for metadata, while warm metadata works without waiting for an event that has already fired. Cancelled source attempts release their listeners; errors identify the affected startup stage without exposing source URLs.
- Make player closing idempotent, stop queued animations and prevent delayed play/pause callbacks from touching disposed controls.
- Deduplicate and briefly cache optional artwork/Home metadata404 responses. Missing optional metadata retains placeholders and existing data; required catalog lookups and other service failures still report errors. Diagnostics identify the metadata service with an allowlisted label, never its URL/query.
- Android version alignment only; Android playback is unchanged. Existing resume sync, cache protection and buffered-playback recovery remain enabled.

TheBeast showed repeated pre-player startup attempts on0.36.1. The disconnected watchdog and unsafe queued UI callback are confirmed code defects; the old diagnostic TypeError contains no stack, so its precise origin cannot be proven. Normal playback confirmation on TheBeast remains separate from isolated regression tests.
