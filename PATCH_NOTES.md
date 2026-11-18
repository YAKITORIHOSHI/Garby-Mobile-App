# GARBY Mobile App - Patch Notes

## Every Controls-tab control checked against the Pi — 2026-11-18

- Each control was traced to the Pi function its request reaches and now behaves as the Pi
  monitor's Controls tab / Developer window does. Full table:
  [robot repository, `docs/phone-controls-rundown-2026-11-18.md`](https://github.com/YAKITORIHOSHI/Garby).
- Ordinary requests (Release, Continue, Reverse, sonar, read-only, restart) no longer carry
  the `sensors`/`fast` fields; only bench tests carry the sensor selection and only nudges
  Normal/Fast. The example rules refuse unknown fields, which would have refused every one of
  them as "not on GARBY's operator list".
- Developer controls follow the Pi's own readiness (`developerEnabled`) and show its reason
  (`developerBlocked`), the running/last test (`developerMessage`) and the result. Stop test and
  Stop are available while a test or nudge runs, and may overtake the developer request they
  stop. Sensor chips lock while a test runs; turns are offered only with the gyro ticked.
  Bench-test confirmations list the sensors obeyed/ignored and the floor warning for turns.
- Remote drive is **Tap = short nudge** (owner's choice): each arrow tap drives 0.6 s, timed
  and stopped by the Pi, about 18 cm Normal / 36 cm Fast; no sensor checks, as on the Pi.
- RunStart shows the list saved on the robot (steps, done/current marks, place, speed and
  acceleration). Continue run / Reverse to start follow the Pi's RunStart tab, including after
  an emergency stop (the Pi releases it first); Discard saved works whenever a list is saved
  and not moving.
- RunStart editor (owner's follow-up): numbered rows with what each step does and a steps
  field, a bin per row, Add step (up to 20), Copy saved list, Max speed and Acceleration fields
  (2351 and 4800 by default) and Start run. The rows are checked with the Pi window's rules
  and words; Start run stays locked with that reason until they pass, and the confirmation
  lists the steps. The rows lock while a list moves or stands part-way, as on the Pi.
- Buttons side by side are now one height whatever their labels wrap to, with a shared reason
  shown once underneath; the nudge pad is five identical cells. RunStart uses the Pi window's
  short names (Start run, Continue run, Reverse to start, Discard saved). Long confirmations
  scroll inside the dialog.
- Developer outcomes say "taken by GARBY's Pi" instead of "confirmed by the controllers".
  `firebase/manual-disposal.rules.example.json` lists all 32 actions and the optional
  `sensors`, `fast`, `program`, `speed` and `accel` fields; it was not published.
- Lint: `MapScreen.kt` read `Locale.getDefault()` in a composable (NonObservableLocale, a lint
  error from the earlier map change); it now reads `LocalConfiguration`.
- Needs robot software `PHONEDEV-20261118`; with older Pi software the RunStart group says to
  update it. JVM unit tests (104), Kotlin compilation, Android lint and the static checker pass
  on Windows; nothing was run on a phone or the robot. MCU upload: None.

## Home activity, centered map and inspection cleanup — 2026-11-17

- Added app-wide internet session handling using the existing Android validated-network
  callbacks. A three-second confirmed outage cancels sign-in/local workflow tracking and
  returns to Connect from any tab while retaining the approved anonymous Firebase identity,
  as the owner selected. A brief network handoff cancels the pending session closure.
  Connect is disabled offline; recovery requires a deliberate Connect tap.
  Late completion of a cancelled authentication task cannot reopen a closed session. No
  HTTP polling, new worker pool or robot command is introduced.
  Network callbacks now consume their delivered capabilities and track the current default
  network; loss of an old network during handoff cannot overwrite the replacement's state.
- Home adds a Current activity section: Running, Blocked, Turning, Idle, Parked, Emergency
  stopped or Route fault. It uses the existing operator mirror; no extra database listener
  or polling loop. Holds override route-leg turns, emergency stop dominates, and absent,
  stale or disconnected reports show activity unavailable. The card observes a distinct
  projection so unchanged clock/map updates do not redraw the grid. Idle does not prove location.
- Centered the map's loading viewport, wrapped waiting text and Range group. Range choices
  can wrap at larger text sizes and expose the selected radio state to accessibility services.
- The 84 dp radar progress sweep now rotates on every performance profile while Map is
  resumed and waiting. It reads animation phase only in drawing and caches brushes/geometry.
  It pauses when backgrounded/leaving the tab and is removed on live data or ten-second sleep.
  The user explicitly requested this active loader; decorative motion remains reduced.
- Removed an unused dashboard import and the unreferenced RingGauge/GlassDock helpers with
  exclusive imports. Map decimal formatting now supplies the device locale explicitly.
  No inspection rules were disabled or warnings broadly suppressed. The configured Firebase
  registration APIs were checked against current official documentation and retained.
- Inspection results from the Windows IDE are not available in this environment; an actual
  Problems export/list is still needed to account for every reported warning/error. Existing
  static checks and source evidence are recorded in `docs/verification/2026-11-17-inspection-map/`.
  Compilation, JVM tests and Android lint cannot start here without the Gradle/SDK toolchain.
  MCU upload and Pi deployment: None for these Android changes.

## Map/Controls content behind floating bars — 2026-11-17

- Moved Map and Controls/Developer scaffold padding inside their scrolling content. Their
  viewport now extends behind the floating top bar and bottom tabs/emergency-stop container,
  matching Home and Activity. Previously outer padding stopped content at the bars and
  exposed a solid-looking background strip. End spacing still lets the last item scroll clear
  of the dock. Red/rose colors, glass surfaces, command gates and polling are unchanged.
- Source/static checks recorded in `docs/verification/2026-11-17-floating-scroll/`. Android
  compilation/layout cannot run on this host without Gradle/SDK access; rebuild the existing
  benchmark variant on Windows for the visual check. MCU upload/Pi deployment: None.

## Connect-screen rendering and debug-build diagnosis — 2026-11-17

- The owner confirms lag before connecting and use of Android Studio Run/Debug. Live-data
  polling does not explain lag on that screen; release-equivalent measurement is required.
  The existing `benchmark` variant already provides R8 optimization without debug
  instrumentation. The README now points Windows users to it and to a scoped frame capture.
- Bundled Montserrat fonts load asynchronously instead of blocking first text composition.
  The same fonts, red/rose palette and glass visuals remain; fallback text can reflow once.
- The conservative profile bypasses authentication/loading `AnimatedContent` entirely and
  avoids the tab icon's idle animation state/graphics layer. The Connect screen uses a lazy
  list instead of constraint-time subcomposition and an eager scrolling column. Operator and
  return ViewModels are created after authentication, retaining their activity scope across tabs.
- Source changes need a fresh build on Windows. No new phone frame-rate improvement is claimed;
  this host has no Android SDK/ADB or cached Gradle distribution. MCU upload and Pi deployment:
  None for this follow-up. Earlier arrival-related Executor/Pi requirements remain separate.

## App-wide monitoring, floating dock and original palette — 2026-11-17

- Navigation no longer collects complete operator state on every clock tick/map frame. The
  small dock observes a distinct stop-button projection; genuine stop/link/status changes
  still reach it immediately.
- Operator, return and dashboard monitoring share one foreground clock. Dashboard freshness
  keeps its five-second cadence; operator/return age checks keep their one-second cadence.
  Authentication and internet status use callbacks instead of per-second main-thread polls.
  Return sharing stops immediately when unobserved and clears its live-state replay.
- Tabs/emergency stop overlay the full-size page inside their glass container, without a solid
  external strip. Measured dock height pads page content so the last action stays reachable.
  The conservative profile skips dock color/scale transitions as well as other reduced effects.
- Restored the original GARBY red/rose Controls/Developer palette, retaining glass rims,
  stronger contrast and explicit enabled/disabled labels. No manufacturer/model branches.
- Virtual-clock/sharing and dock-projection regressions were added. Static, source-scope and
  contrast checks pass; Android compilation/tests/lint cannot start on this host because the
  Gradle distribution is uncached and downloads are denied. No new phone frame-rate result
  or APK is claimed. Windows project/SDK/build paths are unchanged. MCU upload: None for this
  app-only change; the earlier Executor/Pi arrival deployment requirement remains separate.

## Controls and Developer glass redesign — 2026-11-17

- Controls uses cyan and Developer uses violet over a navy glass backdrop, with separate
  section tiles, reflective rims, deeper surfaces, bright opaque text and clearer headings.
- Action buttons keep readable labels when disabled, with lock icons and the existing reason.
  Sensor gates explicitly show ON/OFF, with checkbox/radio semantics; sensor mode cards show
  CHECKS ON, READ ONLY, OFF or UNKNOWN. Speed chips wrap on narrow screens.
- The directional pad uses available width instead of fixed button widths. Stop/cancel actions
  remain red, separated from the cyan/violet selection colors. Press feedback is brief; the
  conservative profile retains its no-animation path. No live blur, shaders or new bitmaps.
- Existing action bindings, operator gates, confirmations, sensor mask and send arguments are
  retained. All styling is scoped to ControlsScreen.kt; other screens keep their design.
- Includes normal, Developer and enlarged-text Android Studio previews plus a labelled design
  reference. Static/scope/contrast checks passed; full Android compile/lint could not start
  because this host cannot download uncached Gradle. Device appearance remains unverified.
  Windows directory/SDK/build paths are unchanged. MCU upload: None for this UI-only task.

## Operator ViewModel compilation correction — 2026-11-17

- Both lifecycle observers use Unit block bodies, preventing a last `launch` from becoming
  the function's Job return value. Flow collection imports and callback types are explicit.
- The map-session helper now lives in the existing `realtime/OperatorDb.kt`; the separate
  `MapSession.kt` is removed. The same database file supplies `mapTimestamp`, an ordinary
  three-argument `send`, and the five-argument configured developer `send`.
- Update `OperatorViewModel.kt` and `OperatorDb.kt` together. The static checker detects old
  companion files, absent map APIs and duplicate map-session definitions. A JVM API contract
  test checks the actual observer return signatures and the consumer method references.
- Static checks and negative source-contract checks passed. Gradle Kotlin compilation, JVM
  tests and lint could not start on this host because Gradle is not cached and network sockets
  are denied. Windows folder, SDK and build paths are unchanged. This app-only follow-up
  requires no MCU upload; the previous Executor arrival change remains a separate requirement.

## Compact map, Activity controls and route arrivals — 2026-11-17

- Map display capped at 320 dp; 2/4/8 m ranges and scan detail retained. Scan geometry is cached.
  After 10 s without a new usable scan, the full frame listener detaches, decoded scan and
  drawing data are released, and the loader stops. A scalar timestamp listener and the small
  publication lease allow automatic wake on fresh data. Leaving Map clears the scan immediately.
- Removed the floating Home return dock. Send back to base is an inline card in Activity,
  with the existing confirmation, readiness checks and correlated return progress.
- Phone emergency stop requires an online phone/cloud, a fresh Pi mirror, BLE connection and
  fresh MCU support. It can still preempt a pending action once those conditions hold.
- Displays completed outbound arrival at point B/disposal area and return arrival at point A/
  station from Pi status. Generic IDLE no longer claims station arrival. Activity retains the
  last completed arrival independently of current travel/unknown status.
- Windows local.properties, SDK/build paths, settings and the project folder are unchanged.
  Source/static checks are recorded in docs/verification/2026-11-17-map-route/. Gradle unit
  tests, Kotlin compilation and lint could not start: no cached Gradle distribution and this
  host denies network sockets. No APK was built or published for these changes.

## Phone control and the LiDAR map — 2026-11-15

- **Bottom navigation:** Home, Map, Controls, Activity, with an emergency stop button above the
  tabs on every screen. Screen changes slide the way the bar moves and settle with a spring;
  cards show moving glass skeletons until their data arrives; the map has a radar-sweep loader.
  All motion is off on the conservative performance profile.
- **Controls:** Release stop, Continue run, Reverse to start, obstacle sonar, front/rear LiDAR
  read-only and restart, enabled from the Pi's confirmed state and sent through the Pi's own
  gate. Needs robot software `PHONE-20261114` and controller firmware `REVERSE-20261114` for
  Reverse.
- **Map:** the Pi's LiDAR frame with likely people (red), moving objects (amber) and objects
  between the walls (orange). Published by the Pi only while the tab is open.
- **Operator list:** only listed phones may send requests or open the map lease. A phone that is
  not listed is told so on Controls and on Map, and Controls shows its ID for the list.
- **Robot state:** "Driving back to the start" for the new reversing state.
- **Build:** a GitHub Actions workflow on `main` runs the static checker, the unit tests and
  lint, and builds the release APK signed with the app's persistent release key (alias
  `garby-release`, certificate SHA-256 `753c3352…f818e5d7`). It fails rather than sign with any other key. That
  key is new: the first APK signed with it does not install over the earlier
  development-signed APK, so uninstall the old app once; later releases update in place.
- Version code 7, "Most Latest · Phone Control". Not yet run on a phone against the robot.

## GarbyMostLatest-Test2 — 2026-11-14

- Integrates the confirmed manual-disposal feature below. A late matching cloud report now
  replaces a local timeout notice; a callback arriving before the timeout result cannot have
  its accepted/rejected outcome hidden. Another request cannot resolve this request's warning.
- Android: 64 passing unit tests, lint with no issues, optimized development-signed APK and
  unsigned release APK built on Windows. Physical phone performance was not retested.
- The mobile repository contains Android source and app evidence only. Its single
  **Most Latest** release distributes only `GarbyMobileApp.apk`. Robot source and the
  preserved Test1/Test2 ZIPs remain in the separate
  [Garby repository](https://github.com/YAKITORIHOSHI/Garby).
- [Review, exact changes and verification limits](docs/2026-11-14-integration-review.md).

## Most Latest · Manual Disposal (versionCode 6)

2026-11-13 — Confirmed manual disposal for end-of-school collection.

- Dashboard button and confirmation send an idle GARBY to the collection point before
  bin/gas/load thresholds are met. Obstacles, heading and emergency stop still gate motion.
- Transactional, expiring requests cannot replace unresolved requests; sent and controller
  acceptance are separate. Unconfirmed delivery never shows as success or auto-resends.
- Reuses existing reduced effects, lazy dashboard, bounded background dispatcher and
  lifecycle-owned listeners. No new dependency, image, animation, phone polling or thread pool.
- Source static checks run in the coordinated workspace. Android tests, lint and assembly now
  pass on Windows; low-end phone performance remains unmeasured for this feature. Matching Pi/MCU deployment
  and an explicit disposal Firebase rule are required.

## Most Latest

2026-11-14 — Dashboard categories, indicators and fullscreen experience.

- Android's status and navigation bars are hidden on all app screens and confirmation
  dialogs. Swipe from an edge to reveal system controls temporarily; immersive mode is
  restored when the app or dialog regains focus. Cutout padding remains respected.
- Replaced the padded bitmap launcher foreground with a centered vector traced from the
  official GARBY wordmark and cart. Round, rounded-square and themed icons share the same
  recognizable mark inside Android's safe area, without the print-only ring and tiny tagline.
- Bin, Air and System now have inset category dividers with distinct icons, subtle accent
  rules, concise descriptions and summaries of the readings below them.
- Live counts, gas bands, dispatch weight and system issues use words and icons as well as
  colour. Missing, invalid, stale or disconnected telemetry cannot produce an all-clear.
- Metric cards give their status a separate row so longer labels fit narrow cards. Stale
  sensors use neutral icon accents and a clock indicator. System tiles use status symbols.
- Larger system text selects fewer grid columns, metric cards share a consistent minimum
  height, and explanatory update text wraps instead of being cut off.
- Status capsules use quieter borders, tinted text and static dot halos. Category titles
  remain accessible headings, and their status wraps when space or font size requires it.
- The app's displayed version name and this release title are **Most Latest**. Android keeps
  a numeric build code internally for upgrade ordering.
- Audited Firebase subscriptions and background work. Return-state calculations and server
  response handling now use the existing bounded background dispatcher; saved request state
  stays on the main thread. Database paths, readiness gates and robot commands are unchanged.
- Verification: 53 unit tests, lint with no issues, static checks and debug/release/optimized
  builds. Headless Compose layouts cover 320–400 dp widths and text scales up to 200%.
- Installed and checked the exact optimized APK on the authorized test phone. In a fixed
  scroll comparison against its debug installation, janky frames fell from 57.1% to 20.0%,
  P90 frame time from 81 to 36 ms, and PSS from 153.3 to 61.1 MiB. This includes the build-type
  change; some jank remains and only one phone was measured. Fullscreen/dialog/resume and
  blocked return-screen navigation passed without sending a robot command.
- **MCU upload: None; Pi deployment: None.** Only the Android app changes.

Fullscreen, icon and runtime implementation/verification are recorded in
[the fullscreen report](docs/2026-11-14-fullscreen.md).

## 2026-11-14 Device-wide rendering and memory performance (2.0.1)

- Removed live blur/Haze layers and continuous decorative animations on every device, including
  mid-range phones. Smoked glass, readable status words and all controls remain. Short finite
  transitions remain on capable devices.
- Capacity-based profiles use RAM, heap class and CPU count only, with smaller images, immediate
  navigation/gauges and fewer glows/shadows on constrained devices. No brand/model special cases.
- Reuses one bounded coroutine dispatcher view (one background CPU task at a time on reduced
  devices, two otherwise), without adding custom executors or changing Firebase's worker pools.
- Snapshot bursts conflate before decoding; unchanged telemetry is suppressed. Connection and
  robot-status listeners are shared between the dashboard and return workflow, with replay
  cleared after the last observer leaves and errors delivered to every observer.
- Background image decoding, sampled artwork and a byte-limited cache reduce main-thread work
  and bitmap memory. Memory-trim callbacks drop cached off-screen artwork safely.
- Added a development-key-signed `benchmark` build with release optimizations for phone checks.
  Production release signing stays external. Readiness, freshness and robot protocols are unchanged.
- After installation on the connected test phone, sampled median scrolling frame time changed
  from 300 ms to 26 ms, and process PSS from 200.6 MiB to 58.5 MiB. This includes a debug-to-optimized
  build change; some jank remains and other physical devices were not measured. All 43 unit tests,
  lint, static checks and APK builds passed. Details:
  [performance report](docs/2026-11-14-low-end-performance.md).
  **MCU upload: None; Pi deployment: None.**

## 2026-11-13 Glass redesign, current robot protocol, toolchain update (2.0.0)

Current behaviour is described in [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md).

- Redesigned in glassmorphism (Haze 2.1 backdrop blur on Android 12+, opaque smoked glass
  below): start, dashboard and return screens; floating glass top bar and dock.
- Shows the robot state the Pi publishes (idle, outbound, at the collection point, returning,
  route fault, controllers unknown) with a route track, and heartbeat-based presence that turns
  "Last known" after 90 s. Previously the online label could stay ONLINE after the Pi went silent.
- Bin and gas cards use the executor's thresholds (4 kg load trigger; MQ135 four bands; the gas
  danger rule). The old 0.7/1.0 kg and single 400/700 ppm bands were out of date.
- Return: readiness checklist; the server status and command are re-read immediately before the
  write; the Pi's ACK, completion and failure reason are shown; the request survives leaving the
  screen or the app. The screen no longer locks the operator in for 15 minutes.
- Fixed: bin distance above 300 cm displayed as 300; malformed boolean/string fields failed a
  whole stream; launch FCM write before sign-in; white window flash; navigation bar force-hidden;
  notification small icon rendered as a white disc; logo/backgrounds density-scaled to ~80 MB;
  device-to-device transfer of the anonymous session on Android 12+.
- FCM moved from deprecated registration tokens to Firebase Installation IDs
  (`devices/<id>/fcmInstallationId`).
- Toolchain: AGP 9.5.1, Gradle 9.9.1 (checksum-pinned), Kotlin 2.4.21, Compose BOM 2026.09.00,
  Firebase BoM 35.2.0, navigation 2.10.2, compileSdk 37.2, targetSdk 37. Unused WorkManager
  removed; adaptive launcher icon added.
- Cleanup (2026-11-14): new [README.md](README.md); Firebase note rewritten with the app's
  read/write paths; launcher label "GARBY". Removed the Pi compatibility launcher
  (`final_w_serial.py`; run `RasPi/final_w_serial.py`), a May Gradle debug log, template-only
  `proguard-rules.pro` and example tests, and stale `.kotlin/`, `build/` and Gradle 9.6.1
  caches. Older audit, validation and startup-plan reports moved to `docs/history/`.

## 2026-10-31 Firebase client configuration review

- Synced `app/google-services.json` from `RasPi/Firebase/google-services.json`.
  Both files currently match byte-for-byte and register `com.thesis.garby`.
- Kept the existing anonymous-authentication flow. The provided Admin SDK
  private key and plaintext account password were not copied into Android.
- See [Firebase configuration](FIREBASE_CONFIGURATION.md) for the project and
  database consistency checks and credential handling.

## 2026-10-02 coordinated repair

See [release notes](../RELEASE_NOTES.md) and [current architecture](../SYSTEM_ARCHITECTURE.md).
Return uses exact server-issued IDs and actual MCU ACK/DONE/FAILED, fresh live
readiness gates, and a 15-minute completion window. The obsolete APP flag is no
longer written with commands. Historical sections below describe earlier releases.

## 2026-08-11 deployed-database integration

- Mapped each dashboard metric to the exact legacy path in the supplied
  `GARBY-DATABASE.json`, while retaining the newer `/devices/{id}/sensors/...`
  path as the preferred rolling-upgrade source.
- Replaced five whole-database sensor subscriptions with narrow listeners for
  only the relevant device and legacy sensor nodes.
- Narrowed robot-status, reset-status, and return-readiness listeners as well,
  so sensor writes no longer resend/reparse the entire RTDB tree in unrelated
  app streams.
- Removed ambiguous numeric-child guessing that could mistake `updatedAt` for
  a sensor value; deployed value-field names are now explicit and tested.
- Made the structured reset command and `/APP/isReadyToReset` compatibility
  flag one atomic multi-location write.
- Fixed a reset race where `APP/isReadyToReset = false` could incorrectly turn
  a fresh structured `pending`/`ack` command into a displayed completion.
- Batched the three FCM-token compatibility writes and stopped logging the
  registration token value.
- Added an optional system-health line for Pi temperature/throttling and
  BLE/LiDAR/sensor-board connectivity; older database snapshots remain fully
  compatible because absent fields are hidden.
- Added schema and reset-compatibility unit tests.

## Security

- Removed embedded Firebase login credential.
- Removed legacy Basic-Auth direct-Pi client and trust-all TLS/hostname bypass.
- Disabled app backup and cleartext traffic.
- Removed unnecessary network-state mutation permission.
- Authentication uses Firebase anonymous auth with no embedded password or
  service credential; Firebase SDK owns token refresh. Anonymous auth must be
  enabled for this Firebase project, and RTDB rules must still restrict writes
  to authenticated clients and the intended reset paths.

## Reset/control safety

- Added explicit confirmation dialog.
- Added `ResetViewModel` so reset state survives recomposition/configuration.
- Requires authenticated UID, validated Android internet, and Firebase connected state before send.
- Disabled Firebase disk persistence to avoid persisted control-write replay.
- Purges outstanding writes around failed reset transmission.
- Correlates completion with a newer request marker and requester identity.
- Unknown/timeout status is presented as unknown; the app never claims completion without matching `done`.

## Monitoring correctness

- Missing sensor values no longer default to zero.
- Added finite-value and timestamp validation.
- Added periodic stale detection.
- Added explicit robot online/offline/stale status separate from Firebase cloud connection.
- Battery `null` is no longer displayed as `0%`.

## Reliability/performance

- Replaced singleton Firebase listener lifecycle with collector-owned callback Flows.
- Retry only transient RTDB failures with bounded exponential backoff/jitter.
- Conflated latest-value monitoring streams.
- Removed leaked process coroutine scope from network monitor.
- Removed dead unbounded thread pools and double gauge animation.
- Pruned unused libraries/plugins.
- Enabled release minification/resource shrinking.
- Reduced font and drawable payload substantially.
- Debug unit tests, Android lint, debug assembly, and R8 release assembly all
  pass in the current workspace.

## Compatibility note

Reset field names/status strings are unchanged. `requestedBy` now contains the authenticated Firebase UID instead of the previous constant Android label. Verify the Raspberry Pi does not require an exact literal value.

## Deployment order

1. Rotate any credential that was exposed by an older application build.
2. Enable Firebase anonymous auth and verify RTDB rules.
3. Confirm Pi compatibility with UID-valued `requestedBy`.
4. Build/test the Android app with the real Android SDK/Gradle environment.
5. Run wheels-lifted reset/STOP integration tests.
6. Sign and deploy the mobile app only after those checks pass.
