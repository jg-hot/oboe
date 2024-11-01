#!/usr/bin/env bash

# source this file before running build /tests to ensure the correct NDK
# and cmake versions are used
export ANDROID_NDK="${ANDROID_HOME}/ndk/27.2.12479018"
export PATH="$PATH:${ANDROID_HOME}/cmake/3.30.5/bin"
