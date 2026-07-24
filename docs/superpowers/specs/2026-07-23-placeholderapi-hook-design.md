# PlaceholderAPI Hook Design

## Overview

Full-featured PlaceholderAPI integration for the Alkatraz magic plugin. Exposes all player stats, element data, spell data, research progress, hotbar configuration, and a server-wide leaderboard via the `%alkatraz_<handler>_<param>%` placeholder format.

## Architecture

### File Structure

```
core/src/main/java/me/nagasonic/alkatraz/hooks/
├── PluginHook.java                    (existing, unchanged)
├── PlaceholderAPIHook.java            (modified - registers expansion)
└── placeholder/
    ├── Placeholder.java               (handler interface)
    ├── AlkatrazPlaceholder.java        (main expansion, routes to handlers)
    ├── StatsPlaceholder.java           (core stats)
    ├── ElementPlaceholder.java         (element affinity/resistance/points)
    ├── SpellPlaceholder.java           (spell mastery, cooldowns, metadata)
    ├── ResearchPlaceholder.java        (research states and counts)
    ├── HotbarPlaceholder.java          (hotbar spell slots)
    └── LeaderboardPlaceholder.java     (top-N and player rank)
```

### Modified Files

| File | Change |
|---|---|
| `PlaceholderAPIHook.java` | `ifPresent()` creates and registers `AlkatrazPlaceholder` |
| `Alkatraz.java` | `onEnable()` instantiates `PlaceholderAPIHook` and calls `ifPresent()` |
| `config.yml` | Add `placeholders.leaderboard` section |

### Registration Flow

```
Alkatraz.onEnable()
  → new PlaceholderAPIHook()           // checks if PlaceholderAPI plugin is loaded
  → hook.ifPresent()                    // only runs if PlaceholderAPI is present
    → new AlkatrazPlaceholder()
    → registers all 6 handlers
    → expansion.register()              // PlaceholderAPI.registerExpansion()
```

### Placeholder Format

```
%alkatraz_<handler>_<param>%
```

- `handler` = stats | elements | spells | research | hotbar | leaderboard
- `param` = handler-specific parameter

Examples:
- `%alkatraz_stats_circle%`
- `%alkatraz_elements_affinity_fire%`
- `%alkatraz_spells_mastery_fireball%`
- `%alkatraz_leaderboard_top_1_name%`

### Handler Interface

```java
package me.nagasonic.alkatraz.hooks.placeholder;

import org.bukkit.entity.Player;

public interface Placeholder {
    /** Handler name used in placeholder identifier (e.g. "stats", "elements") */
    String name();

    /**
     * Resolve a placeholder request.
     * @param player the online player
     * @param params the parameter portion after the handler name
     * @return the resolved value, or "" for unknown params
     */
    String onPlaceholderRequest(Player player, String params);
}
```

### AlkatrazPlaceholder (Main Expansion)

```java
public class AlkatrazPlaceholder extends PlaceholderExpansion {
    private final Map<String, Placeholder> handlers = new LinkedHashMap<>();

    // Identifier: "alkatraz"
    // Expansion persist/loaded: true (loaded once, stays in memory)

    // onPlaceholderRequest(identifier, player):
    //   1. Split identifier on first "_" -> [handlerName, params]
    //   2. Look up handler by name
    //   3. Delegate to handler.onPlaceholderRequest(player, params)
    //   4. Return "" if handler not found
}
```

## Placeholder Reference

### Stats (`%alkatraz_stats_*%`)

| Placeholder | Returns | Type | Source |
|---|---|---|---|
| `circle` | Circle level | int | `profile.getStat("circleLevel")` |
| `circle_roman` | Roman numeral (I-IX) | String | Converted from circle level |
| `mana` | Current mana | double | `profile.getStat("mana")` |
| `max_mana` | Maximum mana | double | `profile.getStat("maxMana")` |
| `mana_percent` | Mana as percentage | double | `mana / maxMana * 100` |
| `mana_regen` | Mana regeneration/sec | double | `profile.getStat("manaRegeneration")` |
| `spell_power` | Spell power (equipment) | double | `profile.getStat("spell_power")` |
| `stat_points` | Unspent stat points | int | `profile.getStat("statPoints")` |
| `reset_tokens` | Reset tokens | int | `profile.getStat("resetTokens")` |
| `arcane_knowledge` | Arcane knowledge | double | `profile.getStat("arcaneKnowledge")` |
| `research_points` | Research points | double | `profile.getStat("researchPoints")` |
| `casting` | Currently casting | boolean | `profile.getStat("casting")` -> `true`/`false` |
| `stealth` | In stealth mode | boolean | `profile.getStat("stealth")` -> `true`/`false` |
| `can_cast` | Can cast spells | boolean | `profile.getStat("canCast")` -> `true`/`false` |
| `cast_mode` | Current cast mode | String | `profile.getStat("castMode")` -> `code`/`hotbar` |
| `disguise` | Active disguise type | String | `profile.getStat("disguise")` |
| `tutorial_seen` | Has seen tutorial | boolean | `profile.getStat("tutorialSeen")` -> `true`/`false` |
| `total_spells` | Total registered spells | int | `SpellRegistry.getAllSpellsByID().size()` |
| `total_enabled_spells` | Enabled spells | int | Count of enabled spells from registry |
| `discovered_count` | Discovered spell count | int | `profile.getAllDiscoveredSpellTypes().size()` |

