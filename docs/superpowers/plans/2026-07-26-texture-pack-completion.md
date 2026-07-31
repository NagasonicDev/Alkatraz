# Texture Pack System Completion Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the texture pack system so all custom items render with pack textures when installed, and gracefully fall back to vanilla materials when not installed.

**Architecture:** Four independent tasks: (1) wire CMD values into magic item YAMLs, (2) wire CMD values into spell GUI icons, (3) implement resource pack auto-delivery, (4) add ASCII title fallback to remaining menus.

**Tech Stack:** Java 17, Spigot/Paper API 1.19-1.26, Maven multi-module.

## Global Constraints

- Minecraft 1.19 through 1.26 compatibility
- No new dependencies; use existing Bukkit/Spigot API
- Graceful fallback when resource pack is not installed (CMD=0 means no `setCustomModelData`)
- Do NOT commit after each task; accumulate all changes and commit once at the end

---

## File Structure

### Modified Files

| File | Change |
|------|--------|
| `core/src/main/resources/magic/items/*.yml` (123 files) | Add `custom_model_data` field |
| `core/src/main/resources/spells/*.yml` (36 files) | Add `gui_custom_model_data` field |
| `core/src/main/java/me/nagasonic/alkatraz/spells/Spell.java` | Load `gui_custom_model_data` from YAML, expose via getter |
| `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellsMenu.java` | Apply spell CMD to display items |
| `core/src/main/java/me/nagasonic/alkatraz/texturepack/TexturePackManager.java` | Load resource pack settings (URL, hash, prompt, message) |
| `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/EquipmentMenu.java` | Add ASCII title fallback |
| `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java` | Add ASCII title fallback |
| `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeBookMenu.java` | Add ASCII title fallback |
| `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/StatsMenu.java` | Add ASCII title fallback |
| `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/GrimoirePageMenu.java` | Add ASCII title fallback |
| `core/src/main/java/me/nagasonic/alkatraz/Alkatraz.java` | Register ResourcePackListener |

### New Files

| File | Purpose |
|------|---------|
| `core/src/main/java/me/nagasonic/alkatraz/texturepack/ResourcePackListener.java` | PlayerJoinEvent listener to send resource pack |

---

## Task 1: Add custom_model_data to all magic item YAMLs

**Files:**
- Modify: `core/src/main/resources/magic/items/*.yml` (123 files)

**Why:** `MagicItemConfigLoader.java:39` reads `config.getInt("custom_model_data", 0)`. Without this field, all items get CMD=0 and render as vanilla materials even with the pack installed.

**Mapping source:** `texturepack.yml` lines 30-144 define the CMD values per item key.

- [ ] **Step 1: Add `custom_model_data` to all wand YAMLs (9 files)**

For each wand file, add `custom_model_data: <value>` after the `material:` line. The mapping:

| File | CMD |
|------|-----|
| `wooden_wand.yml` | 1001 |
| `reinforced_wand.yml` | 1002 |
| `runic_wand.yml` | 1003 |
| `blaze_wand.yml` | 1004 |
| `glacier_wand.yml` | 1005 |
| `mountain_wand.yml` | 1006 |
| `storm_wand.yml` | 1007 |
| `luminous_wand.yml` | 1008 |
| `void_wand.yml` | 1009 |

Example for `wooden_wand.yml`:
```yaml
key: alkatraz:wooden_wand
material: STICK
custom_model_data: 1001
display_name: '&r&fWooden Wand'
```

- [ ] **Step 2: Add `custom_model_data` to all scroll YAMLs (4 files)**

| File | CMD |
|------|-----|
| `magic_missile_scroll.yml` | 1101 |
| `heal_scroll.yml` | 1102 |
| `fireball_scroll.yml` | 1103 |
| `earth_spike_scroll.yml` | 1104 |

- [ ] **Step 3: Add `custom_model_data` to all ring YAMLs (16 files)**

