#!/usr/bin/env bash
# GUI menu system runtime tests.
set -uo pipefail

WORKDIR="$1"
LOG_FILE="$2"
LOADER="$3"
VERSION="$4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

begin_test_section "GUI Menus"

send_command "spells"
sleep 1
assert_log_contains "Only players can use this command" "/spells requires player"

send_command "recipes"
sleep 1
assert_log_contains "Only players can use this command" "/recipes requires player"

send_command "alkatraz stats"
sleep 1
assert_log_contains "Only players can use this command" "/alkatraz stats requires player"

send_command "alkatraz equipment"
sleep 1
assert_log_contains "Only players can use this command" "/alkatraz equipment requires player"

send_command "alkatraz editor"
sleep 1
assert_log_contains "Only players can use this command" "/alkatraz editor requires player"

assert_log_not_contains "Error.*inventory|Error.*MenuListener|Error.*Menu|Error.*GUI" "No GUI/inventory errors during startup"

assert_no_exceptions "No exceptions from GUI system"

end_test_section
