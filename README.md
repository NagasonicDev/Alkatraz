<h1 align="center">
  <img src="https://img.shields.io/badge/version-0.9-blue?style=for-the-badge" alt="Version">
  <br>
  Alkatraz
</h1>

<p align="center">
  A Spigot/Paper/Purpur magic plugin with spell casting, equipment, research, and more.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk" alt="Java 17">
  <img src="https://img.shields.io/badge/MC-1.19—1.26-green?style=flat-square" alt="Minecraft 1.19–1.26">
  <img src="https://img.shields.io/badge/Spigot|Paper|Purpur-supported-brightgreen?style=flat-square" alt="Server Platforms">
</p>

---

## Feature Overview

| System | Count | Description |
|--------|-------|-------------|
| 🪄 **Spells** | 36 | Across 7 elements, 6 tiers, code-cast via click sequences |
| 🧙 **Wands** | 2 | Wooden (Circle 0–2) and Reinforced (Circle 0–5) |
| 🔮 **Magic Circles** | 6 | Circle 0–5 with escalating requirements |
| ⚔️ **Equipment Slots** | 11 | 6 vanilla + 5 virtual (Body, Ring, Necklace, Bracelet, Pendant) |
| 📜 **Engravings** | 28 | Trigger-based runes: Offense, Defense, Utility, Mana, Life, Special |
| 📚 **Research Tree** | 21 nodes | 7 categories: Magic, Fire, Water, Earth, Air, Light, Dark |
| 🧟 **Magic Mobs** | 3 variants | Spell-casting mobs with elemental affinities |
| 🎒 **Loot Sources** | 6 | Chests, mob drops, fishing, villager trades, structure loot |
| 📊 **Stats** | 15 | 6 affinities, 6 resistances, spell power, max mana, mana regen |
| 🗃️ **Item Definitions** | 123 | Wands, armor, accessories, scrolls, grimoires |
| 📦 **Public API** | Full | Maven dependency for external plugin integration |
| 🌐 **Localization** | Yes | YAML-based language file overrides |
| 📜 **Recipes** | 8 stations | Config-driven crafting: shaped, shapeless, cooking, smithing, stonecutter, brewing, anvil, custom; unlockable via requirements |

## How It Works

Hold a wand to enter magic mode — your XP bar becomes a **mana bar**. Cast spells by clicking the wand in specific sequences (**R**ight click, **L**eft click, **S**wap hand). For example, Magic Missile is cast with `RRRRR`. Discover new spells by finding spellbooks in loot, then level them up through the mastery system. Progress through magic circles to unlock stronger spells and invest stat points into elemental affinities.

<details>
<summary><strong>Full Spell List (36 spells)</strong></summary>

### Circle 0
| Spell | Element | Description |
|-------|---------|-------------|
| Magic Missile | None | Shoots a beam up to 20 blocks, dealing damage on hit |

### Circle 1
| Spell | Element | Description |
|-------|---------|-------------|
| Air Burst | Air | Shoots a burst of air, pushing back enemies |
| Earth Throw | Earth | Throws a chunk of ground, launching enemies into the air |
| Fireball | Fire | Shoots a fireball that explodes on impact |
| Water Sphere | Water | Summons a slow-moving water ball dealing damage |

### Circle 2
| Spell | Element | Description |
|-------|---------|-------------|
| Air Blades | Air | Launches sharp blades of wind |
| Buff | None | Temporarily boosts target's stats |
| Debuff | None | Temporarily weakens target's stats |
| Detect | None | Scans surroundings, making nearby entities glow |
| Earth Spike | Earth | Sends a spike of earth toward the target |
| Fire Blast | Fire | Shoots a large fireball, igniting surroundings |
| Lesser Heal | Light | Heals 1–2.5 hearts based on Light Affinity |
| Stealth | None | Hides the player from others |
| Swift | Air | Launches the player forward |
| Water Pulse | Water | Sends a pulse of water at the target |
| Wind Vortex | Air | Creates a vortex pulling in nearby entities |

### Circle 3
| Spell | Element | Description |
|-------|---------|-------------|
| Barrier | None | Creates a protective barrier around the caster |
| Dark Tendrils | Dark | Summons tendrils of darkness |
| Disguise | None | Disguises the player as another player |
| Fire Wall | Fire | Creates a wall of flames in the direction you look |
| Geyser | Water | Erupts a geyser beneath the target |
| Heal | Light | Heals the target or self |
| Tremor | Earth | Creates a powerful shockwave |

