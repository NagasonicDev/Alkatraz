# Recipe CI Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add in-depth recipe-subsystem integration tests to the existing GitHub Actions shell harness, driven by per-recipe logInfo anchors and in-repo fixture YAMLs.

**Architecture:** Three non-behavioral `logInfo` lines are added to `RecipeRegistry` to create deterministic grep anchors. Twenty fixture YAMLs in `scripts/test-fixtures/recipes/` are copied into the server's recipe data folder before boot by a small hook in `run-server-test.sh`. A new `scripts/test-recipes.sh` (auto-discovered by the existing `test-*.sh` glob) asserts load, reload, conflict/override, malformed-file, and gating/command behavior on every CI matrix cell.

**Tech Stack:** Bash (GitHub Actions Ubuntu), GNU grep -E, Java 21 + Bukkit API (plugin), YAML (recipe fixtures), Minecraft server JARs (Paper/Purpur/Spigot, 1.19+).

## Global Constraints

- **Single final commit + push** at the end of the plan (project convention; the previous plan shipped exactly one commit `fa7ed54`). No per-task commits. Before the final commit, evidence (briefs, reports, diffs, ledger entries) must exist under `.superpowers/sdd/`.
- Compile verification: `mvn compile -pl core -am -q` must exit 0 with no output for every Java change.
- Shell changes: syntax-check with `bash -n <file>` when a bash binary is available on this Windows machine (Git-Bash); otherwise verify by careful re-read and rely on CI.
- Functional verification happens in CI (GitHub Actions `test-plugin.yml` matrix). No local server can run on this machine.
- No changes to `.github/workflows/test-plugin.yml`.
- No changes under `core/src/main/resources/` — fixtures are test-only, never shipped in the plugin.
- Console-only harness: no player is ever online. Player-only paths are exercised through their command error branches.
- Fixture keys stay in the default `alkatraz:` namespace (via `MagicKeys`) and are lowercased. Single exception: `ci_conflict.yml` deliberately uses `minecraft:stick`.

## Deviations from the approved spec

Three intentional adjustments, discovered during planning:

