#!/usr/bin/env bash
set -o xtrace

rm -rf build/

source ./build_environment.sh
./prefab_build.sh

mvn install:install-file \
    -Dfile=./build/prefab/oboe-patched-1.9.0-patch1.aar \
    -DpomFile=./build/prefab/oboe-patched-1.9.0-patch1.pom \
    -Dpackaging=aar \