| File | CMD |
|------|-----|
| `zephyr_ring.yml` | 1201 |
| `void_ring.yml` | 1202 |
| `titan_ring.yml` | 1203 |
| `storm_ring.yml` | 1204 |
| `inferno_ring.yml` | 1205 |
| `abyss_ring.yml` | 1206 |
| `tempest_ring.yml` | 1207 |
| `divine_ring.yml` | 1208 |
| `nether_ring.yml` | 1209 |
| `mystic_ring.yml` | 1210 |
| `arcane_ring.yml` | 1211 |
| `ethereal_ring.yml` | 1212 |
| `ember_ring.yml` | 1213 |
| `frost_ring.yml` | 1214 |
| `boulder_ring.yml` | 1215 |
| `mountain_ring.yml` | 1216 |

- [ ] **Step 4: Add `custom_model_data` to all necklace YAMLs (16 files)**

| File | CMD |
|------|-----|
| `zephyr_necklace.yml` | 1217 |
| `void_necklace.yml` | 1218 |
| `titan_necklace.yml` | 1219 |
| `storm_necklace.yml` | 1220 |
| `inferno_necklace.yml` | 1221 |
| `abyss_necklace.yml` | 1222 |
| `tempest_necklace.yml` | 1223 |
| `divine_necklace.yml` | 1224 |
| `nether_necklace.yml` | 1225 |
| `mystic_necklace.yml` | 1226 |
| `arcane_necklace.yml` | 1227 |
| `ethereal_necklace.yml` | 1228 |
| `ember_necklace.yml` | 1229 |
| `frost_necklace.yml` | 1230 |
| `boulder_necklace.yml` | 1231 |
| `mountain_necklace.yml` | 1232 |

- [ ] **Step 5: Add `custom_model_data` to all pendant YAMLs (16 files)**

| File | CMD |
|------|-----|
| `zephyr_pendant.yml` | 1233 |
| `void_pendant.yml` | 1234 |
| `titan_pendant.yml` | 1235 |
| `storm_pendant.yml` | 1236 |
| `inferno_pendant.yml` | 1237 |
| `abyss_pendant.yml` | 1238 |
| `tempest_pendant.yml` | 1239 |
| `divine_pendant.yml` | 1240 |
| `nether_pendant.yml` | 1241 |
| `mystic_pendant.yml` | 1242 |
| `arcane_pendant.yml` | 1243 |
| `ethereal_pendant.yml` | 1244 |
| `ember_pendant.yml` | 1245 |
| `frost_pendant.yml` | 1246 |
| `boulder_pendant.yml` | 1247 |
| `mountain_pendant.yml` | 1248 |

- [ ] **Step 6: Add `custom_model_data` to all bracelet YAMLs (16 files)**

| File | CMD |
|------|-----|
| `zephyr_bracelet.yml` | 1249 |
| `void_bracelet.yml` | 1250 |
| `titan_bracelet.yml` | 1251 |
| `storm_bracelet.yml` | 1252 |
| `inferno_bracelet.yml` | 1253 |
| `abyss_bracelet.yml` | 1254 |
| `tempest_bracelet.yml` | 1255 |
| `divine_bracelet.yml` | 1256 |
| `nether_bracelet.yml` | 1257 |
| `mystic_bracelet.yml` | 1258 |
| `arcane_bracelet.yml` | 1259 |
| `ethereal_bracelet.yml` | 1260 |
| `ember_bracelet.yml` | 1261 |
| `frost_bracelet.yml` | 1262 |
| `boulder_bracelet.yml` | 1263 |
| `mountain_bracelet.yml` | 1264 |

Note: `radiant_*` and `shadow_*` files exist in the directory but have no matching entries in `texturepack.yml`. Leave these without `custom_model_data` (they will correctly default to 0).

- [ ] **Step 7: Add `custom_model_data` to all equipment YAMLs (12 files)**

