# AnvilVM — Android Virtual Machine Platform

**AnvilVM** is an academic/research project that enables running full x86_64, ARM64, and RISC-V operating systems inside a virtual machine on Android devices — without root access.

> **This is an academic project.** AnvilVM is developed as a research and educational platform exploring mobile virtualization, cross-architecture emulation, and operating system fundamentals on Android. Community contributions, feedback, and collaboration are essential to its growth.

---

## Overview

AnvilVM packages QEMU as a native Android library (`.so`) and provides a modern Jetpack Compose interface with an embedded terminal emulator and VNC display client. It allows users to boot Linux distributions, custom operating systems, and experimental kernels directly on their Android phone or tablet.

### Key Features

- **No Root Required** — Runs entirely in user-space via QEMU TCG (Tiny Code Generator)
- **Multi-Architecture** — Emulates x86_64, aarch64, and riscv64 guests on arm64 Android host
- **Android 14+ Compatible** — Handles Phantom Process Killer and W^X restrictions
- **Embedded Terminal** — Full VT100/ANSI color terminal with 256-color and 24-bit RGB support
- **VNC Display** — RFB 3.8 protocol client for graphical OS desktops
- **Image Store** — Download and manage OS images (Alpine, Debian, Arch, AegisOS, etc.)

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│              AnvilVM Android App                     │
│  ┌─────────────┬──────────────┬──────────────────┐  │
│  │  Terminal    │  VNC Display │  Image Store     │  │
│  │  (ANSI)     │  (RFB 3.8)  │  (QCOW2/ISO)    │  │
│  └──────┬──────┴──────┬───────┴──────────────────┘  │
│         │             │                              │
│  ┌──────┴─────────────┴───────────────────────────┐ │
│  │         Kotlin Engine Layer                     │ │
│  │  QemuEngine | PtyBridge | VncBridge | Runtime  │ │
│  └──────────────────┬────────────────────────────┘  │
│                     │ JNI                            │
│  ┌──────────────────┴────────────────────────────┐  │
│  │         C++ NDK Bridge                         │ │
│  │  qemu_bridge | pty_bridge | vnc_bridge        │ │
│  └──────────────────┬────────────────────────────┘  │
│                     │                                │
│  ┌──────────────────┴────────────────────────────┐  │
│  │  libqemu-system-x86_64.so (QEMU Binary)       │ │
│  │  libqemu-system-aarch64.so                     │ │
│  └────────────────────────────────────────────────┘ │
│                                                      │
│  Foreground Service (Phantom Process Protection)     │
└─────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (Dagger) |
| Engine | QEMU 8.2 (cross-compiled via NDK r26) |
| Native Bridge | C++20 / CMake / NDK |
| Terminal | Custom ANSI/VT100 parser + Canvas renderer |
| Display | RFB 3.8 VNC client (Raw + CopyRect encoding) |
| Networking | QEMU SLiRP (user-mode, no root) |
| Build | Gradle 8.7 / AGP 8.5.1 / Kotlin 2.0 |

---

## Building QEMU for Android

AnvilVM includes a complete Docker-based build system that cross-compiles QEMU for Android arm64-v8a.

### Quick Build (Docker)

```bash
# One command — builds everything inside Docker
./scripts/build-docker.sh

# Output: app/src/main/jniLibs/arm64-v8a/libqemu-system-x86_64.so
```

### Manual Build (Linux Host)

```bash
# Set NDK path
export ANDROID_NDK_HOME=/path/to/android-ndk-r26d

# Run build script
./scripts/build-qemu-android.sh
```

### Build Dependencies (handled by Docker)

- Android NDK r26d
- zlib 1.3.1 (static, cross-compiled)
- pixman 0.42.2 (static, cross-compiled)
- glib 2.78.4 (static, cross-compiled)
- QEMU 8.2.4 source

---

## Testing with Alpine Linux

### Download Test Image

```bash
./scripts/test/download-alpine.sh
```

This downloads Alpine Linux Virtual 3.20 (x86_64) and creates a 2GB QCOW2 disk.

### Host Test (verify QEMU boots the kernel)

```bash
./scripts/test/run-e2e-test.sh
```

### Android Device Test

```bash
# Connect device via ADB, install AnvilVM APK, then:
./scripts/test/android-e2e-test.sh
```

### Expected Boot Sequence

