#!/usr/bin/env bash
# Run the Android unified evidence instrumented test on a connected
# device/emulator and attempt to pull the resulting JSON artifact.
#
# Usage:
#   scripts/run_evidence_connected.sh
#
# Requires: adb on PATH, a connected device or running emulator, and the
# Android SDK configured for the project.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

PACKAGE="com.reader.android"
EVIDENCE_DIR="files/unified-evidence"

echo "=== Connected devices ==="
adb devices

echo ""
echo "=== Running :app:connectedDebugAndroidTest (UnifiedEvidenceInstrumentedTest) ==="
./gradlew :app:connectedDebugAndroidTest \
    --tests "com.reader.UnifiedEvidenceInstrumentedTest" \
    --no-daemon

echo ""
echo "=== Attempting to list evidence artifacts on device ($PACKAGE) ==="
ARTIFACTS=$(adb shell run-as "$PACKAGE" ls "$EVIDENCE_DIR/" 2>/dev/null || true)
if [[ -z "$ARTIFACTS" ]]; then
    echo "(could not list $EVIDENCE_DIR/ — app may not be debuggable, or no artifact was written)"
else
    echo "$ARTIFACTS"

    echo ""
    echo "=== Attempting to pull latest artifact ==="
    # The `ls` output is one filename per line; pick the last (newest by name
    # due to the timestamp suffix).
    LATEST=$(echo "$ARTIFACTS" | tail -1 | awk '{print $NF}')
    if [[ -n "$LATEST" ]]; then
        echo "Latest artifact: $LATEST"
        OUT_PATH="$REPO_ROOT/evidence-android-$LATEST"
        adb shell run-as "$PACKAGE" cat "$EVIDENCE_DIR/$LATEST" > "$OUT_PATH"
        echo "Pulled to: $OUT_PATH"
        echo ""
        echo "=== Artifact preview (first 60 lines) ==="
        head -n 60 "$OUT_PATH"
    fi
fi

echo ""
echo "=== Done ==="
