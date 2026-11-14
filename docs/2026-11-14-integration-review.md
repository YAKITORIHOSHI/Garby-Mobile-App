# Android manual-disposal review — 2026-11-14

This repository and its corrected Test2 ZIP contain the Android app, app tests, Firebase
client contract and app verification only. Robot source, firmware patches and robot logs
belong to [Garby](https://github.com/YAKITORIHOSHI/Garby).

The review integrated confirmed manual disposal and repaired the Android timeout race:
a matching late server report clears a local uncertainty notice, including when the
listener runs before the write callback. Another request or unknown status cannot resolve
that warning, and a receipt cannot downgrade an existing outcome or replace a newer request.
Acceptance remains separate from arrival. Five additional unit tests cover these cases.

The existing runtime verification is retained: **64 tests passed**, lint found no issues,
and optimized development-signed and unsigned release APKs built successfully. The
repository-boundary correction changes documentation, verification ownership and packaging;
Android production source and both APKs are unchanged, so builds were not repeated.
Physical phone performance for the disposal feature, live Firebase rules and multi-phone
operation remain unverified. No app install or live database command was performed.

[App files and evidence](verification/2026-11-14-mobile-integration/VERIFICATION.md).
The app static checker now rejects robot source folders and robot verification artifacts.

Test1 is preserved unchanged. Test2 contains this app source and its matching APK under
`apk/`, with no Pi, bridge or executor source. Robot deployment is documented only in the
[robot repository](https://github.com/YAKITORIHOSHI/Garby).