| File | CMD |
|------|-----|
| `scholar_hat.yml` | 2801 |
| `scholar_robe.yml` | 2802 |
| `scholar_leggings.yml` | 2803 |
| `scholar_boots.yml` | 2804 |
| `enchanter_hat.yml` | 2805 |
| `enchanter_robe.yml` | 2806 |
| `enchanter_leggings.yml` | 2807 |
| `enchanter_boots.yml` | 2808 |
| `archmage_hat.yml` | 2809 |
| `archmage_robe.yml` | 2810 |
| `archmage_leggings.yml` | 2811 |
| `archmage_boots.yml` | 2812 |

Note: `apprentice_*` files exist but have no entries in `texturepack.yml`. Leave without `custom_model_data`.

- [ ] **Step 8: Add `custom_model_data` to miscellaneous items (9 files)**

| File | CMD |
|------|-----|
| `magic_stone.yml` | 5001 |
| `leather_grimoire.yml` | 5002 |
| `runic_grimoire.yml` | 5003 |
| `blaze_grimoire.yml` | 5004 |
| `glacier_grimoire.yml` | 5005 |
| `mountain_grimoire.yml` | 5006 |
| `storm_grimoire.yml` | 5007 |
| `luminous_grimoire.yml` | 5008 |
| `void_grimoire.yml` | 5009 |

---

## Task 2: Add CMD to spell GUI icons

**Files:**
- Modify: `core/src/main/resources/spells/*.yml` (36 files)
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/Spell.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellsMenu.java`

**Why:** Spell icons in the SpellsMenu use vanilla materials without CMD. The texturepack.yml defines CMDs 4101-4136 but they're never applied.

- [ ] **Step 1: Add `gui_custom_model_data` field to Spell.java**

In `Spell.java`, add a field and getter for the GUI CMD:

```java
// After line 43 (protected ItemStack guiItem;)
protected int guiCustomModelData;
```

In `loadCommonConfig()` after line 230 (`this.guiItem = ...`), add:

```java
this.guiCustomModelData = spellConfig.getInt("gui_custom_model_data", 0);
```

Add getter after the existing `getGuiItem()` method (after line 411):

```java
public int getGuiCustomModelData() { return guiCustomModelData; }
```

- [ ] **Step 2: Add `gui_custom_model_data` to all 36 spell YAMLs**

Add `gui_custom_model_data: <value>` to each spell file. The mapping (from `texturepack.yml` lines 160-195):

| File | CMD |
|------|-----|
| `magic_missile.yml` | 4101 |
| `fireball.yml` | 4102 |
| `heal.yml` | 4103 |
| `tornado.yml` | 4104 |
| `meteor_shower.yml` | 4105 |
| `tsunami.yml` | 4106 |
| `earthsplitter.yml` | 4107 |
| `radiance.yml` | 4108 |
| `shadow_realm.yml` | 4109 |
| `buff.yml` | 4110 |
| `light_buff.yml` | 4111 |
| `debuff.yml` | 4112 |
| `stealth.yml` | 4113 |
| `air_blades.yml` | 4114 |
| `air_burst.yml` | 4115 |
| `barrier.yml` | 4116 |
| `blink.yml` | 4117 |
| `dark_tendrils.yml` | 4118 |
| `detect.yml` | 4119 |
| `disguise.yml` | 4120 |
| `earthen_wall.yml` | 4121 |
| `earth_spike.yml` | 4122 |
| `earth_throw.yml` | 4123 |
| `flaming_volley.yml` | 4124 |
| `geyser.yml` | 4125 |
| `summon_zombies.yml` | 4126 |
| `swift.yml` | 4127 |
| `tremor.yml` | 4128 |
| `water_pulse.yml` | 4129 |
| `water_sphere.yml` | 4130 |
| `whirlpool.yml` | 4131 |
| `wind_barrier.yml` | 4132 |
| `wind_vortex.yml` | 4133 |
| `fire_blast.yml` | 4134 |
| `fire_wall.yml` | 4135 |
| `lesser_heal.yml` | 4136 |

Example for `magic_missile.yml`:
```yaml
id: 'magic_missile'
display_name: '&bMagic Missile'
gui_item: 'IRON_NUGGET'
gui_custom_model_data: 4101
```

- [ ] **Step 3: Apply spell CMD in SpellsMenu.java**

In `SpellsMenu.java`, modify `createDiscoveredSpellItem()` (around line 114) to apply the CMD:

```java
// Replace:
ItemStack item = ItemBuilder.of(spell.getGuiItem().clone())
        .rawName(ColorFormat.format(spell.getDisplayName()))
        .rawLore(lore)
        .hideAttributes()
        .glint(false)
        .build();

