#!/usr/bin/env python3
"""Fast source-only release invariants for the GARBY Android client.

This is intentionally independent of Gradle/Android SDK so high-value security
and control-path regressions can be caught even on a minimal host.
"""
from __future__ import annotations

import re
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main"
JAVA = SRC / "java"
MANIFEST = SRC / "AndroidManifest.xml"
BUILD = ROOT / "app/build.gradle.kts"
VERSIONS = ROOT / "gradle/libs.versions.toml"

failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


for robot_folder in ("RasPi", "BLE_Receiver-Final", "MAIN_EXECUTOR_CODE"):
    if (ROOT / robot_folder).exists():
        fail(f"Separate robot repository content found: {robot_folder}")
for path in (ROOT / "docs/verification").rglob("*"):
    if path.is_file() and (path.name == "robot-integration.patch" or
            path.name.startswith(("firmware-", "bridge-target", "executor-target", "pi-"))):
        fail(f"Robot verification belongs in the robot repository: {path.relative_to(ROOT)}")


# Parse configuration/resources.
with VERSIONS.open("rb") as handle:
    tomllib.load(handle)
for path in [MANIFEST, *(SRC / "res").rglob("*.xml")]:
    try:
        ET.parse(path)
    except Exception as exc:  # pragma: no cover - host validation path
        fail(f"XML parse failed: {path.relative_to(ROOT)}: {exc}")

manifest_text = MANIFEST.read_text(encoding="utf-8")
if 'android:allowBackup="false"' not in manifest_text:
    fail("Application backup must remain disabled")
if 'android:usesCleartextTraffic="false"' not in manifest_text:
    fail("Cleartext traffic must remain disabled")
if "CHANGE_NETWORK_STATE" in manifest_text:
    fail("CHANGE_NETWORK_STATE should not be requested")
if 'android:dataExtractionRules="@xml/data_extraction_rules"' not in manifest_text:
    fail("Android 12+ device transfer must stay excluded by data extraction rules")

source_files = list(JAVA.rglob("*.kt"))
source_text = "\n".join(path.read_text(encoding="utf-8") for path in source_files)
build_text = BUILD.read_text(encoding="utf-8")

for label, pattern in {
    "trust-all TLS": r"NoopHostnameVerifier|X509TrustManager|TrustAll",
    "embedded credential constants": r"const\s+val\s+(?:PASSWORD|USERNAME|EMAIL)\s*=",
    "manual RTDB disk persistence": r"setPersistenceEnabled\s*\(",
    "legacy direct HTTP stack": r"org\.apache\.hc|SensorAPIHelper|BasicCredentialsProvider",
    "unbounded/dead custom executor": r"LinkedBlockingQueue|AdaptiveThreadPool|ExecutorServices",
    "FCM registration token value logging": r"Log\.[A-Za-z]+\([^\n]*(?:\$token|\+\s*token)",
    "deprecated FCM registration-token API": r"FirebaseMessaging\.getInstance\(\)\.token|override\s+fun\s+onNewToken",
}.items():
    if re.search(pattern, source_text, re.IGNORECASE):
        fail(f"Forbidden pattern detected: {label}")

if re.search(r"(?:reading\?\.value|batteryPercent)\s*\?:\s*0", source_text):
    fail("Unavailable telemetry must not silently default to zero")

for required in [
    "ServerValue.TIMESTAMP",
    "purgeOutstandingWrites()",
    "waitForFirebaseConnection()",
    "requestedBy = uid",
    "else -> Unknown",
    # The server copy of the robot state is re-checked immediately before the write.
    "notReadyMessage(unmet, status)",
]:
    if required not in source_text:
        fail(f"Reset safety invariant missing: {required}")

for forbidden_dependency in [
    "httpclient",
    "gson",
    "kotlinx.serialization",
    "firebase.analytics",
    "firebase.crashlytics",
    "datastore",
    "constraintlayout",
    "androidx.window",
]:
    if forbidden_dependency in build_text:
        fail(f"Unused/legacy dependency returned: {forbidden_dependency}")

# The new control must remain confirmed, transactional and lifecycle-bound.
disposal_sources = {
    "MainDashboard.kt": ["Start manual disposal?", "Yes, start disposal", "confirmDisposal = false"],
    "realtime/GarbyRealtimeDb.kt": ["commands/dispose", "ref.runTransaction(",
        "canReplaceDisposal(", "extractDeviceStatus(null, statusSnapshot)"],
    "realtime/DeviceViewModel.kt": ["collectDisposal(this)", "disposalJob?.isActive",
        "GarbyDispatchers.background"],
    "realtime/DisposalLogic.kt": ["manualDisposalReady != true", "RobotState.Idle",
        "DisposalStatus.Unknown", "Follow the robot status for arrival"],
}
for relative, required in disposal_sources.items():
    path = JAVA / "com/thesis/garby" / relative
    text = path.read_text(encoding="utf-8") if path.is_file() else ""
    for marker in required:
        if marker not in text:
            fail(f"Manual disposal invariant missing in {relative}: {marker}")

# Keep the ViewModel and its database API together. A partial source update previously left
# unresolved map helpers and a five-argument call against the old three-argument send method.
operator_root = JAVA / "com/thesis/garby"
operator_vm = (operator_root / "OperatorViewModel.kt").read_text(encoding="utf-8")
operator_db = (operator_root / "realtime/OperatorDb.kt").read_text(encoding="utf-8")
for name in ("observeWhileVisible", "observeMapWhileVisible"):
    if not re.search(rf"suspend\s+fun\s+{name}\(\)\s*\{{", operator_vm):
        fail(f"Operator observer {name} must use a Unit block body; a launch result is a Job")
