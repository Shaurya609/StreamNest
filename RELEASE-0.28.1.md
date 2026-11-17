# StreamNest 0.28.1 — Android mobile layout fix

Fixes the mobile header and navigation overlapping Android's status/notification bar. Browsing and settings now reserve system-bar, display-cutout and keyboard space, including side insets in landscape. Repeated inset changes do not accumulate padding. Fullscreen video playback keeps its existing immersive behavior.

Universal, ARM32 and ARM64 APKs are below. This is an Android-only update; Windows remains on 0.28.0. [Windows standalone installer and portable downloads](https://github.com/Shaurya609/StreamNest/releases/tag/desktop-v0.28.0).

Validation and device-test limits are recorded in VALIDATION.md.
