# Recipe CI Testing Design

- **Date:** 2026-08-01
- **Status:** Approved design
- **Owner:** Alkatraz core + CI

## Goal

Add in-depth testing of the recipe subsystem to the existing GitHub Actions integration-test
harness. The plan's T21 testing doc (`docs/superpowers/guides/recipe-testing.md`) lists unit targets
and an integration checklist; this spec implements the **integration/CI** portion using the existing
shell-script server harness.

## Decisions (from brainstorming)

1. **Shell integration tests only** — no JUnit; the repo has no test infra and the requested depth is
   server-side behavior. Tests live in `scripts/test-*.sh`, auto-wired by the existing glob in
   `run-server-test.sh` (so they run on every matrix cell: all MC versions x paper/purpur/spigot).
2. **Full coverage** — all 8 station types load, reload lifecycle, conflict + override_vanilla,
   malformed-file handling, gating + notifications + commands.
3. **Small plugin-side logging allowed** — per-recipe registration/removal/override `logInfo` lines
   give deterministic assertion anchors.
4. **Fixture YAML files in the repo** — `scripts/test-fixtures/recipes/`, copied into
   `plugins/Alkatraz/magic/recipes/` before server start via a small hook in `run-server-test.sh`.
   Never shipped inside plugin resources.

## Plugin observability changes (3 logInfo additions, non-behavioral)

All in `core/src/main/java/me/nagasonic/alkatraz/items/magic/recipe/RecipeRegistry.java`:

1. `register(AlkatrazRecipe)` — after the duplicate-key check, log
   `Alkatraz.logInfo("Registered recipe " + key);`.
2. `unregisterAll()` — inside the `for (NamespacedKey key : BY_KEY.keySet())` loop (before/after
   `Bukkit.removeRecipe(key)`), log `Alkatraz.logInfo("Removed recipe " + key);`.
3. `registerNativeRecipes()` — in the `recipe.isOverrideVanilla()` branch, log
   `Alkatraz.logInfo("Overrode vanilla recipe " + recipe.getKey());`.

No behavior, config, lang, or API change. Existing warning lines are reused as negative assertions
(`Duplicate recipe key overwritten`, the Bukkit-conflict warning, and RecipeLoader's warnings).

## Fixtures

New directory `scripts/test-fixtures/recipes/`, one YAML per scenario. All keys under the default
`alkatraz` namespace (`MagicKeys` default), lowercased. Legacy-format shaped fixture mirrors the
shipped `wooden_wand.yml` style; new-format fixtures use the modern fields (`type:`, `result:`,
`requirements:`, `permissions:`, `hidden_when_locked:`, `unlock.message:`, `override_vanilla:`).

Proposed fixture files (names are illustrative; final set pinned in the implementation plan):

| File | Type / scenario |
|---|---|
| `ci_shaped.yml` | shaped (legacy format) |
| `ci_shapeless.yml` | shapeless |
| `ci_smithing.yml` | smithing |
| `ci_stonecutter.yml` | stonecutter |
| `ci_brewing.yml` | brewing |
| `ci_anvil.yml` | anvil |
| `ci_cooking.yml` | cooking (furnace variant) |
| `ci_custom.yml` | custom + `unlock.message` |
| `ci_gated.yml` | shaped + `requirements: [recipe_unlocked ...]` |
| `ci_hidden.yml` | shaped + `hidden_when_locked: true` |
| `ci_permission.yml` | shaped + `permissions:` |
| `ci_dup_a.yml` / `ci_dup_b.yml` | same key, no override (duplicate + Bukkit conflict) |
| `ci_override_a.yml` / `ci_override_b.yml` | same key, one with `override_vanilla: true` |
| `ci_bad_shape.yml` | empty/invalid shape |
| `ci_bad_ingredient.yml` | unknown ingredient |
| `ci_bad_type.yml` | unknown type |
| `ci_missing_key.yml` | missing definition/id |

Note: the plugin re-saves its own shipped default recipe files into
`plugins/Alkatraz/magic/recipes/` on first boot. Assertions therefore use the **per-recipe
register/removal logs**, not exact total-counts.

## Harness wiring

In `scripts/run-server-test.sh`, before starting the server: create
`$WORKDIR/plugins/Alkatraz/magic/recipes/` and copy every `scripts/test-fixtures/recipes/*.yml` into
it. Guard so it is a no-op when the fixtures dir does not exist (keeps other repos' usage/PRs safe).

## test-recipes.sh

New `scripts/test-recipes.sh` following the existing pattern (sources `test-helpers.sh`, takes
`$WORKDIR $LOG_FILE $LOADER $VERSION`, uses `begin_test_section`/`end_test_section`,
`assert_log_contains`/`assert_log_not_contains`/`assert_no_exceptions`, console commands via
`send_command`). Sections:

1. **Startup & load** — `wait_for_server_ready`; assert no exceptions; assert the
   `Loaded ... recipes ...` summary line; assert each fixture's `Registered recipe alkatraz:ci_*` line;
   assert zero `logWarning` for the well-formed fixtures.
2. **Reload lifecycle** — write an extra fixture file post-start into
   `$WORKDIR/plugins/Alkatraz/magic/recipes/`; `alkatraz reload`; assert its `Registered recipe` line.
   Delete a fixture; reload; assert its `Removed recipe` line.
3. **Conflict & override** — assert `Duplicate recipe key overwritten: alkatraz:ci_dup*` and the
   Bukkit-conflict warning for the no-override pair; assert `Overrode vanilla recipe alkatraz:ci_override_*`
   and absence of the conflict warning for the override pair.
4. **Malformed files** — assert each bad fixture's specific `logWarning` (empty shape / unknown
   ingredient / unknown type / missing key), assert its key is absent from register lines, and
   `assert_no_exceptions`.
5. **Gating / notifications / commands** — gated + hidden + permission fixtures register cleanly with
   no warnings; `recipes.unlock_notifications` config parses (no config warnings); console-path
   `/recipes unlock|lock|give <id>` (no player arg) → `commands.console_require_player`;
   `/recipes check <nobody> <id>` → `commands.player_not_found`; `/recipes reload` → clean reload
   summary; no stack traces on any command.

Each failing assertion is surfaced by the existing harness (SECTIONS + LOG_SNAPSHOTS in artifacts and
the `report-warnings` job summary).

## Workflow changes

None in `.github/workflows/test-plugin.yml` beyond what exists — the `test-*.sh` glob picks up
`test-recipes.sh` and the fixture hook is inside `run-server-test.sh` (no new jobs, no matrix change).

## Known limits (documented in the script header + spec)

- Crafting gating is player-only; the console-only harness cannot execute live craft/gate behavior.
  Gating coverage = clean registration + config parse + command error branches.
- Assertions are console-log based (`logInfo`/`logWarning`/exception greps).

## Verification

- Local: run `bash scripts/test-recipes.sh <workdir> <log> paper 1.20.4` against a local server if
  available; otherwise rely on the CI matrix run.
- CI: full `test-plugin.yml` run (workflow_dispatch) — every matrix cell must pass or fail with clear
  SECTIONS/LOG_SNAPSHOTS.
- `mvn compile -pl core -am -q` exit 0 (plugin log changes only).

## Out of scope

- JUnit unit tests (documented in the testing guide as future work).
- Live player/craft simulation, visual menu checks.
- Changes to plugin.yml, config.yml, lang, api/ module, or any recipe behavior.
