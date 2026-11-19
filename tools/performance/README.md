# Repeatable performance checks

Use a separate extracted Windows release under `reference/`, never the installed application. Install `psutil` in a task-local Python environment, then run:

```
python tools/performance/profile-desktop.py --app reference/extracted/StreamNest --output reference/performance-comparison/run-1
```

The tool uses separate Local/Roaming/Chromium directories, opens Home in the hidden smoke window, samples all owned application processes, and restores the injected probe. It records first-profile and second-profile Home readiness,35 seconds of CPU/working-set/private-commit observations, and errors. CPU is cumulative process-tree seconds; private commit is not physical RAM/PSS. Summed working sets can count shared pages more than once. First-profile launch is not an OS cache flush. Use matching history, assets, display size, network conditions and at least several alternating version runs before claiming a percentage improvement. Results with errors, missing Home or timeouts are rejected.

Android `PlaybackRegression` has isolated-package `optimizationProfile=true` (unlocked Low Memory Home launch and30-second main-process PSS/CPU) and `optimizationData=true` (resume lookup, removal/legacy semantics and diagnostic hashing). Use only a separately built verification application ID. Restore production build configuration afterward. This is a tablet policy check, not a hard1GB projector simulation. Do not treat locked-device profiles as acceptance.

See PERFORMANCE.md and VALIDATION.md for measured results and limits. No production history, sync key or account should be copied into a benchmark profile.
