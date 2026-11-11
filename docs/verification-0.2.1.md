# Verification 0.2.1

- `tests/run.sh`: timeline 8 checks, media services 11 checks, API permissions/quality/interval/variants/origin isolation passed.
- Read-only lifecycle review: removed old player listeners before release; stale Activity/socket callbacks guarded; generation checks before/after socket handshake; native live players released in background and recreated on return. These changes address observed risks, but do not establish the root cause of a target-phone crash without its log.
- Adaptive building icon reuses the existing login illustration, with its aspect ratio preserved. Label: Камеры дома №9. Version: 0.2.1 (6).
- Instrumentation selectors updated for the new login and tabs; mock endpoint injected only by optional test setup. No real credentials in fixtures.
- No successful device/emulator execution: host lacks KVM and the previous software-emulated API30 boot remained ADB-offline. Keystore/MediaStore, drag gestures, video playback and visual behavior must not be represented as runtime-tested on Android.
- Physical phone HEVC/4K playback, decoder behavior, external throughput and background/foreground stability remain unverified.
- Local release and instrumentation APK build passed. Lint emitted transitive Kotlin metadata-version diagnostics, but `lintVitalRelease` and the build completed successfully; this limits the lint analyzer coverage for that dependency.
- Final incremental build after socket-race patch: successful, 88 tasks (8 executed), 1m16s. Release manifest verified: package `ru.lex42.shinobiviewer`, versionCode 6, versionName 0.2.1, minSdk26/targetSdk35.
- Both release APKs verified with apksigner. Certificate SHA-256 matches 0.2.0: `8e434bc3cdab8ce013cb55ad023be178dbdbe96d0481a7b65f1fc95e3d49008d`.
