#
# Copyright 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.22.1)

# Set the path to the Oboe library directory
set (OBOE_DIR ../../../../../)
#message("OBOE_DIR = " + ${OBOE_DIR})

add_subdirectory(${OBOE_DIR} ./oboe-bin)

# include folders
include_directories(
        ${OBOE_DIR}/include
        ${CMAKE_CURRENT_LIST_DIR}
)

# App specific sources
set (APP_SOURCES
        SimpleNoiseMaker.cpp
        MinimalOboeJNI.cpp
        )

# Build the minimaloboe (native) library
add_library(minimaloboe SHARED
        ${APP_SOURCES}
        )

# Enable optimization flags: if having problems with source level debugging,
# disable -Ofast ( and debug ), re-enable after done debugging.
target_compile_options(minimaloboe PRIVATE -Wall -Werror "$<$<CONFIG:RELEASE>:-O3 -ffast-math>")

target_link_libraries( # Specifies the target library.
        minimaloboe
        oboe

        # Links the target library to the log library
        # included in the NDK.
        log)
target_link_options(minimaloboe PRIVATE "-Wl,-z,max-page-size=16384")

# enable ASAN
target_compile_options(minimaloboe PUBLIC -fsanitize=address -fno-omit-frame-pointer)
set_target_properties(minimaloboe PROPERTIES LINK_FLAGS -fsanitize=address)

# https://github.com/android/ndk-samples/blob/main/sanitizers/app/src/main/cpp/CMakeLists.txt

# For asan and ubsan, we need to copy some files from the NDK into our APK.
get_filename_component(HINT_PATH ${ANDROID_C_COMPILER} DIRECTORY)
set(ARCH_STR ${CMAKE_ANDROID_ARCH})
if("${CMAKE_ANDROID_ARCH_ABI}" STREQUAL "arm64-v8a")
        set(ARCH_STR "aarch64")
elseif("${CMAKE_ANDROID_ARCH_ABI}" STREQUAL "armeabi")
        set(ARCH_STR "arm")
elseif("${CMAKE_ANDROID_ARCH_ABI}" STREQUAL "armeabi-v7a")
        set(ARCH_STR "arm")
elseif("${CMAKE_ANDROID_ARCH_ABI}" STREQUAL "x86")
        set(ARCH_STR "i686")
elseif("${CMAKE_ANDROID_ARCH_ABI}" STREQUAL "x86_64")
        set(ARCH_STR "x86_64")
endif()

# Grab libclang_rt.asan-${ARCH_STR}-android.so from the NDK.
file(GLOB ASAN_GLOB "${HINT_PATH}/../lib/clang/*/lib/linux")
find_file(ASAN
        NAMES libclang_rt.asan-${ARCH_STR}-android.so
        PATHS ${ASAN_GLOB})
get_filename_component(ASAN_NAME ${ASAN} NAME)
set(ASAN_NAME ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/${ASAN_NAME})
add_custom_command(
        TARGET minimaloboe PRE_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy ${ASAN} ${ASAN_NAME})

# Grab the asan wrapper script from the NDK.
find_file(WRAP
        NAMES asan.sh
        HINTS ${HINT_PATH}/../../../../../wrap.sh)
set(WRAP_NAME ${CMAKE_CURRENT_SOURCE_DIR}/../resources/lib/${CMAKE_ANDROID_ARCH_ABI}/wrap.sh)
add_custom_command(
        TARGET minimaloboe PRE_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy ${WRAP} ${WRAP_NAME})
