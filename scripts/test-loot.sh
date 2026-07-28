#!/usr/bin/env bash
# Loot system runtime tests.
set -uo pipefail

WORKDIR="$1"
LOG_FILE="$2"
LOADER="$3"
VERSION="$4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

begin_test_section "Loot"

assert_log_not_contains "Error.*loot|Error.*LootInjector|Error.*MobLoot|Error.*SpellbookLoot" "Loot system loaded without errors"

assert_no_exceptions "No exceptions from loot system"

end_test_section
