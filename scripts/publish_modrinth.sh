#!/bin/bash
# Publish all AutoTune version JARs to Modrinth
set -e

if [ -z "$MODRINTH_TOKEN" ]; then
    echo "Error: MODRINTH_TOKEN environment variable not set"
    exit 1
fi

echo "Publishing AutoTune to Modrinth..."

cd "$(dirname "$0")/.."

./gradlew buildAll modrinth

echo "Published successfully."
