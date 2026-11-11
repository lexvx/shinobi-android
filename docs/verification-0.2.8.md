# Verification 0.2.8

Version 0.2.8 / code 13. User reports hang/crash after scrolling live cameras and switching to recordings. No target-device log or reproduction is available; do not claim a proven crash cause or confirmed phone fix.

New diagnostic helper reads last five process-exit metadata records on Android11+: reason, exit status, timestamp and memory samples. It omits OS descriptions and trace streams to avoid camera URLs/credentials. Historical records can belong to older app versions.

- Confirmed source risk: live navigation/retry synchronously called LibVLC.stop on the Android main thread for multiple players. This can block UI; it is not proof of the target phone's exact hang/native-crash cause.
- New lifecycle design dispatches stop to a serial native executor, detaches views on the UI executor, then releases native player ownership; engine release waits for completion. Retained TextureView surfaces prevent view removal from invoking LibVLC's synchronous native teardown callback.
- Independent review caught unsafe teardown after a failed stop; publication held for guarded failure handling and regression tests.
- Failed stop/detach/release now quarantines uncertain native ownership and engine until process exit; failed stop never detaches or releases surfaces. This intentionally retains resources on exceptional teardown instead of risking use-after-free.
- Individual-camera file rows have direct download/share actions that capture their own record and prompt SD/4K without starting playback or changing current quality.
- Final executed JVM suite passed: 12 lifecycle checks (blocked native stop does not block caller, phase ordering, engine ordering and failed-phase quarantine), 24 timeline, 11 media, API contracts.
- Final release/instrumentation build successful in50s,88 tasks (10 executed). Existing Kotlin metadata diagnostics limit dependency lint analysis. No Android/native runtime test executed.
- Both APK signatures verified unchanged: SHA256 `8e434bc3cdab8ce013cb55ad023be178dbdbe96d0481a7b65f1fc95e3d49008d`. Manifest0.2.8/code13,minSdk26/targetSdk35 confirmed.
