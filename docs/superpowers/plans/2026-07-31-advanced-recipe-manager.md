# Advanced Recipe Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current shaped-only, hardcoded recipe system into a data-driven, reloadable, multi-station recipe manager with a centralized pluggable requirement engine, per-player unlock persistence, locked-recipe UX, and a public API for other plugins.

**Architecture:** Reuse and extend the existing centralized pieces rather than building parallel systems — `RequirementFactory` becomes the requirement hub for recipes; `MagicProfile`'s `discoveredSpells` stringSet pattern is the template for unlock state; `RecipeCraftListener`'s event-gating pattern is generalized into per-station `CraftingTypeAdapter`s. Bukkit-native recipes (shaped/shapeless/cooking/smithing/stonecutter) register with `Server#addRecipe`; types with no native Bukkit recipe (brewing, anvil) plus all per-player gating are enforced via event interception.

**Tech Stack:** Spigot/Paper API (MC 1.19–1.26), Java 17, existing Maven multi-module (api/, core/). No new libraries for YAML-backed phase; PlaceholderAPI integration optional via soft-depend.

## Scope Decisions (confirmed)

- **Persistence:** YAML now (via existing `ProfilePersistence`), with a `UnlockStore` abstraction so SQLite/MySQL can be added later without touching the engine.
- **Custom GUI crafting:** define the extension point (`CustomCraftingAdapter`) + docs only; no custom crafting GUI built this iteration.
- **Deliverable:** architecture + signatures + schemas + phased roadmap (no full production code).

## Open Design Questions (decided defaults)

1. **Unlock model: automatic.** The moment a player's requirements are met (evaluated on login + state-change events + first craft attempt), the recipe auto-unlocks once and persists forever, with notification.
2. **`playtime` source: vanilla `Statistic.PLAY_ONE_MINUTE`** (no new persistence).
3. **`papi` requirement type deferred to Phase 4.**
4. **`/recipes give` = grant unlock state** (alias of unlock), not item give.
5. **Unlock permanence: yes** — once unlocked, stays unlocked even if the player later drops below a requirement (matches "discovered spell" semantics).

---

## 1. Architecture & Data Flow

### Components

```
                        ┌────────────────────────────────────────────┐
                        │                 RecipeRegistry              │
                        │  BY_KEY / BY_OUTPUT_MATERIAL / BY_STATION   │
                        │  BY_INGREDIENT  (+ per-player UnlockCache)  │
                        └──────┬──────────────────────┬───────────────┘
       load/reload             │                      │ register native
   ┌──────────────┐   ┌────────▼────────┐   ┌────────▼───────────────┐
   │  RecipeLoader │──▶│  Requirement    │   │  CraftingTypeAdapter    │
   │ (YAML → model)│   │  Factory (hub)  │   │  (shaped, shapeless,    │
   └──────────────┘   └────────┬────────┘   │  cooking, smithing, ...) │
                               │            └────────┬───────────────┘
                    +10 existing types   ┌───────────▼────────────────┐
                    + new: recipe_       │  CraftingEventRouter       │
                    unlocked, papi,      │  (routes Bukkit events to  │
                    xp_level, playtime,  │   the right adapter)       │
                    world                └───────────┬────────────────┘
                                                     │ gate decisions
                               ┌─────────────────────▼───────────────┐
                               │  RecipeGate (canCraft / onUnlock)    │
                               │  + UnlockManager + UnlockStore       │
                               │  + MagicProfile.unlockedRecipes      │
                               └─────────────────────┬───────────────┘
                                                     │ fires
                               ┌─────────────────────▼───────────────┐
                               │  RecipeUnlockedEvent (Bukkit event)  │
                               └─────────────────────┬───────────────┘
                                                     ▼
                               Notifications (Utils/chat/title/sound)
                               RecipeBookMenu / RecipeDetailMenu (locked UX)
                               RecipeManagerAPI (public, in api/ module)
```

### Data flow

