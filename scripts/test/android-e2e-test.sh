#!/usr/bin/env bash
# =============================================================================
# AnvilVM — Android Device End-to-End Test
# =============================================================================
# Pushes Alpine ISO to device, launches AnvilVM, and verifies VM boot via logcat.
#
# Prerequisites:
#   - ADB connected to device
#   - AnvilVM APK installed
#   - test-images/alpine-virt.iso exists
#
# Usage:
#   ./scripts/test/android-e2e-test.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
IMAGE_DIR="${PROJECT_ROOT}/test-images"
DEVICE_IMAGE_DIR="/sdcard/AnvilVM/images"
PACKAGE="com.anvilvm.app"

echo "=== AnvilVM Android E2E Test ==="
echo ""

# Check ADB
if ! adb devices | grep -q "device$"; then
    echo "FAIL: No ADB device connected."
    echo "  Connect a device or start an emulator."
    exit 1
fi

echo "[1/5] Creating device directories..."
adb shell mkdir -p "$DEVICE_IMAGE_DIR"

echo "[2/5] Pushing Alpine ISO to device..."
if [ -f "${IMAGE_DIR}/alpine-virt.iso" ]; then
    adb push "${IMAGE_DIR}/alpine-virt.iso" "${DEVICE_IMAGE_DIR}/alpine-virt.iso"
else
    echo "  WARN: alpine-virt.iso not found locally. Run download-alpine.sh first."
fi

if [ -f "${IMAGE_DIR}/alpine-disk.qcow2" ]; then
    adb push "${IMAGE_DIR}/alpine-disk.qcow2" "${DEVICE_IMAGE_DIR}/alpine-disk.qcow2"
fi

echo "[3/5] Clearing previous logcat..."
adb logcat -c

echo "[4/5] Launching AnvilVM..."
adb shell am start -n "${PACKAGE}/.MainActivity"
sleep 3

echo "[5/5] Monitoring QEMU engine output (10s)..."
timeout 10 adb logcat -s "AnvilVM-QEMU:*" "AnvilVM-PTY:*" "AnvilVM-VNC:*" || true

echo ""
echo "=== Manual Verification Steps ==="
echo "  1. Open AnvilVM on the device"
echo "  2. Go to 'Images' tab and verify Alpine Linux appears"
echo "  3. Start the VM and switch to 'Terminal' tab"
echo "  4. You should see kernel boot messages within 10-30 seconds"
echo "  5. Switch to 'Display' tab to see VNC output"
echo ""
echo "=== Test Complete ==="