### Elements (`%alkatraz_elements_*%`)

| Placeholder | Returns | Type | Source |
|---|---|---|---|
| `points_fire` | Points in fire | int | `profile.getStat("firePoints")` |
| `points_water` | Points in water | int | `profile.getStat("waterPoints")` |
| `points_air` | Points in air | int | `profile.getStat("airPoints")` |
| `points_earth` | Points in earth | int | `profile.getStat("earthPoints")` |
| `points_light` | Points in light | int | `profile.getStat("lightPoints")` |
| `points_dark` | Points in dark | int | `profile.getStat("darkPoints")` |
| `affinity_fire` | Fire affinity | double | `profile.getAffinity(Element.FIRE)` |
| `affinity_water` | Water affinity | double | `profile.getAffinity(Element.WATER)` |
| `affinity_air` | Air affinity | double | `profile.getAffinity(Element.AIR)` |
| `affinity_earth` | Earth affinity | double | `profile.getAffinity(Element.EARTH)` |
| `affinity_light` | Light affinity | double | `profile.getAffinity(Element.LIGHT)` |
| `affinity_dark` | Dark affinity | double | `profile.getAffinity(Element.DARK)` |
| `resistance_fire` | Fire resistance | double | `profile.getResistance(Element.FIRE)` |
| `resistance_water` | Water resistance | double | `profile.getResistance(Element.WATER)` |
| `resistance_air` | Air resistance | double | `profile.getResistance(Element.AIR)` |
| `resistance_earth` | Earth resistance | double | `profile.getResistance(Element.EARTH)` |
| `resistance_light` | Light resistance | double | `profile.getResistance(Element.LIGHT)` |
| `resistance_dark` | Dark resistance | double | `profile.getResistance(Element.DARK)` |
| `magic_affinity` | Base magic affinity | double | `profile.getMagicAffinity()` |
| `magic_resistance` | Base magic resistance | double | `profile.getMagicResistance()` |

### Spells (`%alkatraz_spells_*%`)

| Placeholder | Returns | Type | Source |
|---|---|---|---|
| `has_<spell_id>` | Has discovered spell | boolean | `profile.getDiscoveredSpells().contains(spellId)` -> `true`/`false` |
| `mastery_<spell_id>` | Mastery level | int | `profile.getStat("mastery_" + spellId)` |
| `mastery_<spell_id>_max` | Max mastery | int | `spell.getMaxMastery()` |
| `mastery_<spell_id>_percent` | Mastery as % | double | `mastery / maxMastery * 100` |
| `cooldown_<spell_id>` | Remaining cooldown (sec) | long | Calculated from cooldown timestamp |
| `cooldown_<spell_id>_ready` | Off cooldown | boolean | `true`/`false` |
| `name_<spell_id>` | Display name | String | `spell.getDisplayName()` |
| `element_<spell_id>` | Spell element | String | `spell.getElement().getName()` |
| `circle_<spell_id>` | Required circle | int | `spell.getRequiredCircleLevel()` |
| `mana_cost_<spell_id>` | Mana cost | int | `spell.getCost()` |
| `cooldown_time_<spell_id>` | Base cooldown (sec) | long | `spell.getCooldown()` |
| `code_<spell_id>` | Cast code | String | `spell.getCode()` |

### Research (`%alkatraz_research_*%`)

| Placeholder | Returns | Type | Source |
|---|---|---|---|
| `completed_count` | Completed research count | int | `profile.getResearchCompleted().size()` |
| `has_<research_id>` | Specific research completed | boolean | `true`/`false` |
| `state_<research_id>` | Research state | String | `ResearchService.getState(player, node).name()` |

Research states: `HIDDEN`, `LOCKED`, `AVAILABLE`, `IN_PROGRESS`, `COMPLETED`

### Hotbar (`%alkatraz_hotbar_*%`)

| Placeholder | Returns | Type | Source |
|---|---|---|---|
| `slot_<1-8>` | Spell ID in slot | String | `profile.getHotbarSpellIds()[slot - 1]` |
| `count` | Assigned spell count | int | Count of non-empty hotbar slots |

