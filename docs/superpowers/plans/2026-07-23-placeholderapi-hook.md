# PlaceholderAPI Hook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement full PlaceholderAPI integration exposing all player stats, elements, spells, research, hotbar, and leaderboard data via `%alkatraz_<handler>_<param>%` placeholders.

**Architecture:** Single `PlaceholderExpansion` subclass (`AlkatrazPlaceholder`) routes placeholder requests to category-specific handler implementations (`Placeholder` interface). Each handler is a focused class (~80-150 lines) managing one data domain. Leaderboard uses a timed cache that scans playerdata folders.

**Tech Stack:** Java 21, PlaceholderAPI 2.12.3 (provided dependency), Bukkit/Spigot API, existing `MagicProfile`/`ProfileManager`/`SpellRegistry`/`ResearchService` APIs.

## Global Constraints

- PlaceholderAPI dependency already in `pom.xml` as `provided` scope
- `plugin.yml` already lists `PlaceholderAPI` as `softdepend`
- All player data accessed via `ProfileManager.getProfile(player, MagicProfile.class)`
- Boolean placeholders return `true`/`false` (lowercase strings)
- Unknown params return `""` (empty string)
- Base path: `core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/`

---

### Task 1: Placeholder Interface + AlkatrazPlaceholder Expansion

**Files:**
- Create: `core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/Placeholder.java`
- Create: `core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/AlkatrazPlaceholder.java`

**Interfaces:**
- Consumes: None (first task)
- Produces: `Placeholder` interface with `name()` and `onPlaceholderRequest(Player, String)`. `AlkatrazPlaceholder` extends `PlaceholderExpansion` with `registerHandler(Placeholder)` and routing logic.

- [ ] **Step 1: Create the Placeholder interface**

```java
package me.nagasonic.alkatraz.hooks.placeholder;

import org.bukkit.entity.Player;

public interface Placeholder {
    String name();
    String onPlaceholderRequest(Player player, String params);
}
```

- [ ] **Step 2: Create AlkatrazPlaceholder expansion**

```java
package me.nagasonic.alkatraz.hooks.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public class AlkatrazPlaceholder extends PlaceholderExpansion {
    private final Map<String, Placeholder> handlers = new LinkedHashMap<>();

    @Override
    public String getIdentifier() {
        return "alkatraz";
    }

    @Override
    public String getAuthor() {
        return "Nagasonic";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    public void registerHandler(Placeholder handler) {
        handlers.put(handler.name(), handler);
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null || identifier == null) return "";

        int sep = identifier.indexOf('_');
        if (sep <= 0) return "";

        String handlerName = identifier.substring(0, sep);
        String params = identifier.substring(sep + 1);

        Placeholder handler = handlers.get(handlerName);
        if (handler == null) return "";

        try {
            return handler.onPlaceholderRequest(player, params);
        } catch (Exception e) {
            return "";
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/
git commit -m "feat: add Placeholder interface and AlkatrazPlaceholder expansion"
```

---

### Task 2: StatsPlaceholder

**Files:**
- Create: `core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/StatsPlaceholder.java`

**Interfaces:**
- Consumes: `Placeholder` interface, `MagicProfile` via `ProfileManager.getProfile(player, MagicProfile.class)`, `SpellRegistry.getAllSpellsByID()`
- Produces: Handler named `"stats"` returning values for all stats placeholders

- [ ] **Step 1: Create StatsPlaceholder**

