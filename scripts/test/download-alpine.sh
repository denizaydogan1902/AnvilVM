#!/usr/bin/env bash
# =============================================================================
# AnvilVM — Download Alpine Linux Virtual (x86_64) for testing
# =============================================================================
# Downloads a minimal Alpine Linux ISO and creates a QCOW2 disk image
# ready to boot in AnvilVM's QEMU engine.
#
# Usage:
#   ./scripts/test/download-alpine.sh
#
# Output:
#   test-images/alpine-virt.iso
#   test-images/alpine-disk.qcow2
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
IMAGE_DIR="${PROJECT_ROOT}/test-images"

ALPINE_VERSION="3.20.0"
ALPINE_URL="https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/x86_64/alpine-virt-${ALPINE_VERSION}-x86_64.iso"
DISK_SIZE="2G"

mkdir -p "$IMAGE_DIR"

echo "=== AnvilVM Test Image Setup ==="
echo "  Alpine Version: ${ALPINE_VERSION}"
echo "  Output: ${IMAGE_DIR}"
echo ""

# Download Alpine ISO
if [ ! -f "${IMAGE_DIR}/alpine-virt.iso" ]; then
    echo "[1/3] Downloading Alpine Linux Virtual ${ALPINE_VERSION}..."
    curl -L -o "${IMAGE_DIR}/alpine-virt.iso" "$ALPINE_URL"
    echo "  Downloaded: $(ls -lh "${IMAGE_DIR}/alpine-virt.iso" | awk '{print $5}')"
else
    echo "[1/3] Alpine ISO already exists, skipping download."
fi

# Create QCOW2 disk image
if [ ! -f "${IMAGE_DIR}/alpine-disk.qcow2" ]; then
    echo "[2/3] Creating ${DISK_SIZE} QCOW2 disk image..."
    qemu-img create -f qcow2 "${IMAGE_DIR}/alpine-disk.qcow2" "$DISK_SIZE"
else
    echo "[2/3] Disk image already exists, skipping."
fi

# Create test launch script
echo "[3/3] Creating test launch script..."
cat > "${IMAGE_DIR}/run-test-vm.sh" << 'EOF'
#!/usr/bin/env bash
# Launch Alpine VM for testing (run on Linux host with QEMU installed)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

qemu-system-x86_64 \
    -machine virt \
    -cpu max \
    -m 1024 \
    -smp 2 \
    -cdrom "${SCRIPT_DIR}/alpine-virt.iso" \
    -drive file="${SCRIPT_DIR}/alpine-disk.qcow2",format=qcow2,if=virtio \
    -netdev user,id=net0,hostfwd=tcp::2222-:22 \
    -device virtio-net-pci,netdev=net0 \
    -vnc :0 \
    -serial stdio \
    -boot d

# After Alpine boots:
#   1. Login as 'root' (no password)
#   2. Run 'setup-alpine' for installation
#   3. VNC available at localhost:5900
EOF
chmod +x "${IMAGE_DIR}/run-test-vm.sh"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "To test on a Linux host:"
echo "  cd test-images && ./run-test-vm.sh"
echo ""
echo "To test on Android (after QEMU build):"
echo "  1. Push alpine-virt.iso to /sdcard/AnvilVM/images/"
echo "  2. Push alpine-disk.qcow2 to /sdcard/AnvilVM/images/"
echo "  3. Select 'Alpine Linux' from AnvilVM Image Store"
echo ""
