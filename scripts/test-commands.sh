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
sleep 1
assert_log_contains "Reloaded configs" "alkatraz reload works"

send_command "alkatraz spawnmob zombie_mage"
sleep 1
assert_log_contains "Spawned zombie_mage" "spawnmob zombie_mage works"

send_command "alkatraz spawnmob zombie_fighter"
sleep 1
assert_log_contains "Spawned zombie_fighter" "spawnmob zombie_fighter works"

send_command "alkatraz spawnmob skeletal_mage"
sleep 1
assert_log_contains "Spawned skeletal_mage" "spawnmob skeletal_mage works"

send_command "alkatraz spawnmob nonexistent_mob"
sleep 1
assert_log_contains "Unknown magic mob" "spawnmob invalid type shows error"

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
assert_log_contains "Couldn't find a player" "give without target shows player-not-found"

send_command "alkatraz arcaneknowledge set 100"
sleep 1
assert_log_contains "Couldn't find a player" "arcaneknowledge without target shows player-not-found"

send_command "alkatraz circle set 5"
sleep 1
assert_log_contains "Couldn't find a player" "circle without target shows player-not-found"

assert_no_exceptions "No exceptions during command execution"

end_test_section