1. **Load:** `MagicItemBootstrap.loadDefinitions()` reads `magic/recipes/*.yml` → `RecipeLoader` → `AlkatrazRecipe` objects → `RecipeRegistry` (indexed). Bukkit-native recipes registered via `Server#addRecipe`; vanilla conflicts optionally removed.
2. **Gate:** On any craft/prepare event, `CraftingEventRouter` finds the recipe by station+ingredients/output, then `RecipeGate.canCraft(player, recipe)` = (unlocked if `require_unlock`) AND (all `requirements` met via `RequirementFactory`-built objects) AND (all `permissions` held). Failure → result nulled (`PrepareItemCraftEvent`/`PrepareSmithingEvent`/`PrepareAnvilEvent`) or `FurnaceStartSmeltEvent` cancelled.
3. **Unlock:** `UnlockManager.evaluate(player)` runs on login + on requirement-relevant state-change events (circle-up, research complete, spell discovery, first craft attempt). When a recipe's requirements transition to met, it persists `unlockedRecipes` → fires `RecipeUnlockedEvent` → notification.
4. **Display:** `RecipeBookMenu` renders from registry indexes + `UnlockManager` state; locked recipes show requirement descriptions + progress bars, hidden if `hidden_when_locked`.
5. **Reload:** `/alkatraz reload` → `RecipeRegistry.reload()` → unregister old Bukkit recipes (`Bukkit.removeRecipe`), clear maps, re-run loader, re-register.

---

## 2. Class / Interface List

### 2.1 Centralized requirement engine (modify existing files)

| Type | Location | Responsibility |
|---|---|---|
| `Requirement` (interface) | `core/.../configuration/requirement/Requirement.java` — **MODIFY** | Add `default int getProgress(Player)` (100 when met, else 0; overridden by numeric types for progress bars) and `default String getDescription(Player)` (falls back to `getDescription()`). Existing `isMet`/`getDescription` unchanged → all current implementers keep working. |
| `RequirementFactory` | `core/.../configuration/requirement/RequirementFactory.java` — **MODIFY** | Stays the hub. Add types: `xp_level` (vanilla `player.getLevel()`), `playtime` (vanilla `Statistic.PLAY_ONE_MINUTE` → minutes), `world` (`worlds` list), `papi` (Phase 4; PlaceholderAPI boolean expression, unmet if PAPI absent), `recipe_unlocked` (Phase 1; checks `UnlockManager`). Add `public static void register(String type, Builder)` so third-party plugins + future core types register without modifying the factory. |
| `MagicItemRecipeManager` | `core/.../items/magic/recipe/MagicItemRecipeManager.java` — **REWORK** | Delete the private hard-coded `parseRequirement`/`parseRequirementFromMap` switches; route all recipe requirement parsing through `RequirementFactory.create(null, section)` + a new `createFromMap` helper (same pattern `ProgressionRequirementRegistry` already uses). Accept **both** list-of-maps and section forms (engraving compat). |
| `RecipeCraftListener` | `core/.../items/magic/listener/RecipeCraftListener.java` — **REWORK** | Imbue handling stays. Magic-item gating logic moves into the adapter framework (§2.2); remove leftover `logInfo` debug calls. |

### 2.2 New core recipe engine (new files, in existing `items/magic/recipe/` package)

