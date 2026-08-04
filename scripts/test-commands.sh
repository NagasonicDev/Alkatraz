#!/usr/bin/env bash
# Command system runtime tests.
set -uo pipefail

WORKDIR="$1"
LOG_FILE="$2"
LOADER="$3"
VERSION="$4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

begin_test_section "Commands"

assert_log_not_contains "Could not load '.*alkatraz.*\\.jar'" "Plugin JAR loaded without errors"
assert_log_not_contains "Error occurred while enabling Alkatraz" "Plugin onEnable() succeeded"
assert_no_exceptions "No exceptions during startup"
assert_log_contains "NMS version.*registered" "NMS version module registered"

send_command "alkatraz"
assert_log_not_contains "Unknown command.*alkatraz" "alkatraz command registered"

send_command "alkatraz reload"
wait_for_new_log_match 1 "Reloaded" "alkatraz reload works"

send_command "alkatraz summon zombie_mage"
sleep 1
assert_log_contains "must specify a location or player" "summon zombie_mage requires location or player from console"

send_command "alkatraz summon zombie_fighter"
sleep 1
assert_log_contains "must specify a location or player" "summon zombie_fighter requires location or player from console"

send_command "alkatraz summon skeletal_mage"
sleep 1
assert_log_contains "must specify a location or player" "summon skeletal_mage requires location or player from console"

send_command "alkatraz summon zombie_mage 1 bogus_target"
sleep 1
assert_log_contains "Unknown location or player" "summon invalid location shows error"

send_command "alkatraz summon nonexistent_mob"
sleep 1
assert_log_contains "Unknown magic mob" "summon invalid type shows error"

send_command "alkatraz"
sleep 1
assert_log_contains "Please add an argument" "alkatraz no-args shows usage"

send_command "spells"
sleep 1
assert_log_contains "Only players can use this command" "/spells requires player"

send_command "recipes"
sleep 1
assert_log_contains "Only players can use this command" "/recipes requires player"

send_command "cast test"
sleep 1
assert_log_contains "Only players can use this command" "/cast requires player"

send_command "alkatraz stats"
sleep 1
assert_log_contains "Only players can use this command" "/alkatraz stats requires player"

send_command "alkatraz equipment"
sleep 1
assert_log_contains "Only players can use this command" "/alkatraz equipment requires player"

send_command "alkatraz editor"
sleep 1
assert_log_contains "Only players can use this command" "/alkatraz editor requires player"

send_command "alkatraz give wooden_wand"
sleep 1
assert_log_contains "must specify a player" "give requires player from console"

send_command "alkatraz arcaneknowledge set 100"
sleep 1
assert_log_contains "must specify a player" "arcaneknowledge requires player from console"

send_command "alkatraz circle set 5"
sleep 1
assert_log_contains "must specify a player" "circle requires player from console"

assert_no_exceptions "No exceptions during command execution"

end_test_section