1. QEMU loads BIOS/firmware
2. Alpine kernel decompresses and initializes
3. Serial output appears in Terminal tab (boot messages, login prompt)
4. VNC display shows Alpine framebuffer (if graphical mode)

---

## Project Structure

```
AnvilVM/
├── app/
│   ├── src/main/cpp/              # C++ NDK (QEMU/PTY/VNC bridges)
│   ├── src/main/java/com/anvilvm/app/
│   │   ├── engine/                # QemuEngine, PtyBridge, VncBridge, VMService
│   │   ├── ui/terminal/           # Compose terminal + ANSI parser
│   │   ├── ui/display/            # VNC display + RFB protocol client
│   │   ├── ui/vmstore/            # OS image store
│   │   └── di/                    # Hilt modules
│   └── src/main/jniLibs/          # Compiled QEMU .so binaries
├── docker/                         # Docker build environment
├── scripts/
│   ├── build-qemu-android.sh      # Full QEMU cross-compile script
│   ├── build-docker.sh            # One-command Docker build
│   ├── qemu-android-toolchain.cmake
│   └── test/                       # E2E test scripts
└── test-images/                    # Downloaded OS images (gitignored)
```

---

## Requirements

- **Minimum:** Android 8.0 (API 26), arm64-v8a device
- **Recommended:** Android 14+, 6GB+ RAM, Snapdragon 8-series / Dimensity 9000+
- **Build:** Linux host with Docker (or NDK r26 + build-essential)

---

## Academic Context

AnvilVM is developed as an **academic and educational project** exploring the following research areas:

- **Mobile Virtualization** — Running full operating systems on resource-constrained ARM devices without hardware virtualization extensions
- **Cross-Architecture Emulation** — Performance characteristics of TCG-based x86_64 emulation on arm64 hosts
- **OS Education** — Providing students a portable lab environment to experiment with kernels, bootloaders, and system software
- **Security Research** — Isolated execution environments for malware analysis and penetration testing tools on mobile

### Research Goals

1. Measure and optimize QEMU TCG performance on modern Android SoCs
2. Explore memory management strategies under Android's process lifecycle constraints
3. Develop efficient framebuffer transport between QEMU and Android UI layer
4. Investigate AVF/KVM integration paths for hardware-accelerated VMs on supported devices

---

## Community & Contributing

**Community support is vital to this project.** AnvilVM is maintained by a small team and relies on contributions from the open-source community to grow and improve.

### How to Contribute

- **Code** — Performance optimizations, new encoding support (ZRLE, Hextile), hardware acceleration paths
- **Testing** — Try different OS images, report boot issues, test on various Android devices
- **Documentation** — Improve guides, add tutorials, translate to other languages
- **Research** — Share benchmarks, publish findings, propose architectural improvements
- **OS Images** — Create and share pre-configured QCOW2 images optimized for AnvilVM

### Getting Involved

1. Fork the repository and explore the codebase
2. Check [Issues](https://github.com/denizaydogan1902/AnvilVM/issues) for open tasks
3. Join discussions in [Discussions](https://github.com/denizaydogan1902/AnvilVM/discussions)
4. Submit pull requests — all contributions are welcome regardless of experience level

---

## Roadmap

- [x] Project skeleton (NDK, Compose, Hilt)
- [x] QEMU NDK cross-compilation pipeline
- [x] VT100/ANSI terminal parser (256-color, 24-bit RGB)
- [x] RFB 3.8 VNC client (Raw, CopyRect encoding)
- [ ] Full QEMU .so build and integration test
- [ ] ZRLE and Hextile VNC encoding support
- [ ] Touch keyboard for terminal input
- [ ] VM snapshot/restore (savevm/loadvm)
- [ ] OS image download manager with progress
- [ ] Performance profiling and TCG optimization
- [ ] AVF/KVM acceleration (Android 13+ devices with kernel support)
- [ ] RISC-V guest support

---

## License

AnvilVM is licensed under **GPLv3**.

---

## Acknowledgments

- [QEMU Project](https://www.qemu.org/) — The virtualization engine at the core of AnvilVM
- [Alpine Linux](https://alpinelinux.org/) — Primary test OS for its minimal footprint
- [Android NDK](https://developer.android.com/ndk) — Cross-compilation toolchain
- [Jetpack Compose](https://developer.android.com/compose) — Modern declarative UI framework

---

**Version:** 0.1.0-alpha  
**Status:** Academic/Research — Active Development  
**Maintainer:** [@denizaydogan1902](https://github.com/denizaydogan1902)
