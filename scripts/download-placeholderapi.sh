#!/usr/bin/env bash
# Downloads the latest PlaceholderAPI jar from GitHub releases.
# Usage: download-placeholderapi.sh <output_directory>
set -uo pipefail

OUT_DIR="$1"
mkdir -p "$OUT_DIR"

# Use GitHub API to find the latest release's jar asset URL
echo "Fetching latest PlaceholderAPI release info from GitHub..."
DOWNLOAD_URL=$(curl -sSL "https://api.github.com/repos/PlaceholderAPI/PlaceholderAPI/releases/latest" 2>/dev/null \
    | grep '"browser_download_url"' \
    | grep '\.jar"' \
    | head -1 \
    | sed 's/.*"browser_download_url":\s*"\(.*\)".*/\1/' || true)

if [ -n "$DOWNLOAD_URL" ]; then
    echo "Downloading from: $DOWNLOAD_URL"
    HTTP_CODE=$(curl -sSL -w "%{http_code}" -o "$OUT_DIR/PlaceholderAPI.jar" "$DOWNLOAD_URL" 2>/dev/null || true)
    if [ "$HTTP_CODE" = "200" ] && [ -s "$OUT_DIR/PlaceholderAPI.jar" ]; then
        FILESIZE=$(stat -c%s "$OUT_DIR/PlaceholderAPI.jar" 2>/dev/null || stat -f%z "$OUT_DIR/PlaceholderAPI.jar" 2>/dev/null || echo 0)
        if [ "$FILESIZE" -gt 10000 ]; then
            echo "Downloaded PlaceholderAPI (${FILESIZE} bytes)"
            exit 0
        fi
    fi
    echo "Download failed (HTTP $HTTP_CODE, size $FILESIZE)"
else
    echo "Could not determine download URL from GitHub API"
fi

echo "::warning::Could not download PlaceholderAPI — PAPI tests will be skipped"
rm -f "$OUT_DIR/PlaceholderAPI.jar"
exit 0
