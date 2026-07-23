#!/usr/bin/env bash
# Fetches a prebuilt Paper server jar for the given MC version.
# Usage: fetch-paper.sh <mc_version> <output_path>
set -euo pipefail

VERSION="$1"
OUT="$2"

echo "Looking up latest Paper build for ${VERSION}..."
BUILD=$(curl -sSL "https://api.papermc.io/v2/projects/paper/versions/${VERSION}/builds" \
  | jq -r '.builds[-1].build')

if [ -z "${BUILD}" ] || [ "${BUILD}" = "null" ]; then
  echo "Could not find any Paper build for MC version ${VERSION}" >&2
  exit 1
fi

FILENAME="paper-${VERSION}-${BUILD}.jar"
URL="https://api.papermc.io/v2/projects/paper/versions/${VERSION}/builds/${BUILD}/downloads/${FILENAME}"

echo "Downloading Paper ${VERSION} build ${BUILD}..."
curl -sSL -o "${OUT}" "${URL}"
echo "Saved to ${OUT}"
