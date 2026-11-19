# StreamNest 0.41.0 — Android projector startup follow-up

Android / Google TV / projector update. Windows remains at 0.40.0; its installer and portable downloads are available at https://github.com/Shaurya609/StreamNest/releases/tag/v0.40.0.

- Replace synchronous native have_piece calls in startup readiness, verified completion, subtitle hashing and HTTP reads with a shared verified-piece view. Completion/hash-failure alerts update it; status snapshots recover pieces already present in a resumed cache. Missing pieces still block video reads; sparse file length is never considered verification.
- Keep native work and worker control on separate threads. Shutdown remains token-authorized, with writer leases released only after confirmed process death. Validate exact worker name and all UID fields via /proc if the OEM process list omits an app-owned worker. Failure to validate retains the reservation.
- Stop automatic source cycling while worker shutdown remains unconfirmed.
- Collect at most three stack samples per stalled attempt, including pulse age, peers, transfer rate and progress. Report cache category, free space and the cache write-probe elapsed time without uploading the folder path. Existing diagnostic-sharing controls apply.

Limits: status snapshots and other engine operations still enter native code. This targets the observed blocking call and improves diagnosis/recovery; it does not establish the underlying firmware/native root cause or prove physical-projector playback. Exact projector S1E3, internal/USB storage, paused downloading, retries and relaunch acceptance remain open.

Validation is recorded in VALIDATION.md. Install through the normal signed update feed; Android requires installation confirmation.
