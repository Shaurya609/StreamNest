# StreamNest backlog

Updated 20 November 2026. This is the current source of truth for open work. PENDING-FIXES.md, DESKTOP-PENDING.md and older SPRINTS.md entries contain release history; their historical pending statements do not reopen completed work.

## Owner backlog — tests for the owner

| ID | Test | What to confirm |
| --- | --- | --- |
| O1 | Physical TV: The Last of Us S1E3 on 0.44.0 | Retry playback after the normal update. Report whether startup, resume/seek and sustained playback work; if not, provide the approximate attempt time so the developer can inspect diagnostics. The cause of the earlier buffering is still unconfirmed. |
| O2 | Physical projector: exFAT USB and longer acceptance session | First confirm the projector recognizes the freshly formatted exFAT USB (label StreamNest), select its cache folder and retry the same episode/source. Then check longer playback, repeated starts, paused downloading, episode transitions, internal/USB cache and relaunching. S1E3 smooth playback on 0.41.0 is already passed and must not be listed as an unimplemented fix. |
| O3 | TheBeast: original next-episode/recovery cases | Confirm Last of Us S1E2 advances to S1E3 rather than repeating, with preload on and off when convenient; observe normal buffered-stall recovery. Isolated handoff/recovery checks passed. General TV preload was already owner-confirmed working and remains closed. |

Owner checks are acceptance tests, not unfinished implementation. The developer retains responsibility for diagnosis, code fixes and developer-run verification. An owner report can create or update a developer issue; it does not automatically reopen every historical issue.

## Developer backlog — investigations, fixes and features

| ID | Work | Current scope / next step |
| --- | --- | --- |
| D0 | Projector USB-cache streaming failure — priority | Owner confirms projector internal cache plays, but selecting a folder on the plugged-in USB drive causes streaming to fail. Latest retrieved projector report is 0.41.0, uploaded20Nov01:03:58 IST. Removable-classified attempts passed a small write probe (~0.5–0.7s, >11GiB free) but included native status-call stalls and unconfirmed worker shutdown. Later custom-classified playback advanced from681 to6755 displayed frames. The owner identified the failing location as the plugged-in USB drive; compare the same source after switching between internal cache and USB, and inspect actual large/random I/O and cleanup. Fix inconsistent storage classification: worker uses app-private/removability checks, player uses path prefixes, and custom cannot identify built-in versus removable storage. Root cause is unconfirmed; this is developer investigation, not merely owner acceptance. USB follow-up: saved S1E3 sources include a5,111,689,673-byte video, while startup requests footer pieces. Check drive filesystem/per-file size limit (FAT32 candidate), then random-write/sync performance and file-error handling. Current telemetry omits filesystem/selected file size and the alert listener does not capture FileErrorAlert; a small one-byte write probe does not validate large torrent files. Read-only laptop check now confirms the USB is FAT32 (~15.65GB total/~13.80GB free). Cached torrent metadata includes a5,456,108,844-byte S1E1 video that cannot fit FAT32; saved S1E3 candidate is5,111,689,673bytes. This proves a large-file incompatibility, not the sole cause of smaller-source stalls. Implement filesystem/file-size-aware source validation with clear errors/fallback, consistent storage labels and bounded disk-error evidence; separately investigate smaller-source I/O and cleanup. Owner subsequently authorized formatting: the SanDisk Cruzer Blade USB was formatted successfully as exFAT, labelled StreamNest, and Windows reports it healthy. Prior contents were erased. FAT32 incompatibility is addressed for this drive; projector recognition and playback acceptance remain O2. General app safeguards and any residual smaller-source stall investigation remain developer work. |
| D1 | TV playback reliability — priority | Analyze the range, piece-wait, delivery and decoder evidence from O1; reproduce and fix the supported cause. Instrumentation and ARM32 tablet playback passed; a physical-TV fix is not yet established. Waiting for fresh device evidence is a dependency, not an owner coding task. |
| D2 | Anime source/member verification | 0.43.0 Android fixes Black Clover121 filename variants and rejects wrong episodes against four real metadata packs. Verify actual episode playback/source availability; inspect desktop parity before claiming that parser correction applies there too. Keep wrong/missing sources distinct from parsing defects. Do not reopen all anime coverage as one endless task. |
| D5 | Project website and guides | Explain the project and how it works in plain language; include download/setup, preloading, sync groups, cache-folder selection and other necessary usage guides. Requested feature, not yet implemented. |
| D6 | Optional further memory redesign — not the same as acceptance testing | Removing the remaining whole-piece fragmented assembly/hash allocation requires a separately scoped engine/store redesign. Previously released bounded reads, cache budgets, hash yielding and startup improvements stay closed. Do not treat O2 alone as unfinished optimization implementation. |

### Completed developer pass — reliability Sprints4/5, 0.44.0

Implemented concrete interaction/subtitle-status and metadata-duplication gaps found in review. Native isolated tablet first-touch/remote activation, resume refresh and known-next passed; 244 Android unit tests/lint/build and252 desktop/catalog/backend checks passed. Existing canonical history, sync tombstones and production data remain intact. No unidentified legacy record was guessed into an episode; additional migration needs a concrete identifiable case, not a recurring generic backlog item. Publication/installed update status is tracked in VALIDATION.md. Physical TV/projector acceptance remains separate above.

### Tracking rules

- Every open item belongs to owner testing or developer work. Link dependencies rather than combining both into an ambiguous pending item.
- Developer tests on the available tablet/laptop and log analysis belong to the developer backlog. Physical-device checks that need the owner's use belong to the owner backlog.
- Close owner-confirmed issues and record acceptance in VALIDATION.md. Do not reintroduce them from older notes.
- Source availability or missing provider timestamps are external limitations unless a concrete app defect or approved improvement is identified. Broader Korean dub/sub, filler and skip coverage do not become recurring generic backlog items without a specific failed case.
- Windows signing is intentionally out of scope. Personal subtitle-account retention is out of scope (anonymous access only). Cloudflare usage monitoring is routine operation, not backlog.
