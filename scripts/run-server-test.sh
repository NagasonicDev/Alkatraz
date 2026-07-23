#!/usr/bin/env bash
# Boots a Minecraft server with the plugin installed and verifies it
# starts cleanly and the plugin loads without errors.
#
# Usage: run-server-test.sh <server_jar> <plugin_jar> <loader> <mc_version>
set -uo pipefail

SERVER_JAR="$1"
PLUGIN_JAR="$2"
LOADER="$3"
VERSION="$4"

WORKDIR=$(mktemp -d /tmp/mc-test-XXXXXX)
mkdir -p "${WORKDIR}/plugins"
cp "${SERVER_JAR}" "${WORKDIR}/server.jar"
cp "${PLUGIN_JAR}" "${WORKDIR}/plugins/"
echo "eula=true" > "${WORKDIR}/eula.txt"
# Keep worlds tiny/fast for CI
cat > "${WORKDIR}/server.properties" <<EOF
online-mode=false
level-type=flat
generate-structures=false
max-players=1
EOF

cd "${WORKDIR}"

# Keep a FIFO open as stdin so we can send "stop" to the console later
# without the server's stdin hitting EOF and dying early.
mkfifo cmd_pipe
exec 3<>cmd_pipe

echo "Starting ${LOADER} ${VERSION} with plugin $(basename "${PLUGIN_JAR}")..."
timeout 120 java -jar server.jar --nogui <&3 > server.log 2>&1 &
SERVER_PID=$!

STARTED=false
for i in $(seq 1 45); do
  if grep -q "Done (" server.log 2>/dev/null; then
    STARTED=true
    break
  fi
  if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
    break
  fi
  sleep 2
done

if [ "${STARTED}" != "true" ]; then
  echo "::error::${LOADER} ${VERSION} did not finish starting in time"
  echo "=== server.log ==="
  cat server.log
  exit 1
fi

# Give the plugin a moment to finish onEnable() and settle
sleep 3

# Stop the server cleanly
echo "stop" >&3
wait "${SERVER_PID}" 2>/dev/null
exec 3>&-

echo "=== server.log ==="
cat server.log

# Fail on plugin load failures or uncaught exceptions during startup
if grep -qiE "Could not load '.*${PLUGIN_JAR##*/}'|Error occurred while enabling|Exception in thread \"main\"" server.log; then
  echo "::error::${LOADER} ${VERSION} - plugin failed to load cleanly"
  exit 1
fi

echo "PASS: ${LOADER} ${VERSION}"
