cmake_minimum_required(VERSION 3.22.1)
project("anvilvm" VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# QEMU Engine Bridge — JNI interface between Android and QEMU process
add_library(anvilvm-engine SHARED
    qemu_bridge.cpp
    pty_bridge.cpp
    vnc_bridge.cpp
)

target_link_libraries(anvilvm-engine
    android
    log
)
