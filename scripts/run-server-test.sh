#!/usr/bin/env bash
# Boots a Minecraft server with the plugin installed and runs all
# runtime integration tests. Always exits 0 so the CI job continues
# to the report step — test failures are reported via artifacts.
#
# Usage: run-server-test.sh <server_jar> <plugin_jar> <loader> <mc_version>
set -uo pipefail

SERVER_JAR="$1"
PLUGIN_JAR="$2"
LOADER="$3"
VERSION="$4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

WORKDIR=$(mktemp -d /tmp/mc-test-XXXXXX)
mkdir -p "${WORKDIR}/plugins"
cp "${SERVER_JAR}" "${WORKDIR}/server.jar"
echo "eula=true" > "${WORKDIR}/eula.txt"
cat > "${WORKDIR}/server.properties" <<EOF
online-mode=false
level-type=flat
generate-structures=false
max-players=1
EOF

LOG_FILE="${WORKDIR}/server.log"
RESULTS_FILE="${WORKDIR}/test-results.txt"

cleanup() {
    if kill -0 "${SERVER_PID:-}" 2>/dev/null; then
        echo "stop" >&3 2>/dev/null || true
        wait "${SERVER_PID}" 2>/dev/null || true
    fi
    exec 3>&- 2>/dev/null || true
}
trap cleanup EXIT

# Download PlaceholderAPI
echo "Downloading PlaceholderAPI..."
chmod +x "${SCRIPT_DIR}/download-placeholderapi.sh"
"${SCRIPT_DIR}/download-placeholderapi.sh" "${WORKDIR}/plugins"

cp "${PLUGIN_JAR}" "${WORKDIR}/plugins/"

cd "${WORKDIR}"

mkfifo cmd_pipe
exec 3<>cmd_pipe

echo "Starting ${LOADER} ${VERSION} with plugin $(basename "${PLUGIN_JAR}")..."
timeout 180 java -jar server.jar --nogui <&3 > "$LOG_FILE" 2>&1 &
SERVER_PID=$!

if ! wait_for_server_ready 90; then
    echo "::error::${LOADER} ${VERSION} did not finish starting in time"
    echo "SERVER_START_TIMEOUT" > "$RESULTS_FILE"
    exit 0
fi

echo "Server started. Running tests..."
sleep 3

# Run all test scripts, collecting results
TOTAL_PASS=0
TOTAL_FAIL=0
TEST_SECTIONS=""

for test_script in "${SCRIPT_DIR}"/test-*.sh; do
    [ -f "$test_script" ] || continue
    echo ""
    echo "Running $(basename "$test_script")..."
    chmod +x "$test_script"

    # Run test in subshell, capture output for result parsing
    OUTPUT=$(bash "$test_script" "$WORKDIR" "$LOG_FILE" "$LOADER" "$VERSION" 2>&1 || true)
    echo "$OUTPUT"

    # Parse RESULT:PASS:N and RESULT:FAIL:N lines from test output
    SCRIPT_PASS=$(echo "$OUTPUT" | grep -c "^RESULT:PASS:" || true)
    SCRIPT_FAIL=$(echo "$OUTPUT" | grep -c "^RESULT:FAIL:" || true)
    TOTAL_PASS=$((TOTAL_PASS + SCRIPT_PASS))
    TOTAL_FAIL=$((TOTAL_FAIL + SCRIPT_FAIL))
    TEST_SECTIONS="${TEST_SECTIONS}$(basename "$test_script"): ${SCRIPT_PASS} pass, ${SCRIPT_FAIL} fail\n"
done

# Check for plugin load failures
if grep -qiE "Could not load '.*${PLUGIN_JAR##*/}'|Error occurred while enabling" "$LOG_FILE"; then
    echo "::error::${LOADER} ${VERSION} - plugin failed to load cleanly"
    TOTAL_FAIL=$((TOTAL_FAIL + 1))
fi

# Write results file for CI artifact upload
echo "LOADER=${LOADER}" > "$RESULTS_FILE"
echo "VERSION=${VERSION}" >> "$RESULTS_FILE"
echo "PASS=${TOTAL_PASS}" >> "$RESULTS_FILE"
echo "FAIL=${TOTAL_FAIL}" >> "$RESULTS_FILE"
echo -e "SECTIONS:\n${TEST_SECTIONS}" >> "$RESULTS_FILE"

echo ""
echo "========================================="
echo "  TEST RESULTS: ${LOADER} ${VERSION}"
echo "  PASS: ${TOTAL_PASS}  FAIL: ${TOTAL_FAIL}"
echo "========================================="

if [ "${TOTAL_FAIL}" -gt 0 ]; then
    echo "::warning::${TOTAL_FAIL} test(s) failed on ${LOADER} ${VERSION}"
fi

# Always exit 0 — failures are reported via the results artifact
exit 0
