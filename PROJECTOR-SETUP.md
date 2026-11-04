# Projector setup

1. Install the ARM64 APK on 64-bit Android firmware, or the ARM32 APK on 32-bit firmware. Android 14 alone does not identify the ABI. The universal APK includes both but is larger. The projector was not attached to verify its ABI.
2. Open Settings → Cache folder & Low Memory mode → Low Memory mode. Use Automatic on a detected 1 GB device, or explicitly choose Low Memory mode.
3. Start with Quality → 1080p and an x264/H.264 source. Test 4K/HEVC separately only if the device's decoder supports it.
4. For external storage, select an app-storage location shown under Change cache folder. Keep the drive attached while playing. Choose a drive, then its app folder or browse a particular folder. Shared/USB folder browsing requires optional Android Files access; app folders need no additional permission. No raw path entry is required.
5. Use the remote's directional keys and center/OK to navigate. Media play/pause, stop, rewind and fast-forward keys are supported; the on-screen player buttons can also be focused and selected.

The user confirmed that the app works on the Android 14 projector. Long binge sessions, memory/performance measurements and external-storage hardware remain to be tested on its 1 GB hardware.

Playback is full-screen landscape. Controls hide after 3.5 seconds; tap the video or use a directional/OK remote key to reveal them. Settings → Exit StreamNest clears video cache after playback stops. After a force-stop, leftover video cache is cleared on next launch.
