#!/usr/bin/env bash
# Recipe-subsystem integration tests.
#
# Runs against a live server through the console (no player online). Asserts on
# the per-recipe registration/removal/override logInfo anchors in RecipeRegistry
# and the existing RecipeLoader warnings. Fixture YAMLs live in
# scripts/test-fixtures/recipes/ and are copied into
# $WORKDIR/plugins/Alkatraz/magic/recipes/ by run-server-test.sh before boot.
#
# Known limits (see spec): crafting gating is player-only, so gating coverage is
# clean registration + command error branches.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

WORKDIR="$1"
LOG_FILE="$2"
LOADER="$3"
VERSION="$4"

RECIPES_DIR="${WORKDIR}/plugins/Alkatraz/magic/recipes"

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

# --- Section 1: Startup & load ---------------------------------------------
begin_test_section "Recipe load"

assert_no_exceptions "no exceptions during startup"
assert_log_contains "Loaded [0-9]+ item definitions, [0-9]+ recipes, [0-9]+ engravings\\." \
    "definition/recipe summary logged"

for key in ci_shaped ci_shapeless ci_smithing ci_stonecutter ci_brewing ci_anvil \
           ci_cooking ci_custom ci_gated ci_hidden ci_permission; do
    assert_log_contains "Registered recipe alkatraz:${key}" "registered ${key}"
done
assert_log_contains "Registered recipe alkatraz:ci_dup" "duplicate-pair key registered"
assert_log_contains "Registered recipe alkatraz:ci_override" "override-pair key registered"
assert_log_contains "Registered recipe minecraft:stick" "conflict fixture key registered"

for key in ci_shaped ci_shapeless ci_smithing ci_stonecutter ci_brewing ci_anvil \
           ci_cooking ci_custom ci_gated ci_hidden ci_permission; do
    full_key="alkatraz:${key}"
    assert_log_not_contains "(Empty recipe shape for|Missing ingredients for recipe|Empty ingredients for shapeless recipe|Unknown result for recipe) ${full_key}|Invalid definition key in recipe: ${full_key}" \
        "no loader warnings for ${key}"
done

end_test_section

# --- Section 2: Reload lifecycle -------------------------------------------
begin_test_section "Recipe reload"

cat > "${RECIPES_DIR}/ci_postadd.yml" <<'EOF'
definition: alkatraz:ci_postadd
type: SHAPED
result:
  item: STICK
  amount: 1
shape:
  - "S"
ingredients:
  S: STICK
EOF

send_command "recipes reload"
wait_for_new_log_match 1 "Recipes reloaded \(.*\)" "reload command acknowledged"
assert_log_contains "Registered recipe alkatraz:ci_postadd" "reload registers added fixture"

WINDOW=$(wc -l < "$LOG_FILE")
rm -f "${RECIPES_DIR}/ci_shapeless.yml"
send_command "recipes reload"
wait_for_new_log_match $((WINDOW + 1)) "Recipes reloaded \(.*\)" "second reload acknowledged"
assert_window_contains "$WINDOW" "Removed recipe alkatraz:ci_shapeless" "reload removes deleted fixture"
assert_window_not_contains "$WINDOW" "Registered recipe alkatraz:ci_shapeless" "deleted fixture not re-registered"
assert_no_exceptions "no exceptions during reloads"

end_test_section

# --- Section 3: Conflict & override ----------------------------------------
begin_test_section "Recipe conflict and override"

assert_log_contains "Duplicate recipe key overwritten: alkatraz:ci_dup" "duplicate warning for no-override pair"
assert_log_not_contains "Recipe key alkatraz:ci_dup conflicts" "no conflict warning for no-override pair"
assert_log_not_contains "Overrode vanilla recipe alkatraz:ci_dup" "no override log for no-override pair"

assert_log_contains "Recipe key minecraft:stick conflicts with an existing Bukkit recipe" "conflict warning for vanilla-key fixture"
assert_log_not_contains "Overrode vanilla recipe minecraft:stick" "no override log for conflict fixture"

assert_log_contains "Duplicate recipe key overwritten: alkatraz:ci_override" "duplicate warning for override pair"
assert_log_contains "Overrode vanilla recipe alkatraz:ci_override" "override log for override pair"
assert_log_not_contains "Recipe key alkatraz:ci_override conflicts" "no conflict warning for override pair"

end_test_section

# --- Section 4: Malformed fixture handling ---------------------------------
begin_test_section "Recipe malformed fixtures"

assert_log_contains "Empty recipe shape for alkatraz:ci_bad_shape" "bad shape warning"
assert_log_not_contains "Registered recipe alkatraz:ci_bad_shape" "bad shape not registered"

assert_log_contains "Unknown ingredient 'definitely_not_a_material' in recipe" "bad ingredient warning"
assert_log_contains "Empty ingredients for shapeless recipe alkatraz:ci_bad_ingredient" "bad shapeless empty warning"
assert_log_not_contains "Registered recipe alkatraz:ci_bad_ingredient" "bad ingredient not registered"

assert_log_contains "Unknown recipe type 'WAFFLE', defaulting to SHAPED" "bad type warning"
assert_log_not_contains "Registered recipe alkatraz:ci_bad_type" "bad type not registered"

assert_log_contains "Recipe missing 'definition'/'id' key" "missing key warning"
assert_no_exceptions "no exceptions from malformed fixtures"

end_test_section

# --- Section 5: Gating / notifications / commands --------------------------
begin_test_section "Recipe gating and commands"

for key in ci_gated ci_hidden ci_permission; do
    full_key="alkatraz:${key}"
    assert_log_not_contains "(Empty recipe shape for|Missing ingredients for recipe|Unknown result for recipe) ${full_key}|Invalid definition key in recipe: ${full_key}" \
        "no loader warnings for ${key}"
done

send_command "recipes unlock alkatraz:ci_shaped"
assert_log_contains "You must specify a player when running this from console" "console unlock requires player"

send_command "recipes lock alkatraz:ci_shaped"
assert_log_contains "You must specify a player when running this from console" "console lock requires player"

send_command "recipes give alkatraz:ci_shaped"
assert_log_contains "You must specify a player when running this from console" "console give requires player"

send_command "recipes check nobody alkatraz:ci_shaped"
assert_log_contains "Couldn't find a player named nobody\\." "check unknown player message"

MARKER=$(wc -l < "$LOG_FILE")
send_command "recipes reload"
wait_for_new_log_match $((MARKER + 1)) "Recipes reloaded \(.*\)" "recipes reload acknowledged"
assert_no_exceptions "no exceptions during command tests"

end_test_section
