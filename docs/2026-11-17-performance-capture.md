# Measure the app on Windows — 2026-11-17

The owner reports lag on the Connect screen and confirms using Android Studio Run/Debug.
That screen has no active robot-monitoring subscriptions. Debug instrumentation is a likely
contributor; this does not establish the complete cause without a fresh phone measurement.

## Run the existing optimized variant

Keep this project in its current folder and retain `local.properties`, SDK and build-output
paths. In Android Studio open **View → Tool Windows → Build Variants**, set `app` to
**benchmark**, and press **Run**. Use **debug** when a Java/Kotlin debugger is needed.

Alternatively, from PowerShell in the existing project folder:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleBenchmark
```

The existing benchmark variant enables R8 and resource shrinking and disables debug
instrumentation. It uses the configured production key when supplied; otherwise it uses
the local development key. It has the same app ID and data as the other variants. A signature
mismatch needs the matching key; uninstalling would discard this phone's anonymous operator
identity. Benchmark is for local checks; the published APK is still the signed release build.

## Capture before connecting

Enable USB debugging and select the connected phone in Android Studio. These commands only
inspect the Android app and reset its frame counters. They do not send any robot command.
Use the SDK location already configured on your Windows PC; no Linux path is needed.

Run in PowerShell, supplying the actual Windows path when prompted:

```powershell
$garbyAdb = Read-Host 'Full Windows path to platform-tools\adb.exe'
& $garbyAdb devices
$garbySerial = Read-Host 'Serial of the phone listed above'
$garbyCapture = Join-Path $PWD ('docs\verification\phone-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $garbyCapture | Out-Null
& $garbyAdb -s $garbySerial shell dumpsys package com.thesis.garby |
    Out-File (Join-Path $garbyCapture 'installed-package.txt') -Encoding utf8
& $garbyAdb -s $garbySerial shell dumpsys gfxinfo com.thesis.garby reset |
    Out-File (Join-Path $garbyCapture 'previous-frames.txt') -Encoding utf8
```

First verify GARBY is foreground. On the Connect screen, scroll up and down for **30 seconds**
without connecting. Then run:

```powershell
& $garbyAdb -s $garbySerial shell dumpsys gfxinfo com.thesis.garby framestats |
    Out-File (Join-Path $garbyCapture 'connect-frames.txt') -Encoding utf8
& $garbyAdb -s $garbySerial shell dumpsys meminfo com.thesis.garby |
    Out-File (Join-Path $garbyCapture 'connect-memory.txt') -Encoding utf8
```

Record the source revision, selected variant and whether this was the first launch. The
package report should omit `DEBUGGABLE` for benchmark. Android Studio's Run deployment can
still add `TEST_ONLY`; that flag controls installation and does not mean debugging is enabled.
An existing signed-in session can be checked on Home first; signing out to see Connect is a
deliberate operator choice, not part of these commands.

For an authenticated comparison, create a second capture directory, reset frame counters,
and repeat the same 30-second scroll/tab sequence. Compare optimized builds with optimized
builds; a debug-to-benchmark improvement cannot be attributed solely to source edits. Package
and frame reports carry no operator command payloads; do not substitute full Firebase/log dumps.

The capture measures frame timing and memory, not physical robot behavior. If the optimized
Connect screen still stalls, collect an Android Studio **System Trace** around the same
interaction to identify time in composition, layout, font loading or rendering.

## References

- [Compose performance](https://developer.android.com/develop/ui/compose/performance): use a
  release build with R8 to assess performance; debug mode adds overhead.
- [Compose font loading strategies](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/font/FontLoadingStrategy):
  asynchronous resource fonts load on a background thread and may reflow once ready.
- [Android frame statistics](https://developer.android.com/tools/dumpsys#gfxinfo): inspect
  per-app graphics timing with `dumpsys gfxinfo`.
- [Android Studio build outputs](https://developer.android.com/build/build-for-release):
  the Run button adds the installation-only `testOnly` attribute.

No phone was attached to the editing environment for this follow-up. These steps have not
been executed against the current source. The November 14 phone results remain historical.