```java
package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.entity.Player;

public class StatsPlaceholder implements Placeholder {

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);

        return switch (params) {
            case "circle" -> String.valueOf(profile.getCircleLevel());
            case "circle_roman" -> {
                int lvl = profile.getCircleLevel();
                yield (lvl >= 0 && lvl < ROMAN.length) ? ROMAN[lvl] : "";
            }
            case "mana" -> String.valueOf(profile.getMana());
            case "max_mana" -> String.valueOf(profile.getMaxMana());
            case "mana_percent" -> {
                double max = profile.getMaxMana();
                yield max > 0 ? String.valueOf(profile.getMana() / max * 100) : "0";
            }
            case "mana_regen" -> String.valueOf(profile.getManaRegeneration());
            case "spell_power" -> String.valueOf(profile.getDouble("spell_power"));
            case "stat_points" -> String.valueOf(profile.getStatPoints());
            case "reset_tokens" -> String.valueOf(profile.getResetTokens());
            case "arcane_knowledge" -> String.valueOf(profile.getArcaneKnowledge());
            case "research_points" -> String.valueOf(profile.getResearchPoints());
            case "casting" -> String.valueOf(profile.getBool("casting"));
            case "stealth" -> String.valueOf(profile.getBool("stealth"));
            case "can_cast" -> String.valueOf(profile.getBool("canCast"));
            case "cast_mode" -> profile.getCastMode();
            case "disguise" -> profile.getDisguise();
            case "tutorial_seen" -> String.valueOf(profile.getBool("tutorialSeen"));
            case "total_spells" -> String.valueOf(SpellRegistry.getAllSpellsByID().size());
            case "total_enabled_spells" -> String.valueOf(
                    (int) SpellRegistry.getAllSpellsByID().values().stream()
                            .filter(s -> s.isEnabled())
                            .count()
            );
            case "discovered_count" -> String.valueOf(profile.getAllDiscoveredSpellTypes().size());
            default -> "";
        };
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/StatsPlaceholder.java
git commit -m "feat: add StatsPlaceholder for core player stats"
```

---

### Task 3: ElementPlaceholder

**Files:**
- Create: `core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/ElementPlaceholder.java`

**Interfaces:**
- Consumes: `Placeholder` interface, `MagicProfile`, `Element` enum
- Produces: Handler named `"elements"` returning affinity/resistance/points per element

- [ ] **Step 1: Create ElementPlaceholder**

```java
package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Element;
import org.bukkit.entity.Player;

import java.util.Map;

public class ElementPlaceholder implements Placeholder {

    private static final Map<String, Element> ELEMENT_MAP = Map.of(
            "fire", Element.FIRE,
            "water", Element.WATER,
            "air", Element.AIR,
            "earth", Element.EARTH,
            "light", Element.LIGHT,
            "dark", Element.DARK
    );

    @Override
    public String name() {
        return "elements";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);

        if (params.startsWith("points_")) {
            String elementName = params.substring(7);
            Element element = ELEMENT_MAP.get(elementName);
            if (element == null) return "";
            return String.valueOf(profile.getPoints(element));
        }

        if (params.startsWith("affinity_")) {
            String elementName = params.substring(9);
            Element element = ELEMENT_MAP.get(elementName);
            if (element == null) return "";
            return String.valueOf(profile.getAffinity(element));
        }

        if (params.startsWith("resistance_")) {
            String elementName = params.substring(11);
            Element element = ELEMENT_MAP.get(elementName);
            if (element == null) return "";
            return String.valueOf(profile.getResistance(element));
        }

        return switch (params) {
            case "magic_affinity" -> String.valueOf(profile.getMagicAffinity());
            case "magic_resistance" -> String.valueOf(profile.getMagicResistance());
            default -> "";
        };
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/ElementPlaceholder.java
git commit -m "feat: add ElementPlaceholder for affinity/resistance/points"
```

---

### Task 4: SpellPlaceholder

**Files:**
- Create: `core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/SpellPlaceholder.java`

**Interfaces:**
- Consumes: `Placeholder` interface, `MagicProfile`, `SpellRegistry`, `Spell` class
- Produces: Handler named `"spells"` returning mastery/cooldown/metadata per spell

- [ ] **Step 1: Create SpellPlaceholder**

