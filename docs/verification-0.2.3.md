# Verification 0.2.3

Version 0.2.3/code8. Server camera configuration unchanged.

Runtime limitations: no usable KVM on this host; prior software-emulated Android did not complete boot. Instrumentation compilation is not device execution. Real phone playback, decoder-supported playback rates and gesture/layout appearance require device verification.

- Missing timeline root cause found in measurement: HorizontalScrollView measures child with an unspecified horizontal constraint; a plain View can resolve to zero width. Timeline now explicitly measures four viewport widths and 76dp height, before layout, with no layout-param mutation inside onSizeChanged.
- Instrumentation regression added: create viewer with no selected cameras, measure/layout, assert actual timeline child width equals four viewport widths and height equals76dp. This is an executable on-device regression, but was only compiled here.
- Direct camera-tile recording action has a distinct accessibility description; smoke tab lookup uses description-only to avoid duplicate «Записи» labels.
- Playback speed review: individual and synchronized players read actual applied speed and handle rejected/clamped rates; pause state restored after PlaybackParams changes. Hardware acceptance of16× remains unverified.
- `tests/run.sh`:19 timeline checks,11 media checks, API contracts passed.
- Final `assembleRelease assembleDebugAndroidTest`: successful in1m2s,88 tasks (28 executed); instrumentation including actual View measurement regression compiles. Existing Kotlin dependency metadata diagnostics from bundled lint remain; dependency lint coverage is limited.
- Both APK signatures verified against existing certificate SHA256 `8e434bc3cdab8ce013cb55ad023be178dbdbe96d0481a7b65f1fc95e3d49008d`. Manifest verified0.2.3/code8, minSdk26/targetSdk35.
