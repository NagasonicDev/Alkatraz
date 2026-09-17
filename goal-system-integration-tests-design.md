# Goal System + AI Framework Integration Test Coverage

Date: 2026-09-17

## Purpose

Add runtime integration tests for the goal system and general AI framework so the
GitHub Action (`integration-test` job in `.github/workflows/test-plugin.yml`) verifies
brains load from config, goals/targets apply per config semantics, reload re-reads the
fixture files, and mob spawns with goals wired do not throw. Mirrors the existing
`test-recipes.sh` / `test-fixtures/recipes` pattern.

## Scope

- New integration test script: `scripts/test-brains.sh` (auto-discovered by
  `run-server-test.sh`).
- New brain fixtures: `scripts/test-fixtures/brains/{zombie_mage,zombie_fighter,skeletal_mage}.yml`
  (overrides of the three shipped brain ids).
- New config fixture: `scripts/test-fixtures/config.yml` setting `verbose: high`.
- Modified: `scripts/run-server-test.sh` (add two fixture-copy blocks).
- No production source or workflow changes.

## Why `verbose: high`

`MagicBrains.reload` emits `"Registered brain '<id>' (wand=..., melee-range=...)"` via
`Alkatraz.logHigh`, which is suppressed at the default `verbose: normal`. A pre-placed
`config.yml` with `verbose: high` makes per-brain registration observable. Because
`saveResource(name, false)` never overwrites an existing file and `ConfigUpdater` preserves
existing disk values while filling in missing keys, the pre-placed fixture survives startup.

### Verbosity side-effect audit (performed)

Audited every `logHigh`/`logVeryHigh` call site in `core` against every
`assert_log_not_contains` pattern across all `scripts/test-*.sh`. Raising verbosity to
`high` adds only these startup lines, none of which match any existing negative assertion:

- `SpellRegistry`: `Registered spell: ...`, `Registered API spell: ...`
- `MagicEntityRegistry`: `Registered profile '...' (...)` , `Total cached profiles: N`
- `MagicBrains`: `Registered brain '...' (...)` (intended)

Existing `assert_log_not_contains` patterns are all `Error.*`-gated or exact strings
(`Registered recipe `, `Overrode vanilla recipe ...`) and remain unaffected.

## Fixture inventory

### `scripts/test-fixtures/config.yml`
```yaml
verbose: high
```

### `brains/zombie_mage.yml` — fixture-read proof
```yaml
display-name: "&fCI Zombie Mage"
melee-range: 2.5
wand: ci_wand_test
goals:
  move:
    type: water_avoiding_stroll
    priority: 2
  cast:
    type: cast_spell
    priority: 3
    min-cast-dist: 4
    max-cast-dist: 10
    cast-range: 14
    cooldown-ticks: 60
targets:
  hunt:
    type: nearest_attackable_target
    priority: 1
```
Distinctive `wand`/`melee-range` values prove the fixture file (not the bundled
resource) is read.

### `brains/zombie_fighter.yml` — default-path proof
```yaml
display-name: "&fCI Zombie Fighter"
goals:
  idle:
    type: float
  look:
    type: look_at_player
    priority: 5
```
No `wand`/`melee-range` keys → registration logs `wand=null, melee-range=0.0`,
proving default fallbacks.

### `brains/skeletal_mage.yml` — permissive-parsing proof
```yaml
display-name: "&fCI Skeletal Mage"
melee-range: 0
wand: ci_wand2
goals:
  hold:
    type: keep_spell_range
    min-distance: 5
    max-distance: 11
    speed: 1.2
  broken:
    type: does_not_exist_goal
targets:
  hurt:
    type: hurt_by_target
```
The unknown goal type must not crash registration (GoalFactory NO-OPs unknown types);
the brain still registers (`Registered brain 'skeletal_mage' (wand=ci_wand2, melee-range=0.0)`).

## `run-server-test.sh` changes

Insert two blocks after the existing recipe-fixture block (line ~51), before `cd "${WORKDIR}"`:

```bash
# Copy brain test fixtures into the plugin's brains data folder (no-op when absent)
if [ -d "${SCRIPT_DIR}/test-fixtures/brains" ]; then
    mkdir -p "${WORKDIR}/plugins/Alkatraz/brains"
    cp "${SCRIPT_DIR}/test-fixtures/brains/"*.yml "${WORKDIR}/plugins/Alkatraz/brains/"
fi

# Copy a config fixture that raises verbosity so brain registration lines are emitted
if [ -f "${SCRIPT_DIR}/test-fixtures/config.yml" ]; then
    mkdir -p "${WORKDIR}/plugins/Alkatraz"
    cp "${SCRIPT_DIR}/test-fixtures/config.yml" "${WORKDIR}/plugins/Alkatraz/config.yml"
fi
```

## `test-brains.sh` assertions

Uses `test-helpers.sh` (`assert_log_contains`, `assert_log_not_contains`,
`assert_no_exceptions`, `send_command`, `wait_for_new_log_match`, windowed MARKER).

1. `assert_log_not_contains` brain/goal error patterns:
   `Error.*brain|Error.*goal|Error.*magic.*mob|Error.*MobProfile|Error.*mob.*config`.
2. Startup: `Loaded [0-9]+ magic mob brain(s)` present (logInfo, always visible).
3. Fixture-read: `Registered brain 'zombie_mage' (wand=ci_wand_test, melee-range=2.5)`.
4. Defaults: `Registered brain 'zombie_fighter' (wand=null, melee-range=0.0)`.
5. Permissive: `Registered brain 'skeletal_mage' (wand=ci_wand2, melee-range=0.0)`
   despite `does_not_exist_goal`.
6. Reload: windowed MARKER + `send_command alkatraz reload` +
   `wait_for_new_log_match "Loaded [0-9]+ magic mob brain(s)"`, then re-assert the
   `zombie_mage` registration line appears again (brains re-read from disk on reload);
   `assert_no_exceptions`.
7. Spawn: `send_command "alkatraz summon zombie_mage 2 0,64,0"` →
   `Spawned 2 zombie_mage`; `assert_no_exceptions` (exercises goal wiring through the
   NMS bridge on a real server).

`report-warnings` step needs no filter change: brain fixtures intentionally produce no
warnings (unknown goal types are silent NO-OPs).

## Non-goals

- No changes to NMS modules, core production code, or the workflow YAML.
- No in-game goal behavior verification (requires player presence); behavior is covered
  by unit tests.