// With:
ItemStack item = ItemBuilder.of(spell.getGuiItem().clone())
        .rawName(ColorFormat.format(spell.getDisplayName()))
        .rawLore(lore)
        .hideAttributes()
        .glint(false)
        .build();
if (spell.getGuiCustomModelData() > 0) {
    item.getItemMeta().setCustomModelData(spell.getGuiCustomModelData());
    item.setItemMeta(item.getItemMeta());
}
```

Note: `ItemBuilder.build()` returns a finished `ItemStack`. We apply CMD after build since `ItemBuilder` doesn't have a CMD method that takes a Spell reference. Alternatively, check if `ItemBuilder.customModelData(int)` exists and use it in the builder chain.

---

## Task 3: Implement resource pack delivery

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/texturepack/TexturePackManager.java`
- Create: `core/src/main/java/me/nagasonic/alkatraz/texturepack/ResourcePackListener.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/Alkatraz.java`

**Why:** `texturepack.yml` defines URL, hash, prompt, and message for the resource pack, but `loadResourcePackSettings()` is empty and no listener sends the pack to players.

- [ ] **Step 1: Implement loadResourcePackSettings() in TexturePackManager.java**

Replace the empty method (lines 55-57):

```java
private static String resourcePackUrl = "";
private static String resourcePackHash = "";
private static String resourcePackPrompt = "";
private static String resourcePackMessage = "";

private static void loadResourcePackSettings() {
    resourcePackUrl = config.getString("resource_pack.url", "");
    resourcePackHash = config.getString("resource_pack.hash", "");
    resourcePackPrompt = config.getString("resource_pack.prompt", "");
    resourcePackMessage = config.getString("resource_pack.message", "");
}
```

Add getters:

```java
public static String getResourcePackUrl() { return resourcePackUrl; }
public static String getResourcePackHash() { return resourcePackHash; }
public static String getResourcePackPrompt() { return resourcePackPrompt; }
public static String getResourcePackMessage() { return resourcePackMessage; }
```

- [ ] **Step 2: Create ResourcePackListener.java**

```java
package me.nagasonic.alkatraz.texturepack;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.logging.Level;

public class ResourcePackListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!TexturePackManager.isResourcePackEnabled()) return;

        String url = TexturePackManager.getResourcePackUrl();
        if (url == null || url.isEmpty()) return;

        Player player = event.getPlayer();
        String hash = TexturePackManager.getResourcePackHash();
        String prompt = TexturePackManager.getResourcePackPrompt();
        boolean required = Alkatraz.isResourcePackForced();

        try {
            player.setResourcePack(
                    "alkatraz",
                    url,
                    hash != null ? hash : "",
                    prompt != null ? prompt : "Alkatraz Texture Pack",
                    required
            );
        } catch (Exception e) {
            Alkatraz.logWarning("Failed to send resource pack to " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        Player player = event.getPlayer();

        switch (status) {
            case ACCEPTED -> Alkatraz.getInstance().getLogger().info(player.getName() + " accepted the resource pack");
            case DECLINED -> Alkatraz.getInstance().getLogger().info(player.getName() + " declined the resource pack");
            case FAILED_DOWNLOAD -> Alkatraz.getInstance().getLogger().warning(player.getName() + " failed to download the resource pack");
            case DOWNLOADED -> Alkatraz.getInstance().getLogger().info(player.getName() + " downloaded the resource pack");
            case SUCCESS -> Alkatraz.getInstance().getLogger().info(player.getName() + " successfully applied the resource pack");
        }
    }
}
```

