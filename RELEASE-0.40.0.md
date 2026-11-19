# StreamNest 0.40.0

Projector worker recovery and a targeted large-piece memory improvement.

- Android workers accept a token-validated self-termination command through their independent control thread. Parent cleanup keeps its binding while waiting for Binder-confirmed process death; an OEM process list omitting a PID no longer counts as proof that a writer stopped.
- Timed-out cleanup can be retried. Cache protection/reservations remain until confirmed death. Cache-writer contention and exhausted worker slots have distinct safe error classes.
- Diagnostic reports include shutdown mode/duration and, when sharing is enabled, one bounded slow-worker Java stack requested off the control thread. No raw paths, credentials or exception messages are uploaded.
- Windows reuses a complete large piece's existing buffer only when every block is contiguous in the same bounded backing allocation. Small, fragmented, incomplete or unrelated-backed pieces keep the upstream assembly path. Hash verification and storage commit are unchanged.

Validated: fully frozen worker retains its cache lease; queued self-abort stops it without process-list lookup; retry and three same-cache restarts work. Real S1E3 starts in an isolated Low Memory tablet app, retains position during a ten-second pause while downloading continues, resumes and plays through a further60-second observation. Physical-projector acceptance is still required.

A local128MiB webseed transfer was hash-verified with and without the memory change. Alternating engine-only runs showed lower external/RSS peaks for contiguous pieces; this is not a universal whole-app RAM percentage improvement. Fragmented-piece assembly and the underlying trigger of any remaining native stalls remain open. Use the normal in-app updater; Windows remains unsigned by owner choice.
