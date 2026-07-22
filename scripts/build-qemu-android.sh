#!/usr/bin/env bash
# =============================================================================
# AnvilVM — QEMU Cross-Compilation for Android NDK
# =============================================================================
# Builds QEMU as a shared library (.so) targeting arm64-v8a Android devices.
# Output: libqemu-system-x86_64.so, libqemu-system-aarch64.so
#
# Prerequisites:
#   - Android NDK r26+ (set ANDROID_NDK_HOME)
#   - Linux host (Ubuntu 22.04+ recommended)
#   - Dependencies: ninja-build, pkg-config, python3, glib2 source
#
# Usage:
#   export ANDROID_NDK_HOME=/path/to/ndk
#   ./scripts/build-qemu-android.sh
# =============================================================================

set -euo pipefail

# --- Configuration ---
QEMU_VERSION="8.2.4"
QEMU_URL="https://download.qemu.org/qemu-${QEMU_VERSION}.tar.xz"
API_LEVEL=26
HOST_ARCH="linux-x86_64"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="${PROJECT_ROOT}/build-qemu"
OUTPUT_DIR="${PROJECT_ROOT}/app/src/main/jniLibs"

# --- Validate NDK ---
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "ERROR: ANDROID_NDK_HOME is not set"
    echo "  export ANDROID_NDK_HOME=/path/to/android-ndk-r26d"
    exit 1
fi

TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/${HOST_ARCH}"
if [ ! -d "$TOOLCHAIN" ]; then
    echo "ERROR: NDK toolchain not found at ${TOOLCHAIN}"
    exit 1
fi

echo "=== AnvilVM QEMU Build System ==="
echo "  QEMU Version: ${QEMU_VERSION}"
echo "  NDK: ${ANDROID_NDK_HOME}"
echo "  API Level: ${API_LEVEL}"
echo "  Output: ${OUTPUT_DIR}"
echo ""

# --- Download QEMU source ---
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

if [ ! -d "qemu-${QEMU_VERSION}" ]; then
    echo "[1/5] Downloading QEMU ${QEMU_VERSION}..."
    if [ ! -f "qemu-${QEMU_VERSION}.tar.xz" ]; then
        curl -L -o "qemu-${QEMU_VERSION}.tar.xz" "$QEMU_URL"
    fi
    echo "[1/5] Extracting..."
    tar xf "qemu-${QEMU_VERSION}.tar.xz"
else
    echo "[1/5] QEMU source already present, skipping download."
fi

# --- Build static dependencies (glib, pixman, zlib) ---
SYSROOT="${BUILD_DIR}/sysroot-arm64"
mkdir -p "$SYSROOT"

build_zlib() {
    echo "[2/5] Building zlib for arm64..."
    cd "$BUILD_DIR"
    if [ ! -f "$SYSROOT/lib/libz.a" ]; then
        ZLIB_VERSION="1.3.1"
        [ -d "zlib-${ZLIB_VERSION}" ] || {
            curl -L -o zlib.tar.gz "https://zlib.net/zlib-${ZLIB_VERSION}.tar.gz"
            tar xf zlib.tar.gz
        }
        cd "zlib-${ZLIB_VERSION}"
        export CC="${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang"
        export AR="${TOOLCHAIN}/bin/llvm-ar"
        export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
        ./configure --prefix="$SYSROOT" --static
        make -j"$(nproc)" && make install
        unset CC AR RANLIB
    fi
}

build_pixman() {
    echo "[3/5] Building pixman for arm64..."
    cd "$BUILD_DIR"
    if [ ! -f "$SYSROOT/lib/libpixman-1.a" ]; then
        PIXMAN_VERSION="0.42.2"
        [ -d "pixman-${PIXMAN_VERSION}" ] || {
            curl -L -o pixman.tar.gz \
                "https://cairographics.org/releases/pixman-${PIXMAN_VERSION}.tar.gz"
            tar xf pixman.tar.gz
        }
        cd "pixman-${PIXMAN_VERSION}"

        cat > android-cross.txt << EOF
[binaries]
c = '${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang'
ar = '${TOOLCHAIN}/bin/llvm-ar'
strip = '${TOOLCHAIN}/bin/llvm-strip'
[host_machine]
system = 'linux'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
[properties]
sys_root = '${TOOLCHAIN}/sysroot'
EOF
        meson setup builddir --cross-file android-cross.txt \
            --prefix="$SYSROOT" \
            --default-library=static \
            -Dgtk=disabled -Dlibpng=disabled -Dtests=disabled
        ninja -C builddir install
    fi
}

