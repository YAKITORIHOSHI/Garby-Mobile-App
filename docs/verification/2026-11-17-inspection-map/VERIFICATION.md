# Inspection review, Map, Home activity and internet sessions — 2026-11-17

This follow-up keeps the existing Windows project directory, SDK/build paths, dependency
versions, original Controls/Developer palette and floating top/bottom bars.

## Implemented

- **Internet:** the existing process-wide Android monitor requires INTERNET and VALIDATED.
  Callbacks consume their delivered capabilities rather than making synchronous platform
  queries inside a callback. They track the current default network, ignoring obsolete events
  from a replaced network. No HTTP polling, periodic pings or worker pool was added.
- **Offline session:** after three seconds without validated internet, AuthViewModel cancels
  sign-in and clears local operator/return workflow state before returning to Connect. A
  brief handoff cancels the pending closure. The owner selected **keep the approved phone ID**:
  offline handling retains Firebase authentication and closes only the local app session.
  Connect is disabled offline; recovery requires a deliberate tap. Late authentication
  callbacks cannot reopen a closed local session. The explicit Sign out action retains its
  previous Firebase sign-out behavior.
- **Home:** a Current activity card shows Running, Blocked, Turning, Idle, Parked, Emergency
  stopped or Route fault from the existing fresh operator mirror. Missing/stale/link-down
  evidence is unavailable. Reported holds override turn legs; emergency stop dominates.
  The explicit parking alignment message reports Turning. Idle does not prove station arrival,
  and a running/turning route step is telemetry rather than physical wheel-motion evidence.
  The card observes a deduplicated background projection, reusing the existing listener/clock.
- **Map:** a full-width host centers the loading viewport. Waiting text and the wrapping Range
  group are centered. Range chips publish their selected radio state. The small radar sweep
  rotates on all capacity profiles while the waiting Map is RESUMED, reads phase only in
  drawing, and caches brushes/geometry. It pauses on tab/background transitions and is removed
  by live data or ten-second map sleep. Range, display size and publication lease are unchanged.
- **Inspection cleanup:** removed the unused StatusDot import and unreferenced RingGauge and
  GlassDock helpers, including exclusive imports. Map formatting supplies an explicit device
  locale. No inspection profile or broad warning suppression was changed.

The Android files actually changed are recorded with before/after hashes in
[production-changes.json](production-changes.json). All source/docs/evidence paths are in
[changes.json](changes.json), with a reviewable [patch](changes.patch).

## Inspection limits

No connected IDE/document session or current inspection export exists in the shared workspace.
The Windows Problems list has not been provided. This review addresses source findings and the
requested behavior; it **does not establish that every IDE warning/error is cleared**. A pasted
Problems list or inspection export is needed to match the remaining entries to code.

The Firebase registration APIs in the configured BoM were checked against the current official
[FirebaseMessagingService API](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessagingService)
and retained. Replacing the supported onRegistered callback with a deprecated token callback
would not be an appropriate cleanup. The distinction between preserving the approved anonymous
ID and signing out follows the [Android FirebaseAuth API](https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseAuth).

## Checks

| Check | Result |
| --- | --- |
| Existing static quality checker | PASS; 42 production Kotlin files and 3 font files |
| `git diff --check` | PASS; existing CRLF notices only |
| Textual main/test unused-import review | No remaining candidates; not semantic inspection |
| Edited Kotlin delimiters | Balanced; not Kotlin compilation |
| Fourteen path/build/palette/inspection/gateway/workflow hashes | Unchanged |
| Debug and benchmark Kotlin compile, debug unit tests, both lint variants | Could not start; Gradle 9.9.1 uncached, download denied by host socket restrictions |
| Android screenshots, loader timing, network transitions and Home status on phone | Not run; no SDK/ADB, connected phone or new built APK here |

Seven new JVM tests are prepared but **not executed**: four cover execution-state precedence,
turn/hold parsing and unavailable evidence; three exercise the actual internet watcher with
virtual time for confirmation, handoffs/reconnect, startup offline and cancellation. Existing
operator/map/arrival tests remain unchanged. Auth/Android callback integration still needs a
device check; the flow tests do not substitute for it.

Exact commands, logs, outcomes and skips: [checks.json](checks.json). Source hashes:
[source-before.json](source-before.json), [source-after.json](source-after.json). Preserved paths:
[preserved-files.json](preserved-files.json). No APK was generated, so no new binary hash exists.

Run in PowerShell from the existing Windows project folder:

```powershell
python -B tools/static_quality_check.py
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:compileBenchmarkKotlin :app:lintBenchmark
```

Then run benchmark. Check Home's reported activity, centered Map at normal/large font sizes,
radar rotation until a scan/sleep, and top/bottom scroll-through. Turn internet off on each tab:
after three seconds it must return to Connect and clear local activity/map views. Re-enable
internet: Connect becomes available but does not automatically open Home. After pressing it,
the Controls operator ID must match the pre-outage ID. Also check a short Wi-Fi/data handoff,
background/resume and an outage while connecting. Do not send robot commands for these UI checks.

The callback implementation follows [Android network-state guidance](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).
Lifecycle-aware radar behavior uses the existing
[lifecycle Compose integration](https://developer.android.com/topic/libraries/architecture/lifecycle).

**MCU upload: None. Pi deployment: None for this follow-up.** Only Android source, tests and
documentation changed. The existing Pi mirror already supplies the needed route/hold fields;
no firmware or Pi consumer changes are required. Earlier ARRIVAL-20261117 requirements remain
separate: the complete MAIN_EXECUTOR_CODE sketch and entire RasPi directory. Installed versions
were not inspected here. No upload, deployment, release publication, live database mutation or
robot command was performed for this follow-up.