| Type | Responsibility |
|---|---|
| `RecipeType` (enum) | `SHAPED, SHAPELESS, FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE, BREWING, SMITHING, STONECUTTER, ANVIL, CUSTOM`. |
| `AlkatrazRecipe` (final class, replaces `RecipeData` record) | Key, type, result ItemStack, result amount, per-type payload (shape/ingredients, single input, smithing base+addition, brewing input+ingredient, anvil base+addition), `experience`, `cookingTime`, `List<Requirement> requirements`, `List<String> permissions`, `boolean hiddenWhenLocked`, `String unlockMessage`, display metadata, `overrideVanilla` flag. |
| `Ingredient` (interface) + impls | `MaterialIngredient`, `ExactItemIngredient` (PDC-aware magic item match, not just `ExactChoice`), `TypeIngredient` (item_types.yml categories like `sword`), `TagIngredient` (Bukkit `Tag`). API: `RecipeChoice toChoice()` (for native recipes), `boolean matches(ItemStack)` (for event interception), `String describe()`. |
| `RecipeRegistry` | Replaces `MagicItemRecipeManager.RECIPES` storage. Indexes: `BY_KEY`, `BY_OUTPUT_MATERIAL`, `BY_STATION`, `BY_INGREDIENT`. `reload()`, `unregisterBukkitRecipes()`, `registerNativeRecipes()`, lookup helpers, `getRequirements(key)` retained for compat. |
| `RecipeLoader` | `ConfigurationSection`/map → `AlkatrazRecipe`. Backward-compatible: existing files (`definition:` + `shape:` + `ingredients:` + `requirements:`) parse to `type: SHAPED` with result from `ITEM_DEFINITIONS`; new fields (`type:`, `result:`, `experience:`, `brewing:`, `smithing:`, `anvil:`, `permissions:`, `hidden_when_locked:`, `unlock:`) supported. `recipe_schema_version` key for future migration. |
| `CraftingTypeAdapter` (abstract) | `RecipeType type()`; `void registerNative(AlkatrazRecipe)`; gating hooks `onPrepare(PrepareItemCraftEvent)`, `onFurnaceStart(FurnaceStartSmeltEvent)`, `onBrew(BrewEvent)`, `onAnvil(PrepareAnvilEvent)`, `onSmith(PrepareSmithingEvent)`. |
| `ShapedCraftingAdapter`, `ShapelessCraftingAdapter` | Build `ShapedRecipe`/`ShapelessRecipe` from `Ingredient.toChoice()`; gate via `PrepareItemCraftEvent` when recipe has requirements/permissions/unlock. |
| `CookingCraftingAdapter` | One class, 4 registrations (furnace/blast/smoker/campfire) via `FurnaceRecipe` subclasses; gate via `FurnaceStartSmeltEvent` cancel. |
| `SmithingCraftingAdapter` | `SmithingRecipe` registration (1.19+ transform/trim variants noted); gate via `PrepareSmithingEvent` result null. |
| `StonecutterCraftingAdapter` | `StonecuttingRecipe`; gate via stonecutter prepare/select event (result null). |
| `BrewingCraftingAdapter` | No native recipe. `BrewEvent` intercept: match input potion + ingredient → apply result; gate by cancelling (stand contents unmodified). |
| `AnvilCraftingAdapter` | No native recipe. `PrepareAnvilEvent` intercept: match base+addition (repair with magic item / combine), gate via `event.setResult(null)`. |
| `CustomCraftingAdapter` (abstract, hook) | Public extension point for third-party GUI stations: `RecipeType type()`, `void handleClick(InventoryClickEvent, Player)`; routing wired into `CraftingEventRouter`. Docs only — no built-in custom GUI. |
| `CraftingEventRouter` | One registered `Listener`; routes `PrepareItemCraftEvent`, `FurnaceStartSmeltEvent`, `BrewEvent`, `PrepareAnvilEvent`, `PrepareSmithingEvent`, `InventoryClickEvent` to matching adapters via `BY_STATION`. Replaces most of `RecipeCraftListener` (imbue path stays in place). |
| `RecipeGate` | `boolean canCraft(Player, AlkatrazRecipe)`, `List<Requirement> getUnmet(...)`, permission check; shared by all adapters and the GUI. |

### 2.3 Unlock & persistence

