# Verification 0.2.5

Version 0.2.5 / code 10. No server or camera configuration changes.

Runtime limitations: Android instrumentation is compiled only, no successful device/emulator execution. Physical-phone visual behavior and playback remain unverified here. Pure JVM checks and compilation must not be described as device acceptance.

- Theme review: migration marker `system_theme_migrated_v1` resets inherited theme to system once; later explicit choices remain untouched. Instrumentation adds legacy-dark→system migration and explicit-light→restart persistence assertions, compiled only.
- Feed review: home uses one column and no grid/feed preference or toggle. Existing order and scroll behavior retained; refresh label now explicitly refers to the camera list.
- Live status review: explicit phases classify connecting/buffering/retrying as orange, playing as green, exhausted attempts as red; no message-substring classification.
- Archive review: playback has no lamps; transient loading/buffering/errors remain readable text; successful SD/4K hides the status overlay. Timeline sizing/time bubble unchanged.
- Executed JVM checks: 24 timeline, 11 media and API contract checks passed.
- Local release and instrumentation build passed in 1m13s, 88 tasks (28 executed). Existing Kotlin dependency metadata diagnostic limits dependency lint analysis; build succeeded.
- Both signed APKs verified with existing certificate SHA256 `8e434bc3cdab8ce013cb55ad023be178dbdbe96d0481a7b65f1fc95e3d49008d`. Manifest confirmed 0.2.5/code10, minSdk26/targetSdk35.
