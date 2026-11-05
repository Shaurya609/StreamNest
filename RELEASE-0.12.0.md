# StreamNest 0.12.0

Automatic application update checks on launch for Android, Google TV/projectors and standalone Windows. Updates download in the background from GitHub releases, are checked against a signed platform-specific feed and SHA256, and are offered for installation.

Android requires its system installation confirmation and may require enabling Allow from this source. The APK must have the same package and signing certificate and a higher version code. Windows offers Install and restart, waits until playback is inactive, exits, runs the per-user installer in the existing folder and relaunches. Existing desktop-shortcut preference is retained during automatic updates.

Settings has Check for updates on Android; the desktop About/update action checks manually. Catalog updates retain their separate signed channel. Settings, subtitle accounts and cache preferences are preserved by in-place application updates. A manual installation of 0.12.0 is required once to enable the new updater.

The Windows runtime remains the unsigned legacy NW.js preview. Android packages remain development-signed. Device validation details are recorded in VALIDATION.md.
