#!/usr/bin/env bash
# Goal-system / general-AI integration tests.
#
# Runs against a live server through the console (no player online). The brain
# YAML fixtures in scripts/test-fixtures/brains/ and the config.yml verbosity
# fixture are copied into the plugin data folder by run-server-test.sh before
# boot. Registration lines are emitted at HIGH verbosity ("Registered brain
# '<id>' (wand=..., melee-range=...)"), so the config fixture sets verbose: high.
#
# Coverage: clean registration of all three shipped brain ids, fixture-over-bundled
# precedence, default fallbacks for missing keys, permissive unknown-goal handling,
# reload lifecycle, and post-reload spawn integrity.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

WORKDIR="$1"
LOG_FILE="$2"
LOADER="$3"
VERSION="$4"

# Windowed assertions: grep only log lines written after a marker line.
assert_window_contains() {
    local from_line="$1" pattern="$2" test_name="$3"
    if tail -n +"$from_line" "$LOG_FILE" | grep -qE "$pattern"; then
        echo "  PASS: $test_name"
        echo "RESULT:PASS:$test_name"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "  FAIL: $test_name (pattern not found after line $from_line: $pattern)"
        echo "RESULT:FAIL:$test_name"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

assert_window_not_contains() {
    local from_line="$1" pattern="$2" test_name="$3"
    if tail -n +"$from_line" "$LOG_FILE" | grep -qE "$pattern"; then
        echo "  FAIL: $test_name (unexpected pattern after line $from_line: $pattern)"
        echo "RESULT:FAIL:$test_name"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    else
        echo "  PASS: $test_name"
        echo "RESULT:PASS:$test_name"
        PASS_COUNT=$((PASS_COUNT + 1))
    fi
}

# --- Section 1: Startup registration ---------------------------------------
begin_test_section "Brain registration"

assert_no_exceptions "no exceptions during brain registration"
assert_log_not_contains "Error.*brain|Error.*goal|Error.*magic.*mob|Error.*MobProfile|Error.*mob.*config" \
    "brain/goal system loaded without errors"
assert_log_contains "Loaded [0-9]+ magic mob brain\(s\)" "brain count summary logged"

assert_log_contains "Registered brain 'zombie_mage' \(wand=runic_wand, melee-range=2\.5\)" \
    "zombie_mage fixture read over bundled resource"
assert_log_contains "Registered brain 'zombie_fighter' \(wand=null, melee-range=0\.0\)" \
    "zombie_fighter uses default wand and melee-range"
assert_log_contains "Registered brain 'skeletal_mage' \(wand=blaze_wand, melee-range=0\.0\)" \
    "skeletal_mage registers despite unknown goal type"

end_test_section

# --- Section 2: Reload lifecycle -------------------------------------------
begin_test_section "Brain reload"

MARKER=$(wc -l < "$LOG_FILE")
send_command "alkatraz reload"
wait_for_new_log_match $((MARKER + 1)) "Loaded [0-9]+ magic mob brain\(s\)" "reload re-registers brains"

assert_window_contains $((MARKER + 1)) "Registered brain 'zombie_mage' \(wand=runic_wand, melee-range=2\.5\)" \
    "reload keeps fixture brain values"
assert_window_not_contains $((MARKER + 1)) "Error.*brain|Error.*goal" \
    "no brain errors during reload"
assert_no_exceptions "no exceptions during reload"

end_test_section

# --- Section 3: Spawn integrity --------------------------------------------
begin_test_section "Brain spawn"

MARKER=$(wc -l < "$LOG_FILE")
send_command "alkatraz summon zombie_mage 2 0,64,0"
sleep 1
assert_window_contains $((MARKER + 1)) "Spawned 2 zombie_mage" \
    "summon after reload still spawns magic mobs"
assert_no_exceptions "no exceptions during spawn"

end_test_section