```java
package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class SpellPlaceholder implements Placeholder {

    @Override
    public String name() {
        return "spells";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);

        if (params.startsWith("has_")) {
            String spellId = params.substring(4);
            return String.valueOf(profile.getAllDiscoveredSpellTypes().contains(spellId.toLowerCase()));
        }

        if (params.startsWith("mastery_")) {
            String rest = params.substring(8);
            Spell spell = SpellRegistry.getSpell(rest);
            if (spell == null) return "";
            return String.valueOf(profile.getSpellMastery(spell));
        }

        if (params.startsWith("mastery_") && params.endsWith("_max")) {
            String spellId = params.substring(8, params.length() - 4);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell == null) return "";
            return String.valueOf(spell.getMaxMastery());
        }

        if (params.startsWith("mastery_") && params.endsWith("_percent")) {
            String spellId = params.substring(8, params.length() - 8);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell == null) return "";
            int mastery = profile.getSpellMastery(spell);
            int max = spell.getMaxMastery();
            return max > 0 ? String.valueOf(mastery * 100.0 / max) : "0";
        }

        if (params.startsWith("cooldown_") && params.endsWith("_ready")) {
            String spellId = params.substring(9, params.length() - 6);
            return String.valueOf(isReady(profile, spellId));
        }

        if (params.startsWith("cooldown_")) {
            String spellId = params.substring(9);
            return String.valueOf(getRemainingCooldown(profile, spellId));
        }

        if (params.startsWith("name_")) {
            String spellId = params.substring(5);
            Spell spell = SpellRegistry.getSpell(spellId);
            return spell != null ? spell.getDisplayName() : "";
        }

        if (params.startsWith("element_")) {
            String spellId = params.substring(8);
            Spell spell = SpellRegistry.getSpell(spellId);
            return spell != null ? spell.getElement().getColorlessName() : "";
        }

        if (params.startsWith("circle_")) {
            String spellId = params.substring(7);
            Spell spell = SpellRegistry.getSpell(spellId);
            return spell != null ? String.valueOf(spell.getRequiredCircleLevel()) : "";
        }

        if (params.startsWith("mana_cost_")) {
            String spellId = params.substring(10);
            Spell spell = SpellRegistry.getSpell(spellId);
            return spell != null ? String.valueOf(spell.getCost()) : "";
        }

        if (params.startsWith("cooldown_time_")) {
            String spellId = params.substring(14);
            Spell spell = SpellRegistry.getSpell(spellId);
            return spell != null ? String.valueOf(spell.getCooldown()) : "";
        }

        if (params.startsWith("code_")) {
            String spellId = params.substring(5);
            Spell spell = SpellRegistry.getSpell(spellId);
            return spell != null ? spell.getCode() : "";
        }

        return "";
    }

    private boolean isReady(MagicProfile profile, String spellId) {
        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) return true;
        Long cooldownSet = profile.getCooldown(spell);
        if (cooldownSet == null) return true;
        long elapsed = System.currentTimeMillis() - cooldownSet;
        return TimeUnit.MILLISECONDS.toSeconds(elapsed) >= spell.getCooldown();
    }

    private long getRemainingCooldown(MagicProfile profile, String spellId) {
        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) return 0;
        Long cooldownSet = profile.getCooldown(spell);
        if (cooldownSet == null) return 0;
        long elapsed = System.currentTimeMillis() - cooldownSet;
        long remaining = spell.getCooldown() - TimeUnit.MILLISECONDS.toSeconds(elapsed);
        return Math.max(0, remaining);
    }
}
```

- [ ] **Step 2: Fix mastery parsing order**

The switch cases for `mastery_*_max` and `mastery_*_percent` must come BEFORE the plain `mastery_*` case. Move them above:

```java
if (params.startsWith("mastery_") && params.endsWith("_percent")) {
    String spellId = params.substring(8, params.length() - 8);
    Spell spell = SpellRegistry.getSpell(spellId);
    if (spell == null) return "";
    int mastery = profile.getSpellMastery(spell);
    int max = spell.getMaxMastery();
    return max > 0 ? String.valueOf(mastery * 100.0 / max) : "0";
}

if (params.startsWith("mastery_") && params.endsWith("_max")) {
    String spellId = params.substring(8, params.length() - 4);
    Spell spell = SpellRegistry.getSpell(spellId);
    if (spell == null) return "";
    return String.valueOf(spell.getMaxMastery());
}

if (params.startsWith("mastery_")) {
    String spellId = params.substring(8);
    Spell spell = SpellRegistry.getSpell(spellId);
    if (spell == null) return "";
    return String.valueOf(profile.getSpellMastery(spell));
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/SpellPlaceholder.java
git commit -m "feat: add SpellPlaceholder for mastery/cooldown/metadata"
```

---

### Task 5: ResearchPlaceholder

**Files:**
- Create: `core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/ResearchPlaceholder.java`

**Interfaces:**
- Consumes: `Placeholder` interface, `MagicProfile`, `ResearchService`, `ResearchState`
- Produces: Handler named `"research"` returning research states and counts

- [ ] **Step 1: Create ResearchPlaceholder**

