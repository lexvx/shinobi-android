# Verification 0.2.4

Version0.2.4/code9. No server/camera configuration changes.

Device limitation: no successful Android emulator boot or physical-device execution here. Instrumentation tests, including the eight-viewport timeline measurement assertion, are compiled only. Actual touch appearance/playback still requires device verification.

- Navigation review: immutable recording origin captures home tab versus fullscreen camera and quality; date changes preserve origin; toolbar and system Back share the return helper. Archive fullscreen exits fullscreen first. Camera wall restores persisted layout/order and saved scroll position.
- Bubble review: fixed-size sibling above the horizontal scroll viewport avoids clipping by scroll content; x is clamped inside viewport. Time uses post-scroll content coordinate, updates during touch/scroll and hides on release/cancel/background.
- Instrumentation expectation updated to eight viewport widths. Added direct tile recordings → toolbar back → camera wall assertion. These tests are compiled, not executed here.
- Executed JVM checks passed:24 timeline (including 8× width, finger time and bubble clamp),11 media, API contracts.
- Release and instrumentation build passed in58s,88 tasks (28 executed). Existing transitive Kotlin metadata lint diagnostic remains a limitation of dependency lint analysis.
- Both APK signatures verified unchanged: SHA256 `8e434bc3cdab8ce013cb55ad023be178dbdbe96d0481a7b65f1fc95e3d49008d`. Manifest version0.2.4/code9, minSdk26/targetSdk35 confirmed.
