#!/usr/bin/env bash
# =============================================================================
# AnvilVM — One-command QEMU build via Docker
# =============================================================================
# Builds QEMU for Android arm64-v8a inside a Docker container.
# No local dependencies needed (NDK downloaded inside container).
#
# Usage:
#   ./scripts/build-docker.sh
#
# Output will be in: docker/output/arm64-v8a/
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=== AnvilVM Docker QEMU Builder ==="
echo "Building QEMU for Android arm64-v8a..."
echo ""

cd "$PROJECT_ROOT"

# Build and run
docker build -t anvilvm-qemu-builder -f docker/Dockerfile.qemu-builder .
docker run --rm -v "$(pwd)/docker/output:/output" anvilvm-qemu-builder

# Copy to jniLibs
echo ""
echo "=== Copying to jniLibs ==="
mkdir -p app/src/main/jniLibs/arm64-v8a
cp docker/output/arm64-v8a/*.so app/src/main/jniLibs/arm64-v8a/

echo ""
echo "=== Done! ==="
echo "QEMU libraries installed to app/src/main/jniLibs/arm64-v8a/"
ls -lh app/src/main/jniLibs/arm64-v8a/*.so
