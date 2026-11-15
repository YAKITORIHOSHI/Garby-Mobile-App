# Android Firebase configuration

Updated 2026-11-14.

## Client configuration

`app/google-services.json` registers package `com.thesis.garby` in project `garby-thesis`.
The database URL must agree with `realtime/Constants.kt` and the independently deployed
backend. Backend implementation and optional cross-repository contract checks belong to
[Garby](https://github.com/YAKITORIHOSHI/Garby), not this Android repository.

## Credential boundaries

- The app signs in with Firebase **anonymous authentication** (`firestore/AuthViewModel.kt`). It
  contains no password and no service-account key.
- Backend Admin credentials belong only on the robot's supervisor and must never enter the
  Android repository or APK.
- `google-services.json` is client configuration, not an Admin credential. Access control comes
  from Authentication settings, database rules and any API-key restrictions.

## What the app reads

| Path | Use |
| --- | --- |
| `.info/connected`, `.info/serverTimeOffset` | Cloud connection; server clock for return ages |
| `devices/garby-bin-01/status` | Heartbeat `lastSeen`, `robotState`, `isReadyToReturn`, BLE/LiDAR/sensor links, CPU temperature, throttle flags |
| `RASPI/STATES` | Legacy fallback for the heartbeat and ready flag |
| `devices/garby-bin-01/sensors/{level,weight,mq135,mq137,mq4}` | Readings with `updatedAt` |
| `RASPI/VALUES/...` | Legacy sensor paths, read only while a device record is absent |
| `devices/garby-bin-01/commands/reset` | The latest return request and the Pi's outcome |
| `devices/garby-bin-01/commands/dispose` | Latest manual-disposal request and matching executor acceptance/rejection |
| `devices/garby-bin-01/operator` | The Pi's mirror of its Controls tab: confirmed stop state, what is allowed, sonar, read-only, hold reason, route step, `updatedAt` |
| `devices/garby-bin-01/commands/operator` | The latest operator request and the Pi's outcome (`claimed`, `accepted`, `failed`, `unconfirmed`, `reason`) |
| `devices/garby-bin-01/map/frame` | One LiDAR frame (360 distances, markers, zones), only while a phone holds the map lease |

## What the app writes

| Path | When |
| --- | --- |
| `devices/garby-bin-01/commands/reset/{requestedAt, requestedBy, status, ackAt, doneAt, failedAt, reason}` | One operator-confirmed return: server-timestamp `requestedAt`, the operator's anonymous UID, `pending`, prior outcome fields cleared |
| `devices/garby-bin-01/commands/dispose` | One confirmed transaction: `version: 1`, random 32-hex `requestId`, server `requestedAt`, authenticated UID `requestedBy`, `status: pending`; refuses replacing unresolved requests |
| `devices/garby-bin-01/commands/operator` | One operator action in a transaction: `version: 1`, random 32-hex `requestId`, server `requestedAt`, `requestedBy`, `action`, `status: pending`. It never replaces a request the Pi is still handling, except with an emergency stop |
| `devices/garby-bin-01/commands/map` | `{leaseAt: server time, by: uid}` every 20 s while the Map tab is open |
| `devices/garby-bin-01/fcmInstallationId`, `fcmInstallationIdUpdatedAt` | After sign-in: the Firebase Installation ID used by FCM |

The app no longer writes `APP/fcmToken`, `devices/<id>/fcmToken` or `RASPI/fcmToken` (FCM
registration tokens are deprecated), and no longer reads `APP/isReadyToReset`. The Pi writes
everything else with the Admin SDK, which bypasses database rules.

## Database rules

The deployed rules are not in this repository and have not been verified. They should allow
authenticated reads and only the writes above, and should make the return command a fresh,
self-attributed pending request. A starting point, **not tested against the live project**:

```json
{
  "rules": {
    ".read": "auth != null",
    "devices": {
      "$device": {
        "commands": {
          "reset": {
            ".write": "auth != null && newData.child('requestedBy').val() === auth.uid && newData.child('status').val() === 'pending' && newData.child('requestedAt').val() === now"
          }
        },
        "fcmInstallationId": { ".write": "auth != null && newData.isString()" },
        "fcmInstallationIdUpdatedAt": { ".write": "auth != null" }
      }
    }
  }
}
```

Test any rule change with the Firebase emulator or the Rules Playground before deploying: a rule
that rejects the return write shows in the app as "Delivery not confirmed" or "Nothing was sent".

## Manual disposal rules preparation — 2026-11-13

The new path needs an explicit rule when your existing rules only allow `commands/reset`.
[`firebase/manual-disposal.rules.example.json`](firebase/manual-disposal.rules.example.json)
is a **disposal-only merge example**, not a replacement for the project's full rules. It retains
the app's existing authenticated-operator model, requires a new random identity and a fresh
server timestamp, blocks replacement of pending/dispatching requests, and only lets clients
write the five request fields with status `pending`. The Pi Admin SDK alone records
`dispatching`, controller identity, claim time and the terminal result. Parent rules must not
already grant broader writes, because a child rule cannot revoke such permission.

The example has been JSON-checked only; its Firebase rule semantics, deployed permissions and
multi-client transactions require Emulator/Rules Playground acceptance before deployment.
Test: authenticated valid request allowed; unauthenticated, reused identity, old timestamp,
extra claim/result fields, invented accepted status, and replacing unresolved requests denied.
Test a normal request after each terminal result (accepted/failed/unconfirmed) as well.
No live rules or database records were changed by this task.

Status now includes `manualDisposalReady` and `manualDisposalUpdatedAt`, written by the Pi from
fresh executor eligibility. The app requires explicit IDLE and expires eligibility after
15 seconds. Bin/gas/load readings are not prerequisites. The Pi rejects pending requests
over 30 seconds old or more than 5 seconds in the future and claims each one before queuing
one START. A restart only queries the controller ID recorded in the claim. A matching MCU
ACK means `accepted`, not arrival; after 15 seconds without confirmation the claim becomes
`unconfirmed`. Return requests retain their separate path and behavior.

## Operator control rules — 2026-11-15

Sign-in is anonymous, and the APK is public. Without an allow-list anyone who installs the app
could stop, release or restart the robot. `firebase/manual-disposal.rules.example.json` therefore
allows writes to `commands/operator` and `commands/map` only when
`operators/<uid>` is `true`, validates every field of a request (the action must be one of the
eleven the Pi knows, `requestedAt` must be the server's `now`, `requestedBy` must be the
caller), makes `operator` and `map/frame` read-only for clients, and lets a phone read only its
own entry under `operators`.

To add a phone: open its **Controls** tab, read "This phone's ID" at the bottom, and add
`operators/<that id>: true` in the Firebase console. An anonymous account's ID changes when the
app is uninstalled or signed out, so it then has to be added again.

These rules were prepared and JSON-checked here. They were **not deployed or tested against the
live database**. Manual disposal and the return request keep their existing rules, which accept
any signed-in account.
