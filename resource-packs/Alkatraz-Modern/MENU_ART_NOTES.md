# Alkatraz menu art — notes

Every menu now has a title-font glyph (same technique as the Spells example: glyph at the GUI's top-left, shifted by `\uF808`). Glyph height depends on menu size: **27 slots = 83px, 45 slots = 119px, 54 slots = 137px**; font `ascent` is 13 for all. Not tested in-game.

## 1. Title codes

`texturepack_menu_titles.yml` has the full `ascii_codes.menu_titles` block. `TexturePackManager.loadAsciiCodes()` currently hard-codes six keys; change it to read every key under `menu_titles`, then have each menu use its key when `resource_pack.enabled` is true.

| Key | Code | Slots | Baked title | Menu |
|---|---|---|---|---|
| `spells` | `\uF808\uF001` | 54 | SPELLS | SpellsMenu |
| `equipment` | `\uF808\uF002` | 45 | EQUIPMENT | EquipmentMenu |
| `research` | `\uF808\uF003` | 54 | RESEARCH LIBRARY | ResearchGraphMenu |
| `recipes` | `\uF808\uF004` | 45 | RECIPE MANAGER | RecipeCategoryMenu |
| `stats` | `\uF808\uF005` | 54 | STATS | StatsMenu (title was '<player>'s Stats') |
| `grimoire` | `\uF808\uF006` | 54 | GRIMOIRE | GrimoirePageMenu |
| `arcane_choice` | `\uF808\uF010` | 27 | ARCANE TABLE | EnchantingTableChoiceMenu (2 choices) |
| `arcane_hub` | `\uF808\uF011` | 27 | ARCANE TABLE | WandTableSelectionMenu (3 choices) |
| `progression` | `\uF808\uF012` | 27 | PROGRESSION | ProgressionMenu (circle track I-IX) |
| `circle_confirm` | `\uF808\uF013` | 27 | CONFIRM CIRCLE UP | CircleUpConfirmationMenu |
| `warp_points` | `\uF808\uF014` | 27 | WARP POINTS | WarpPointsMenu |
| `warp_destination` | `\uF808\uF015` | 27 | WARP DESTINATION | WarpDestinationMenu (title was "Warp - Destination") |
| `engraving_table` | `\uF808\uF016` | 45 | ENGRAVING TABLE | EngravingTableMenu |
| `confirm_engraving` | `\uF808\uF017` | 45 | CONFIRM ENGRAVING | ConfirmEngravingMenu |
| `recipe_create` | `\uF808\uF018` | 45 | CREATE RECIPE | RecipeCreateMenu |
| `recipe_delete` | `\uF808\uF019` | 45 | DELETE RECIPE? | RecipeDeleteConfirmMenu |
| `recipe_shape` | `\uF808\uF01A` | 45 | RECIPE SHAPE | RecipeShapeSubMenu |
| `recipe_shapeless` | `\uF808\uF01B` | 45 | INGREDIENTS | RecipeShapelessIngredientsSubMenu |
| `recipe_requirements` | `\uF808\uF01C` | 45 | REQUIREMENTS | RecipeRequirementsSubMenu |
| `recipe_permissions` | `\uF808\uF01D` | 45 | PERMISSIONS | RecipePermissionsSubMenu |
| `item_detail` | `\uF808\uF01E` | 45 | EDIT ITEM | ItemDetailMenu (title was "Edit: <defKey>") |
| `lore` | `\uF808\uF01F` | 45 | EDIT LORE | LoreSubMenu |
| `components` | `\uF808\uF020` | 45 | EDIT COMPONENTS | ComponentsSubMenu |
| `attributes` | `\uF808\uF021` | 45 | EDIT ATTRIBUTES | AttributesSubMenu (title was "Edit: attributes") |
| `recipe_sub` | `\uF808\uF022` | 45 | EDIT RECIPE | RecipeSubMenu (editor) |
| `requirements_editor` | `\uF808\uF023` | 45 | EDIT REQUIREMENTS | RequirementsSubMenu (editor) |
| `hotbar_config` | `\uF808\uF024` | 54 | CONFIGURE SPELL HOTBAR | HotbarSpellSelectionMenu |
| `spell_options` | `\uF808\uF025` | 54 | SPELL OPTIONS | SpellOptionsMenu (title was "<spell> - Options") |
| `spell_option_values` | `\uF808\uF026` | 54 | SELECT VALUE | SpellOptionValuesMenu (title was "<option> - Select Value") |
| `grimoire_select` | `\uF808\uF027` | 54 | SELECT A SPELL | GrimoireSpellSelectMenu |
| `item_editor` | `\uF808\uF028` | 54 | ITEM EDITOR | ItemEditorMenu |
| `pooled_3` | `\uF808\uF029` | 54 | CONFIGURE SLOTS | PooledSlotSelectionMenu, 3 header slots (buff / debuff) |
| `pooled_2` | `\uF808\uF02A` | 54 | CONFIGURE SLOTS | PooledSlotSelectionMenu, 2 header slots (apotheosis) |
| `trigger_select` | `\uF808\uF02B` | 54 | SELECT TRIGGER TYPE | TriggerSelectionMenu |
| `research_entry` | `\uF808\uF02C` | 54 | RESEARCH ENTRY | ResearchEntryMenu (title was "Research: <node>") |
| `disguise` | `\uF808\uF02D` | 54 | DISGUISE TARGET | Disguise spell GUI (title was "Disguise Target") |
| `recipe_list` | `\uF808\uF02E` | 54 | RECIPE MANAGER | RecipeListMenu |
| `recipe_detail` | `\uF808\uF02F` | 54 | RECIPE DETAILS | RecipeDetailMenu |
| `recipe_edit` | `\uF808\uF030` | 54 | EDIT RECIPE | RecipeEditMenu |

## 2. Panes must be CMD 4004 for the art to show

Item icons draw over the glyph, so any full-slot pane hides it. 4004 is now fully transparent (the old tile is in `optional/`). These still use other panes and will tint or cover the art until switched to the registry blank (4004):

- `§7`-named `GRAY_STAINED_GLASS_PANE` CMD 0 backgrounds: RecipeEditMenu, ItemDetailMenu, LoreSubMenu, ComponentsSubMenu, AttributesSubMenu, RecipeSubMenu, RequirementsSubMenu, TriggerSelectionMenu (border rows).
- GrimoirePageMenu: `BROWN` cover panes and `BLACK` spine panes (CMD 0). The glyph draws the book cover, pages and spine itself.
- Empty-slot placeholders (equipment 13/20/24/31, hotbar headers 0-7, engraving strip) can stay as CMD 0 glass if you like a glass-over-socket look, or move to 4004 for bare sockets.

## 3. Icons that are still plain vanilla

These need a CMD in the plugin to pick up existing art: raw `BARRIER` back buttons -> `4003`; raw `LIME_CONCRETE`/`RED_CONCRETE` confirm/cancel -> `4007`/`4008`. Spells menu extras from earlier: hotbar button `4015`, locked spell `4016`. Circle panes, stats icons, warp states, trigger hooks, engraving strip and node icons are not redrawn yet.

## 4. Titles that lost dynamic text

A title glyph replaces the whole title, so these no longer show their variable part (the header item's tooltip still does): Stats (player name), Spell Options (spell), Select Value (option id), Edit Item (item key), Edit Attributes (section name), Research Entry (node name), Warp Destination (hyphen dropped). Leave those menus on the text title if you want the names.

## 5. Plugin slot positions that make some menus off-centre

The art follows `menus.md` exactly, so these stay lopsided: Warp Points / Destination (slots 10-14, centred would be 11-15), Hotbar config headers (0-7), Confirm Engraving (31 confirm / 33 cancel), Recipe Details (40-42), Recipe Edit row 2 (19-23). Moving them to centred slots would let the art be symmetric too.

## 6. Shared look

Gold = navigation / structure, green/red beds = confirm/cancel/delete, cyan beds = read-only info (stats), recessed sockets = item slots, gold pedestals = headers and results. Every centred element is even-width so it sits on the 8|9 pixel boundary of its slot and x=87.5 of the GUI.
