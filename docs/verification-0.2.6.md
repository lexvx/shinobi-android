# Verification 0.2.6

Version 0.2.6 / code 11. Server and camera settings unchanged.

Android runtime verification is unavailable on this host. Instrumentation is compiled, not executed. Screenshots from the target phone motivate the fixes; source review and build results are not a claim that target-phone regressions have been reproduced or accepted.

- Archive focus root cause: old double-tap rebuilt/cleared slots, while an in-progress load could make sync return without starting the replacement player. It also did not navigate to a landscape player. Focus now passes the resolved interval/time/quality without mutating slots. MainActivity snapshots viewer state before opening a dedicated fullscreen recording; return restores cameras/day/time/quality/speed/play state.
- Instrumentation regression added with a synthetic slot and loading=true: focus must retain the identical VideoView/row/version and pass correct interval/time/quality. Missing interval must not clear the existing player. This test is compiled only.
- Login review: username is ordinary text; Next focuses password and Done submits. Scrollable form and IME-specific padding/hiding of illustration, plus manifest adjustResize/stateAlwaysHidden. Compiled smoke checks verify label, input variation, IME actions and focus navigation; actual keyboard overlap remains device-unverified.
- Live controls review: compact recordings text sits beside the enlarged status lamp, preserving a 44dp touch wrapper. Existing timeline8×/bubble unchanged.
- Executed JVM checks passed: 24 timeline, 11 media, API contracts.
- Release and instrumentation build passed in 56s, 88 tasks (28 executed). Existing transitive Kotlin metadata diagnostics limit lint dependency analysis; compilation and packaging succeeded.
- Both signed APKs verified using unchanged certificate SHA256 `8e434bc3cdab8ce013cb55ad023be178dbdbe96d0481a7b65f1fc95e3d49008d`. Package version0.2.6/code11, minSdk26/targetSdk35 confirmed.
