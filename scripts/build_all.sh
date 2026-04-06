#!/bin/bash
# Build AutoTune for all supported Minecraft versions
set -e

echo "Building AutoTune for all Minecraft versions..."

cd "$(dirname "$0")/.."

./gradlew buildAll

echo ""
echo "Build complete. JARs:"
find versions/*/build/libs -name "autotune-*.jar" -not -name "*-dev.jar" -not -name "*-sources.jar" 2>/dev/null | sort