| Type | Location | Responsibility |
|---|---|---|
| `UnlockStore` (interface) | `core/.../items/magic/recipe/unlock/` — NEW | `Set<String> loadUnlocked(UUID)`, `saveUnlocked(UUID, Set<String>)`, `setUnlocked(UUID, String key, boolean)`. SQLite/MySQL impls documented as future. |
| `ProfileUnlockStore` | same package — NEW | YAML impl delegating to `MagicProfile`'s new stringSet. |
| `MagicProfile` | `core/.../playerdata/profiles/implementation/MagicProfile.java` — **MODIFY** | Add `stringSetStat("unlockedRecipes")` + `hasUnlockedRecipe(key)` / `setRecipeUnlocked(key, boolean)` — exact `discoveredSpells` pattern. Persistence is automatic via `ProfilePersistence`. |
| `UnlockManager` | `core/.../items/magic/recipe/unlock/UnlockManager.java` — NEW | `evaluate(player)` (recompute + fire unlocks), `unlock/lock/hasUnlocked`, per-player `UnlockCache` (ConcurrentHashMap) with `invalidate(player)`; notification dispatch via Utils + config channels. |
| `RecipeUnlockedEvent` | `core/.../events/RecipeUnlockedEvent.java` — NEW | `extends Event` (not cancellable), fields: player, `NamespacedKey recipe`, cause enum (`AUTO/ADMIN/COMMAND`). Copy the `CastEvent` static `HandlerList` pattern exactly. |

### 2.4 Public API (`api/` module)

| Type | Responsibility |
|---|---|
| `RecipeManagerAPI` (interface) | `Optional<AlkatrazRecipe> getRecipe(NamespacedKey)`, `Collection<AlkatrazRecipe> getRecipes()`, `boolean isUnlocked(Player, NamespacedKey)`, `void unlock/lock(Player, NamespacedKey)`, `boolean canCraft(Player, NamespacedKey)`, `void registerRequirementType(String, RequirementFactory.Builder)`, `void registerCustomAdapter(CustomCraftingAdapter)`, `void reload()`. |
| `RecipeRequirement` (interface) | Third-party-facing requirement contract (`isMet(Player)`, `getDescription()`, default `getProgress`); adapts into core `Requirement` at registration. |
| Wiring | `Alkatraz.getRecipeManager()` returns a core implementation; `RecipeUnlockedEvent` is public and listenable by other plugins. |

### 2.5 Player-facing

| Type | Location | Responsibility |
|---|---|---|
| `RecipeBookMenu` | `core/.../gui/implementation/RecipeBookMenu.java` — **MODIFY** | Filter tab (All/Locked/Unlocked), lock icon overlay, hidden-when-locked exclusion, requirement + progress lore via `req.getDescription(player)` / `req.getProgress(player)`. |
| `RecipeDetailMenu` | same package — **MODIFY** | Requirement checklist (✔/✘ via `progression.requirement_met`/`unmet` pattern), progress bars, Unlock button when requirements met but not unlocked. |
| `RecipesCommand` | `core/.../commands/RecipesCommand.java` — **MODIFY** | Subcommands: `/recipes` (book), `/recipes unlock\|lock <id> [player]`, `/recipes reload`, `/recipes give <id> [player]` (unlock alias), `/recipes check <player> <id>`. Mirror `AlkatrazCommand`'s switch+permission pattern. New `Permission` enum entries: `COMMAND_RECIPE_UNLOCK("alkatraz.recipe.unlock")`, `COMMAND_RECIPE_RELOAD("alkatraz.recipe.reload")`, `COMMAND_RECIPE_GIVE`, `COMMAND_RECIPE_CHECK`. |
| `AlkatrazCommand.handleReload` | — **MODIFY** | Add `RecipeRegistry.reload()` so recipe/definition changes take effect without restart (fixes the orphaned `MagicItemBootstrap.reload()`). |
| `Alkatraz.onEnable` | — **MODIFY** | Register `CraftingEventRouter` listener + `UnlockManager.initialize()`. |

---

## 3. Config File Schemas

### 3.1 Unified recipe schema (`magic/recipes/*.yml`)

