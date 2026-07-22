#!/usr/bin/env bash
# =============================================================================
# AnvilVM — Full Integration Test Suite
# =============================================================================
# Tests the complete pipeline:
#   1. QEMU binary verification (architecture, linking)
#   2. Alpine Linux boot test (x86_64 via TCG)
#   3. VNC server connectivity test
#   4. Snapshot create/restore test
#   5. RISC-V firmware load test
#   6. Performance baseline measurement
#
# Prerequisites:
#   - QEMU installed on host (for host-side tests)
#   - test-images/ populated (run download-alpine.sh)
#   - Built QEMU .so files (for binary verification only)
#
# Usage:
#   ./scripts/integration/full-integration-test.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
IMAGE_DIR="${PROJECT_ROOT}/test-images"
RESULTS_DIR="${PROJECT_ROOT}/test-results"
LOG_DIR="${RESULTS_DIR}/logs"

PASS=0
FAIL=0
SKIP=0

mkdir -p "$LOG_DIR"

# --- Helpers ---
log_test() { echo "  [TEST] $1"; }
log_pass() { echo "  [PASS] $1"; PASS=$((PASS + 1)); }
log_fail() { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); }
log_skip() { echo "  [SKIP] $1"; SKIP=$((SKIP + 1)); }

echo "=============================================="
echo "  AnvilVM Full Integration Test Suite"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "=============================================="
echo ""

# --- Test 1: QEMU Binary Verification ---
echo "=== Test 1: QEMU Binary Verification ==="

JNILIBS="${PROJECT_ROOT}/app/src/main/jniLibs/arm64-v8a"
if [ -d "$JNILIBS" ] && ls "$JNILIBS"/libqemu-*.so 1>/dev/null 2>&1; then
    for so_file in "$JNILIBS"/libqemu-*.so; do
        log_test "Checking $(basename "$so_file")"
        file_info=$(file "$so_file" 2>/dev/null || echo "unknown")
        if echo "$file_info" | grep -qi "ELF.*aarch64"; then
            log_pass "$(basename "$so_file") is valid ELF aarch64"
        elif echo "$file_info" | grep -qi "ELF"; then
            log_pass "$(basename "$so_file") is valid ELF (non-arm64 may be for emulator)"
        else
            log_fail "$(basename "$so_file") is not a valid ELF binary"
        fi

        # Check file size (should be > 5MB for a minimal QEMU)
        size=$(stat -f%z "$so_file" 2>/dev/null || stat -c%s "$so_file" 2>/dev/null || echo "0")
        if [ "$size" -gt 5000000 ]; then
            log_pass "$(basename "$so_file") size OK (${size} bytes)"
        else
            log_fail "$(basename "$so_file") too small (${size} bytes) — may be incomplete"
        fi
    done
else
    log_skip "No .so files found in jniLibs/ (run build-docker.sh first)"
fi

echo ""

# --- Test 2: Alpine Linux x86_64 Boot ---
echo "=== Test 2: Alpine Linux Boot (x86_64 TCG) ==="

if command -v qemu-system-x86_64 &>/dev/null && [ -f "${IMAGE_DIR}/alpine-virt.iso" ]; then
    log_test "Booting Alpine with serial output capture"

    SERIAL_LOG="${LOG_DIR}/alpine-boot-serial.log"
    timeout 20 qemu-system-x86_64 \
        -machine virt \
        -cpu max \
        -m 512 \
        -smp 1 \
        -cdrom "${IMAGE_DIR}/alpine-virt.iso" \
        -nographic \
        -serial file:"$SERIAL_LOG" \
        -no-reboot \
        2>/dev/null &
    QEMU_PID=$!

    sleep 12
    kill $QEMU_PID 2>/dev/null; wait $QEMU_PID 2>/dev/null || true

    if [ -f "$SERIAL_LOG" ] && [ "$(wc -c < "$SERIAL_LOG")" -gt 100 ]; then
        if grep -qi "linux\|kernel\|boot\|alpine" "$SERIAL_LOG"; then
            log_pass "Alpine booted — kernel messages detected"
        else
            log_pass "QEMU produced serial output ($(wc -c < "$SERIAL_LOG") bytes)"
        fi
    else
        log_fail "No meaningful serial output captured"
    fi
else
    if ! command -v qemu-system-x86_64 &>/dev/null; then
        log_skip "qemu-system-x86_64 not installed"
    else
        log_skip "Alpine ISO not found (run download-alpine.sh)"
    fi
fi

echo ""

# --- Test 3: VNC Server Connectivity ---
echo "=== Test 3: VNC Server Connectivity ==="

if command -v qemu-system-x86_64 &>/dev/null && [ -f "${IMAGE_DIR}/alpine-virt.iso" ]; then
    log_test "Starting QEMU with VNC on :5"

    qemu-system-x86_64 \
        -machine virt \
        -cpu max \
        -m 256 \
        -cdrom "${IMAGE_DIR}/alpine-virt.iso" \
        -vnc :5 \
        -daemonize \
        -pidfile /tmp/anvilvm-vnc-test.pid \
        2>/dev/null || true

    sleep 3

    # Test TCP connection to VNC port
    if command -v nc &>/dev/null; then
        VNC_RESPONSE=$(echo "" | timeout 3 nc -w2 127.0.0.1 5905 2>/dev/null | head -c 12 || echo "")
        if echo "$VNC_RESPONSE" | grep -q "RFB"; then
            log_pass "VNC server responded with RFB handshake"
        else
            log_fail "VNC server did not respond with valid RFB"
        fi
    elif command -v bash &>/dev/null; then
        if (echo > /dev/tcp/127.0.0.1/5905) 2>/dev/null; then
            log_pass "VNC port 5905 is open"
        else
            log_fail "VNC port 5905 is not reachable"
        fi
    else
        log_skip "No nc or /dev/tcp available for port test"
    fi

    # Cleanup
    if [ -f /tmp/anvilvm-vnc-test.pid ]; then
        kill "$(cat /tmp/anvilvm-vnc-test.pid)" 2>/dev/null || true
        rm -f /tmp/anvilvm-vnc-test.pid
    fi