build_glib() {
    echo "[4/5] Building glib for arm64..."
    cd "$BUILD_DIR"
    if [ ! -f "$SYSROOT/lib/libglib-2.0.a" ]; then
        GLIB_VERSION="2.78.4"
        GLIB_MAJOR="2.78"
        [ -d "glib-${GLIB_VERSION}" ] || {
            curl -L -o glib.tar.xz \
                "https://download.gnome.org/sources/glib/${GLIB_MAJOR}/glib-${GLIB_VERSION}.tar.xz"
            tar xf glib.tar.xz
        }
        cd "glib-${GLIB_VERSION}"

        cat > android-cross.txt << EOF
[binaries]
c = '${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang'
ar = '${TOOLCHAIN}/bin/llvm-ar'
strip = '${TOOLCHAIN}/bin/llvm-strip'
pkgconfig = 'pkg-config'
[host_machine]
system = 'linux'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
[properties]
sys_root = '${TOOLCHAIN}/sysroot'
pkg_config_libdir = '${SYSROOT}/lib/pkgconfig'
[built-in options]
c_args = ['-I${SYSROOT}/include']
c_link_args = ['-L${SYSROOT}/lib']
EOF
        meson setup builddir --cross-file android-cross.txt \
            --prefix="$SYSROOT" \
            --default-library=static \
            -Dtests=false -Dxattr=false -Dlibmount=disabled \
            -Dnls=disabled -Dinstalled_tests=false
        ninja -C builddir install
    fi
}

build_zlib
build_pixman
build_glib

# --- Configure & Build QEMU ---
echo "[5/5] Configuring QEMU for Android arm64..."
cd "${BUILD_DIR}/qemu-${QEMU_VERSION}"

export PKG_CONFIG_PATH="${SYSROOT}/lib/pkgconfig"
export PKG_CONFIG_LIBDIR="${SYSROOT}/lib/pkgconfig"

# Build x86_64 system emulator
mkdir -p build-android && cd build-android

../configure \
    --cross-prefix="aarch64-linux-android${API_LEVEL}-" \
    --cc="${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang" \
    --cxx="${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang++" \
    --host-cc=gcc \
    --ar="${TOOLCHAIN}/bin/llvm-ar" \
    --strip="${TOOLCHAIN}/bin/llvm-strip" \
    --target-list="x86_64-softmmu,aarch64-softmmu,riscv64-softmmu" \
    --prefix="${BUILD_DIR}/install" \
    --extra-cflags="-I${SYSROOT}/include -fPIC" \
    --extra-ldflags="-L${SYSROOT}/lib -fPIC -shared" \
    --disable-werror \
    --disable-docs \
    --disable-gtk \
    --disable-sdl \
    --disable-opengl \
    --disable-virglrenderer \
    --disable-vte \
    --disable-brlapi \
    --disable-curl \
    --disable-curses \
    --disable-gnutls \
    --disable-nettle \
    --disable-gcrypt \
    --disable-libusb \
    --disable-usb-redir \
    --disable-libssh \
    --disable-libnfs \
    --disable-libiscsi \
    --disable-rbd \
    --disable-spice \
    --disable-xen \
    --disable-tools \
    --disable-guest-agent \
    --disable-capstone \
    --enable-vnc \
    --enable-slirp \
    --enable-tcg \
    --enable-qmp \
    --static

echo "Building QEMU (this may take 15-30 minutes)..."
make -j"$(nproc)"

# --- Package as .so files ---
echo ""
echo "=== Packaging QEMU binaries as .so ==="

mkdir -p "${OUTPUT_DIR}/arm64-v8a"

# Copy and rename as .so (Android extracts .so from APK lib/ directory)
cp qemu-system-x86_64 "${OUTPUT_DIR}/arm64-v8a/libqemu-system-x86_64.so"
cp qemu-system-aarch64 "${OUTPUT_DIR}/arm64-v8a/libqemu-system-aarch64.so"
cp qemu-system-riscv64 "${OUTPUT_DIR}/arm64-v8a/libqemu-system-riscv64.so"

# Also package qemu-img for snapshot support
cp qemu-img "${OUTPUT_DIR}/arm64-v8a/libqemu-img.so" 2>/dev/null || true

# Strip debug symbols to reduce size
"${TOOLCHAIN}/bin/llvm-strip" "${OUTPUT_DIR}/arm64-v8a/libqemu-system-x86_64.so"
"${TOOLCHAIN}/bin/llvm-strip" "${OUTPUT_DIR}/arm64-v8a/libqemu-system-aarch64.so"
"${TOOLCHAIN}/bin/llvm-strip" "${OUTPUT_DIR}/arm64-v8a/libqemu-system-riscv64.so"
"${TOOLCHAIN}/bin/llvm-strip" "${OUTPUT_DIR}/arm64-v8a/libqemu-img.so" 2>/dev/null || true

echo ""
echo "=== Build Complete ==="
echo "  x86_64 emulator:  ${OUTPUT_DIR}/arm64-v8a/libqemu-system-x86_64.so"
echo "  aarch64 emulator: ${OUTPUT_DIR}/arm64-v8a/libqemu-system-aarch64.so"
echo "  riscv64 emulator: ${OUTPUT_DIR}/arm64-v8a/libqemu-system-riscv64.so"
echo "  qemu-img tool:    ${OUTPUT_DIR}/arm64-v8a/libqemu-img.so"
ls -lh "${OUTPUT_DIR}/arm64-v8a/"*.so
