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

begin_test_section() {
    CURRENT_SECTION="$1"
    echo ""
    echo "=== $CURRENT_SECTION ==="
}

end_test_section() {
    echo "--- $CURRENT_SECTION complete ---"
}
