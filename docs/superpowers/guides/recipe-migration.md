# Recipe Manager — Migration & Versioning

## Schema versioning

Recipe files may declare `recipe_schema_version`. The value is reserved for future migration; the
loader does not yet run version-gated transforms (no-op initially, per the plan). Recipes without the
key default to the current supported format.

## Backward compatibility

Legacy recipe files are supported without edits. A legacy file (using `definition:` + `shape:` +
`ingredients:` + `requirements:`) parses to a SHAPED recipe whose result is resolved from
`ITEM_DEFINITIONS`.

New fields supported by the loader: `type:` (shaped, shapeless, cooking/furnace/smoker/blast_furnace/
campfire, smithing, stonecutter, brewing, anvil, custom), `result:`, `experience:`, `brewing:`,
`smithing:`, `anvil:`, `permissions:`, `hidden_when_locked:`, `unlock:`, `override_vanilla:`.

`override_vanilla: true` removes any conflicting Bukkit recipe with the same key before native
registration; without it, a startup warning is logged when a key conflict is detected.

## New-schema summary

| Field | Purpose |
|---|---|
| `type` | Station type; `custom` recipes are never registered with Bukkit. |
| `requirements` | List-of-maps or legacy section form; supports all registered requirement types. |
| `unlock.message` | Per-recipe unlock message (falls back to `recipes.unlock_chat`). |
| `override_vanilla` | Replace an existing Bukkit recipe with the same key. |

Requirements are standardized on the list-of-maps form; a codemod document is planned to convert
legacy section-style requirements, with a deprecated-warning in the meantime.

## Configuration merge

On startup `ConfigUpdater` merges the shipped `config.yml` into the server copy. New sections such as
`recipes.unlock_notifications` are written automatically for existing installs.

## Unlock state

Unlock state has no migration: it is a new per-player string set that defaults to empty.
