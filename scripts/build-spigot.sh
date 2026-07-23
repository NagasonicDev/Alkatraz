#!/usr/bin/env bash
# Builds a Spigot server jar for the given MC version using BuildTools.
# This is the slow, NMS-remapping step - it should only ever run on a
# cache miss (see build-spigot-jars job in the workflow), never on
# every CI run.
#
# Usage: build-spigot.sh <mc_version> <output_path>
set -euo pipefail

VERSION="$1"
OUT="$2"

WORKDIR=$(mktemp -d)
echo "Building Spigot ${VERSION} in ${WORKDIR} (this can take several minutes)..."

cd "${WORKDIR}"
curl -sSL -o BuildTools.jar \
  "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"

java -jar BuildTools.jar --rev "${VERSION}" --output-dir "${WORKDIR}/out"

BUILT_JAR="${WORKDIR}/out/spigot-${VERSION}.jar"
if [ ! -f "${BUILT_JAR}" ]; then
  echo "BuildTools did not produce spigot-${VERSION}.jar - check BuildTools output above" >&2
  exit 1
fi

mkdir -p "$(dirname "${OUT}")"
cp "${BUILT_JAR}" "${OUT}"
rm -rf "${WORKDIR}"

echo "Saved to ${OUT}"