#!/usr/bin/env bash
set -o xtrace

rm -rf build/

# TODO: ensure the correct cmake version is being used (from the NDK)
# this might not work if cmake is already on the path before sourcing
# build_environment.sh
source ./build_environment.sh
./prefab_build.sh

mvn install:install-file \
    -Dfile=./build/prefab/oboe-patched-1.9.0-patch1.aar \
    -DpomFile=./build/prefab/oboe-patched-1.9.0-patch1.pom \
    -Dpackaging=aar \
