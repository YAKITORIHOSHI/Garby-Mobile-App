# Android verification — 2026-11-14

64 tests passed with zero failures/errors/skips. Lint reported no issues. Optimized benchmark
and unsigned release APKs assembled successfully. See `android-final.log`, `unit-tests.xml`,
`lint-results-debug.txt`, `apk-metadata.json` and the APK signature reports.

`verification-summary.json` records app checks only; `source-after.json` and
`binary-artifacts.json` contain only app hashes. `CHANGES.json` identifies the app edits.
Robot source patches, MCU binaries/hashes, compiler logs and Pi test results were moved to
https://github.com/YAKITORIHOSHI/Garby/tree/main/docs/verification/2026-11-14-robot-review.

The repository separation does not change Android production source or APK bytes. No
rebuild was needed. Live Firebase permissions, multi-phone behavior and physical performance
for this feature remain unverified. No app install or control write was performed.
