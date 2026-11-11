# Verification 0.2.7

Version 0.2.7 / code 12. Server configuration unchanged.

Device limitation: no successful Android runtime execution here. Instrumentation tests are compiled only. Keyboard geometry, fullscreen controls and playback on the target phone remain unverified at runtime.

- Two read-only reviews agreed: login content measures form intrinsically, anchors its scroll container at viewport bottom, assigns remaining space to illustration with a small minimum floor. API30+ uses max(systemBar,IME) bottom once; older Android uses adjustResize without duplicate padding. No illustration hiding remains.
- Compiled instrumentation now measures login at700/400/200dp viewport heights, checking bottom anchoring, nonzero illustration and no form/art overlap. These are simulated layout constraints, not an actual keyboard test.
- Export review: top download/share menus capture selected recording and pass independent SD/4K selection; playback quality state is not modified by export choice.
- Live lamps removed; ordinary playback hides textual status while connecting/retry/error remain available. Fullscreen has one top export pair and one bottom transport/quality/rate arrangement.
- Executed JVM checks passed:24 timeline,11 media,API contracts. Release/instrumentation build successful in57s,88 tasks (28 executed). Existing Kotlin metadata diagnostics limit lint analysis of that dependency.
- Both APKs verified with unchanged signing certificate SHA256 `8e434bc3cdab8ce013cb55ad023be178dbdbe96d0481a7b65f1fc95e3d49008d`. Manifest confirms0.2.7/code12,minSdk26/targetSdk35.
