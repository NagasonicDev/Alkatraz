#!/usr/bin/env bash
# Fetches a prebuilt Paper server jar for the given MC version.
# Uses the PaperMC v3 Fill API (api.papermc.io/v2 was sunset Dec 2025).
# Usage: fetch-paper.sh <mc_version> <output_path>
set -euo pipefail

VERSION="$1"
OUT="$2"
UA="alkatraz-ci/1.0"

echo "Looking up latest Paper build for ${VERSION}..."
RESPONSE=$(curl -sSL -H "User-Agent: ${UA}" \
  "https://fill.papermc.io/v3/projects/paper/versions/${VERSION}/builds/latest")

# Check for API error
if echo "${RESPONSE}" | jq -e '.ok == false' > /dev/null 2>&1; then
  ERROR_MSG=$(echo "${RESPONSE}" | jq -r '.message // "Unknown error"')
  echo "Paper API error: ${ERROR_MSG}" >&2
  exit 1
fi

FILENAME=$(echo "${RESPONSE}" | jq -r '.downloads["server:default"].name')
URL=$(echo "${RESPONSE}" | jq -r '.downloads["server:default"].url')

if [ -z "${URL}" ] || [ "${URL}" = "null" ]; then
  echo "Could not find any Paper build for MC version ${VERSION}" >&2
  exit 1
fi

echo "Downloading Paper ${VERSION} (${FILENAME})..."
curl -sSL -o "${OUT}" "${URL}"
echo "Saved to ${OUT}"
