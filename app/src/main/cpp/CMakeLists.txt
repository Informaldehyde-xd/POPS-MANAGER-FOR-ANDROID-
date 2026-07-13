cmake_minimum_required(VERSION 3.22.1)
project(popsmanager_native)

# Writing straight into src/main/jniLibs/<ABI>/ uses Android's guaranteed
# packaging mechanism (see earlier fix notes) instead of relying on AGP's
# CMake-integration auto-packaging of add_executable() targets.
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# IMPORTANT — version compatibility:
# cue2pops (install) and pops2cue (extract) MUST agree on the exact same VCD
# header format. pops2cue's own docs state it only reads VCDs made by
# "CUE2POPS v2.0". israpps/cue2pops's own banner confirms it IS v2.0
# ("BIN/CUE to IMAGE0.VCD conversion tool v2.0"). bucanero/pops2cue's own
# bundled cue2pops.c is a DIFFERENT, later v2.3 — using that instead (as an
# earlier version of this build did) produces VCDs pops2cue can't parse
# ("disc type array wasn't found"), even though both come from "the same
# repo". So: cue2pops from israpps (genuine v2.0), pops2cue.c from bucanero
# (the only available reconstruction) — and bucanero's own cue2pops.c is
# deliberately never compiled at all.
set(CUE2POPS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cue2pops)
set(POPS2CUE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/pops2cue)

file(GLOB CUE2POPS_SOURCES
    ${CUE2POPS_DIR}/*.c
    ${CUE2POPS_DIR}/*.cpp
)

if(NOT CUE2POPS_SOURCES)
    message(FATAL_ERROR "cue2pops sources missing at ${CUE2POPS_DIR}. "
                         "Run the vendor step (see .github workflow) before building.")
endif()
if(NOT EXISTS ${POPS2CUE_DIR}/pops2cue.c)
    message(FATAL_ERROR "pops2cue.c missing at ${POPS2CUE_DIR}. "
                         "Run the vendor step (see .github workflow) before building.")
endif()

add_executable(cue2pops ${CUE2POPS_SOURCES})
set_target_properties(cue2pops PROPERTIES OUTPUT_NAME "cue2pops" SUFFIX ".so" PREFIX "lib")
target_include_directories(cue2pops PRIVATE ${CUE2POPS_DIR})

# Only pops2cue.c itself — deliberately NOT bucanero's bundled cue2pops.c
# (wrong version, and would also cause a duplicate main() link error).
add_executable(pops2cue ${POPS2CUE_DIR}/pops2cue.c)
set_target_properties(pops2cue PROPERTIES OUTPUT_NAME "pops2cue" SUFFIX ".so" PREFIX "lib")
target_include_directories(pops2cue PRIVATE ${POPS2CUE_DIR})