```java
package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.progression.research.ResearchState;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import org.bukkit.entity.Player;

import java.util.Optional;

public class ResearchPlaceholder implements Placeholder {

    @Override
    public String name() {
        return "research";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);

        if (params.equals("completed_count")) {
            return String.valueOf(profile.getCompletedResearchIds().size());
        }

        if (params.startsWith("has_")) {
            String researchId = params.substring(4);
            return String.valueOf(profile.hasCompletedResearch(researchId));
        }

        if (params.startsWith("state_")) {
            String researchId = params.substring(6);
            Optional<ResearchNode> node = ResearchService.getNode(researchId);
            if (node.isEmpty()) return "";
            ResearchState state = ResearchService.getState(player, node.get());
            return state.name();
        }

        return "";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/ResearchPlaceholder.java
git commit -m "feat: add ResearchPlaceholder for research states/counts"
```

---

### Task 6: HotbarPlaceholder

**Files:**
- Create: `core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/HotbarPlaceholder.java`

**Interfaces:**
- Consumes: `Placeholder` interface, `MagicProfile.getHotbarSpellIds()`
- Produces: Handler named `"hotbar"` returning spell IDs per slot

- [ ] **Step 1: Create HotbarPlaceholder**

```java
package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.entity.Player;

import java.util.Map;

public class HotbarPlaceholder implements Placeholder {

    @Override
    public String name() {
        return "hotbar";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        Map<Integer, String> hotbar = profile.getHotbarSpellIds();

        if (params.startsWith("slot_")) {
            try {
                int slot = Integer.parseInt(params.substring(5));
                return hotbar.getOrDefault(slot - 1, "");
            } catch (NumberFormatException e) {
                return "";
            }
        }

        if (params.equals("count")) {
            return String.valueOf(hotbar.size());
        }

        return "";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/HotbarPlaceholder.java
git commit -m "feat: add HotbarPlaceholder for hotbar spell slots"
```

---

### Task 7: LeaderboardPlaceholder

**Files:**
- Create: `core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/LeaderboardPlaceholder.java`

**Interfaces:**
- Consumes: `Placeholder` interface, `MagicProfile`, `ProfilePersistence` (for disk scan), `Alkatraz` (for data folder path)
- Produces: Handler named `"leaderboard"` with cached top-N and per-player rank

- [ ] **Step 1: Create LeaderboardPlaceholder**

```java
package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public class LeaderboardPlaceholder implements Placeholder {

    private static class LeaderboardEntry {
        final UUID uuid;
        final String name;
        final double arcaneKnowledge;
        final int circleLevel;

        LeaderboardEntry(UUID uuid, String name, double arcaneKnowledge, int circleLevel) {
            this.uuid = uuid;
            this.name = name;
            this.arcaneKnowledge = arcaneKnowledge;
            this.circleLevel = circleLevel;
        }
    }

    private final long refreshIntervalMs;
    private final int maxEntries;
    private List<LeaderboardEntry> cachedBoard = new ArrayList<>();
    private long lastRefresh = 0;

    public LeaderboardPlaceholder(long refreshIntervalMinutes, int maxEntries) {
        this.refreshIntervalMs = refreshIntervalMinutes * 60 * 1000;
        this.maxEntries = maxEntries;
    }

    @Override
    public String name() {
        return "leaderboard";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        refreshIfNeeded();

        if (params.equals("total_players")) {
            return String.valueOf(cachedBoard.size());
        }

        if (params.equals("rank")) {
            UUID uuid = player.getUniqueId();
            for (int i = 0; i < cachedBoard.size(); i++) {
                if (cachedBoard.get(i).uuid.equals(uuid)) {
                    return String.valueOf(i + 1);
                }
            }
            return "0";
        }

        if (params.startsWith("top_")) {
            String[] parts = params.split("_", 3);
            if (parts.length < 3) return "";
            int index;
            try {
                index = Integer.parseInt(parts[1]) - 1;
            } catch (NumberFormatException e) {
                return "";
            }
            if (index < 0 || index >= cachedBoard.size()) return "";
            LeaderboardEntry entry = cachedBoard.get(index);
            String field = parts[2];
            return switch (field) {
                case "name" -> entry.name;
                case "uuid" -> entry.uuid.toString();
                case "ak" -> String.valueOf(entry.arcaneKnowledge);
                case "circle" -> String.valueOf(entry.circleLevel);
                default -> "";
            };
        }

        return "";
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < refreshIntervalMs) return;
        lastRefresh = now;

        File playerDataDir = new File(Alkatraz.getInstance().getDataFolder().getParentFile(), "Alkatraz/playerdata");
        if (!playerDataDir.exists() || !playerDataDir.isDirectory()) return;

        File[] uuidFolders = playerDataDir.listFiles(File::isDirectory);
        if (uuidFolders == null) return;

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (File folder : uuidFolders) {
            try {
                UUID uuid = UUID.fromString(folder.getName());
                MagicProfile profile = ProfileManager.getProfile(uuid, MagicProfile.class);
                double ak = profile.getArcaneKnowledge();
                int circle = profile.getCircleLevel();
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                String name = offline.getName() != null ? offline.getName() : "Unknown";
                entries.add(new LeaderboardEntry(uuid, name, ak, circle));
            } catch (IllegalArgumentException ignored) {
            }
        }

        entries.sort((a, b) -> Double.compare(b.arcaneKnowledge, a.arcaneKnowledge));
        cachedBoard = entries.size() > maxEntries ? entries.subList(0, maxEntries) : entries;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/LeaderboardPlaceholder.java
git commit -m "feat: add LeaderboardPlaceholder with timed cache"
```

