cmake_minimum_required(VERSION 3.22.1)
project(popsmanager_native)

# Writing straight into src/main/jniLibs/<ABI>/ uses Android's guaranteed
# packaging mechanism instead of relying on AGP's CMake-integration
# auto-packaging of add_executable() targets, which has proven unreliable.
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Only cue2pops (BIN/CUE -> VCD, install direction) is a native tool now.
# VCD -> BIN/CUE (extract direction) is implemented directly in Kotlin
# (see VcdExtractor.kt) — the previously-used native pops2cue reconstruction
# had a real, unresolved compatibility bug with our verified-correct VCD
# files, so it's been replaced entirely rather than worked around.
set(CUE2POPS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cue2pops)

file(GLOB CUE2POPS_SOURCES
    ${CUE2POPS_DIR}/*.c
    ${CUE2POPS_DIR}/*.cpp
)

if(NOT CUE2POPS_SOURCES)
    message(FATAL_ERROR "cue2pops sources missing at ${CUE2POPS_DIR}. "
                         "Run the vendor step (see .github workflow) before building.")
endif()

add_executable(cue2pops ${CUE2POPS_SOURCES})
set_target_properties(cue2pops PROPERTIES OUTPUT_NAME "cue2pops" SUFFIX ".so" PREFIX "lib")
target_include_directories(cue2pops PRIVATE ${CUE2POPS_DIR})
