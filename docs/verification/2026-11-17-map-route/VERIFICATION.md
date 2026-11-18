# Mobile map, Activity and route arrivals — 2026-11-17

Source implemented in the existing `Garby_MobileApp` checkout. Its directory, Windows SDK
setting and Windows build-output setting were preserved byte for byte. Existing developer
control and map-arrow edits were retained; the baseline is `pre-existing.diff`.

- Map display is capped at 320 dp, with the same 2/4/8 m ranges and all 360 returns.
- Ten seconds without a new usable frame removes the decoded scan, cached drawing and full
  frame listener. Only a timestamp listener remains; a fresh timestamp wakes the map.
  The small publication lease remains active while Map is visible and Firebase is connected.
- Send back to base is inside Activity. Home no longer has a floating return dock.
- The phone stop requires an online phone/cloud, fresh Pi status and a reporting MCU.
- Point B/disposal area and point A/station come from completed executor routes. Generic IDLE
  does not claim station arrival; Activity shows the last completed arrival separately.

| Check | Result |
| --- | --- |
| `python3 -B tools/static_quality_check.py` | PASS; existing source-only invariants |
| `git diff --check` | PASS |
| Windows configuration SHA-256 comparison | PASS; see `windows-paths.json` |
| Gradle unit tests, Kotlin compilation, lint | Could not start: Gradle is not cached and this host denies network sockets |
| APK, phone display/memory profile, live Firebase | Not exercised; no APK built or published |

New virtual-clock session tests cover the ten-second boundary, listener cancellation,
automatic wake, Pi sequence restart, duplicate/stale data and absent initial data. Revised
stop cases cover offline/stale/unknown controller state and pending-action preemption. New
location cases prevent generic IDLE from claiming station arrival. These JVM tests are
prepared but were not executed here.

Run from this same checkout on Windows, with the existing settings:

```bat
gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug
```

Exact edited files and hashes: [changes.json](changes.json). Task-only review patch:
[changes.patch](changes.patch). Exact checks and failure log: [checks.json](checks.json),
[gradle.log](gradle.log). Source hashes: [source-after.json](source-after.json).

Robot verification and the Executor-only upload requirement are recorded in the separate
robot workspace, `artifacts/mobile-route-2026-11-17/robot-checks.json`. Deploy the full `RasPi/`
directory and the complete Executor sketch to obtain the new arrival telemetry; the existing
bridge forwards it without a new bridge flash for this task. No deployment was performed.
