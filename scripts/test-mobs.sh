#!/usr/bin/env bash
# Mob/entity system runtime tests.
set -uo pipefail

WORKDIR="$1"
LOG_FILE="$2"
LOADER="$3"
VERSION="$4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

begin_test_section "Mobs"

assert_log_not_contains "Error.*magic.*mob|Error.*MobProfile|Error.*mob.*config" "Magic mob profiles loaded without errors"

send_command "alkatraz summon zombie_mage"
sleep 1
assert_log_contains "must specify a location or player|Spawned 1 zombie_mage" "zombie_mage spawn handled"

send_command "alkatraz summon zombie_fighter"
sleep 1
assert_log_contains "must specify a location or player|Spawned 1 zombie_fighter" "zombie_fighter spawn handled"

send_command "alkatraz summon skeletal_mage"
sleep 1
assert_log_contains "must specify a location or player|Spawned 1 skeletal_mage" "skeletal_mage spawn handled"

send_command "alkatraz summon zombie_mage 3 0,64,0"
sleep 1
assert_log_contains "Spawned 3 zombie_mage" "summon count and location works"

send_command "alkatraz summon fake_mob"
sleep 1
assert_log_contains "Unknown magic mob" "invalid mob type rejected"

assert_no_exceptions "No exceptions from mob system"

end_test_section