---

### Task 8: Modify PlaceholderAPIHook + Alkatraz.java + config.yml

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/hooks/PlaceholderAPIHook.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/Alkatraz.java:161`
- Modify: `core/src/main/resources/config.yml` (or wherever config.yml lives)

**Interfaces:**
- Consumes: All placeholder handlers from Tasks 1-7
- Produces: Wired-up registration in plugin startup

- [ ] **Step 1: Modify PlaceholderAPIHook**

```java
package me.nagasonic.alkatraz.hooks;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.hooks.placeholder.*;
import org.bukkit.configuration.file.YamlConfiguration;

public class PlaceholderAPIHook extends PluginHook {
    public PlaceholderAPIHook() {
        super("PlaceholderAPI");
    }

    @Override
    public void ifPresent() {
        AlkatrazPlaceholder expansion = new AlkatrazPlaceholder();

        expansion.registerHandler(new StatsPlaceholder());
        expansion.registerHandler(new ElementPlaceholder());
        expansion.registerHandler(new SpellPlaceholder());
        expansion.registerHandler(new ResearchPlaceholder());
        expansion.registerHandler(new HotbarPlaceholder());

        YamlConfiguration config = Alkatraz.getPluginConfig();
        if (config.getBoolean("placeholders.leaderboard.enabled", true)) {
            long refreshMinutes = config.getLong("placeholders.leaderboard.refresh_interval_minutes", 5);
            int maxEntries = config.getInt("placeholders.leaderboard.max_entries", 10);
            expansion.registerHandler(new LeaderboardPlaceholder(refreshMinutes, maxEntries));
        }

        expansion.register();
        Alkatraz.logInfo("PlaceholderAPI hook registered successfully!");
    }
}
```

- [ ] **Step 2: Modify Alkatraz.java onEnable()**

Add after line 161 (`SpellComponentHandler.tick();`):

```java
logVeryHigh("Initializing PlaceholderAPI hook...");
PlaceholderAPIHook placeholderHook = new PlaceholderAPIHook();
if (placeholderHook.isPresent()) {
    placeholderHook.ifPresent();
}
```

Also add the import at the top:

```java
import me.nagasonic.alkatraz.hooks.PlaceholderAPIHook;
```

- [ ] **Step 3: Add config.yml section**

Add to the end of `config.yml`:

```yaml
# PlaceholderAPI integration settings
placeholders:
  leaderboard:
    # Enable the leaderboard placeholders (%alkatraz_leaderboard_*)
    enabled: true
    # How often to refresh the leaderboard cache (in minutes)
    refresh_interval_minutes: 5
    # Maximum number of entries on the leaderboard
    max_entries: 10
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/hooks/PlaceholderAPIHook.java
git add core/src/main/java/me/nagasonic/alkatraz/Alkatraz.java
git add core/src/main/resources/config.yml
git commit -m "feat: wire PlaceholderAPI hook into plugin startup"
```

---

### Task 9: Build and Verify

- [ ] **Step 1: Run Maven build**

```bash
mvn clean package -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Verify all files exist**

```bash
ls -la core/src/main/java/me/nagasonic/alkatraz/hooks/placeholder/
```

Expected: 8 files (Placeholder.java, AlkatrazPlaceholder.java, StatsPlaceholder.java, ElementPlaceholder.java, SpellPlaceholder.java, ResearchPlaceholder.java, HotbarPlaceholder.java, LeaderboardPlaceholder.java)

- [ ] **Step 3: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: address build issues in PlaceholderAPI hook"
```
