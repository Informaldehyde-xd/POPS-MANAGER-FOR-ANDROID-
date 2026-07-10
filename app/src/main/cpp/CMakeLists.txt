cmake_minimum_required(VERSION 3.22.1)
project(popsmanager_native)

# CI clones israpps/cue2pops here before building (see .github workflow).
# Local build: git clone https://github.com/israpps/cue2pops app/src/main/cpp/cue2pops
set(CUE2POPS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cue2pops)

file(GLOB CUE2POPS_SOURCES
    ${CUE2POPS_DIR}/*.c
    ${CUE2POPS_DIR}/*.cpp
)

if(NOT CUE2POPS_SOURCES)
    message(FATAL_ERROR "cue2pops sources missing at ${CUE2POPS_DIR}. "
                         "Run the vendor step (see README) before building.")
endif()

# Built as an executable, but named libcue2pops.so and placed under jniLibs/<abi>/
# by Gradle's externalNativeBuild packaging. Android only allows exec() of files
# inside applicationInfo.nativeLibraryDir, so this naming trick lets us ship and
# run a real CLI binary from Kotlin via ProcessBuilder.
add_executable(cue2pops ${CUE2POPS_SOURCES})

set_target_properties(cue2pops PROPERTIES
    OUTPUT_NAME "cue2pops"
    SUFFIX ".so"
    PREFIX "lib"
)

target_include_directories(cue2pops PRIVATE ${CUE2POPS_DIR})
