# Verification 0.2.0

- Local `assembleRelease assembleDebug assembleDebugAndroidTest`: passed (105 tasks, 83 seconds).
- `tests/run.sh`: timeline 8 checks, media services 11 checks, API permissions/quality/interval/variants/origin checks passed.
- Deployed server API source inspected read-only: authorized monitor filtering, archive variants and date filtering match client contract.
- Release certificate continuity checked against shared Android signing key (`dark-led`).
- Live playback on the user's physical phone is not yet verified for this release. Mock UI checks do not establish decoder performance or external network throughput.
