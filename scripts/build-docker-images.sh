#!/bin/bash
set -e

echo "=========================================="
echo "Building all Scala JARs with sbt..."
echo "=========================================="

# Detect OS and use appropriate sbt command
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    SBT_CMD="sbt.bat"
else
    SBT_CMD="sbt"
fi

# Build all modules in parallel for speed
$SBT_CMD clean

echo "Building all JARs..."
$SBT_CMD "project restApi" assembly
$SBT_CMD "project playerService" assembly
$SBT_CMD "project tournamentService" assembly
$SBT_CMD "project botService" assembly
$SBT_CMD "project spark" assembly

echo "=========================================="
echo "All JARs built successfully!"
echo "=========================================="

echo "Building Docker images..."
docker-compose build

echo "=========================================="
echo "All Docker images built successfully!"
echo "=========================================="
