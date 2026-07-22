# =============================================================================
# AnvilVM — CMake Toolchain for QEMU Android Cross-Compilation
# =============================================================================
# Usage (for dependencies that use CMake):
#   cmake -DCMAKE_TOOLCHAIN_FILE=scripts/qemu-android-toolchain.cmake ..
# =============================================================================

set(CMAKE_SYSTEM_NAME Android)
set(CMAKE_SYSTEM_VERSION 26)
set(CMAKE_ANDROID_ARCH_ABI arm64-v8a)
set(CMAKE_ANDROID_NDK $ENV{ANDROID_NDK_HOME})
set(CMAKE_ANDROID_STL_TYPE c++_shared)

set(CMAKE_C_COMPILER_TARGET aarch64-linux-android26)
set(CMAKE_CXX_COMPILER_TARGET aarch64-linux-android26)

# Use LLVM/Clang from NDK
set(TOOLCHAIN_PREFIX "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64")
set(CMAKE_C_COMPILER "${TOOLCHAIN_PREFIX}/bin/aarch64-linux-android26-clang")
set(CMAKE_CXX_COMPILER "${TOOLCHAIN_PREFIX}/bin/aarch64-linux-android26-clang++")
set(CMAKE_AR "${TOOLCHAIN_PREFIX}/bin/llvm-ar" CACHE FILEPATH "Archiver")
set(CMAKE_RANLIB "${TOOLCHAIN_PREFIX}/bin/llvm-ranlib" CACHE FILEPATH "Ranlib")
set(CMAKE_STRIP "${TOOLCHAIN_PREFIX}/bin/llvm-strip" CACHE FILEPATH "Strip")

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
