cmake_minimum_required(VERSION 3.22.1)
project(popsmanager_native)

# IMPORTANT: relying on AGP's CMake-integration to auto-package an
# add_executable() target (even one named lib*.so) has proven unreliable
# across AGP versions — the file compiles fine but doesn't always make it
# into the APK. Writing straight into src/main/jniLibs/<ABI>/ instead uses
# Android's actual guaranteed packaging mechanism (anything there always
# gets bundled), sidestepping the CMake-integration guesswork entirely.
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# CI clones both tools here before building (see .github workflow).
# Local build:
#   git clone https://github.com/israpps/cue2pops app/src/main/cpp/cue2pops
#   git clone https://github.com/bucanero/pops2cue app/src/main/cpp/pops2cue
set(CUE2POPS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cue2pops)
set(POPS2CUE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/pops2cue)

file(GLOB CUE2POPS_SOURCES
    ${CUE2POPS_DIR}/*.c
    ${CUE2POPS_DIR}/*.cpp
)
file(GLOB POPS2CUE_SOURCES
    ${POPS2CUE_DIR}/*.c
    ${POPS2CUE_DIR}/*.cpp
)
# The pops2cue repo bundles its own reference copy of cue2pops.c alongside
# pops2cue.c — both define main(), which breaks the link with a duplicate
# symbol error. We only want the actual pops2cue tool here.
list(FILTER POPS2CUE_SOURCES EXCLUDE REGEX ".*/cue2pops\\.c$")

if(NOT CUE2POPS_SOURCES)
    message(FATAL_ERROR "cue2pops sources missing at ${CUE2POPS_DIR}. "
                         "Run the vendor step (see README) before building.")
endif()
if(NOT POPS2CUE_SOURCES)
    message(FATAL_ERROR "pops2cue sources missing at ${POPS2CUE_DIR}. "
                         "Run the vendor step (see README) before building.")
endif()

# Built as executables, but named lib*.so — Android only allows exec() of
# files inside applicationInfo.nativeLibraryDir, so this naming trick lets us
# ship and run real CLI binaries from Kotlin via ProcessBuilder. Writing them
# straight to src/main/jniLibs/<ABI>/ (set above) is what actually gets them
# bundled into the APK.
add_executable(cue2pops ${CUE2POPS_SOURCES})
set_target_properties(cue2pops PROPERTIES OUTPUT_NAME "cue2pops" SUFFIX ".so" PREFIX "lib")
target_include_directories(cue2pops PRIVATE ${CUE2POPS_DIR})

add_executable(pops2cue ${POPS2CUE_SOURCES})
set_target_properties(pops2cue PROPERTIES OUTPUT_NAME "pops2cue" SUFFIX ".so" PREFIX "lib")
target_include_directories(pops2cue PRIVATE ${POPS2CUE_DIR})
