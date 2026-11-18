# Floating top and bottom bars — 2026-11-17

The owner reports that Map and Controls content disappears at a rectangular boundary before
the bottom dock, and requests the same correction for the top bar.

Both screens applied scaffold padding **before** `verticalScroll`. That reduced the viewport
to the area between the header and dock, leaving the page background visible as a wall around
the floating glass. The fix moves the same padding **after** `verticalScroll`: it becomes part
of the scrollable content while the viewport fills the page. Initial and final spacing still
let the first and last controls clear the bars. Controls and Developer share this layout.
Home and Activity already use lazy-list/grid content padding and need no source change.

Actual source changes:

- `app/src/main/java/com/thesis/garby/MapScreen.kt`: modifier order and explanatory comment.
- `app/src/main/java/com/thesis/garby/ControlsScreen.kt`: same correction for both sections.
- `PATCH_NOTES.md` and `SYSTEM_ARCHITECTURE.md`: document the behavior and its cause.

The source comparison checks that these are the only two production changes. All other
Controls/Map statements, including palette, confirmation, action and map-observation code,
are preserved. Thirteen other inspected files keep their recorded hashes: navigation, dock,
shared glass, Home/Activity, operator VM/API, palette, and Windows SDK/build configuration.
No live blur, new animation, polling or background work was added.

| Check | Result |
| --- | --- |
| `python3 -B tools/static_quality_check.py` | PASS |
| `git diff --check` | PASS; existing CRLF conversion notices only |
| Exact production delta / preserved hashes | PASS; `scope-checks.log` |
| `:app:compileBenchmarkKotlin :app:lintBenchmark` | Could not start; uncached Gradle 9.9.1 download denied by host socket restrictions |
| On-device layout and scrolling | Not run; no Android SDK/ADB or connected phone here |

On Windows, rebuild/run the existing **benchmark** variant. In Map, Controls and Developer,
check that scrolling content remains visible in the gaps around both glass bars. At each end
of the scroll, the first/last item must remain reachable. Repeat with larger font scaling.
Home and Activity should retain their existing behavior. This check concerns visual scrolling;
the stop button and other controls retain their own touch targets and command rules.

[Android's layout guidance](https://developer.android.com/codelabs/jetpack-compose-layouts)
describes keeping spacing in scroll content instead of clipping the viewport with outer
padding. Source inspection supports this fix; it is not a phone screenshot or visual acceptance.

Exact file list and hashes: [changes.json](changes.json). Source delta:
[changes.patch](changes.patch). Check commands/results/skips: [checks.json](checks.json).

**MCU upload: None. Pi deployment: None for this UI correction.** Only Android layout and
documentation changed; there are no firmware/Pi consumers. Earlier arrival-related Executor
and Pi deployment requirements remain separate; their installed versions are unknown here.
No upload, deployment, APK publication, live database mutation or robot command was performed.
