# Phone control, the LiDAR map and the bottom bar — 2026-11-15

This repository contains the Android app only. The robot side (the Pi's command relay, status
mirror, map frames and headless launcher) is in
[Garby](https://github.com/YAKITORIHOSHI/Garby), `docs/phone-control-headless-2026-11-15.md`.

**State: source implemented; unit tests, lint and the signed release build run in this
repository's workflow; published as the Most Latest `GarbyMobileApp.apk` on 2026-11-15. Not yet
installed on a phone and not run against the robot or the live database.**

## What was added

| Area | Files |
| --- | --- |
| Rules that can be tested without Android: actions, button enablement, request replacement, outcome wording, map frame decoding | `realtime/OperatorModels.kt`, `OperatorModelsTest.kt` |
| Database access: status mirror, operator request transaction, map lease and frames | `realtime/OperatorDb.kt` |
| State shared by the stop button, Controls and Map | `OperatorViewModel.kt` |
| Screens | `ControlsScreen.kt`, `MapScreen.kt` |
| Bottom bar with the stop button | `ui/BottomBar.kt` |
| Screen-change motion, skeleton placeholders, radar loader | `ui/glass/GlassMotion.kt` |
| Navigation | `MainActivity.kt` (four tabs, each keeps its state) |

## How a request travels

1. The Pi mirrors its Controls tab to `devices/<id>/operator`. The app enables a control only
   from a mirror under 30 s old that says the action is allowed. The emergency stop is the
   exception: it can always be sent.
2. A tap (after a confirmation where the robot may move) writes one request to
   `devices/<id>/commands/operator` in a transaction. It does not replace a request the Pi is
   still handling, unless it is a stop or the old request is over 30 s old.
3. The Pi claims it, passes it through the gate its own buttons use, and writes `accepted`,
   `failed` with a reason, or `unconfirmed`. The app shows that under "Last request".
4. The stop button reads "STOPPED / Confirmed by the controllers" only from a fresh mirror that
   says the stop is latched. A tap alone never shows as stopped.

## The map

The Map tab writes `commands/map {leaseAt, by}` every 20 s while it is on screen; the Pi
publishes `map/frame` only under a fresh lease. A renewal that fails or times out is tried again
20 s later; it never ends the lease loop. A frame older than 3 s is drawn faded and
labelled stale, and its markers are hidden. The decoder is tested on
`app/src/test/resources/map_frame_golden.json`, the same file the Pi's encoder is tested on.

The red dot is the Pi's guess from size and movement. The LiDAR scans one plane at chest height
and cannot recognise people; the tab says so.

## Motion and loading

- Tab changes: the new screen comes from the side of its tab, with a spring and a slight scale;
  the old one eases away the other way. Signing in grows the app into place.
- Loading: glass skeletons shaped like the cards with a travelling sheen (the dashboard's metric
  placeholders use it too), and a radar sweep on the map.
- All of it is skipped on the conservative performance profile (low-RAM or slow devices).

Not done: the plan's "card expands into its screen" shared-element transition. It could not be
tried on a device here and was left out rather than shipped unseen. Predictive back is not set up
explicitly either: the app targets API 37, so on Android 16 and later the system enables it by
default and the navigation library animates going back with the gesture; Android 14 and 15
phones would need `android:enableOnBackInvokedCallback="true"` in the manifest. Not checked on a
phone.

## Operator list

Sign-in is anonymous and the APK is public, so the example rules allow operator requests and
map leases only for accounts listed under `operators/<uid>`. The phone's ID is shown at the
bottom of the Controls tab. See `FIREBASE_CONFIGURATION.md`.

A phone that is not listed is told so instead of waiting: the database refuses the write with
`PERMISSION_DENIED`, Controls answers "This phone is not on GARBY's operator list. Nothing was
sent.", and Map says "Not on GARBY's operator list". The refusal is recognised by the error code,
not its text. It needs the live rules and a phone to check; no unit test can reach it.

## Build and signing

`.github/workflows/android.yml` runs on every push to `main`: static checker, unit tests, lint,
then `assembleRelease`, and uploads `GarbyMobileApp.apk` with its SHA-256 and signer report as a
workflow artifact.

The release APK is signed with the app's persistent release key, from two repository secrets:

| Secret | Content |
| --- | --- |
| `GARBY_KEYSTORE_BASE64` | the PKCS12 keystore, base64 |
| `GARBY_KEYSTORE_PASSWORD` | its password; PKCS12 uses the same password for the key |

| Key | Value |
| --- | --- |
| Alias | `garby-release` |
| Certificate SHA-256 | `753c3352ac396e1a006f11ccb1a30e06f5bf990a9f94c0414469950af818e5d7` |

The keystore (created 2026-11-15) is the owner's and is kept outside both repositories. Keep a
backup of it and its password: without them no later APK can update the installed app. The workflow
never signs with anything else: it stops with an error when a secret is missing, when the alias
is not in the keystore, when the password does not open it, or when the key's certificate is not
the one above. On the finished APK, `apksigner` checks the signatures and
`tools/apk_signer_cert.py` reads the signer's certificate from the signature block and compares
it with the same certificate. The
static checker keeps these checks in the workflow. Keep `versionCode` rising with each release:
then every later APK updates the installed app in place, with its data.

The first APK signed with this key does not install over the earlier development-signed release:
uninstall the old app once. Anonymous sign-in then gives the phone a new ID for the operator
list.
