# On-device playback regression

Build `:app:assembleDebugAndroidTest`, install the matching app APK and test APK, then run:

```
adb shell am instrument -w app.streamnest.test/app.streamnest.PlaybackRegression
```

The test needs an unlocked device and an MP4 at the target application's private cache path `playback-regression.mp4`. It does not bundle a media fixture. Validation used the public [W3C Big Buck Bunny trailer](https://media.w3.org/2010/05/bunny/trailer.mp4), copied to the app cache through `adb shell run-as app.streamnest`. The test plays the local file in the actual player, dismisses the subtitle menu with Back, opens/closes subtitle account settings, checks decoded TextureView pixels and position advancement, and verifies deliberate pause preservation. The placeholder torrent does not supply the fixture.

An explicit catalog check can instead run with `-e catalog true`. It reports confirmed Hindi HD records, mixed-page Hindi count and a Hindi series page. The movie snapshot and normal live movie provider are used; no credentials are read or printed by this diagnostic.

The test app is separate from the distributed APK and can be uninstalled after verification. Remove only the supplied test fixture from the app cache afterward.
