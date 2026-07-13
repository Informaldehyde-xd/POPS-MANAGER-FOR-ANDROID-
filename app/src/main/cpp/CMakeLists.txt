cmake_minimum_required(VERSION 3.22.1)
project(popsmanager_native)

# IMPORTANT: relying on AGP's CMake-integration to auto-package an
# add_executable() target (even one named lib*.so) has proven unreliable
# across AGP versions. Writing straight into src/main/jniLibs/<ABI>/ instead
# uses Android's actual guaranteed packaging mechanism.
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Both tools are built from the SAME repo (bucanero/pops2cue), which bundles
# a matched, verified-compatible pair: its own reconstructed CUE2POPS v2.3
# alongside POPS2CUE v1.0. Mixing two independently-reconstructed tools
# (e.g. israpps/cue2pops for install + this repo's pops2cue for extract)
# causes a VCD header format mismatch — each author reverse-engineered the
# format separately, so their exact byte layouts don't perfectly agree,
# which is exactly what produced "Invalid VCD: disc type array wasn't
# found" during extraction. Building both tools from this one repo
# guarantees they speak the exact same VCD format.
set(TOOLS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/pops2cue)

if(NOT EXISTS ${TOOLS_DIR}/cue2pops.c OR NOT EXISTS ${TOOLS_DIR}/pops2cue.c)
    message(FATAL_ERROR "Expected cue2pops.c and pops2cue.c inside ${TOOLS_DIR}. "
                         "Run the vendor step (see .github workflow) before building.")
endif()

# Any other .c/.cpp files in the repo (shared helpers, if present) get
# compiled into both tools; the two main() files themselves are kept separate
# per target so each program only contains its own entry point.
file(GLOB ALL_TOOL_SOURCES ${TOOLS_DIR}/*.c ${TOOLS_DIR}/*.cpp)
set(SHARED_SOURCES ${ALL_TOOL_SOURCES})
list(REMOVE_ITEM SHARED_SOURCES ${TOOLS_DIR}/cue2pops.c ${TOOLS_DIR}/pops2cue.c)

add_executable(cue2pops ${TOOLS_DIR}/cue2pops.c ${SHARED_SOURCES})
set_target_properties(cue2pops PROPERTIES OUTPUT_NAME "cue2pops" SUFFIX ".so" PREFIX "lib")
target_include_directories(cue2pops PRIVATE ${TOOLS_DIR})

add_executable(pops2cue ${TOOLS_DIR}/pops2cue.c ${SHARED_SOURCES})
set_target_properties(pops2cue PROPERTIES OUTPUT_NAME "pops2cue" SUFFIX ".so" PREFIX "lib")
target_include_directories(pops2cue PRIVATE ${TOOLS_DIR})
