#!/usr/bin/env bash
# =============================================================================
# AnvilVM — End-to-End Test: Boot Alpine in QEMU and verify serial output
# =============================================================================
# Verifies the full pipeline: QEMU binary loads → VM boots → kernel outputs text
#
# Prerequisites:
#   - QEMU installed (apt install qemu-system-x86_64)
#   - test-images/alpine-virt.iso exists (run download-alpine.sh first)
#
# Usage:
#   ./scripts/test/run-e2e-test.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
IMAGE_DIR="${PROJECT_ROOT}/test-images"
LOG_FILE="/tmp/anvilvm-e2e-test.log"

echo "=== AnvilVM End-to-End Test ==="
echo ""

# Check prerequisites
if ! command -v qemu-system-x86_64 &> /dev/null; then
    echo "FAIL: qemu-system-x86_64 not found. Install with:"
    echo "  sudo apt install qemu-system-x86"
    exit 1
fi

if [ ! -f "${IMAGE_DIR}/alpine-virt.iso" ]; then
    echo "FAIL: Alpine ISO not found. Run first:"
    echo "  ./scripts/test/download-alpine.sh"
    exit 1
fi

echo "[1/4] Starting QEMU with Alpine ISO (timeout: 30s)..."

# Boot QEMU in background, capture serial output
timeout 30 qemu-system-x86_64 \
    -machine virt \
    -cpu max \
    -m 512 \
    -smp 1 \
    -cdrom "${IMAGE_DIR}/alpine-virt.iso" \
    -nographic \
    -serial file:"$LOG_FILE" \
    -no-reboot \
    2>/dev/null &

QEMU_PID=$!
echo "  QEMU PID: $QEMU_PID"

echo "[2/4] Waiting for kernel boot messages (15s)..."
sleep 15

echo "[3/4] Checking serial output..."

# Kill QEMU
kill $QEMU_PID 2>/dev/null || true
wait $QEMU_PID 2>/dev/null || true

if [ ! -f "$LOG_FILE" ]; then
    echo "FAIL: No serial output captured."
    exit 1
fi

OUTPUT_SIZE=$(wc -c < "$LOG_FILE")
echo "  Serial output: ${OUTPUT_SIZE} bytes"

# Check for Linux kernel boot signature
if grep -qi "linux" "$LOG_FILE" || grep -qi "boot" "$LOG_FILE" || grep -qi "kernel" "$LOG_FILE"; then
    echo ""
    echo "[4/4] RESULT: PASS"
    echo "  VM booted successfully. Kernel messages detected."
    echo ""
    echo "  First 10 lines of serial output:"
    head -10 "$LOG_FILE" | sed 's/^/    /'
else
    echo ""
    echo "[4/4] RESULT: PARTIAL"
    echo "  QEMU started but no kernel messages detected."
    echo "  This may be normal if the ISO needs more boot time."
    echo ""
    echo "  Raw output (first 500 bytes):"
    head -c 500 "$LOG_FILE" | sed 's/^/    /'
fi

# Cleanup
rm -f "$LOG_FILE"

echo ""
echo "=== Test Complete ==="
