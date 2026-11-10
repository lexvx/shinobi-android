# Android client redesign

User requirements, 2026-11-10:
- Default server https://cam.lex42.ru, editable in Settings and before login.
- Designed login screen; optional password storage encrypted with Android Keystore; erase on explicit logout/forget.
- Authorized monitor API determines camera visibility, never a fixed list of eight IDs.
- Cameras tab: compact grid of every authorized camera; double tap opens landscape fullscreen; quality, snapshot, per-camera file archive.
- Viewer tab: authorized camera selection, date/time picker, shared timeline, synchronized multi-camera archive playback, missing-recording gaps.
- Light, dark and system theme.
- SD default for live and archive; explicit high-quality selection.

Server implementation inspected locally:
- shinobi-low-archive/all-cameras/deployment-status.json: eight SD recorders, authenticated variants API, Range support.
- shinobi-portal/player.js: variants=1 metadata; separate playback and download selection.
- shinobi-portal/wall.js: shared absolute time, recording interval lookup, per-camera gaps, bounded metadata requests.
- Verify deployed source before relying on these snapshots; do not modify server or concurrent portal work.

Acceptance:
- Restricted account cannot see or request unauthorized cameras.
- Successful and failed login, expired session, logout, encrypted remembered credentials.
- Grid, double tap, fullscreen/back/rotation, snapshots and permissions.
- Both qualities, SD absence, buffering/error/retry.
- Viewer synchronizes absolute timestamps, including gaps and segment boundaries.
- Both themes, background lifecycle, physical phone playback, release signature continuity.

Confirmed additions: pinch zoom; long-press drag reorder persisted per account; archive date filter; download and share SD or 4K; clear buffering and connection indicators. No mobile-data restriction on high quality.
