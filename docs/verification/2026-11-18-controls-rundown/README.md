# Controls rundown — 2026-11-18

Every Controls-tab control (Controls and Developer sections) traced to the Pi function its
request reaches, and gated as the Pi monitor's Controls tab / Developer window gates it. The
per-control table and the Pi-side fixes are in the robot repository:
`docs/phone-controls-rundown-2026-11-18.md` (https://github.com/YAKITORIHOSHI/Garby).

## Checks (Windows, JDK 21, Gradle 9.9.1 offline)

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | 104 tests, 0 failures, 0 errors (OperatorModelsTest 22, OperatorApiContractTest 4); an earlier note said 106, an adding error |
| `:app:compileDebugKotlin` | OK |
| `:app:lintDebug` | 0 issues (after the `MapScreen.kt` NonObservableLocale fix) |
| `tools/static_quality_check.py` | PASS, 42 Kotlin files |
| Action names: app enum vs Pi relay vs example rules (script) | 32 = 32 = 32 |
| Device run, phone-to-robot test, rules publication | not done |

Includes the RunStart editor (rows, Max speed, Acceleration, Start run) and equal-height button
rows. Requires robot software `PHONEDEV-20261118` with `DEV_RUN_START`. No APK was built or published. MCU upload: None.

## SHA-256 after the change

```
0c6f5d0d947bf183ae1288cd0c98e2679ac124dd600c46a471a4aaf6fc94d5b7  app/src/main/java/com/thesis/garby/ControlsScreen.kt
e59c8a5e0c5acb3ca13173ae77eeada0e404d6aeb95f69e7d8c55186dcaa6495  app/src/main/java/com/thesis/garby/OperatorViewModel.kt
40072573ce7fe899bd85233d9c2a466be210e5c3d2cb7b79d33fbc21a70017c2  app/src/main/java/com/thesis/garby/MapScreen.kt
7d8ccea9f9e5fea4af411a9c45e7872007ee0348d56dffc0fb6371368a21aef9  app/src/main/java/com/thesis/garby/realtime/OperatorModels.kt
1c4b5038b0e1df3d6e6b023665ac4c81dbd40d1aceb6a94f03433946d8b08ef7  app/src/main/java/com/thesis/garby/realtime/OperatorDb.kt
8cd3c40ad8a59df8df3a4e9aa8f7486a95af188378aa8adec1efbad81ac6de4e  app/src/test/java/com/thesis/garby/realtime/OperatorModelsTest.kt
459cd673a3a57854fec7431af3a94e104c17c33cce8210771d0676372c22bc1b  app/src/test/java/com/thesis/garby/OperatorApiContractTest.kt
1e66f313b0616aa3333a0b2b76e865d4a91880e8afb4648013da019bfdd83ee8  firebase/manual-disposal.rules.example.json
```