### Leaderboard (`%alkatraz_leaderboard_*%`)

| Placeholder | Returns | Type | Source |
|---|---|---|---|
| `top_<N>_name` | Player name at rank N | String | Cached leaderboard |
| `top_<N>_uuid` | Player UUID at rank N | String | Cached leaderboard |
| `top_<N>_ak` | Arcane knowledge at rank N | double | Cached leaderboard |
| `top_<N>_circle` | Circle level at rank N | int | Cached leaderboard |
| `rank` | Player's rank by AK | int | Binary search in cached list |
| `total_players` | Total players on leaderboard | int | Size of cached list |

N = 1 to configurable max (default 10).

## Leaderboard Implementation

### Data Structure

```java
private static class LeaderboardEntry {
    final UUID uuid;
    final String name;
    final double arcaneKnowledge;
    final int circleLevel;
}

private List<LeaderboardEntry> cachedBoard = new ArrayList<>();
private long lastRefresh = 0;
```

### Refresh Cycle

1. Check if `System.currentTimeMillis() - lastRefresh > refreshInterval`
2. If stale, scan `Alkatraz/playerdata/` directory for UUID folders
3. For each UUID folder, load `MagicProfile` from disk (bypass cache)
4. Create `LeaderboardEntry` for each player
5. Sort by `arcaneKnowledge` descending
6. Keep top N entries
7. Update `lastRefresh`

### Config

```yaml
placeholders:
  leaderboard:
    enabled: true
    refresh_interval_minutes: 5
    max_entries: 10
```

### Rank Resolution

Binary search `cachedBoard` for the requesting player's UUID. Return index + 1 (1-based rank). Return `0` if player not found in the leaderboard.

### Performance

- Full scan of ~1000 player folders: ~100-200ms (acceptable every 5 minutes)
- Placeholder requests during refresh: serve stale data (no blocking)
- Memory: ~100 bytes per entry x 10 entries = ~1KB cache

## Error Handling

| Scenario | Behavior |
|---|---|
| Unknown param | Return `""` |
| Profile not loaded | Return `""`, log warning at DEBUG level |
| Spell ID not found | Return `""` |
| Research ID not found | Return `""` |
| Leaderboard not yet built | Return `""` |
| Leaderboard disabled in config | Don't register `LeaderboardPlaceholder` |

## Integration Points

### PlaceholderAPIHook (modified)

```java
public class PlaceholderAPIHook extends PluginHook {
    public PlaceholderAPIHook() {
        super("PlaceholderAPI");
    }

    @Override
    public void ifPresent() {
        AlkatrazPlaceholder expansion = new AlkatrazPlaceholder();

        // Register all handlers
        expansion.registerHandler(new StatsPlaceholder());
        expansion.registerHandler(new ElementPlaceholder());
        expansion.registerHandler(new SpellPlaceholder());
        expansion.registerHandler(new ResearchPlaceholder());
        expansion.registerHandler(new HotbarPlaceholder());

        // Only register leaderboard if enabled in config
        YamlConfiguration config = Alkatraz.getPluginConfig();
        if (config.getBoolean("placeholders.leaderboard.enabled", true)) {
            long refreshMinutes = config.getLong("placeholders.leaderboard.refresh_interval_minutes", 5);
            int maxEntries = config.getInt("placeholders.leaderboard.max_entries", 10);
            expansion.registerHandler(new LeaderboardPlaceholder(refreshMinutes, maxEntries));
        }

        expansion.register();
    }
}
```

### Alkatraz.java (modified)

In `onEnable()`, after all other initialization:

```java
logVeryHigh("Initializing PlaceholderAPI hook...");
PlaceholderAPIHook placeholderHook = new PlaceholderAPIHook();
if (placeholderHook.isPresent()) {
    placeholderHook.ifPresent();
}
```

### config.yml (addition)

```yaml
placeholders:
  leaderboard:
    enabled: true
    refresh_interval_minutes: 5
    max_entries: 10
```

## Design Decisions

1. **Single prefix `%alkatraz_*%`**: Admins only need to remember one prefix. Handler routing is internal.
2. **Handler pattern over monolithic class**: Each handler is ~80-150 lines. Easy to find, modify, and extend.
3. **Boolean format**: `true`/`false` (lowercase). Standard and parseable by other plugins.
4. **Leaderboard cache**: Avoids scanning disk on every placeholder request. 5-minute refresh is a good balance.
5. **Empty string for unknown**: Standard PlaceholderAPI convention. Prevents garbled output.
6. **Configurable leaderboard**: Admins can disable it or tune refresh interval/max entries.
7. **No database required**: Leaderboard works with existing flat-file storage by scanning folders.
