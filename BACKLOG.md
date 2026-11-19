# StreamNest backlog

Updated 20 November 2026. This is the current source of truth for open work. PENDING-FIXES.md, DESKTOP-PENDING.md and older SPRINTS.md entries contain release history; their historical pending statements do not reopen completed work.

## Owner backlog — tests for the owner

| ID | Test | What to confirm |
| --- | --- | --- |
| O1 | Physical TV: The Last of Us S1E3 on 0.43.0 | Retry playback after the normal update. Report whether startup, resume/seek and sustained playback work; if not, provide the approximate attempt time so the developer can inspect diagnostics. The cause of the earlier buffering is still unconfirmed. |
| O2 | Physical projector: longer acceptance session | Longer playback, repeated starts, paused downloading, episode transitions, internal/USB cache and relaunching. S1E3 smooth playback on 0.41.0 is already passed and must not be listed as an unimplemented fix. |
| O3 | TheBeast: original next-episode/recovery cases | Confirm Last of Us S1E2 advances to S1E3 rather than repeating, with preload on and off when convenient; observe normal buffered-stall recovery. Isolated handoff/recovery checks passed. General TV preload was already owner-confirmed working and remains closed. |

Owner checks are acceptance tests, not unfinished implementation. The developer retains responsibility for diagnosis, code fixes and developer-run verification. An owner report can create or update a developer issue; it does not automatically reopen every historical issue.

## Developer backlog — investigations, fixes and features

| ID | Work | Current scope / next step |
| --- | --- | --- |
| D1 | TV playback reliability — priority | Analyze the new 0.43.0 range, piece-wait, delivery and decoder evidence from O1; reproduce and fix the supported cause. Instrumentation and ARM32 tablet playback passed; a physical-TV fix is not yet established. Waiting for fresh device evidence is a dependency, not an owner coding task. |
| D2 | Anime source/member verification | 0.43.0 Android fixes Black Clover121 filename variants and rejects wrong episodes against four real metadata packs. Verify actual episode playback/source availability; inspect desktop parity before claiming that parser correction applies there too. Keep wrong/missing sources distinct from parsing defects. Do not reopen all anime coverage as one endless task. |
| D3 | Resume, subtitles and interaction — reliability Sprint4 | Review remaining legacy anime-history consolidation, subtitle-status accuracy, touch/remote consistency and Continue Watching gaps. Reproduce a concrete remaining gap before changing code. American Dad S2E1 subtitles on TheBeast are resolved and excluded. |
| D4 | Browsing and cleanup — reliability Sprint5 | Review remaining metadata request deduplication/negative caching, Home rendering and text normalization. Measure a concrete issue and preserve already-released optimizations; safe test-history cleanup must not alter real watch history. |
| D5 | Project website and guides | Explain the project and how it works in plain language; include download/setup, preloading, sync groups, cache-folder selection and other necessary usage guides. Requested feature, not yet implemented. |
| D6 | Optional further memory redesign — not the same as acceptance testing | Removing the remaining whole-piece fragmented assembly/hash allocation requires a separately scoped engine/store redesign. Previously released bounded reads, cache budgets, hash yielding and startup improvements stay closed. Do not treat O2 alone as unfinished optimization implementation. |

### Tracking rules

- Every open item belongs to owner testing or developer work. Link dependencies rather than combining both into an ambiguous pending item.
- Developer tests on the available tablet/laptop and log analysis belong to the developer backlog. Physical-device checks that need the owner's use belong to the owner backlog.
- Close owner-confirmed issues and record acceptance in VALIDATION.md. Do not reintroduce them from older notes.
- Source availability or missing provider timestamps are external limitations unless a concrete app defect or approved improvement is identified. Broader Korean dub/sub, filler and skip coverage do not become recurring generic backlog items without a specific failed case.
- Windows signing is intentionally out of scope. Personal subtitle-account retention is out of scope (anonymous access only). Cloudflare usage monitoring is routine operation, not backlog.
