# Operator ViewModel compile correction — 2026-11-17

The reported Unit/Job mismatch came from an observer expression body. Both observers now
have Unit block bodies. Flow collection imports and callback types are explicit.
The [coroutineScope contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/coroutine-scope.html) returns its block result; lifecycle observers must not expose the last launch job.

The reported unresolved map functions and three-argument database signature indicate a
ViewModel paired with older companion files. The complete implementation is now in two
existing files: `OperatorViewModel.kt` and `realtime/OperatorDb.kt`. The latter includes the
map session, scalar timestamp listener, ordinary send overload and configured developer send
overload. The superseded `realtime/MapSession.kt` was removed; use these matching files together.

| Check | Result |
| --- | --- |
| App static checker, including matching operator APIs | PASS |
| Negative checks using the previous observer and the older three-argument database file | PASS: both broken source combinations were rejected |
| `git diff --check` | PASS |
| Windows configuration hashes | Unchanged |
| `:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` | Could not start; wrapper download denied with `java.net.SocketException: Operation not permitted` |

`OperatorApiContractTest.kt` checks the JVM suspend result type (Unit, not Job) and typed
references to the database/map APIs. It is prepared but not executed here. Static source
checks do not prove Android compilation or on-device behavior.

From the same existing project folder on Windows:

```bat
python tools/static_quality_check.py
gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

The folder, SDK setting and Windows build output setting were preserved byte for byte.
No robot source was changed, so this follow-up needs **MCU upload: None** and no Pi deployment.
The preceding ARRIVAL-20261117 Executor/Pi deployment requirement remains separate; installed
versions are unknown. No upload, deployment or live database write was performed.

Exact changes and hashes: [changes.json](changes.json). Logs and limits: [checks.json](checks.json).