```yaml
id: alkatraz:runic_wand            # default namespace: alkatraz
type: shaped                       # shaped|shapeless|furnace|blast_furnace|smoker|campfire|brewing|smithing|stonecutter|anvil
recipe_schema_version: 1           # reserved for future migration
result:                            # optional: omitted for legacy `definition:` form
  item: alkatraz:runic_wand        # item definition key, or material name, or inline {material:, display_name:, lore:}
  amount: 1
shape:                             # shaped only (legacy 3x3)
  - "D S"
  - " M "
  - " B "
ingredients:                       # shaped: char→ingredient; shapeless: list; smithing/anvil: base/addition; cooking: input
  D: DIAMOND                      # material name (MaterialCompat) OR
  M: MAGIC_STONE                  # magic item key → ExactItemIngredient OR
  S: type:sword                   # item_types.yml category → TypeIngredient OR
  B: tag:logs                     # Bukkit tag → TagIngredient
experience: 7.5                    # cooking family (default 0)
cooking_time: 200                  # cooking family, ticks (default 200)
brewing:                           # type: brewing
  input: AWKWARD_POTION
  ingredient: BLAZE_POWDER
smithing:                          # type: smithing
  base: DIAMOND_SWORD
  addition: NETHERITE_INGOT
requirements:                      # ANY RequirementFactory type (centralized)
  - type: number_stat
    stat: circleLevel
    minimum: 5
    description: "Requires Circle Level 5"
  - type: recipe_unlocked          # chain gating
    recipe: alkatraz:wooden_wand
    description: "Craft the Wooden Wand first"
permissions:
  - alkatraz.craft.runic_wand
hidden_when_locked: false
override_vanilla: false            # true → Bukkit.removeRecipe(vanillaKey) on register
unlock:
  message: "&dYou unlocked the Runic Wand recipe!"
  sound: ENTITY_PLAYER_LEVELUP
```

**Backward compatibility:** all existing files (e.g. `wooden_wand.yml` with `definition:` + `shape:` + `ingredients:`) parse unchanged — `type` defaults to `shaped`, `result` derives from `definition`. Engraving `recipe:` sections keep working through the same `RequirementFactory` path.

### 3.2 Unlock persistence (per-player YAML — no file changes needed)

`playerdata/<uuid>/magic.yml` gains a line via the existing stats serializer:

```yaml
stats:
  stringSets:
    discoveredSpells: [magic_missile]
    unlockedRecipes: [alkatraz:wooden_wand, alkatraz:runic_wand]
```

### 3.3 Config additions (`config.yml`)

```yaml
recipes:
  recipe_book:
    show_locked: true          # false → hide all locked entries entirely
    locked_item: BARRIER       # icon for locked rows (texturepack-aware)
  unlock_notifications:
    chat: true
    title: true
    actionbar: false
    sound: true
    particles: true
  data_store: yaml             # yaml (now) | sqlite | mysql (future)
```

### 3.4 New lang keys (`lang/english.lang`)

```
recipes.locked = &cLocked
recipes.unlocked = &aUnlocked
recipes.unlock_button = &eClick to Unlock
recipes.filter_all = &fAll
recipes.filter_locked = &cLocked Only
recipes.filter_unlocked = &aUnlocked Only
recipes.progress_header = &7&m---&r &bProgress &7&m---
recipes.unlock_title = &dRecipe Unlocked!
recipes.unlock_chat = &dYou unlocked the &f%recipe% &drecipe!
recipes.already_unlocked = &cThis recipe is already unlocked.
recipes.not_found = &cUnknown recipe: &f%id%
recipes.commands.unlock = &aUnlocked &f%id% &afor %player%.
recipes.commands.reload = &aRecipes reloaded (&f%count%&a).
```

---

## 4. Phased Build Roadmap

Each phase ends with a testable deliverable. **Commits happen once at the very end** per the project's commit strategy (single large commit after final review).

### Phase 0 — Centralization groundwork
- [ ] **T1. Unify requirement parsing.** Route `MagicItemRecipeManager`'s requirement parsing through `RequirementFactory`; accept list-of-maps and section forms. Add `getProgress`/`getDescription(Player)` defaults to `Requirement`.
- [ ] **T2. Add requirement types.** `xp_level`, `playtime`, `world` in `RequirementFactory` (all no-dependency). `papi` deferred to Phase 4.
- [ ] **T3. Fix reload.** `RecipeRegistry.reload()` (clear maps, `Bukkit.removeRecipe` on old keys, re-run loader) + wire into `/alkatraz reload` + `MagicItemBootstrap.reload()`.