- [ ] **Step 3: Register ResourcePackListener in Alkatraz.java**

In `onEnable()`, after the existing listener registrations (around line 155), add:

```java
registerListener(new ResourcePackListener());
```

Add the import at the top of the file:

```java
import me.nagasonic.alkatraz.texturepack.ResourcePackListener;
```

---

## Task 4: Add ASCII title fallback to remaining menus

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/EquipmentMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeBookMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/StatsMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/GrimoirePageMenu.java`

**Why:** Only SpellsMenu uses `TexturePackManager.getMenuTitleCode()` for custom menu titles. The other 5 main menus use plain lang strings, so their custom title textures (defined in `texturepack.yml` ascii_codes.menu_titles) are never used.

**Pattern** (from `SpellsMenu.java` lines 46-52):
```java
private static String getResourceTitle() {
    String code = Alkatraz.getTexturePackManager().getMenuTitleCode("spells");
    if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
        return lang().get("menu.spells");
    }
    return code;
}
```

- [ ] **Step 1: Add fallback to EquipmentMenu.java**

Current title (line 38): `ColorFormat.format(Alkatraz.getLangManager().get("menu.equipment"))`

Add a static method and update the constructor call:

```java
private static String getResourceTitle() {
    String code = Alkatraz.getTexturePackManager().getMenuTitleCode("equipment");
    if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
        return ColorFormat.format(Alkatraz.getLangManager().get("menu.equipment"));
    }
    return code;
}
```

Update the `super()` call to use `getResourceTitle()`.

- [ ] **Step 2: Add fallback to ResearchGraphMenu.java**

Current title (line 61): `lang().get("menu.research_library")`

Add:
```java
private static String getResourceTitle() {
    String code = Alkatraz.getTexturePackManager().getMenuTitleCode("research");
    if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
        return lang().get("menu.research_library");
    }
    return code;
}
```

- [ ] **Step 3: Add fallback to RecipeBookMenu.java**

Current title (line 30): `ColorFormat.format(Alkatraz.getLangManager().get("menu.recipe_book"))`

Add:
```java
private static String getResourceTitle() {
    String code = Alkatraz.getTexturePackManager().getMenuTitleCode("recipes");
    if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
        return ColorFormat.format(Alkatraz.getLangManager().get("menu.recipe_book"));
    }
    return code;
}
```

- [ ] **Step 4: Add fallback to StatsMenu.java**

Current title (line 35): `lang().get("menu.stats", "player", target.getName())`

Add:
```java
private String getResourceTitle() {
    String code = Alkatraz.getTexturePackManager().getMenuTitleCode("stats");
    if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
        return lang().get("menu.stats", "player", target.getName());
    }
    return code;
}
```

Note: `StatsMenu` takes a `target` player parameter, so the fallback must include the player name substitution. The resource pack code is a static image and won't include the player name - this is acceptable since the custom texture is decorative.

- [ ] **Step 5: Add fallback to GrimoirePageMenu.java**

Current title (line 40): `lang().get("menu.grimoire")`

Add:
```java
private static String getResourceTitle() {
    String code = Alkatraz.getTexturePackManager().getMenuTitleCode("grimoire");
    if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
        return lang().get("menu.grimoire");
    }
    return code;
}
```

---

## Verification

After all tasks are complete:

1. Build the project: `mvn clean package -pl core -am`
2. Verify no compilation errors
3. Check that all 123 magic item YAMLs have `custom_model_data` values matching `texturepack.yml`
4. Check that all 36 spell YAMLs have `gui_custom_model_data` values matching `texturepack.yml`
5. Verify `ResourcePackListener` is registered in `Alkatraz.java`
6. Verify all 6 main menus have the `getResourceTitle()` pattern
