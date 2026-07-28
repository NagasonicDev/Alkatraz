#!/usr/bin/env bash
# Spell system runtime tests.
set -uo pipefail

WORKDIR="$1"
LOG_FILE="$2"
LOADER="$3"
VERSION="$4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

begin_test_section "Spells"

send_command "cast"
sleep 1
assert_log_contains "Only players can use this command" "/cast requires player"

send_command "cast fireball"
sleep 1
assert_log_contains "Only players can use this command" "/cast fireball requires player (console)"

assert_no_exceptions "No exceptions from spell system"

end_test_section
