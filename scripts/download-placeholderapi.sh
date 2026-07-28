#!/usr/bin/env bash
# Downloads the latest PlaceholderAPI jar from the SpigotMC resources API.
# Usage: download-placeholderapi.sh <output_directory>
set -euo pipefail

OUT_DIR="$1"
mkdir -p "$OUT_DIR"

# Try the SpigotMC resource API first
RESOURCE_ID=6245
URL="https://api.spigotmc.org/legacy/update.php?resource=${RESOURCE_ID}"

echo "Checking SpigotMC for PlaceholderAPI..."
HTTP_CODE=$(curl -sSL -w "%{http_code}" -o "$OUT_DIR/PlaceholderAPI.jar" "$URL" 2>/dev/null || true)

if [ "$HTTP_CODE" = "200" ] && [ -s "$OUT_DIR/PlaceholderAPI.jar" ]; then
    FILESIZE=$(stat -c%s "$OUT_DIR/PlaceholderAPI.jar" 2>/dev/null || stat -f%z "$OUT_DIR/PlaceholderAPI.jar" 2>/dev/null || echo 0)
    if [ "$FILESIZE" -gt 10000 ]; then
        echo "Downloaded PlaceholderAPI (${FILESIZE} bytes)"
        return 0
    fi
fi

# Fallback: try GitHub releases
echo "SpigotMC download failed, trying GitHub releases..."
GITHUB_URL="https://github.com/PlaceholderAPI/PlaceholderAPI/releases/latest/download/PlaceholderAPI.jar"
HTTP_CODE=$(curl -sSL -w "%{http_code}" -o "$OUT_DIR/PlaceholderAPI.jar" "$GITHUB_URL" 2>/dev/null || true)

if [ "$HTTP_CODE" = "200" ] && [ -s "$OUT_DIR/PlaceholderAPI.jar" ]; then
    FILESIZE=$(stat -c%s "$OUT_DIR/PlaceholderAPI.jar" 2>/dev/null || stat -f%z "$OUT_DIR/PlaceholderAPI.jar" 2>/dev/null || echo 0)
    if [ "$FILESIZE" -gt 10000 ]; then
        echo "Downloaded PlaceholderAPI from GitHub (${FILESIZE} bytes)"
        return 0
    fi
fi

echo "::warning::Could not download PlaceholderAPI — PAPI tests will be skipped"
rm -f "$OUT_DIR/PlaceholderAPI.jar"
return 0
