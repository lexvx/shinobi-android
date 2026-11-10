# Verification 0.2.0

- Local `assembleRelease assembleDebug assembleDebugAndroidTest`: passed (105 tasks, 83 seconds).
- `tests/run.sh`: timeline 8 checks, media services 11 checks, API permissions/quality/interval/variants/origin checks passed.
- Deployed server API source inspected read-only: authorized monitor filtering, archive variants and date filtering match client contract.
- Release certificate continuity checked against shared Android signing key (`dark-led`).
- Live playback on the user's physical phone is not yet verified for this release. Mock UI checks do not establish decoder performance or external network throughput.
- Android 30 x86_64 emulator could be launched only with software CPU emulation (no KVM). It remained ADB-offline during the available boot check. Instrumentation APK compiled, but Keystore/MediaStore and UI smoke tests have NOT executed successfully. Do not count these as passed.
- Test fixture `tests/mock_server.py` uses only dummy credentials and camera names. The optional instrumentation `-e uiSmoke true` expects this fixture at `http://10.0.2.2:18765`.