1. `ci_override_a.yml` and `ci_override_b.yml` **both** set `override_vanilla: true` (spec: "one with `override_vanilla: true`"). `registerNativeRecipes()` picks its branch from whichever recipe survives in `BY_KEY` after the duplicate overwrite, and directory load order is not guaranteed — a non-override survivor would make the `Overrode vanilla recipe` log nondeterministic. Both flagged → the log is guaranteed.
2. Added `ci_conflict.yml` (key `minecraft:stick`). The spec §3 "Bukkit-conflict warning for the no-override pair" is **unachievable**: two same-key recipes overwrite in `BY_KEY` (one entry), so `Bukkit.getRecipe(alkatraz:ci_dup)` is always null at native-registration time and the warning at `RecipeRegistry.java:87` never fires. A key colliding with a real vanilla Bukkit recipe (`minecraft:stick`) triggers it deterministically.
3. `ci_shaped.yml` includes an explicit `result:` block. The shipped `wooden_wand.yml` omits it only because `alkatraz:wooden_wand` IS an ItemDefinition (auto-result); `alkatraz:ci_shaped` is not, so without `result:` the loader logs `Unknown result for recipe`.
4. **Post-final-review fix (user-approved 2026-08-01):** the conflict fixture exposed a real bug — `registerNativeRecipes()` logs the conflict warning for a non-override conflicting key but still calls `CraftingEventRouter.registerNative(recipe)` (`RecipeRegistry.java:92`), and `Bukkit.addRecipe` throws `IllegalStateException` on a duplicate key, so `minecraft:stick` would crash the plugin at enable (and on `recipes reload`), failing the whole CI matrix. Fix: `continue` after the conflict warning (matching the existing pattern at `MagicItemRecipeManager.java:45`) so non-override conflicting recipes are skipped natively, plus a targeted `try/catch (IllegalStateException)` around the `registerNative` call as defense-in-depth so a single bad recipe can never disable the plugin. This is a genuine behavior fix the conflict fixture exposes and the test then verifies (Section 3's `conflict warning` + `no override log` + `assert_no_exceptions`).

---

## Task 1: RecipeRegistry observability anchors

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/items/magic/recipe/RecipeRegistry.java` (three insertions)

**Interfaces:**
- Consumes: (none)
- Produces: log lines `Registered recipe <key>`, `Removed recipe <key>`, `Overrode vanilla recipe <key>` — the assertion anchors for Task 4.

- [ ] **Step 1: Add the `register` anchor**

In `register(AlkatrazRecipe recipe)`, insert one line after the duplicate-warning `if` block (before `BY_KEY.put`):

```java
    public static void register(AlkatrazRecipe recipe) {
        NamespacedKey key = recipe.getKey();
        if (BY_KEY.containsKey(key)) {
            Alkatraz.logWarning("Duplicate recipe key overwritten: " + key);
        }
        Alkatraz.logInfo("Registered recipe " + key);
        BY_KEY.put(key, recipe);
```

- [ ] **Step 2: Add the `unregisterAll` anchor**

Insert one line inside the loop, after `Bukkit.removeRecipe(key);`:

```java
    public static void unregisterAll() {
        for (NamespacedKey key : BY_KEY.keySet()) {
            Bukkit.removeRecipe(key);
            Alkatraz.logInfo("Removed recipe " + key);
        }
        BY_KEY.clear();
```

- [ ] **Step 3: Add the override anchor**

Insert one line inside the `if (recipe.isOverrideVanilla())` branch, after `Bukkit.removeRecipe(recipe.getKey());`:

```java
            if (recipe.isOverrideVanilla()) {
                Bukkit.removeRecipe(recipe.getKey());
                Alkatraz.logInfo("Overrode vanilla recipe " + recipe.getKey());
            } else if (Bukkit.getRecipe(recipe.getKey()) != null) {
```

- [ ] **Step 4: Compile**

Run: `mvn compile -pl core -am -q`
Expected: exit code 0, no output.

- [ ] **Step 5: Verify the three anchors exist**

Run: `grep -n "Registered recipe \|Removed recipe \|Overrode vanilla recipe" core/src/main/java/me/nagasonic/alkatraz/items/magic/recipe/RecipeRegistry.java`
Expected: exactly three matching lines.

- [ ] **Step 6: Record evidence**

Append a brief, report, and diff for this task under `.superpowers/sdd/briefs/` and add a completed ledger line to `.superpowers/sdd/progress.md`.

---

## Task 2: Fixture-copy hook in run-server-test.sh

**Files:**
- Modify: `scripts/run-server-test.sh`

**Interfaces:**
- Consumes: directory `scripts/test-fixtures/recipes/` (created in Task 3)
- Produces: copied fixtures at `$WORKDIR/plugins/Alkatraz/magic/recipes/` before server boot, so the plugin loads them on first boot. Must be a no-op when the fixtures directory is absent (keeps other repos safe).

- [ ] **Step 1: Insert the hook**

`SCRIPT_DIR` is already defined (line 14). Insert this block immediately after the plugin-jar copy (`cp "${PLUGIN_JAR}" "${WORKDIR}/plugins/"`, currently line 45) and before `cd "${WORKDIR}"`:

```bash
# Copy recipe test fixtures into the plugin's recipe data folder (no-op when absent)
if [ -d "${SCRIPT_DIR}/test-fixtures/recipes" ]; then
    mkdir -p "${WORKDIR}/plugins/Alkatraz/magic/recipes"
    cp "${SCRIPT_DIR}/test-fixtures/recipes/"*.yml "${WORKDIR}/plugins/Alkatraz/magic/recipes/"
fi
```

- [ ] **Step 2: Syntax check**

Run: `bash -n scripts/run-server-test.sh` (if a bash binary is available; otherwise skip and rely on CI).
Expected: no output, exit 0.

- [ ] **Step 3: Verify placement**

Read the region around the plugin-jar copy and confirm the block sits between the `cp "${PLUGIN_JAR}"` line and `cd "${WORKDIR}"`.

- [ ] **Step 4: Record evidence**

Brief/report/diff under `.superpowers/sdd/briefs/` + ledger line.

---

## Task 3: Recipe fixture YAMLs

**Files:**
- Create: `scripts/test-fixtures/recipes/ci_shaped.yml`
- Create: `scripts/test-fixtures/recipes/ci_shapeless.yml`
- Create: `scripts/test-fixtures/recipes/ci_smithing.yml`
- Create: `scripts/test-fixtures/recipes/ci_stonecutter.yml`
- Create: `scripts/test-fixtures/recipes/ci_brewing.yml`
- Create: `scripts/test-fixtures/recipes/ci_anvil.yml`
- Create: `scripts/test-fixtures/recipes/ci_cooking.yml`
- Create: `scripts/test-fixtures/recipes/ci_custom.yml`
- Create: `scripts/test-fixtures/recipes/ci_gated.yml`
- Create: `scripts/test-fixtures/recipes/ci_hidden.yml`
- Create: `scripts/test-fixtures/recipes/ci_permission.yml`
- Create: `scripts/test-fixtures/recipes/ci_dup_a.yml`
- Create: `scripts/test-fixtures/recipes/ci_dup_b.yml`
- Create: `scripts/test-fixtures/recipes/ci_override_a.yml`
- Create: `scripts/test-fixtures/recipes/ci_override_b.yml`
- Create: `scripts/test-fixtures/recipes/ci_conflict.yml`
- Create: `scripts/test-fixtures/recipes/ci_bad_shape.yml`
- Create: `scripts/test-fixtures/recipes/ci_bad_ingredient.yml`
- Create: `scripts/test-fixtures/recipes/ci_bad_type.yml`
- Create: `scripts/test-fixtures/recipes/ci_missing_key.yml`

**Interfaces:**
- Consumes: the YAML formats parsed by `RecipeLoader` (see per-file notes)
- Produces: 20 fixture files consumed by the Task 2 hook and asserted by Task 4.

- [ ] **Step 1: Well-formed fixtures (16 files)**

Create each file with exactly this content.

`ci_shaped.yml` — legacy-format shaped recipe (mirrors the shipped `wooden_wand.yml` style: no `type:` field, string-form ingredients; explicit `result:` added per deviation 3):

```yaml
definition: alkatraz:ci_shaped
result:
  item: STICK
  amount: 1
shape:
  - "  M"
  - " W "
  - "S  "
ingredients:
  M: DIAMOND
  W: OAK_WOOD
  S: STICK
```

`ci_shapeless.yml` — shapeless (`ingredients:` is a list of material names):

```yaml
definition: alkatraz:ci_shapeless
type: SHAPELESS
result:
  item: STICK
  amount: 2
ingredients:
  - STICK
  - OAK_PLANKS
```

`ci_smithing.yml`:

```yaml
definition: alkatraz:ci_smithing
type: SMITHING
result:
  item: NETHERITE_INGOT
  amount: 1
smithing:
  base: DIAMOND
  addition: NETHERITE_INGOT
```

`ci_stonecutter.yml`:

```yaml
definition: alkatraz:ci_stonecutter
type: STONECUTTER
result:
  item: STICK
  amount: 1
stonecutter:
  input: COBBLESTONE
```

`ci_brewing.yml`:

```yaml
definition: alkatraz:ci_brewing
type: BREWING
result:
  item: GLOWSTONE_DUST
  amount: 1
brewing:
  input: SUGAR
  ingredient: REDSTONE
```

`ci_anvil.yml`:

```yaml
definition: alkatraz:ci_anvil
type: ANVIL
result:
  item: STICK
  amount: 1
anvil:
  base: DIAMOND
  addition: STICK
```

`ci_cooking.yml` — furnace variant, top-level input/experience/cooking_time:

```yaml
definition: alkatraz:ci_cooking
type: FURNACE
result:
  item: CHARCOAL
  amount: 1
input: OAK_LOG
experience: 0.5
cooking_time: 100
```

`ci_custom.yml` — CUSTOM station type + unlock message:

```yaml
definition: alkatraz:ci_custom
type: CUSTOM
result:
  item: STICK
  amount: 1
unlock:
  message: "You discovered the custom recipe!"
```

`ci_gated.yml` — gated on unlocking `alkatraz:ci_shaped`:

```yaml
definition: alkatraz:ci_gated
type: SHAPED
result:
  item: STICK
  amount: 1
requirements:
  - type: recipe_unlocked
    recipe: alkatraz:ci_shaped
shape:
  - "S"
ingredients:
  S: STICK
```

`ci_hidden.yml`:

```yaml
definition: alkatraz:ci_hidden
type: SHAPED
result:
  item: STICK
  amount: 1
hidden_when_locked: true
shape:
  - "S"
ingredients:
  S: STICK
```

`ci_permission.yml`:

```yaml
definition: alkatraz:ci_permission
type: SHAPED
result:
  item: STICK
  amount: 1
permissions:
  - alkatraz.recipes.ci
shape:
  - "S"
ingredients:
  S: STICK
```

`ci_dup_a.yml` and `ci_dup_b.yml` — identical files, same key, no override (the second file to load triggers `Duplicate recipe key overwritten`):

```yaml
definition: alkatraz:ci_dup
type: SHAPED
result:
  item: STICK
  amount: 1
shape:
  - "S"
ingredients:
  S: STICK
```

`ci_override_a.yml` and `ci_override_b.yml` — identical files, same key, BOTH flagged `override_vanilla: true` (deviation 1; guarantees the override log regardless of load order):

```yaml
definition: alkatraz:ci_override
type: SHAPED
result:
  item: STICK
  amount: 1
override_vanilla: true
shape:
  - "S"
ingredients:
  S: STICK
```

`ci_conflict.yml` — key collides with the vanilla `minecraft:stick` recipe (deviation 2; triggers the Bukkit-conflict warning deterministically):

```yaml
# Key deliberately collides with the vanilla minecraft:stick recipe so the
# Bukkit-conflict warning path (RecipeRegistry.registerNativeRecipes) is tested.
definition: minecraft:stick
type: SHAPED
result:
  item: STICK
  amount: 1
shape:
  - "S"
ingredients:
  S: STICK
```

- [ ] **Step 2: Malformed fixtures (4 files)**

`ci_bad_shape.yml` — empty shape → `Empty recipe shape for alkatraz:ci_bad_shape`, not registered:

```yaml
definition: alkatraz:ci_bad_shape
type: SHAPED
result:
  item: STICK
  amount: 1
shape: []
ingredients:
  S: STICK
```

`ci_bad_ingredient.yml` — shapeless with an unknown ingredient → `Unknown ingredient 'definitely_not_a_material' in recipe` then `Empty ingredients for shapeless recipe alkatraz:ci_bad_ingredient`, not registered:

```yaml
definition: alkatraz:ci_bad_ingredient
type: SHAPELESS
result:
  item: STICK
  amount: 1
ingredients:
  - definitely_not_a_material
```

`ci_bad_type.yml` — unknown type → `Unknown recipe type 'WAFFLE', defaulting to SHAPED`, then empty shape → not registered:

```yaml
definition: alkatraz:ci_bad_type
type: WAFFLE
result:
  item: STICK
  amount: 1
```

`ci_missing_key.yml` — no `definition`/`id` → `Recipe missing 'definition'/'id' key`, not registered:

```yaml
result:
  item: STICK
  amount: 1
```

- [ ] **Step 3: Verify fixture list and YAML**

Run: `Get-ChildItem scripts/test-fixtures/recipes/*.yml | Select-Object Name` (PowerShell).
Expected: exactly the 20 files above.
If a YAML parser is available, parse each file; otherwise verify by review. Every well-formed fixture must have `definition`, a valid `result.item`, and the station payload (`shape`+`ingredients` for SHAPED, `ingredients` list for SHAPELESS, or the `smithing:`/`stonecutter:`/`brewing:`/`anvil:`/`furnace` section).

- [ ] **Step 4: Record evidence**

Brief/report/diff under `.superpowers/sdd/briefs/` + ledger line.

---

## Task 4: scripts/test-recipes.sh

**Files:**
- Create: `scripts/test-recipes.sh`

**Interfaces:**
- Consumes: `test-helpers.sh` (`PASS_COUNT`/`FAIL_COUNT`, `assert_log_contains`, `assert_log_not_contains`, `assert_no_exceptions`, `send_command`, `begin_test_section`, `end_test_section`), the Task 1 log anchors, the Task 3 fixtures, and the `run-server-test.sh` auto-glob (`test-*.sh`) + fd-3 console pipe.
- Produces: `RESULT:PASS:`/`RESULT:FAIL:` lines parsed by `run-server-test.sh` (lines 85–86), adding recipe coverage to every matrix cell.

- [ ] **Step 1: Write the script**

Create `scripts/test-recipes.sh` with exactly this content:

```bash
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
assert_log_contains "Recipes reloaded \([0-9]+\)" "reload command acknowledged"
assert_log_contains "Registered recipe alkatraz:ci_postadd" "reload registers added fixture"

WINDOW=$(wc -l < "$LOG_FILE")
rm -f "${RECIPES_DIR}/ci_shapeless.yml"
send_command "recipes reload"
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

send_command "recipes reload"
assert_log_contains "Recipes reloaded \([0-9]+\)" "recipes reload acknowledged"
assert_no_exceptions "no exceptions during command tests"

end_test_section
```

- [ ] **Step 2: Syntax check**

Run: `bash -n scripts/test-recipes.sh` (if bash available; otherwise skip, rely on CI).
Expected: no output, exit 0.

- [ ] **Step 3: Self-review the assertion patterns**

Confirm every `assert_log_*` pattern matches exactly one of the Task 1 anchors, a `RecipeLoader` warning, or a `RecipesCommand` lang value:
- `Recipes reloaded \([0-9]+\)`, `You must specify a player when running this from console`, `Couldn't find a player named nobody\.`
- `Loaded [0-9]+ item definitions, [0-9]+ recipes, [0-9]+ engravings\.`
- The five `RecipeLoader` warning prefixes used in the "no loader warnings" loops.

- [ ] **Step 4: Record evidence**

Brief/report/diff under `.superpowers/sdd/briefs/` + ledger line.

---

## Task 5: Final verification, commit and push

**Files:** (whole diff)
- Modify: all files changed by Tasks 1–4

**Interfaces:**
- Produces: the complete, reviewable change set and the shipped commit.

- [ ] **Step 1: Full compile**

Run: `mvn compile -pl core -am -q`
Expected: exit 0, no output.

- [ ] **Step 2: Full-diff review**

Run: `git status` and `git diff --stat` (plus `git diff` on any file not yet reviewed).
Expected: only the intended files — `RecipeRegistry.java`, `run-server-test.sh`, `scripts/test-recipes.sh`, and the 20 fixture files.

- [ ] **Step 3: Update the ledger**

Confirm `.superpowers/sdd/progress.md` has a completed line for each of Tasks 1–4, and evidence briefs exist under `.superpowers/sdd/briefs/`.

- [ ] **Step 4: Commit**

Stage and commit all of it with one message following the project's git-commit-style (plain language):
```bash
git add scripts/test-fixtures/recipes core/src/main/java/me/nagasonic/alkatraz/items/magic/recipe/RecipeRegistry.java scripts/run-server-test.sh scripts/test-recipes.sh docs/superpowers/plans/2026-08-01-recipe-ci-testing.md
git commit
```
Suggested message: `add recipe CI integration tests with fixture-based load, reload and gating coverage`
(also carries the previously committed spec `d5de5bc` if it was not pushed).

- [ ] **Step 5: Push**

Run: `git push origin master`
Expected: `master -> master` (or a fast-forward carrying `d5de5bc` if the spec commit had not been pushed).

- [ ] **Step 6: Trigger/confirm CI**

If `test-plugin.yml` does not auto-run on push, dispatch it from the Actions tab (workflow_dispatch) and confirm every matrix cell passes — the SECTIONS must show the new `Recipe load`, `Recipe reload`, `Recipe conflict and override`, `Recipe malformed fixtures`, and `Recipe gating and commands` sections — or fail with clear SECTIONS/LOG_SNAPSHOTS for triage.

---

## Completion criteria

- `mvn compile -pl core -am -q` exits 0.
- One commit pushed to `origin master` containing Tasks 1–4 (+ the spec commit if unpushed).
- `test-plugin.yml` matrix run shows all five new recipe sections, passing on every cell.
- No changes to `.github/workflows/`, `core/src/main/resources/`, plugin behavior, config, or lang.
