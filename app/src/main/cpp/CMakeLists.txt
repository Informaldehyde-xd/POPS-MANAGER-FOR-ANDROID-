cmake_minimum_required(VERSION 3.22.1)
project(popsmanager_native)

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

if(NOT CUE2POPS_SOURCES)
    message(FATAL_ERROR "cue2pops sources missing at ${CUE2POPS_DIR}. "
                         "Run the vendor step (see README) before building.")
endif()
if(NOT POPS2CUE_SOURCES)
    message(FATAL_ERROR "pops2cue sources missing at ${POPS2CUE_DIR}. "
                         "Run the vendor step (see README) before building.")
endif()

add_executable(cue2pops ${CUE2POPS_SOURCES})
set_target_properties(cue2pops PROPERTIES OUTPUT_NAME "cue2pops" SUFFIX ".so" PREFIX "lib")
target_include_directories(cue2pops PRIVATE ${CUE2POPS_DIR})

add_executable(pops2cue ${POPS2CUE_SOURCES})
set_target_properties(pops2cue PROPERTIES OUTPUT_NAME "pops2cue" SUFFIX ".so" PREFIX "lib")
target_include_directories(pops2cue PRIVATE ${POPS2CUE_DIR})
