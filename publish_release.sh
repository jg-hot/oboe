#!/usr/bin/env bash
set -o xtrace

BUILD_DIR="./build/prefab"
rm -rf "./build"

source ./build_environment.sh
./prefab_build.sh

ARTIFACT="oboe-patched-1.9.0-patch1"

mvn deploy:deploy-file \
    -Durl="https://maven.pkg.github.com/jg-hot/oboe" \
    -DrepositoryId="gpr:oboe-patched" \
    -Dfile="${BUILD_DIR}/$ARTIFACT.aar" \
    -DpomFile="${BUILD_DIR}/$ARTIFACT.pom" \
    -Dpackaging=aar \

# or if installing to maven local
# mvn install:install-file \
#     -Dfile="${BUILD_DIR}/$ARTIFACT.aar" \
#     -DpomFile="./android/$ARTIFACT.pom" \
#     -Dpackaging=aar \