for label, pattern in {
    "mapTimestamp": r"fun\s+mapTimestamp\([^)]*\)\s*:\s*Flow<Long>",
    "ordinary send": r"suspend\s+fun\s+send\(\s*deviceId:\s*String,\s*uid:\s*String,\s*action:\s*OperatorAction\s*\)",
    "configured send": r"suspend\s+fun\s+send\(\s*deviceId:\s*String,\s*uid:\s*String,\s*action:\s*OperatorAction,\s*sensors:\s*Int\?,\s*fast:\s*Boolean\?\s*\)",
    "map session": r"suspend\s+fun\s+observeMapSession\(",
}.items():
    if not re.search(pattern, operator_db):
        fail(f"OperatorDb {label} API missing; update it with OperatorViewModel.kt")
if len(re.findall(r"suspend\s+fun\s+observeMapSession\(", source_text)) != 1:
    fail("Exactly one observeMapSession definition is required; remove the superseded MapSession.kt")

# A full operator-state subscription here used to invalidate the navigation host on each
# clock tick and map frame. The dock must observe only its distinct stop-button projection.
activity = (operator_root / "MainActivity.kt").read_text(encoding="utf-8")
bottom_bar = (operator_root / "ui/BottomBar.kt").read_text(encoding="utf-8")
if "operatorViewModel.uiState.collectAsStateWithLifecycle()" in activity:
    fail("Navigation must not collect the full operator/map/clock state")
for marker in ("viewModel.stopState.collectAsStateWithLifecycle()", "Alignment.BottomCenter", "LocalFloatingDockInset"):
    if marker not in activity:
        fail(f"Floating dock state/layout contract missing: {marker}")
if ".background(GarbyColors.Night)" in bottom_bar:
    fail("The floating dock must not paint an opaque background outside its glass container")
for relative in ("OperatorViewModel.kt", "ReturnViewModel.kt", "realtime/DeviceViewModel.kt"):
    text = (operator_root / relative).read_text(encoding="utf-8")
    if "GarbyRealtimeDb.monitoringTimeMs" not in text:
        fail(f"Use the shared monitoring clock in {relative}")
if "delay(1_000L.milliseconds)" in operator_vm:
    fail("Operator monitoring must use callbacks/shared time instead of per-second auth/network polling")

# The published APK is the CI release build, always signed with the one persistent key:
# a different key forces every phone to uninstall the app before it can update.
WORKFLOW = ROOT / ".github/workflows/android.yml"
workflow_text = WORKFLOW.read_text(encoding="utf-8") if WORKFLOW.is_file() else ""
for marker in [
    "GARBY_RELEASE_CERT_SHA256: 753c3352ac396e1a006f11ccb1a30e06f5bf990a9f94c0414469950af818e5d7",
    "GARBY_KEY_ALIAS: garby-release",
    "secrets.GARBY_KEYSTORE_BASE64",
    ":app:assembleRelease",
    'apksigner" verify',
    "python3 tools/apk_signer_cert.py out/GarbyMobileApp.apk",
    "set -o pipefail",
]:
    if marker not in workflow_text:
        fail(f"Release signing invariant missing in android.yml: {marker}")
if not (ROOT / "tools/apk_signer_cert.py").is_file():
    fail("tools/apk_signer_cert.py is missing: the release workflow checks the APK's signer with it")
if re.search(r"debug key", workflow_text, re.IGNORECASE):
    fail("The release workflow must not fall back to a debug key")
if 'if (garbyKeystore != null) signingConfig = signingConfigs.getByName("garby")' not in build_text:
    fail("The release build type must be signed with the persistent garby key")

# Local resource references must resolve by resource stem, in any qualified folder
# (for example drawable-nodpi).
resource_stems: dict[str, set[str]] = {}
for resource_type in ("drawable", "font"):
    stems: set[str] = set()
    for folder in (SRC / "res").glob(f"{resource_type}*"):
        if folder.is_dir():
            stems.update(p.stem for p in folder.iterdir() if p.is_file())
    resource_stems[resource_type] = stems

# Bitmaps in the densityless drawable/ folder are treated as mdpi and scaled up on every
# high-density phone (a 2000 px logo decoded at about 80 MB). Raster images belong in
# drawable-nodpi or a density folder.
for path in (SRC / "res" / "drawable").iterdir():
    if path.suffix.lower() in {".png", ".webp", ".jpg", ".jpeg"}:
        fail(f"Raster image in densityless res/drawable: {path.name}")
for resource_type, name in re.findall(r"R\.(drawable|font)\.([A-Za-z0-9_]+)", source_text):
    if name not in resource_stems[resource_type]:
        fail(f"Missing R.{resource_type}.{name}")

if len(resource_stems["font"]) > 3:
    fail("Unexpected Montserrat font payload growth")

if failures:
    print("STATIC_QUALITY_CHECK: FAIL")
    for item in failures:
        print(f" - {item}")
    sys.exit(1)

print("STATIC_QUALITY_CHECK: PASS")
print(f"Kotlin files checked: {len(source_files)}")
print(f"Bundled font files: {len(resource_stems['font'])}")
