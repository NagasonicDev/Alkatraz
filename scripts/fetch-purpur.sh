#!/usr/bin/env bash
# Fetches the latest prebuilt Purpur server jar for the given MC version.
# Usage: fetch-purpur.sh <mc_version> <output_path>
set -euo pipefail

VERSION="$1"
OUT="$2"

URL="https://api.purpurmc.org/v2/purpur/${VERSION}/latest/download"

echo "Downloading Purpur ${VERSION} (latest build)..."
HTTP_CODE=$(curl -sSL -w "%{http_code}" -o "${OUT}" "${URL}")

if [ "${HTTP_CODE}" != "200" ]; then
  echo "Purpur has no published build for MC version ${VERSION} (HTTP ${HTTP_CODE})" >&2
  rm -f "${OUT}"
  exit 1
fi

echo "Saved to ${OUT}"
