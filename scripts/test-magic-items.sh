#!/usr/bin/env bash
# Magic item system runtime tests.
set -uo pipefail

WORKDIR="$1"
LOG_FILE="$2"
LOADER="$3"
VERSION="$4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

begin_test_section "Magic Items"

send_command "alkatraz give wooden_wand"
sleep 1
assert_log_contains "Couldn't find a player|Only players" "give from console handled correctly"

send_command "alkatraz give fake_item_123"
sleep 1
assert_log_contains "Couldn't find a player|Only players|no item|not found" "give nonexistent item handled"

assert_no_exceptions "No exceptions from magic item system"

end_test_section
