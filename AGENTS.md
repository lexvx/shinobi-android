# Shinobi Android

## Scope

Default server: https://cam.lex42.ru. Only show monitors authorized by the authenticated account. Hide low-recording helper monitors. Preserve upstream server configuration: concurrent server/portal work is owned elsewhere.

User requires compact camera wall, long-press reorder persisted per account, double-tap landscape player, pinch zoom, SD/high switching, screenshots, per-camera archive, synchronized archive viewer, SD/high downloads and sharing, light/dark/system themes and optional encrypted remembered password. Do not restrict 4K on mobile data.

## Local build and signing

Local tool cache: `/tmp/shinobi-android-tools` with `jdk`, `sdk`, `gradle-8.9`, `gradle-home`.
Set JAVA_HOME, ANDROID_HOME and GRADLE_USER_HOME to those paths. Build with `gradle-8.9/bin/gradle --offline --no-daemon assembleRelease`.

Shared signing material: `/home/codex/Сисадминство/.private-signing/android-common/release.p12` and `password.txt`; existing app copy: `/home/codex/Сисадминство/.private-signing/shinobi-android/`. Alias `dark-led`. Verify current file availability and certificate before release. Never print or commit the password or private key. Supply apksigner `--ks-pass file:<password path>`; do not reuse the same single-line file as a separate key-password stream.

GitHub CLI: `/home/codex/Умный дом/kitchen-release-tools/gh`. Persistent authenticated configuration: `GH_CONFIG_DIR=/home/codex/Сисадминство/.github-cli`. Reuse it; check auth before requesting a new device login. Repository: private `lexvx/shinobi-android`. Build locally; upload signed APK and checksum files to the release. Verify uploaded assets and signature. Never upload local credentials, signing files, SDK or test account data.

## Verification

Run meaningful tests under `tests/` plus Android build and available device tests. Distinguish compile/mock/emulator verification from actual target-phone playback. Keep error/status messages free of passwords, tokens and authenticated URLs. Video redirects and export URLs must remain on the authenticated server origin.