### Circle 4
| Spell | Element | Description |
|-------|---------|-------------|
| Blink | None | Teleports the player a short distance |
| Earthen Wall | Earth | Creates a defensive wall of earth |
| Flaming Volley | Fire | Launches multiple flaming projectiles |
| Light Buff | Light | Stronger version of Buff |
| Summon Zombies | Dark | Summons zombie allies |
| Whirlpool | Water | Creates a swirling water vortex |
| Wind Barrier | Air | Creates a protective wind barrier |

### Circle 5
| Spell | Element | Description |
|-------|---------|-------------|
| Meteor Shower | Fire | Rains meteors from the sky |
| Tsunami | Water | Sends a massive wave forward |
| Fissure | Earth | Splits the ground open |
| Tornado | Air | Creates a destructive tornado |
| Radiance | Light | Unleashes a burst of holy energy |
| Shadow Realm | Dark | Opens a portal of darkness |

</details>

## Quick Start

1. **Install** — Drop `Alkatraz.jar` into your `plugins/` folder and restart the server
2. **Get a Wand** — `/alkatraz give wooden_wand` (or find one in loot)
3. **Hold it** — Your XP bar transforms into a mana bar
4. **Cast!** — Right-click `R` ×5 to cast Magic Missile (`RRRRR`)

Spell codes and options are viewable from the `/spells` menu.

<details>
<summary><strong>Commands & Permissions</strong></summary>

### Player Commands

| Command | Description |
|---------|-------------|
| `/spells` | Open the Spell Menu GUI |
| `/recipes` | Open the Recipe Book |
| `/cast <token>` | Cast a spell from the grimoire |

### Admin Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/alkatraz give <item> <player>` | `alkatraz.command.give` | Give an Alkatraz item to a player |
| `/alkatraz discoverspell <spell> <player>` | `alkatraz.command.discoverspell` | Discover a spell for a player |
| `/alkatraz undiscoverspell <spell> <player>` | `alkatraz.command.undiscoverspell` | Undiscover a spell for a player |
| `/alkatraz mastery <spell> <add\|set> <number> [<player>]` | `alkatraz.command.mastery` | Modify a player's spell mastery |
| `/alkatraz circle <add\|set> <number> [<player>]` | `alkatraz.command.circle` | Modify a player's circle level |
| `/alkatraz experience <add\|set> <number> [<player>]` | `alkatraz.command.experience` | Modify a player's magic experience |
| `/alkatraz stats [<player>]` | `alkatraz.command.stats.other` | Open the Stats GUI |
| `/alkatraz equipment` | — | Open the Equipment Menu |
| `/alkatraz editor` | — | Open the Item Editor GUI |
| `/alkatraz reload` | `alkatraz.command.reload` | Reload spell configs |
| `/recipes unlock <id> [<player>]` | `alkatraz.recipe.unlock` | Unlock a recipe for a player |
| `/recipes lock <id> [<player>]` | `alkatraz.recipe.lock` | Lock a recipe for a player |
| `/recipes give <id> [<player>]` | `alkatraz.recipe.give` | Grant a recipe to a player |
| `/recipes check <player> <id>` | `alkatraz.recipe.check` | Check a player's recipe unlock status |
| `/recipes reload` | `alkatraz.recipe.reload` | Reload recipe definitions |
| `/spells <player>` | `alkatraz.command.spells.other` | View another player's spells |

### Permissions

| Permission | Description |
|------------|-------------|
| `alkatraz.allspells` | Use all spells without discovering them |
| `alkatraz.recipe.unlock` | Unlock recipes via command |
| `alkatraz.recipe.lock` | Lock recipes via command |
| `alkatraz.recipe.give` | Grant recipes via command |
| `alkatraz.recipe.check` | Check recipe unlock status |
| `alkatraz.recipe.reload` | Reload recipe definitions |

</details>

## Links

- **Wiki** — [Full documentation for all features](https://github.com/NagasonicDev/Alkatraz/wiki)
- **API Usage** — [Integrate with external plugins](https://github.com/NagasonicDev/Alkatraz/wiki/API-Usage)
- **Issues** — [Report bugs or request features](https://github.com/NagasonicDev/Alkatraz/issues)
