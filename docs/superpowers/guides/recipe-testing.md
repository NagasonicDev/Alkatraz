# Recipe Manager — Testing Plan

## Scope

Covers the recipe manager subsystem: `RecipeLoader`, `RecipeRegistry`, `RequirementFactory`,
`UnlockManager`, `RecipeGate`, the crafting adapters (shaped, shapeless, cooking, smithing,
stonecutter, brewing, anvil), the recipe book menus and notifications.

## Status

There is no JUnit harness in this repository. Verification to date was static: `mvn compile` on both
modules plus independent code review for every task. This document records the test targets and the
manual/integration checklist to run on a live server.

## Unit test targets

Planned as JUnit with a mocked profile or an in-memory `MagicProfile`:

| Target | Cases |
|---|---|
| `RequirementFactory` | Parses every registered type from BOTH the list-of-maps form and the section form; unknown type throws. |
| New requirement types | `xp_level` / `playtime` / `world` / `recipe_unlocked`: met, unmet, and progress values. |
| `RecipeLoader` | A legacy shaped file parses identically to before; new-type files produce correct payloads; malformed files fail loudly with the recipe key + line. |
| `UnlockManager` | `evaluate` unlocks only on a requirement transition; `unlock` persists; admin `lock` removes the unlock. |
| `RecipeGate` | `canCraft` semantics across requirements AND permissions AND unlock state. |

## Integration checklist

Run on a Paper test server after `/alkatraz reload`:

- **Each station** (crafting table, furnace family, smithing table, stonecutter, brewing stand,
  anvil): craft a valid recipe; attempt a locked recipe.
- **Reload**: change a recipe file → `/alkatraz reload` → the new recipe is active and the old Bukkit
  key is removed.
- **Recipe book**: a locked recipe shows its requirements and progress bars; `hidden_when_locked`
  recipes are hidden; the Unlock button works when all requirements are met; the unlock notification
  fires exactly once.
- **Imbuing** still works after the `RecipeCraftListener` rework.

## Known API-constrained behaviors (spigot-api 1.19)

These are expected outcomes on the current API version, not bugs:

| Station | Behavior |
|---|---|
| Furnace / smoker / blast furnace / campfire | Cooking recipes WITH requirements are never registered as native recipes (no per-player smelting gating event). They are unobtainable until a Paper-API upgrade. Requirement-less cooking recipes work normally. |
| Brewing stand | Gated brewing recipes cancel the entire brew. |
| Stonecutter | Gating cancels the click on the result slot; the result preview may still render. |
| Anvil | Gating is per-player (PrepareAnvilEvent); the allowed result is applied via `event.setResult`. |

## Recipe schema files

Example files live under `magic/recipes/` in the plugin data folder. See
[Recipe Migration](recipe-migration.md) for the schema and versioning details.