### Phase 1 — Unlock & persistence
- [ ] **T4. `MagicProfile.unlockedRecipes`** stringSet + helpers (mirrors `discoveredSpells`).
- [ ] **T5. `UnlockStore` + `ProfileUnlockStore` + `UnlockManager`** (cache, evaluate, notifications).
- [ ] **T6. `RecipeUnlockedEvent`** (HandlerList pattern) + core `RecipeManagerAPI` skeleton (query/unlock/lock/register hooks).
- [ ] **T7. `recipe_unlocked` requirement type** (chain gating).

### Phase 2 — Recipe model + multi-station crafting
- [ ] **T8. `AlkatrazRecipe` + `RecipeType` + `Ingredient`** abstraction; `RecipeRegistry` indexes; `RecipeLoader` (backward-compatible + new schema).
- [ ] **T9. `CraftingTypeAdapter` framework + `CraftingEventRouter`**; shaped/shapeless adapters; craft gating through `RecipeGate`; retire hard-coded `RecipeCraftListener` gating (imbue path preserved).
- [ ] **T10. Cooking adapters** (furnace/blast/smoker/campfire) + `FurnaceStartSmeltEvent` gating.
- [ ] **T11. Smithing + stonecutter** adapters.
- [ ] **T12. Brewing adapter** (`BrewEvent`).
- [ ] **T13. Anvil adapter** (`PrepareAnvilEvent`).
- [x] **T14. `CustomCraftingAdapter` hook** + docs.

### Phase 3 — Player-facing UX
- [x] **T15. RecipeBookMenu** locked/unlocked filter, lock icons, hidden-when-locked, progress bars in lore.
- [x] **T16. RecipeDetailMenu** requirement checklist + Unlock button.
- [x] **T17. Notification system** (config-driven channels: chat/title/actionbar/sound/particles).
- [x] **T18. `/recipes` subcommands** (`unlock`/`lock`/`reload`/`give`/`check`) + permissions.

### Phase 4 — API, robustness, docs
- [x] **T19. Complete `RecipeManagerAPI`** + third-party registration docs + `api/` javadocs.
- [x] **T20. Performance & conflict handling**: verify indexes, UnlockCache invalidation events (circle-up, research complete, spell discovery, login), `override_vanilla`, startup conflict warnings.
- [x] **T21. Testing plan + migration doc + wiki update.**

---

## 5. Testing Plan

- **Unit (JUnit, profile mocked or in-memory `MagicProfile`):**
  - `RequirementFactory` parsing of every type (list-of-maps AND section forms); unknown type throws.
  - New types: `xp_level`/`playtime`/`world`/`recipe_unlocked` met/unmet + progress values.
  - `RecipeLoader`: legacy shaped file parses identically; new types produce correct payloads; bad files fail loudly with key+line.
  - `UnlockManager`: evaluate → unlock only on requirement transition; unlock persists; admin lock removes.
  - `RecipeGate`: requirements AND permissions AND unlock-state semantics.
- **Integration (Paper test harness / manual on a test server):**
  - Each station: craft valid recipe; craft locked recipe (result nulled / smelt cancelled / brew not applied / anvil result null).
  - Reload: change a recipe file → `/alkatraz reload` → new recipe active, old Bukkit key removed.
  - Recipe book: locked shows requirements, hidden-when-locked hides, unlock button works, notification fires once.
  - Imbuing still works after `RecipeCraftListener` rework.

## 6. Migration & Versioning

- `recipe_schema_version: 1` in the schema; loader runs migration transforms before parsing when version < current (no-op initially).
- Legacy engraving section-style requirements and list-of-maps item requirements both accepted in Phase 0, then a codemod doc + deprecated-warning to standardize on list-of-maps.
- Unlock state has no migration (new stringSet, empty default).
