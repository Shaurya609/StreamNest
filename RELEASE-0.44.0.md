# StreamNest 0.44.0 — interaction and metadata request fixes

Android: allow a manual subtitle search/language change to supersede an in-flight automatic search. Ignore stale search/download errors after another choice, source/episode change or subtitles-off action. Subtitle attachment status reflects the player return value; attachment does not prove timed cues are present. Remote rewind/fast-forward uses normal seek handling. Home cards activate on the first touch and remain focusable with a remote.

Android metadata: share concurrent requests to the same URL, keep each caller's parsed JSON independent, and suppress repeated404/410 transport lookups for30seconds per URL (bounded128entries). Failed metadata still uses validated saved data where available; no successful-response TTL or stale-success preference is introduced. Cancellation of a waiting caller does not cancel the other callers' transfer.

Windows metadata: share concurrent required and optional JSON lookups while preserving per-caller parsed objects. Different explicit deadlines and required/optional policies stay independent. Existing optional404 caching and required-request error reporting remain intact.

Existing canonical resume selection, removal tombstones and sync remain in place. The isolated tablet first-tap/remote activation, incoming resume refresh and completed-episode-to-known-next checks passed. No speculative remapping of unidentified legacy history, production-history deletion, filesystem change or projector/TV reliability fix is claimed by this release. Changes are covered by unit, desktop/backend and package checks; exact acceptance is recorded in VALIDATION.md.

Use the normal in-app update workflow. Windows remains an unsigned standalone preview. Android requires system installation confirmation.
