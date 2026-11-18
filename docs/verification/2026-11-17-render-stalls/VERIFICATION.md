# Connect-screen lag follow-up — 2026-11-17

The owner confirms lag before connecting and use of Android Studio Run/Debug. The Connect
screen has no active robot-monitoring listeners. Polling therefore cannot explain that
screen's lag. Debug overhead is a likely contributor, based on the selected build type and
[Android's Compose performance guidance](https://developer.android.com/develop/ui/compose/performance);
the remaining cause and frame-time improvement have not been measured on the current app.

## Source applied

- `ui/theme/Type.kt`: all three bundled font weights use asynchronous resource loading.
  Montserrat remains the final typeface; fallback text may reflow when a weight finishes loading.
- `MainActivity.kt`: reduced-effects authentication renders one screen directly. Return/operator
  ViewModels are constructed after authentication, retaining their activity scope and sign-out
  cleanup. The full-effects profile retains its finite transition.
- `StartScreen.kt`: three keyed lazy sections replace the eager scrolling column and
  constraint-time composition. Feature text, Connect behavior, glass, widths and padding remain.
- `ui/glass/GlassMotion.kt`: reduced-effects loaded/skeleton content bypasses `AnimatedContent`.
- `ui/BottomBar.kt`: reduced-effects tab icons omit the scale animation and graphics layer.
  The existing transparent exterior and floating glass container remain.
- README/architecture/patch notes explain the follow-up. The
  [Windows measurement guide](../../2026-11-17-performance-capture.md) uses the existing benchmark
  variant and a 30-second capture of native frame/memory statistics. No permanent diagnostic
  polling, new dependency, build variant or worker was introduced.

## Verification

| Check | Result |
| --- | --- |
| `python3 -B tools/static_quality_check.py` | PASS; 39 Kotlin files and 3 font files |
| `git diff --check` | PASS; existing CRLF conversion notices only |
| Before/after path, build, palette, map and command file hashes | PASS; 11 inspected files unchanged |
| Edited Kotlin delimiters | Balanced; source inspection only, not compilation |
| Debug Kotlin compile, unit tests, lint and benchmark assembly | Could not start; Gradle 9.9.1 is uncached and network sockets are denied |
| Current Android layout/scroll/font fallback and frame timing | Not run; no SDK/ADB/connected phone or built APK on this host |

The before/after hashes confirm `local.properties`, settings, dependency versions, build types,
Controls/Developer colors, OperatorViewModel/API and MapScreen were preserved. This follow-up
does not change command readiness or data freshness. Prior November 17 source fixes and the
historical November 14 phone measurements remain separate evidence.

Run the checks and `assembleBenchmark` from the existing Windows project directory. In
Android Studio select **app → benchmark** in **Build Variants**, then **Run** to assess smoothness.
The installed package must be non-debuggable. Android Studio can still set `TEST_ONLY` for
its Run deployment; this is an installation flag, not a performance-build indicator.

Exact source/documentation changes: [production-changes.json](production-changes.json).
All created/edited files: [changes.json](changes.json). Before/after hashes:
[source-before.json](source-before.json), [source-after.json](source-after.json). Review diff:
[changes.patch](changes.patch). Check commands, results and skips: [checks.json](checks.json).

**MCU upload: None. Pi deployment: None for this follow-up.** These Android presentation edits
have no firmware consumers. The earlier arrival update requires the complete `MAIN_EXECUTOR_CODE`
sketch and entire `RasPi/` directory separately; installed versions were not inspected here.
No firmware upload, Pi deployment, robot command, live database mutation or APK publication
was performed for this follow-up.