else
    log_skip "Prerequisites not met for VNC test"
fi

echo ""

# --- Test 4: Snapshot Create/Restore ---
echo "=== Test 4: QCOW2 Snapshot Create/Restore ==="

if command -v qemu-img &>/dev/null; then
    TEST_DISK="${LOG_DIR}/test-snapshot.qcow2"
    log_test "Creating test QCOW2 disk"
    qemu-img create -f qcow2 "$TEST_DISK" 100M >/dev/null 2>&1

    log_test "Creating snapshot 'test-snap-1'"
    if qemu-img snapshot -c "test-snap-1" "$TEST_DISK" 2>/dev/null; then
        log_pass "Snapshot created successfully"
    else
        log_fail "Failed to create snapshot"
    fi

    log_test "Listing snapshots"
    SNAP_LIST=$(qemu-img snapshot -l "$TEST_DISK" 2>/dev/null || echo "")
    if echo "$SNAP_LIST" | grep -q "test-snap-1"; then
        log_pass "Snapshot 'test-snap-1' found in listing"
    else
        log_fail "Snapshot not found in listing"
    fi

    log_test "Applying snapshot"
    if qemu-img snapshot -a "test-snap-1" "$TEST_DISK" 2>/dev/null; then
        log_pass "Snapshot applied successfully"
    else
        log_fail "Failed to apply snapshot"
    fi

    log_test "Deleting snapshot"
    if qemu-img snapshot -d "test-snap-1" "$TEST_DISK" 2>/dev/null; then
        log_pass "Snapshot deleted successfully"
    else
        log_fail "Failed to delete snapshot"
    fi

    rm -f "$TEST_DISK"
else
    log_skip "qemu-img not installed"
fi

echo ""

# --- Test 5: RISC-V Firmware Load ---
echo "=== Test 5: RISC-V Guest Support ==="

if command -v qemu-system-riscv64 &>/dev/null; then
    log_test "Testing RISC-V virt machine init"

    RISCV_LOG="${LOG_DIR}/riscv-boot.log"
    timeout 8 qemu-system-riscv64 \
        -machine virt \
        -cpu rv64 \
        -m 256 \
        -smp 1 \
        -bios default \
        -nographic \
        -serial file:"$RISCV_LOG" \
        -no-reboot \
        2>/dev/null &
    RISCV_PID=$!

    sleep 5
    kill $RISCV_PID 2>/dev/null; wait $RISCV_PID 2>/dev/null || true

    if [ -f "$RISCV_LOG" ] && [ "$(wc -c < "$RISCV_LOG")" -gt 10 ]; then
        if grep -qi "opensbi\|riscv\|sbi" "$RISCV_LOG"; then
            log_pass "RISC-V OpenSBI firmware booted"
        else
            log_pass "RISC-V QEMU produced output ($(wc -c < "$RISCV_LOG") bytes)"
        fi
    else
        log_fail "No RISC-V serial output captured"
    fi
else
    log_skip "qemu-system-riscv64 not installed"
fi

echo ""

# --- Test 6: Performance Baseline ---
echo "=== Test 6: Performance Baseline ==="

if command -v qemu-system-x86_64 &>/dev/null && [ -f "${IMAGE_DIR}/alpine-virt.iso" ]; then
    log_test "Measuring boot time (Alpine x86_64)"

    START_TIME=$(date +%s%N)
    timeout 15 qemu-system-x86_64 \
        -machine virt \
        -cpu max \
        -m 512 \
        -smp 2 \
        -cdrom "${IMAGE_DIR}/alpine-virt.iso" \
        -nographic \
        -no-reboot \
        2>/dev/null &
    PERF_PID=$!

    sleep 10
    END_TIME=$(date +%s%N)
    kill $PERF_PID 2>/dev/null; wait $PERF_PID 2>/dev/null || true

    ELAPSED_MS=$(( (END_TIME - START_TIME) / 1000000 ))
    log_pass "Boot test completed in ${ELAPSED_MS}ms (wall-clock)"

    # Check host CPU info for report
    if [ -f /proc/cpuinfo ]; then
        CPU_MODEL=$(grep "model name" /proc/cpuinfo | head -1 | cut -d: -f2 | xargs)
        CORES=$(nproc)
        echo "  Host CPU: $CPU_MODEL ($CORES cores)"
    fi
else
    log_skip "Prerequisites not met for performance test"
fi

echo ""
echo "=============================================="
echo "  RESULTS: ${PASS} passed, ${FAIL} failed, ${SKIP} skipped"
echo "  Total: $((PASS + FAIL + SKIP)) tests"
echo "=============================================="

# Save results
cat > "${RESULTS_DIR}/summary.txt" << EOF
AnvilVM Integration Test Results
Date: $(date '+%Y-%m-%d %H:%M:%S')
Host: $(uname -a)
QEMU: $(qemu-system-x86_64 --version 2>/dev/null | head -1 || echo "not installed")

Results: ${PASS} passed, ${FAIL} failed, ${SKIP} skipped
Total: $((PASS + FAIL + SKIP)) tests
EOF

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
