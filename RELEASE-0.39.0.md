# StreamNest 0.39.0

A measured optimization pass for Windows and Android, preserving playback, sync, quality selection and preload behavior.

- Windows uses byte budgets for retained whole-piece caches:32MiB active and8MiB preload. A piece larger than the budget is read normally but not kept in that cache.
- Verified writes apply download backpressure at64MiB active/32MiB preload, resuming when writes drain. Already-requested blocks and required hash verification finish normally; this is not a hard total-memory cap. Adopting a preload restores active byte budgets.
- Home checks a progress revision rather than reserializing/sorting unchanged history every3 seconds. Saves, remote merges and removals still refresh Home; unchanged sync does not create a revision.
- Desktop's diagnostic fallback timer uses its existing30-second sample cadence rather than waking every second. Playback event reporting remains enabled.
- Android startup reads only the selected resume record instead of parsing all history. Legacy numeric positions, invalid records and removal markers retain their behavior. The current diagnostic scope hash is cached and cleared on opt-out/group reset; the scope ID stays identical.

Validation includes actual American Dad video/audio with pause, resume and seek, controlled delayed/error disk writes, preload promotion, Home revisions, native history/scope comparisons, and first-profile/warm Windows process-tree plus unlocked tablet Home profiles. Repeatable performance tools and measurement limits are in tools/performance and PERFORMANCE.md.

Focused allocations/CPU work are reduced. Short whole-app profiles do not establish a consistent overall startup, CPU or RAM percentage improvement. Full-piece hash assembly and long physical-projector playback remain open. Windows stays unsigned by owner choice.
