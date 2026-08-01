#!/usr/bin/env bash
# Shared helper functions for runtime integration tests.
#
# Expects these variables to be set by the caller:
#   WORKDIR   — the server working directory
#   LOG_FILE  — path to server.log

PASS_COUNT=0
FAIL_COUNT=0
CURRENT_SECTION=""

strip_color() {
    sed 's/§[0-9a-fk-or]//gI' | sed 's/\x1b\[[0-9;]*m//g'
}

send_command() {
    echo "$1" >&3
    sleep 1
}

assert_log_contains() {
    local pattern="$1"
    local test_name="$2"
    if grep -qE "$pattern" "$LOG_FILE" 2>/dev/null; then
        echo "  PASS: $test_name"
        echo "RESULT:PASS:$test_name"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "  FAIL: $test_name (pattern not found: $pattern)"
        echo "RESULT:FAIL:$test_name"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

assert_log_not_contains() {
    local pattern="$1"
    local test_name="$2"
    if grep -qE "$pattern" "$LOG_FILE" 2>/dev/null; then
        echo "  FAIL: $test_name (unexpected pattern found: $pattern)"
        echo "RESULT:FAIL:$test_name"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    else
        echo "  PASS: $test_name"
        echo "RESULT:PASS:$test_name"
        PASS_COUNT=$((PASS_COUNT + 1))
    fi
}

assert_no_exceptions() {
    local test_name="$1"
    assert_log_not_contains "Exception in thread|java\\.lang\\.\\w+Exception|NullPointerException|ArrayIndexOutOfBounds|ClassCastException|StackOverflow|OutOfMemory" "$test_name"
}

wait_for_server_ready() {
    local timeout="${1:-90}"
    for i in $(seq 1 "$timeout"); do
        if grep -q "Done (" "$LOG_FILE" 2>/dev/null; then
            return 0
        fi
        sleep 1
    done
    return 1
}

assert_log_contains_any() {
    local test_name="$1"
    shift
    local found=false
    for pattern in "$@"; do
        if grep -qE "$pattern" "$LOG_FILE" 2>/dev/null; then
            found=true
            break
        fi
    done
    if [ "$found" = true ]; then
        echo "  PASS: $test_name"
        echo "RESULT:PASS:$test_name"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "  FAIL: $test_name (none of the patterns found)"
        echo "RESULT:FAIL:$test_name"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

# Condition-based wait: poll the log for a pattern after a marker line instead of
# relying on the fixed 1s send_command sleep. Completion markers (e.g. "Recipes
# reloaded (N).", "Reloaded configs.") are logged only after the reload finishes,
# which can exceed 1s on slower servers. $from_line is inclusive: pass a +1
# boundary when the marker line itself could already contain a stale match.
wait_for_new_log_match() {
    local from_line="$1" pattern="$2" test_name="$3" max_seconds="${4:-20}" i
    for i in $(seq 1 "$max_seconds"); do
        if tail -n +"$from_line" "$LOG_FILE" | grep -qE "$pattern" 2>/dev/null; then
            echo "  PASS: $test_name"
            echo "RESULT:PASS:$test_name"
            PASS_COUNT=$((PASS_COUNT + 1))
            return 0
        fi
        sleep 1
    done
    echo "  FAIL: $test_name (pattern not found after line $from_line within ${max_seconds}s: $pattern)"
    echo "RESULT:FAIL:$test_name"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    return 1
}

begin_test_section() {
    CURRENT_SECTION="$1"
    echo ""
    echo "=== $CURRENT_SECTION ==="
}

end_test_section() {
    echo "--- $CURRENT_SECTION complete ---"
}
