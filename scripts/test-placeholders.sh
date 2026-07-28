#!/usr/bin/env bash
# PlaceholderAPI integration tests.
set -uo pipefail

WORKDIR="$1"
LOG_FILE="$2"
LOADER="$3"
VERSION="$4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

begin_test_section "PlaceholderAPI"

if grep -q "PlaceholderAPI" "$LOG_FILE" 2>/dev/null; then
    assert_log_contains "PlaceholderAPI hook registered successfully" "PlaceholderAPI hook registered"
else
    echo "  SKIP: PlaceholderAPI not present on server"
fi

assert_log_not_contains "Error.*PlaceholderAPI|Error.*placeholder|Error.*AlkatrazPlaceholder" "No PlaceholderAPI errors"

assert_no_exceptions "No exceptions from PlaceholderAPI system"

end_test_section
