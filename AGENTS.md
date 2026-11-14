# GARBY Mobile App repository

This repository owns the Android app, its Firebase client contract, app tests and app-only
documentation/evidence. Robot source belongs to https://github.com/YAKITORIHOSHI/Garby.

- Never copy RasPi, BLE bridge, executor source, firmware patches, robot logs or MCU binaries
  into this repository or its current release packages. Link to the robot repository instead.
- Never embed this app repository or its APKs in the robot repository or robot Test2 package.
- Keep Test1 historical downloads unchanged when preparing a corrected Test2 package.
- Verify app changes with the existing static checker and scoped Gradle tasks. Documentation
  and packaging changes reuse already verified APKs when production source hashes are unchanged.
- Repository synchronization publishes each repository independently; it does not copy one
  repository into the other. Existing user authorization applies to commits and release updates.
