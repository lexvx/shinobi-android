# Verification 0.2.2

Release candidate: version 0.2.2 / code 7. Camera/server configuration is not changed.

Runtime limits: host has no usable KVM; previous software-emulated Android30 remained ADB-offline. Do not count instrumentation compilation as device execution. Physical-phone playback, touch layout, and device-specific decoder stability remain to be verified.

- Read-only integration review: custom Settings page uses the shared screen lifecycle; bottom tabs expose semantic accessibility descriptions; one layout toggle repositions existing LiveWall children.
- Timeline review: content width is four viewports; horizontal panning is distinct from time selection; manual scrolling is not overridden by playback ticks. Position helpers account for actual day duration.
- Instrumentation smoke scenarios updated for semantic tab selectors, layout toggle, Settings, theme redraw and return. These scenarios are compiled only unless an actual device result is separately recorded.
- Per-camera recordings review: one VideoView remains attached while fullscreen changes surrounding layout; switching files clears old player listeners; generation + playback serial reject obsolete callbacks. Desired play/pause state survives quality changes and screen restoration. Fullscreen/back retains list and scroll state.
- First-use hints are dismissible and help is available from Settings. Actual gesture/layout behavior remains untested on a physical phone.
- Final JVM test run passed: ArchiveTimeline 17 (including four-viewport coordinates, edge clamps, centered scrolling, DST), MediaServices 11, API permissions/quality/interval/variant/origin isolation.
- Existing Kotlin 2.2 dependency metadata versus bundled lint analyzer 2.0 emits diagnostics; do not treat dependency lint coverage as complete.
- Final local `assembleRelease assembleDebugAndroidTest`: passed in54s, 88 tasks (11 executed). Test APK compiled; no device run claimed.
- Signed arm64-v8a and armeabi-v7a APKs verified. Certificate SHA-256 remains `8e434bc3cdab8ce013cb55ad023be178dbdbe96d0481a7b65f1fc95e3d49008d`; package/version confirmed `ru.lex42.shinobiviewer`, 0.2.2/code7, minSdk26/targetSdk35.
