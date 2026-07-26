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
LOGFILE="${WORKDIR}/buildtools.log"
echo "Building Spigot ${VERSION} in ${WORKDIR} (this can take several minutes)..."

cd "${WORKDIR}"
curl -sSL -o BuildTools.jar \
  "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"

# Run BuildTools and capture output for debugging
if ! java -jar BuildTools.jar --rev "${VERSION}" --remapped --output-dir "${WORKDIR}/out" 2>&1 | tee "${LOGFILE}"; then
  echo "=== BuildTools FAILED for ${VERSION} (exit code $?) ===" >&2
  echo "Last 50 lines of output:" >&2
  tail -n 50 "${LOGFILE}" >&2
  rm -rf "${WORKDIR}"
  exit 1
fi

BUILT_JAR="${WORKDIR}/out/spigot-${VERSION}.jar"
if [ ! -f "${BUILT_JAR}" ]; then
  echo "=== BuildTools did not produce spigot-${VERSION}.jar ===" >&2
  echo "BuildTools output directory contents:" >&2
  ls -la "${WORKDIR}/out/" 2>/dev/null >&2 || echo "(out dir does not exist)" >&2
  echo "Last 50 lines of BuildTools output:" >&2
  tail -n 50 "${LOGFILE}" >&2
  rm -rf "${WORKDIR}"
  exit 1
fi

mkdir -p "$(dirname "${OUT}")"
cp "${BUILT_JAR}" "${OUT}"
rm -rf "${WORKDIR}"

echo "Saved to ${OUT}"