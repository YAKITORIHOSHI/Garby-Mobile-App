# GARBY Mobile App repository

This repository owns the Android app, its Firebase client contract, app tests and app-only
documentation/evidence. Robot source belongs to https://github.com/YAKITORIHOSHI/Garby.

- Never copy RasPi, BLE bridge, executor source, firmware patches, robot logs or MCU binaries
  into this repository or its current release packages. Link to the robot repository instead.
- Never embed this app repository or its APKs in the robot repository or its release package.
- Publish exactly one release named `Most Latest`, with one installable APK as its only
  uploaded asset: `GarbyMobileApp.apk`. Do not upload source ZIPs, unsigned APKs, old APKs
  or checksum files. The robot package `GarbyMostLatest.zip` belongs only to the separate
  Garby release.
- Verify app changes with the existing static checker and scoped Gradle tasks. Documentation
  and packaging changes reuse already verified APKs when production source hashes are unchanged.
- Repository synchronization publishes each repository independently; it does not copy one
  repository into the other. Existing user authorization applies to commits and release updates.
