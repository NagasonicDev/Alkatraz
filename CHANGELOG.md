# Alkatraz 0.9.1 — The Forging Update

> *Craft, imbue, and engrave. Forge the perfect arsenal.*

0.9.1 turns Alkatraz into a full magic RPG forging suite. A brand-new **Recipe Manager** lets you gate powerful gear behind unlock progression at every station, **Imbuing** converts vanilla weapons and armor into magic items with Magic Stones, and the **Engraving Table** lets you customize equipment with 28 trigger-based runes. Server owners get **PlaceholderAPI**, **localization**, and a configurable **loot & villager trading** system — while players benefit from **stat-driven spell power**, **wind-up cast animations**, and **hotbar safety** that survives even a server crash.

---

## Table of Contents

1. [Full Changelog](#-full-changelog)
2. [Recipe Manager & Recipe Book](#-recipe-manager--recipe-book)
3. [Imbuing](#-imbuing)
4. [Engraving Runes](#-engraving-runes)
5. [Stats, Spell Power & Magic Damage](#-stats-spell-power--magic-damage)
6. [PlaceholderAPI](#-placeholderapi)
7. [Localization](#-localization)
8. [Loot, Trading & Starter Content](#-loot-trading--starter-content)
9. [Hotbar Safety & Spell Hotbar](#-hotbar-safety--spell-hotbar)
10. [Public API](#-public-api)

---

## 📋 Full Changelog

### Added
- **Recipe Manager & Recipe Book** — a fully configurable recipe system covering every crafting station, browsable and editable in-game via `/recipes`
- **Recipe unlock progression** — recipes can be locked behind requirements (stats, permissions, research, spell mastery, playtime, world, and more) and unlocked in-game
- **In-game Recipe Editor** — create, edit, duplicate, and delete recipes from the GUI (admin permissions)
- **Imbuing system** — convert vanilla tools, weapons, and armor into imbued magic items using Magic Stones; 5 tiers grant bonus max mana
- **Magic Stones** — new currency item dropped by magic mobs and sold by librarian villagers
- **Engraving Table** — apply runes to magic items with a trigger selection menu and a confirmation screen showing mana cost and success/failure chances
- **Engraving triggers** — 70+ trigger events across combat, movement, environment, inventory, gathering, player state, social, and WorldGuard region events
- **Passive runes** — always-active runes that grant attributes while installed
- **Magic Damage attribute** — boosts spell damage and explosions
- **Stat-driven spell scaling** — spell power and cast time now scale from player profile stats
- **Equipment that raises your max spell circle** — high-tier gear lets you cast spells above your circle
- **Unified attribute pipeline** — stat points and equipment bonuses are routed through one system
- **PlaceholderAPI expansion** — 60+ placeholders for stats, elements, spells, research, hotbar, and leaderboards
- **Localization system** — `.lang` files with YAML string overrides
- **Configurable loot system** — Magic Stone drops from magic mobs with Looting scaling
- **Villager spellbook trading** — librarian villagers sell tiered spellbooks
- **Starter spell** for new players
- **Grimoire lectern UI** and improved spellbook display
- **Wind-up cast animations** for Meteor Shower, Tsunami, Fissure, Tornado, Shadow Realm, and Radiance
- **Hotbar inventory snapshot persistence** — survives server restarts and crashes
- **`/summon` command** with count and location targeting; `/give` gained a count argument
- **Self-target potion option** for potion spells
- **Item Editor** now opens directly from recipe results
- **Public API module** — spell registry, profile provider, GUI item registry, and full Javadoc for developers
- **Magic mob data persistence** — mob definitions stored in the persistent data container

### Changed
- **Enchanting tables** now open a choice menu: Arcane Table or vanilla enchanting
- **Recipes unlock in-game** — a periodic sweep and live stat refresh unlock eligible recipes without a rejoin
- **Wand cast codes** moved out of item NBT into memory
- **Equipment system redesign** — elemental accessory variants and set bonuses
- **Magic items** got a unique identity and centralized lore formatting; imbue naming reworked
- **Magic mobs** now summon correctly on Minecraft 1.19.1 and 1.19.2
- **Particles** updated for 1.20.5+ enum renames
- **Research menu** panning fixed for diagonal movement
- **Imbuing** prevents imbuing items that are already imbued
- **Magic item stack handling** now guards against air items and missing item meta

### Fixed
- **Recipe editor menus** — lore line breaks, drag-and-drop, and right-click-to-clear
- **Random spellbook item deletion**
- **NBT primitive null error**
- **Hotbar slot lock behavior** and death inventory handling
- **Drop events** being cancelled after magic item component handling
- **Research graph typos** and missing parents in locked lore
- **Lang placeholder keys** no longer percent-wrapped
- **Reload** correctly waits for recipe reload completion

### Removed
- Deprecated **archmage ring** and **arcane artifact** items
- Duplicate core classes (rewired to the API module)
- Generated docs from the repository

---

## 🍳 Recipe Manager & Recipe Book

<details>
<summary><b>Click to expand</b> — configurable crafting, unlock progression, and an in-game recipe editor</summary>

The headline feature of 0.9.1. Every craftable magic item now lives in a **config-driven recipe system** covering **all 11 recipe types** across **9 categories** — from crafting tables and furnaces to brewing stands, smithing tables, stonecutters, and anvils, plus custom recipe types.

### How it works

1. Run **`/recipes`** to open the Recipe Manager
2. Browse the 9 categories — Crafting, Furnace, Blast Furnace, Smoker, Campfire, Brewing, Smithing, Stonecutter, and Other
3. **Search** by recipe ID, display name, or result item, and **sort** alphabetically, by result, by ID, or by recently edited
4. Open a recipe to see its ingredients, result, and requirements

### Unlock Progression

Recipes with requirements are **locked** by default. Players must meet the requirements and then click **Unlock** to permanently unlock the recipe. Requirements can combine stats, permissions, research, spell mastery, playtime, world, and more — each one shows a progress bar so players know exactly what's left.

- Requirements are evaluated on join and whenever a crafting menu is opened
- A **periodic sweep** plus live stat refresh unlocks eligible recipes in-game without a rejoin
- Unlock notifications can fire chat, title, actionbar, sound, and particle channels (configurable)
- Admins can force unlock/lock with `/recipes unlock`, `/recipes lock`, `/recipes give`, `/recipes check`, and `/recipes reload`

### In-Game Recipe Editor

With the right permissions, admins can **create, edit, duplicate, and delete** recipes entirely in-game:
- Change result, amount, display name, shape/ingredients, station inputs, experience, cooking time, requirements, permissions, and unlock message
- Changes write straight to `plugins/Alkatraz/magic/recipes/<key>.yml` and reload the registry

> See the [Recipes](https://github.com/NagasonicDev/Alkatraz/wiki/Recipes) page for the full guide.

</details>

---

## ⚔️ Imbuing

<details>
<summary><b>Click to expand</b> — turn vanilla gear into magic items with Magic Stones</summary>

Imbuing converts a **vanilla tool, weapon, or armor piece** into a **magic item** by infusing it with **Magic Stones** in a crafting grid.

### How to Imbue

1. Obtain **Magic Stones** — dropped by magic mobs (configurable chance + Looting bonus) or bought from librarian villagers
2. Place **1 vanilla item + N Magic Stones** in a crafting grid (shapeless recipe)
3. The result is the same item, now **imbued** — it keeps its material and durability, gains an "Imbued" prefix, and becomes usable in the Engraving Table

The stone cost depends on the item's tier:

| Tier | Materials | Stone Cost |
|------|-----------|------------|
| 1 | Wooden, Leather, Golden | 1 |
| 2 | Stone, Chainmail | 2 |
| 3 | Iron, Bow | 3 |
| 4 | Diamond, Crossbow, Trident | 4 |
| 5 | Netherite, Turtle Helmet | 5 |

Each tier grants **bonus max mana** when equipped (Tier 1: +10 up to Tier 5: +100) and lets the item hold an engraving. Stone costs are configurable under `imbuing.stone_costs` in `config.yml`.

> Imbue recipes are plain Bukkit recipes and do not appear in the Recipe Manager — real imbuing happens in the crafting grid.

See [Equipment and Items](https://github.com/NagasonicDev/Alkatraz/wiki/Equipment-and-Items) for details.

</details>

---

## 🔩 Engraving Runes

<details>
<summary><b>Click to expand</b> — 28 trigger-based and passive runes applied at the Engraving Table</summary>

Customize your equipment with **28 runes** — physical items that are consumed when applied at the **Engraving Table**.

### How to Apply

1. Right-click an **Enchanting Table** and choose **Arcane Table** → **Magic Engineering**
2. Click a **magic item** to select it as the target
3. Click a **rune** from your inventory (type-incompatible runes are rejected)
4. If the rune is **triggered**, pick the trigger that activates it — **passive** runes skip this
5. Review the confirmation screen (target, rune, trigger, mana cost, success chance) and click **Confirm**

Applying costs `100 + (current engravings × 50)` mana with an **80% success chance** — on failure the rune and mana are consumed but the item is unchanged. Each item has a `max_engravings` limit (default 1; higher-tier items up to 2). Runes are crafted at a crafting table using a shaped recipe built around a **Magic Stone**.

### Trigger Events

Runes can trigger on **70+ events**:
- **Combat** — spell cast, melee/ranged kill, critical hit, damage dealt/taken, blocking, projectiles, being targeted
- **Movement** — sprint, swim, glide, fly, jump, land, enter water/lava, climb, teleport, world change, beds
- **Environment** — lightning, explosion, fire, drowning, freezing, fall, void, starvation, rain, thunder
- **Inventory** — use, left-click, swap hands, pick up, drop, consume, durability, repair, enchant
- **Gathering** — break blocks, mine ore, place, fish, shear, buckets
- **Player state** — equip, sneak, held slot, join/quit, death, respawn, level up, XP, heal, low health, mana, spell discovery, research completion
- **Social & region** — chat, commands, villager trades, intervals, day/night, WorldGuard regions

Effects include igniting, potions, damage, healing, explosions, teleporting, sounds, particles, and console commands — all gated by conditions (chance, circle level, mana, cooldowns, and more).

> See the [Engravings](https://github.com/NagasonicDev/Alkatraz/wiki/Engravings) page for the full rune and trigger reference.

</details>

---

## 📈 Stats, Spell Power & Magic Damage

<details>
<summary><b>Click to expand</b> — a unified attribute pipeline for combat scaling</summary>

0.9.1 rewires combat math into a unified **attribute pipeline**:

- **Magic Damage attribute** — increases the damage of spells and spell-caused explosions
- **Spell Power** — now scales from your player profile stats; your **cast time** scales with mastery and stats
- **Max spell circle** — certain equipment raises the maximum circle your wand can cast, letting you reach above your progression
- **Stat points** and **equipment bonuses** flow through the same pipeline, so everything stacks consistently

> See [Stats and Elements](https://github.com/NagasonicDev/Alkatraz/wiki/Stats-and-Elements) and [Progression](https://github.com/NagasonicDev/Alkatraz/wiki/Progression) for the full picture.

</details>

---

## 🔌 PlaceholderAPI

<details>
<summary><b>Click to expand</b> — 60+ placeholders for server owners</summary>

Alkatraz now registers its own **PlaceholderAPI expansion** (`alkatraz`) automatically — no download command needed. Placeholders cover:

- **Stats** — circle, mana, mana regen, spell power, arcane knowledge, research points, stat points, cast state, discovered spells
- **Elements** — affinity and resistance per element, plus magic
- **Spells** — discovered state, name, element, circle, mana cost, cast code, mastery, cooldowns
- **Research** — completed count and per-node state
- **Hotbar** — spell assigned to each slot
- **Leaderboard** — ranked by Arcane Knowledge with configurable refresh interval and entry count

Examples: `%alkatraz_stats_circle_roman%`, `%alkatraz_spells_cooldown_fireball_ready%`, `%alkatraz_leaderboard_top_1_name%`

> See the [PlaceholderAPI](https://github.com/NagasonicDev/Alkatraz/wiki/PlaceholderAPI) page for the complete reference.

</details>

---

## 🌍 Localization

<details>
<summary><b>Click to expand</b> — translate the plugin with `.lang` files</summary>

Every user-facing string — menu titles, spell names, error messages — now lives in a **`.lang` file** using a simple `key = value` format.

- The plugin ships with a bundled **English** base file; set `language: <name>` in `config.yml` and create `plugins/Alkatraz/lang/<language>.lang`
- Only include the keys you want to override — anything missing falls back to English
- **YAML overrides** let you rename spells and research nodes without touching their configs (`override.spells.<id>.name`, `override.research.nodes.<id>.name`)
- `\n` for line breaks, `&` color codes, UTF-8 accents, and `/alkatraz reload` to apply changes

> See the [Localization](https://github.com/NagasonicDev/Alkatraz/wiki/Localization) page for details.

</details>

---

## 📜 Loot, Trading & Starter Content

<details>
<summary><b>Click to expand</b> — Magic Stones, spellbook trades, and a starter spell</summary>

- **Configurable loot** — Magic Stone drop chance from magic mobs scales with the Looting enchantment (`magic_stone.base_drop_chance`, `magic_stone.looting_bonus`)
- **Villager trading** — librarian villagers may sell **spellbooks** with tiered emerald/Magic Stone costs, randomized within configurable ranges
- **Starter spell** — new players begin with a basic spell

> See [Spellbooks and Loot](https://github.com/NagasonicDev/Alkatraz/wiki/Spellbooks-and-Loot) for the full loot table reference.

</details>

---

## 🛡️ Hotbar Safety & Spell Hotbar

<details>
<summary><b>Click to expand</b> — hotbar inventory that survives restarts and crashes</summary>

Hotbar casting got a durability upgrade:

- Your **inventory snapshot is now saved to disk** when entering hotbar mode, so it survives server restarts and crashes
- The snapshot is **restored automatically on join** if the previous session didn't exit cleanly
- On **keep-inventory deaths**, your original items are restored into your live inventory
- Slot-lock behavior and death handling have been fixed so nothing is lost or swapped

</details>

---

## 📦 Public API

<details>
<summary><b>Click to expand</b> — build on Alkatraz from your own plugins</summary>

Alkatraz ships a **public Java API** (`alkatraz-api`) that external plugins can depend on to create custom spells, register magic items, hook into the progression system, and extend the magic item pipeline with custom components, conditions, effects, and attribute sources — every class documented with full Javadoc.

Add the Maven repository and dependency to your plugin:

**Maven repository:**

```xml
<repository>
    <id>repsy</id>
    <name>Alkatraz</name>
    <url>https://repo.repsy.io/nagasonic/alkatrazapi</url>
</repository>
```

**Dependency:**

```xml
<dependency>
    <groupId>me.nagasonic</groupId>
    <artifactId>alkatraz-api</artifactId>
    <version>0.9.1</version>
</dependency>
```

What you can do with it:

- **Custom spells** — extend `Spell` and register it with `SpellAPI.registerSpell()`
- **Magic item definitions** — register items, engravings, components, triggers, conditions, effects, and attribute types through `MagicItemRegistries`
- **Player profiles** — read circle, mana, element affinity/resistance, discovered spells, research, and more via `SpellAPI.getProfile(player)`
- **Recipe API** — query, unlock, and lock recipes plus custom requirement types and crafting adapters via `Alkatraz.getRecipeManager()`
- **Events** — listen to Bukkit events like `SpellPrepareEvent`, `PlayerCastEvent`, and `RecipeUnlockedEvent`
- **Mob API** — declarative mob brains and spell cast configuration

> See the [API Usage](https://github.com/NagasonicDev/Alkatraz/wiki/API-Usage) page for the full guide and a complete example plugin.

</details>

---

*Alkatraz 0.9.1 — August 2026*
