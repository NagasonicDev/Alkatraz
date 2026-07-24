# UI Menu System Improvements Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify the menu system's visual language, eliminate code duplication, and improve UX consistency across all 30+ menus.

**Architecture:** Introduce a shared `ItemBuilder` utility and add convenience methods to the `Menu` base class. Migrate all menus to use `GUIItemRegistry` items consistently. Standardize navigation layout, back buttons, and header conventions. No structural changes to the menu class hierarchy.

**Tech Stack:** Java 17, Bukkit/Spigot API, Maven, NBT-API library, custom `GUIItemRegistry` + `TexturePackManager` integration.

## Global Constraints

- Java 17 (source/target)
- Maven build: `mvn clean package -pl core` from project root
- No unit tests exist — verification is compile-only
- Must preserve `TexturePackManager` integration in `GUIItemRegistry`
- Must not break existing menu click handlers or NBT action routing
- All GUI text goes through `ColorFormat.format()`
- Package root: `me.nagasonic.alkatraz`

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `gui/ItemBuilder.java` | **Create** | Fluent item creation utility |
| `gui/Menu.java` | **Modify** | Add `fillAll()`, `fillBorders(int...)`, `openSound()` |
| `gui/PagedMenu.java` | **Modify** | Standardize nav slot defaults, add `addStandardBorder()` |
| `gui/GUIItemRegistry.java` | **Modify** | Add `"close_button"` registration |
| `util/StringUtils.java` | **Modify** | Add `prettifyKey()` method |
| `gui/implementation/SpellsMenu.java` | **Modify** | Use ItemBuilder, standardize border |
| `gui/implementation/StatsMenu.java` | **Modify** | Use ItemBuilder, standardize borders, add confirmation for reset |
| `gui/implementation/WandTableSelectionMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks |
| `gui/implementation/ProgressionMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks |
| `gui/implementation/HotbarSpellSelectionMenu.java` | **Modify** | Use ItemBuilder, standardize back button |
| `gui/implementation/SpellOptionsMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks |
| `gui/implementation/SpellOptionValuesMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks |
| `gui/implementation/SpellOptionValuesMenu.java` | **Modify** | Use ItemBuilder |
| `gui/implementation/CircleUpConfirmationMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks |
| `gui/implementation/GrimorePageMenu.java` | **Modify** | Use ItemBuilder, fix spine centering |
| `gui/implementation/RecipeBookMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks, deduplicate prettifyKey |
| `gui/implementation/RecipeDetailMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks, deduplicate prettifyKey |
| `gui/implementation/EquipmentMenu.java` | **Modify** | Use ItemBuilder, make empty slots visually distinct |
| `gui/implementation/engraving/EngravingTableMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks, deduplicate prettifyKey |
| `gui/implementation/research/ResearchGraphMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks |
| `gui/implementation/options/PooledSlotSelectionMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks |
| `gui/implementation/editor/ItemEditorMenu.java` | **Modify** | Use ItemBuilder, use GUIItemRegistry blanks |

---

### Task 1: Create ItemBuilder Utility

**Files:**
- Create: `core/src/main/java/me/nagasonic/alkatraz/gui/ItemBuilder.java`

**Interfaces:**
- Consumes: `ColorFormat.format()` for text formatting
- Produces: `ItemBuilder` class used by all subsequent tasks

- [ ] **Step 1: Create the ItemBuilder class**

```java
package me.nagasonic.alkatraz.gui;

import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating ItemStacks with formatted display names and lore.
 *
 * <p>Usage:
 * <pre>
 *   ItemStack item = ItemBuilder.of(Material.PAPER)
 *       .name("&eTitle")
 *       .lore("&7Line one", "&7Line two")
 *       .glint(true)
 *       .build();
 * </pre>
 */
public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder of(ItemStack existing) {
        ItemBuilder b = new ItemBuilder(existing.getType());
        b.item.setItemMeta(existing.getItemMeta());
        return b;
    }

    /** Set display name with color formatting (& codes and hex). */
    public ItemBuilder name(String name) {
        meta.setDisplayName(ColorFormat.format(name));
        return this;
    }

    /** Set display name without color formatting. */
    public ItemBuilder rawName(String name) {
        meta.setDisplayName(name);
        return this;
    }

    /** Add lore lines with color formatting. */
    public ItemBuilder lore(String... lines) {
        for (String line : lines) {
            lore.add(ColorFormat.format(line));
        }
        return this;
    }

    /** Add a single blank lore line. */
    public ItemBuilder blankLine() {
        lore.add("");
        return this;
    }

    /** Set the full lore, replacing any existing lines. Lines are color-formatted. */
    public ItemBuilder setLore(List<String> lines) {
        lore.clear();
        for (String line : lines) {
            lore.add(ColorFormat.format(line));
        }
        return this;
    }

    /** Set the full lore raw (no color formatting). */
    public ItemBuilder rawLore(List<String> lines) {
        lore.clear();
        lore.addAll(lines);
        return this;
    }

    /** Append raw (already-formatted) lore lines. */
    public ItemBuilder appendLore(List<String> lines) {
        lore.addAll(lines);
        return this;
    }

    /** Get the current lore list (for conditional additions). */
    public List<String> getLore() {
        return lore;
    }

    /** Set item amount. */
    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /** Toggle enchant glint (hidden durability enchant). Useful for "selected" or "locked" states. */
    public ItemBuilder glint(boolean enabled) {
        if (enabled) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /** Hide all attribute modifiers. */
    public ItemBuilder hideAttributes() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return this;
    }

    /** Set custom model data (for texture pack integration). */
    public ItemBuilder customModelData(int cmd) {
        meta.setCustomModelData(cmd);
        return this;
    }

    /** Build the final ItemStack. */
    public ItemStack build() {
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl core -q` from `D:\Alkatraz\Alkatraz`
Expected: BUILD SUCCESS (no output on success)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/ItemBuilder.java
git commit -m "feat(gui): add ItemBuilder fluent utility for item creation"
```

---

### Task 2: Add Convenience Methods to Menu Base Classes

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/Menu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/PagedMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/GUIItemRegistry.java`

**Interfaces:**
- Consumes: `GUIItemRegistry.getItem()` for blank items, `ItemBuilder` from Task 1
- Produces: `Menu.fillAll()`, `Menu.fillBorders(int...)`, `PagedMenu.addStandardBorder()`, `GUIItemRegistry.getItem("close_button")`

- [ ] **Step 1: Add border-filling and sound methods to Menu.java**

Add these methods to `Menu.java` after the existing `close()` method:

```java
    /**
     * Fill every inventory slot with the GUIItemRegistry blank item.
     */
    protected void fillAll() {
        ItemStack blank = Alkatraz.getGuiItemRegistry().getItem("blank");
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, blank.clone());
        }
    }

    /**
     * Fill every inventory slot with the GUIItemRegistry blank item,
     * excluding the specified slot numbers.
     */
    protected void fillBorders(int... excludeSlots) {
        java.util.Set<Integer> excluded = new java.util.HashSet<>();
        for (int s : excludeSlots) excluded.add(s);
        ItemStack blank = Alkatraz.getGuiItemRegistry().getItem("blank");
        for (int i = 0; i < size; i++) {
            if (!excluded.contains(i)) {
                inventory.setItem(i, blank.clone());
            }
        }
    }

    /**
     * Play a subtle menu-open sound for the viewer.
     */
    protected void playOpenSound() {
        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }
```

Also modify the `open()` method to call `playOpenSound()`:

```java
    public void open() {
        build();
        viewer.openInventory(inventory);
        activeMenus.put(viewer.getUniqueId(), this);
        playOpenSound();
    }
```

- [ ] **Step 2: Add standard border method to PagedMenu.java**

Add this method to `PagedMenu.java` after `addDecorations()`:

```java
    /**
     * Fill all non-content, non-nav slots with blank panes.
     * Uses the GUIItemRegistry blank item for texture pack support.
     */
    protected void addStandardBorder() {
        java.util.Set<Integer> reserved = new java.util.HashSet<>();
        for (int s : contentSlots) reserved.add(s);
        reserved.add(nextPageSlot);
        reserved.add(previousPageSlot);
        reserved.add(backButtonSlot);
        ItemStack blank = Alkatraz.getGuiItemRegistry().getItem("blank");
        for (int i = 0; i < size; i++) {
            if (!reserved.contains(i)) {
                inventory.setItem(i, blank.clone());
            }
        }
    }
```

Add the necessary import at the top of `PagedMenu.java`:
```java
import me.nagasonic.alkatraz.Alkatraz;
```

- [ ] **Step 3: Add close_button to GUIItemRegistry**

Add to `registerStandardItems()` in `GUIItemRegistry.java`:

```java
        registerItem("close_button", createCloseButton());
```

Add the new method:

```java
    private static ItemStack createCloseButton() {
        Material material = TexturePackManager.getGuiMaterial("button_close");
        if (material == null) material = Material.BARRIER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ColorFormat.format("&cClose"));

        int cmd = TexturePackManager.getGUICMD("close_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        item.setItemMeta(meta);
        return item;
    }
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -pl core -q` from `D:\Alkatraz\Alkatraz`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/Menu.java core/src/main/java/me/nagasonic/alkatraz/gui/PagedMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/GUIItemRegistry.java
git commit -m "feat(gui): add fillAll/fillBorders/openSound to Menu, standardBorder to PagedMenu, close_button to registry"
```

---

### Task 3: Deduplicate prettifyKey into StringUtils

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/util/StringUtils.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeBookMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeDetailMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/EngravingTableMenu.java`

**Interfaces:**
- Consumes: existing `StringUtils` class
- Produces: `StringUtils.prettifyKey(String)` used by 3 menu files

- [ ] **Step 1: Add prettifyKey to StringUtils.java**

Add before the closing brace of `StringUtils`:

```java
    /**
     * Converts a snake_case registry key to a human-readable Title Case string.
     * Strips namespace prefix if present (e.g. "alkatraz:fire_bolt" → "Fire Bolt").
     */
    public static String prettifyKey(String key) {
        if (key == null) return "";
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
```

- [ ] **Step 2: Replace prettifyKey in RecipeBookMenu.java**

Find the private `prettifyKey` method in `RecipeBookMenu.java` (around line 144-156) and delete it entirely. Update the two call sites:

Line 122: change `prettifyKey(recipe.key().getKey())` → `StringUtils.prettifyKey(recipe.key().getKey())`

Add import if not present:
```java
import me.nagasonic.alkatraz.util.StringUtils;
```

- [ ] **Step 3: Replace prettifyKey in RecipeDetailMenu.java**

Delete the private `prettifyKey` method (around line 110-122). Update the call at line 83:

`prettifyKey(materials.get(0).getKey().getKey())` → `StringUtils.prettifyKey(materials.get(0).getKey().getKey())`

Add import:
```java
import me.nagasonic.alkatraz.util.StringUtils;
```

- [ ] **Step 4: Replace prettifyKey in EngravingTableMenu.java**

Delete the private `prettifyKey` method (around line 315-325). Update the two call sites at lines 126 and 128:

`prettifyKey(def.getKey().getKey())` → `StringUtils.prettifyKey(def.getKey().getKey())`
`prettifyKey(t.getKey().getKey())` → `StringUtils.prettifyKey(t.getKey().getKey())`

Add import:
```java
import me.nagasonic.alkatraz.util.StringUtils;
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -pl core -q` from `D:\Alkatraz\Alkatraz`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/util/StringUtils.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeBookMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeDetailMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/EngravingTableMenu.java
git commit -m "refactor: deduplicate prettifyKey into StringUtils"
```

---

### Task 4: Migrate SpellsMenu to ItemBuilder and Standard Border

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellsMenu.java`

**Interfaces:**
- Consumes: `ItemBuilder` (Task 1), `Menu.fillAll()` (Task 2), `GUIItemRegistry` (existing)

- [ ] **Step 1: Rewrite SpellsMenu.java**

Replace the full file content. Key changes:
- `addDecorations()`: use `fillAll()` instead of manual loop
- `createConfigureHotbarItem()`: use `ItemBuilder`
- `createDiscoveredSpellItem()`: use `ItemBuilder`
- `createLockedSpellItem()`: use `ItemBuilder`
- Add `import me.nagasonic.alkatraz.gui.ItemBuilder;`

```java
package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.texturepack.TexturePackManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SpellsMenu extends PagedMenu<Spell> {

    private static final int CONFIGURE_HOTBAR_SLOT = 49;

    public SpellsMenu(Player viewer) {
        super(viewer,
                getResourceTitle(),
                54,
                getSortedSpells(),
                28);
        this.contentSlots = getInnerContentSlots();
    }

    private static List<Spell> getSortedSpells() {
        return SpellRegistry.getAllSpellsByIdFull().values().stream()
                .sorted(Comparator.comparingInt(Spell::getLevel)
                        .thenComparing(Spell::getDisplayName))
                .collect(Collectors.toList());
    }

    private static String getResourceTitle() {
        String code = Alkatraz.getTexturePackManager().getMenuTitleCode("spells");
        if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
            return ColorFormat.format("&5Spells");
        }
        return code;
    }

    private static int[] getInnerContentSlots() {
        int[] slots = new int[28];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[idx++] = row * 9 + col;
            }
        }
        return slots;
    }

    @Override
    protected void addDecorations() {
        fillAll();
        inventory.setItem(CONFIGURE_HOTBAR_SLOT, createConfigureHotbarItem());
    }

    private ItemStack createConfigureHotbarItem() {
        return ItemBuilder.of(Material.COMPARATOR)
                .name("&5Configure Spell Hotbar")
                .lore("&7Assign spells to your wand hotbar slots.",
                      "&7These appear when you hold a wand.")
                .build();
    }

    @Override
    protected ItemStack createDisplayItem(Spell spell, int index) {
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);

        boolean discovered = profile.hasDiscoveredSpell(spell)
                || viewer.hasPermission("alkatraz.allspells");

        return discovered
                ? createDiscoveredSpellItem(spell, profile)
                : createLockedSpellItem(spell);
    }

    private ItemStack createDiscoveredSpellItem(Spell spell, MagicProfile profile) {
        List<String> lore = new ArrayList<>();
        for (String line : spell.getDescription()) {
            lore.add(ColorFormat.format(line));
        }
        lore.add("");
        lore.add(ColorFormat.format("&bCode: " + spell.getCode()));
        lore.add(ColorFormat.format("&bMana Cost: " + spell.getCost()));
        lore.add(ColorFormat.format("&bCooldown: " + spell.getCooldown() + "s"));
        lore.add(ColorFormat.format("&bCast Time: " + spell.getCastTime() + "s"));
        lore.add(ColorFormat.format("&bElement: " + spell.getElement().getName()));
        lore.add(ColorFormat.format("&bMastery: " + profile.getSpellMastery(spell) + "/" + spell.getMaxMastery()));
        lore.add("");
        lore.add(ColorFormat.format("&eCircle: " + spell.getRequiredCircleLevel()));

        if (!spell.getAllOptions().isEmpty()) {
            lore.add("");
            lore.add(ColorFormat.format("&aHas Spell Options"));
            lore.add(ColorFormat.format("&7Click to configure"));
        }

        ItemStack item = ItemBuilder.of(spell.getGuiItem().clone())
                .rawName(ColorFormat.format(spell.getDisplayName()))
                .rawLore(lore)
                .hideAttributes()
                .glint(false)
                .build();

        setMenuData(item, "spell_type", spell.getType());
        setMenuData(item, "has_options", !spell.getAllOptions().isEmpty());

        return item;
    }

    private ItemStack createLockedSpellItem(Spell spell) {
        return ItemBuilder.of(Material.GRAY_DYE)
                .name("&8???")
                .lore(ColorFormat.format("&7Circle: " + spell.getRequiredCircleLevel()))
                .build();
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if ("open_hotbar_config".equals(action)) {
            new HotbarSpellSelectionMenu(viewer).open();
            return true;
        }

        return super.handleClick(event, clicked);
    }

    @Override
    protected void handleContentClick(Spell spell, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        boolean hasOptions = getBoolData(clicked, "has_options");

        if (hasOptions) {
            SpellOptionsMenu optionsMenu = new SpellOptionsMenu(viewer, spell);
            optionsMenu.open();
        }
    }
}
```

**Note on lore handling:** The `lore` list for discovered spells contains raw `ColorFormat.format()` output. We use `rawLore()` instead of `lore()` to avoid double-formatting.

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl core -q` from `D:\Alkatraz\Alkatraz`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellsMenu.java
git commit -m "refactor(gui): migrate SpellsMenu to ItemBuilder and standard border"
```

---

### Task 5: Migrate StatsMenu — Add Reset Confirmation

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/StatsMenu.java`

**Interfaces:**
- Consumes: `ItemBuilder` (Task 1), `Menu.fillAll()` (Task 2)
- Produces: Updated StatsMenu with ItemBuilder usage and confirmation dialog for stat reset

- [ ] **Step 1: Rewrite StatsMenu.java**

Key changes:
- Use `fillAll()` instead of `Utils.getBlank()` loop in `fillBorders()`
- Use `ItemBuilder` for all item creation
- Add confirmation step: "Reset Stats" click opens a confirmation menu (inline, not a separate class — a boolean `confirmingReset` flag that shows confirm/cancel buttons)
- Remove `Utils` import, add `ItemBuilder` import
- Remove `createPane()` private helper (now redundant)

```java
package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.ItemUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class StatsMenu extends Menu {
    private final Player target;
    private final int affinityIncrease;
    private final int resistanceIncrease;
    private boolean confirmingReset = false;

    private static final int[] DISPLAY_SLOTS = {19, 20, 21, 23, 24, 25};
    private static final Element[] DISPLAY_ELEMENTS = {Element.FIRE, Element.WATER, Element.EARTH, Element.AIR, Element.LIGHT, Element.DARK};

    public StatsMenu(Player viewer, Player target) {
        super(viewer, target.getName() + " Stats", 54);
        this.target = target;
        this.affinityIncrease = (Integer) Configs.AFFINITY_PER_POINT.get();
        this.resistanceIncrease = (Integer) Configs.RESISTANCE_PER_POINT.get();
    }

    @Override
    protected void build() {
        MagicProfile profile = ProfileManager.getProfile(target, MagicProfile.class);

        fillAll();

        if (confirmingReset) {
            buildResetConfirmation(profile);
            return;
        }

        inventory.setItem(1, createStatItem(Material.BEACON, "&eCircle Level",
                "&f" + profile.getCircleLevel()));
        inventory.setItem(2, createStatItem(Material.KNOWLEDGE_BOOK, "&bArcane Knowledge",
                "&f" + String.format("%.0f", profile.getArcaneKnowledge())));
        inventory.setItem(3, createStatItem(Material.AMETHYST_SHARD, "&dResearch Points",
                "&f" + profile.getResearchPoints()));
        inventory.setItem(4, createPlayerHead());
        inventory.setItem(5, createStatItem(Material.EXPERIENCE_BOTTLE, "&aStat Points",
                "&f" + profile.getStatPoints()));
        inventory.setItem(6, createStatItem(Material.POTION, "&bMana",
                "&f" + String.format("%.0f", profile.getMana()) + "/" + String.format("%.0f", profile.getMaxMana())));
        inventory.setItem(7, createStatItem(Material.SUGAR, "&bMana Regen",
                "&f" + profile.getManaRegeneration() + "/s"));

        for (int i = 0; i < 6; i++) {
            inventory.setItem(DISPLAY_SLOTS[i], createElementDisplay(profile, DISPLAY_ELEMENTS[i]));
        }

        for (int i = 0; i < 6; i++) {
            inventory.setItem(DISPLAY_SLOTS[i] + 9, createElementInvestItem(profile, DISPLAY_ELEMENTS[i]));
        }

        inventory.setItem(49, createResetButton(profile));
    }

    private void buildResetConfirmation(MagicProfile profile) {
        inventory.setItem(13, ItemBuilder.of(Material.NETHER_STAR)
                .name("&d&lReset All Stats?")
                .lore("",
                      "&7This will refund all invested points",
                      "&7and reset affinity/resistance to zero.",
                      "",
                      "&c&lThis action is NOT undoable!")
                .build());

        inventory.setItem(11, ItemBuilder.of(Material.LIME_WOOL)
                .name("&a&lConfirm Reset")
                .lore("&7Click to reset all stats.")
                .build());

        setMenuData(inventory.getItem(11), "action", "confirm_reset");

        inventory.setItem(15, ItemBuilder.of(Material.RED_WOOL)
                .name("&c&lCancel")
                .lore("&7Go back to stats.")
                .build());

        setMenuData(inventory.getItem(15), "action", "cancel_reset");
    }

    private ItemStack createStatItem(Material material, String label, String value) {
        return ItemBuilder.of(material)
                .name(label)
                .lore(value)
                .build();
    }

    private ItemStack createPlayerHead() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName(ColorFormat.format("&6" + target.getName()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createElementDisplay(MagicProfile profile, Element element) {
        Material material = switch (element) {
            case FIRE -> Material.FIRE_CHARGE;
            case WATER -> Material.HEART_OF_THE_SEA;
            case EARTH -> Material.DIRT;
            case AIR -> Material.FEATHER;
            case LIGHT -> Material.GLOWSTONE_DUST;
            case DARK -> Material.ECHO_SHARD;
            default -> Material.BARRIER;
        };
        return ItemBuilder.of(material)
                .name(element.getName())
                .lore(element.getColor() + "Affinity: &f" + String.format("%.1f", profile.getAffinity(element)),
                      element.getColor() + "Resistance: &f" + String.format("%.1f", profile.getResistance(element)))
                .build();
    }

    private ItemStack createElementInvestItem(MagicProfile profile, Element element) {
        Material material = switch (element) {
            case FIRE -> Material.MAGMA_CREAM;
            case WATER -> Material.HEART_OF_THE_SEA;
            case EARTH -> Material.GRASS_BLOCK;
            case AIR -> Material.FEATHER;
            case LIGHT -> Material.GLOWSTONE_DUST;
            case DARK -> Material.ECHO_SHARD;
            default -> Material.BARRIER;
        };

        List<String> lore = new ArrayList<>();
        int points = profile.getPoints(element);
        lore.add(ColorFormat.format("&eInvested Points: &6" + points));

        if (points > 0) {
            lore.add("");
            lore.add(ColorFormat.format("&eBonus:"));
            lore.add(ColorFormat.format("&7 - " + element.getColor() + "+" +
                (affinityIncrease * points) + " " + element.getName() + " Affinity"));
            lore.add(ColorFormat.format("&7 - " + element.getColor() + "+" +
                (resistanceIncrease * points) + " " + element.getName() + " Resistance"));
        }

        if (profile.getStatPoints() > 0) {
            lore.add("");
            lore.add(ColorFormat.format("&eClick to invest &61 &epoint."));
        }

        ItemStack item = ItemBuilder.of(material)
                .name(element.getName())
                .rawLore(lore)
                .amount(points > 0 ? points : 1)
                .build();

        setMenuData(item, "element", element.name().toLowerCase());
        return item;
    }

    private ItemStack createResetButton(MagicProfile profile) {
        return ItemBuilder.of(Material.BARRIER)
                .name("&dReset Stats")
                .lore("&dReset Tokens: &f" + profile.getResetTokens(),
                      "",
                      "&eClick to reset all invested stats.",
                      "&c&lTHIS IS NOT UNDOABLE")
                .build();
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return true;
        }

        if (!viewer.equals(target)) {
            viewer.sendMessage(ColorFormat.format("&cYou can only modify your own stats!"));
            return true;
        }

        MagicProfile profile = ProfileManager.getProfile(target, MagicProfile.class);
        String action = getStringData(clicked, "action");

        if ("confirm_reset".equals(action)) {
            handleStatsReset(profile);
            confirmingReset = false;
            return true;
        }

        if ("cancel_reset".equals(action)) {
            confirmingReset = false;
            refresh();
            return true;
        }

        if ("reset".equals(action)) {
            confirmingReset = true;
            refresh();
            return true;
        }

        String elementName = getStringData(clicked, "element");
        if (elementName != null && !elementName.isEmpty()) {
            handleElementInvestment(profile, elementName);
        }

        return true;
    }

    private void handleElementInvestment(MagicProfile profile, String elementName) {
        if (profile.getStatPoints() <= 0) {
            viewer.sendMessage(ColorFormat.format("&cYou don't have any stat points available!"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Element element = Element.valueOf(elementName.toUpperCase());

        profile.setStatPoints(profile.getStatPoints() - 1);

        int currentPoints = profile.getPoints(element);
        switch (element) {
            case FIRE -> profile.setFirePoints(currentPoints + 1);
            case WATER -> profile.setWaterPoints(currentPoints + 1);
            case AIR -> profile.setAirPoints(currentPoints + 1);
            case EARTH -> profile.setEarthPoints(currentPoints + 1);
            case LIGHT -> profile.setLightPoints(currentPoints + 1);
            case DARK -> profile.setDarkPoints(currentPoints + 1);
        }

        double currentAffinity = profile.getAffinity(element);
        double currentResistance = profile.getResistance(element);

        switch (element) {
            case FIRE -> {
                profile.setFireAffinity(currentAffinity + affinityIncrease);
                profile.setFireResistance(currentResistance + resistanceIncrease);
            }
            case WATER -> {
                profile.setWaterAffinity(currentAffinity + affinityIncrease);
                profile.setWaterResistance(currentResistance + resistanceIncrease);
            }
            case AIR -> {
                profile.setAirAffinity(currentAffinity + affinityIncrease);
                profile.setAirResistance(currentResistance + resistanceIncrease);
            }
            case EARTH -> {
                profile.setEarthAffinity(currentAffinity + affinityIncrease);
                profile.setEarthResistance(currentResistance + resistanceIncrease);
            }
            case LIGHT -> {
                profile.setLightAffinity(currentAffinity + affinityIncrease);
                profile.setLightResistance(currentResistance + resistanceIncrease);
            }
            case DARK -> {
                profile.setDarkAffinity(currentAffinity + affinityIncrease);
                profile.setDarkResistance(currentResistance + resistanceIncrease);
            }
        }

        viewer.sendMessage(ColorFormat.format("&aInvested 1 point into " + element.getName() + "!"));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        refresh();
    }

    private void handleStatsReset(MagicProfile profile) {
        if (profile.getResetTokens() <= 0) {
            viewer.sendMessage(ColorFormat.format("&cYou don't have any reset tokens!"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int totalPoints = 0;
        for (Element element : Element.values()) {
            if (element != Element.NONE) {
                totalPoints += profile.getPoints(element);
            }
        }

        if (totalPoints <= 0) {
            viewer.sendMessage(ColorFormat.format("&cYou have no stats to reset!"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        profile.setResetTokens(profile.getResetTokens() - 1);

        profile.setFirePoints(0);
        profile.setWaterPoints(0);
        profile.setAirPoints(0);
        profile.setEarthPoints(0);
        profile.setLightPoints(0);
        profile.setDarkPoints(0);

        profile.setFireAffinity(0);
        profile.setFireResistance(0);
        profile.setWaterAffinity(0);
        profile.setWaterResistance(0);
        profile.setAirAffinity(0);
        profile.setAirResistance(0);
        profile.setEarthAffinity(0);
        profile.setEarthResistance(0);
        profile.setLightAffinity(0);
        profile.setLightResistance(0);
        profile.setDarkAffinity(0);
        profile.setDarkResistance(0);

        profile.setStatPoints(profile.getStatPoints() + totalPoints);

        viewer.sendMessage(ColorFormat.format("&aStats reset! Refunded " + totalPoints + " stat points."));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        refresh();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl core -q` from `D:\Alkatraz\Alkatraz`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/StatsMenu.java
git commit -m "feat(gui): migrate StatsMenu to ItemBuilder, add reset confirmation dialog"
```

---

### Task 6: Migrate Remaining Menus (Batch 1 — Simple Menus)

This task migrates menus that need only ItemBuilder + blank-item changes. Do all files in one commit to keep the change atomic.

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/WandTableSelectionMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/ProgressionMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/CircleUpConfirmationMenu.java`

**Interfaces:**
- Consumes: `ItemBuilder` (Task 1), `Menu.fillAll()` (Task 2)

- [ ] **Step 1: Migrate WandTableSelectionMenu.java**

Replace `createPane()`, `createButton()`, `createInfoItem()` with `ItemBuilder`. Replace `GRAY_STAINED_GLASS_PANE` loop with `fillAll()`. Delete the private `createPane()` and `createButton()` helpers.

```java
package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.engraving.EngravingTableMenu;
import me.nagasonic.alkatraz.gui.implementation.research.ResearchGraphMenu;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class WandTableSelectionMenu extends Menu {

    private static final int SLOT_RESEARCH = 11;
    private static final int SLOT_PROGRESSION = 15;
    private static final int SLOT_ENGINEERING = 13;

    public WandTableSelectionMenu(Player viewer) {
        super(viewer, ColorFormat.format("&5Arcane Table"), 27);
    }

    @Override
    protected void build() {
        fillAll();

        inventory.setItem(4, ItemBuilder.of(Material.ENCHANTING_TABLE)
                .name("&dArcane Table")
                .lore("&7Choose your path:",
                      "&7  Research - &7Unlock new knowledge",
                      "&7  Progression - &7Advance your circle",
                      "&7  Engineering - &7Modify your equipment")
                .build());

        inventory.setItem(SLOT_RESEARCH, ItemBuilder.of(Material.BOOKSHELF)
                .name("&dResearch Library")
                .lore("&7Browse research trees, unlock new",
                      "&7spells and abilities through study.",
                      "",
                      "&eClick to open")
                .build());

        inventory.setItem(SLOT_PROGRESSION, ItemBuilder.of(Material.NETHER_STAR)
                .name("&dProgression")
                .lore("&7View your circle progression and",
                      "&7advance to the next circle when",
                      "&7requirements are met.",
                      "",
                      "&eClick to open")
                .build());

        inventory.setItem(SLOT_ENGINEERING, ItemBuilder.of(Material.SMITHING_TABLE)
                .name("&dMagic Engineering")
                .lore("&7Install engravings on your",
                      "&7wand or equipment items.",
                      "",
                      "&eClick to open")
                .build());
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        int slot = event.getSlot();
        if (slot == SLOT_RESEARCH) {
            new ResearchGraphMenu(viewer).open();
            return true;
        }
        if (slot == SLOT_PROGRESSION) {
            new ProgressionMenu(viewer).open();
            return true;
        }
        if (slot == SLOT_ENGINEERING) {
            new EngravingTableMenu(viewer).open();
            return true;
        }
        return true;
    }
}
```

- [ ] **Step 2: Migrate ProgressionMenu.java**

Replace `GRAY_STAINED_GLASS_PANE` loop with `fillAll()`. Use `ItemBuilder` for all items. Delete `createPane()`. Use `StringUtils.toRoman()` (already exists) instead of local `toRoman()`. Standardize back button to use `GUIItemRegistry`.

```java
package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.ProgressionService;
import me.nagasonic.alkatraz.progression.circle.CircleDefinition;
import me.nagasonic.alkatraz.progression.requirement.ProgressionRequirement;
import me.nagasonic.alkatraz.progression.requirement.RequirementContext;
import me.nagasonic.alkatraz.progression.requirement.implementation.ArcaneKnowledgeRequirement;
import me.nagasonic.alkatraz.progression.requirement.implementation.SpellMasteryRequirement;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ProgressionMenu extends Menu {

    public ProgressionMenu(Player viewer) {
        super(viewer, ColorFormat.format("&5Progression"), 27);
    }

    @Override
    protected void build() {
        fillAll();

        MagicProfile profile = ProfileManager.getProfile(viewer.getUniqueId(), MagicProfile.class);
        int currentCircle = profile.getCircleLevel();

        inventory.setItem(13, ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name("&dCircle Progression")
                .lore("&7Current Circle: &f" + currentCircle,
                      "&7Arcane Knowledge: &f" + (int) profile.getArcaneKnowledge(),
                      "",
                      "&aGreen &7= Completed",
                      "&eYellow &7= Working towards",
                      "&cRed &7= Locked")
                .build());

        for (int i = 0; i < 9; i++) {
            int circle = i + 1;
            inventory.setItem(9 + i, createCirclePane(circle, currentCircle, profile));
        }

        inventory.setItem(22, ItemBuilder.of(Material.ARROW)
                .name("&fBack")
                .build());

        setMenuData(inventory.getItem(22), "action", "back");
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if ("back".equals(action)) {
            new WandTableSelectionMenu(viewer).open();
            return true;
        }

        if ("circle_up".equals(action)) {
            int circle = getIntData(clicked, "circle");
            new CircleUpConfirmationMenu(viewer, circle).open();
            return true;
        }

        return true;
    }

    private ItemStack createCirclePane(int circle, int currentCircle, MagicProfile profile) {
        CircleDefinition definition = ProgressionService.getCircleDefinition(circle);
        boolean completed = circle <= currentCircle;
        boolean isNext = circle == currentCircle + 1;
        boolean canAdvance = isNext && ProgressionService.canAdvance(viewer, circle);

        Material material;
        String color;
        String statusText;

        if (completed) {
            material = Material.LIME_STAINED_GLASS_PANE;
            color = "&a";
            statusText = "&a&lCOMPLETED";
        } else if (isNext) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            color = "&e";
            statusText = "&e&lWORKING TOWARDS";
        } else {
            material = Material.RED_STAINED_GLASS_PANE;
            color = "&c";
            statusText = "&c&lLOCKED";
        }

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format(""));
        lore.add(ColorFormat.format(statusText));

        if (completed) {
            lore.add(ColorFormat.format("&7You have already mastered this circle."));
        } else {
            lore.add(ColorFormat.format("&7-------------------"));
            lore.add(ColorFormat.format("&e&lRequirements:"));
            if (definition != null) {
                RequirementContext context = new RequirementContext(viewer, profile, circle);
                for (ProgressionRequirement req : definition.getRequirements()) {
                    boolean met = req.isMet(context);
                    String metColor = met ? "&a" : "&c";
                    String desc = getRequirementDisplay(req, context);
                    lore.add(ColorFormat.format(metColor + "  " + (met ? "+" : "-") + " " + desc));
                }
            }

            lore.add(ColorFormat.format("&7-------------------"));
            lore.add(ColorFormat.format("&e&lRewards:"));
            if (definition != null) {
                if (definition.getStatPoints() > 0)
                    lore.add(ColorFormat.format("&a  +" + definition.getStatPoints() + " Stat Points"));
                lore.add(ColorFormat.format("&a  +" + (int) definition.getMaxMana() + " Max Mana"));
                lore.add(ColorFormat.format("&a  +" + definition.getManaRegeneration() + " Mana Regen/s"));
            }

            lore.add(ColorFormat.format("&7-------------------"));

            if (isNext) {
                if (canAdvance) {
                    lore.add(ColorFormat.format("&e&lClick to advance!"));
                } else {
                    lore.add(ColorFormat.format("&eClick for details"));
                }
            } else if (circle > currentCircle + 1) {
                lore.add(ColorFormat.format("&cComplete the previous circle first"));
            }
        }

        ItemStack item = ItemBuilder.of(material)
                .name(color + "Circle " + StringUtils.toRoman(circle))
                .rawLore(lore)
                .build();

        if (isNext) {
            setMenuData(item, "action", "circle_up");
            setMenuData(item, "circle", circle);
        }

        return item;
    }

    private String getRequirementDisplay(ProgressionRequirement req, RequirementContext context) {
        if (req instanceof ArcaneKnowledgeRequirement akReq) {
            double current = context.getProfile().getArcaneKnowledge();
            double needed = akReq.getAmount();
            return "Arcane Knowledge: " + (int) current + "/" + (int) needed;
        }
        if (req instanceof SpellMasteryRequirement smReq) {
            Spell spell = SpellRegistry.getSpell(smReq.getSpellId());
            String spellName = spell != null ? spell.getDisplayName() : smReq.getSpellId();
            int current = context.getProfile().getSpellMastery(spell);
            int needed = smReq.getMastery();
            boolean met = req.isMet(context);
            String metColor = met ? "&a" : "&c";
            return ColorFormat.format("Spell Mastery (" + spellName + metColor + "): " + Math.max(0, current) + "/" + needed);
        }
        return req.describe();
    }
}
```

- [ ] **Step 3: Migrate CircleUpConfirmationMenu.java**

Replace `createPane()` with `fillAll()`. Use `ItemBuilder`.

```java
package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.ProgressionService;
import me.nagasonic.alkatraz.progression.CircleUpAnimation;
import me.nagasonic.alkatraz.progression.circle.CircleDefinition;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CircleUpConfirmationMenu extends Menu {

    private final int targetCircle;

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;

    public CircleUpConfirmationMenu(Player viewer, int targetCircle) {
        super(viewer, ColorFormat.format("&5&lConfirm Circle Up"), 27);
        this.targetCircle = targetCircle;
    }

    @Override
    protected void build() {
        fillAll();

        MagicProfile profile = ProfileManager.getProfile(viewer.getUniqueId(), MagicProfile.class);
        CircleDefinition def = ProgressionService.getCircleDefinition(targetCircle);

        List<String> infoLore = new ArrayList<>();
        infoLore.add(ColorFormat.format("&7You are about to advance to the"));
        infoLore.add(ColorFormat.format("&d" + StringUtils.toOrdinal(targetCircle) + " &7circle."));
        infoLore.add(ColorFormat.format(""));
        infoLore.add(ColorFormat.format("&e&lRewards:"));
        if (def != null) {
            if (def.getStatPoints() > 0)
                infoLore.add(ColorFormat.format("&a  +" + def.getStatPoints() + " Stat Points"));
            infoLore.add(ColorFormat.format("&a  +" + (int) def.getMaxMana() + " Max Mana"));
            infoLore.add(ColorFormat.format("&a  +" + def.getManaRegeneration() + " Mana Regen/s"));
        }
        infoLore.add(ColorFormat.format(""));
        infoLore.add(ColorFormat.format("&7Are you sure you want to proceed?"));

        inventory.setItem(13, ItemBuilder.of(Material.NETHER_STAR)
                .name("&d&lCircle Up")
                .rawLore(infoLore)
                .build());

        inventory.setItem(SLOT_CONFIRM, ItemBuilder.of(Material.LIME_WOOL)
                .name("&a&lConfirm")
                .lore("&7Advance to the " + StringUtils.toOrdinal(targetCircle) + " circle.")
                .build());

        setMenuData(inventory.getItem(SLOT_CONFIRM), "action", "confirm");

        inventory.setItem(SLOT_CANCEL, ItemBuilder.of(Material.RED_WOOL)
                .name("&c&lCancel")
                .build());

        setMenuData(inventory.getItem(SLOT_CANCEL), "action", "cancel");
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if ("confirm".equals(action)) {
            if (!ProgressionService.canAdvance(viewer, targetCircle)) {
                viewer.sendMessage(ColorFormat.format("&cYou no longer meet the requirements for this circle."));
                new ProgressionMenu(viewer).open();
                return true;
            }
            close();
            CircleUpAnimation.play(viewer, () -> {
                ProgressionService.advance(viewer);
                new ProgressionMenu(viewer).open();
            });
            return true;
        }
        if ("cancel".equals(action)) {
            new ProgressionMenu(viewer).open();
            return true;
        }
        return true;
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -pl core -q` from `D:\Alkatraz\Alkatraz`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/WandTableSelectionMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/ProgressionMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/CircleUpConfirmationMenu.java
git commit -m "refactor(gui): migrate WandTable, Progression, CircleUpConfirmation to ItemBuilder"
```

---

### Task 7: Migrate Remaining Menus (Batch 2 — Spell Option Menus)

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellOptionsMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellOptionValuesMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/HotbarSpellSelectionMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/options/PooledSlotSelectionMenu.java`

**Interfaces:**
- Consumes: `ItemBuilder` (Task 1), `Menu.fillAll()`/`fillBorders()` (Task 2), `PagedMenu.addStandardBorder()` (Task 2)

- [ ] **Step 1: Migrate SpellOptionsMenu.java**

Replace the manual border slot array in `addDecorations()` with `addStandardBorder()` (from PagedMenu). Use `ItemBuilder` for spellInfo and all display items. Remove `Utils` import.

Key changes in `addDecorations()`:
```java
    @Override
    protected void addDecorations() {
        addStandardBorder();

        inventory.setItem(4, ItemBuilder.of(spell.getGuiItem().clone())
                .name("&6" + spell.getDisplayName())
                .lore("&7Configure spell options", "",
                      "&eCircle: " + spell.getRequiredCircleLevel())
                .build());
    }
```

Replace `buildSyntheticItem()` and `createDisplayItem()` to use `ItemBuilder`.

- [ ] **Step 2: Migrate SpellOptionValuesMenu.java**

Replace the manual border slot array with `addStandardBorder()`. Use `ItemBuilder` for optionInfo and display items. Remove `Utils` import.

Key changes in `addDecorations()`:
```java
    @Override
    protected void addDecorations() {
        addStandardBorder();

        inventory.setItem(4, ItemBuilder.of(option.getIcon())
                .name("&e" + option.getId())
                .lore("&7" + option.getDescription(), "",
                      "&7Select a value below")
                .build());
    }
```

- [ ] **Step 3: Migrate HotbarSpellSelectionMenu.java**

Replace `Utils.getBlank()` with `Alkatraz.getGuiItemRegistry().getItem("blank")`. Use `ItemBuilder` for back button and header items. Remove `Utils` import.

Replace `addBackButton()`:
```java
    @Override
    protected void addBackButton() {
        inventory.setItem(backButtonSlot, ItemBuilder.of(Material.BARRIER)
                .name("&cBack to Spells")
                .build());
        setMenuData(inventory.getItem(backButtonSlot), "action", "back");
    }
```

Replace `buildHeaderItem()` to use `ItemBuilder`.

- [ ] **Step 4: Migrate PooledSlotSelectionMenu.java**

Replace `Utils.getBlank()` in `addDecorations()` with `Alkatraz.getGuiItemRegistry().getItem("blank")`. Use `ItemBuilder` for back button and slot items. Remove `Utils` import.

Replace `addBackButton()`:
```java
    @Override
    protected void addBackButton() {
        inventory.setItem(backButtonSlot, ItemBuilder.of(Material.BARRIER)
                .name("&cBack to Options")
                .build());
        setMenuData(inventory.getItem(backButtonSlot), "action", "back");
    }
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -pl core -q` from `D:\Alkatraz\Alkatraz`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellOptionsMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellOptionValuesMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/HotbarSpellSelectionMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/options/PooledSlotSelectionMenu.java
git commit -m "refactor(gui): migrate spell option menus and hotbar config to ItemBuilder"
```

---

### Task 8: Migrate Remaining Menus (Batch 3 — Misc Menus)

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeBookMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeDetailMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/EquipmentMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/GrimorePageMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/EngravingTableMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/ItemEditorMenu.java`

**Interfaces:**
- Consumes: `ItemBuilder` (Task 1), `Menu.fillAll()`/`fillBorders()` (Task 2), `StringUtils.prettifyKey()` (Task 3)

- [ ] **Step 1: Migrate RecipeBookMenu.java**

Replace `Utils.getBlank()` loop with `fillAll()`. Use `ItemBuilder` for info item. Delete local `prettifyKey()` (already done in Task 3). Remove `Utils` import.

- [ ] **Step 2: Migrate RecipeDetailMenu.java**

Replace `Utils.getBlank()` loop with `fillAll()`. Use `ItemBuilder` for back button and ingredient items. Delete local `prettifyKey()` (already done in Task 3). Remove `Utils` import.

- [ ] **Step 3: Migrate EquipmentMenu.java — improve empty slot visibility**

Replace `createBackgroundPane()` with `fillAll()`. Use `ItemBuilder` throughout. Change empty slot material from `LIGHT_GRAY_STAINED_GLASS_PANE` to `LIME_STAINED_GLASS_PANE` to make them visually distinct from the gray background.

Replace `createEmptySlot()`:
```java
    private ItemStack createEmptySlot(EquipmentSlot equipSlot) {
        String slotName = formatSlotName(equipSlot);
        ItemStack item = ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                .name("&a" + slotName + " (Empty)")
                .lore("&7Click then click an item in your",
                      "&7inventory to equip it.")
                .build();
        setMenuData(item, "equip_slot", equipSlot.getKey().getKey());
        setMenuData(item, "filled", false);
        return item;
    }
```

Replace `createFilledSlot()` to use `ItemBuilder`:
```java
    private ItemStack createFilledSlot(ItemStack equipped, EquipmentSlot equipSlot) {
        List<String> existingLore = equipped.hasItemMeta() && equipped.getItemMeta().hasLore()
                ? equipped.getItemMeta().getLore()
                : new ArrayList<>();
        existingLore.add("");
        existingLore.add(ColorFormat.format("&eClick to &cunequip"));

        ItemStack display = ItemBuilder.of(equipped.clone())
                .rawLore(existingLore)
                .build();

        setMenuData(display, "equip_slot", equipSlot.getKey().getKey());
        setMenuData(display, "filled", true);
        return display;
    }
```

Remove the private `createBackgroundPane()` method.

- [ ] **Step 4: Migrate GrimoirePageMenu.java — fix spine centering**

Replace `Utils.getBlank()` with `fillAll()`. Use `ItemBuilder` for pane items and page items. Fix spine centering: currently spine is at column 4 (`row * 9 + 4`). The two pages are at slots 11 (col 2) and 15 (col 6). The spine column should be column 4 (the visual center of a 0-8 range), which is already correct. However, the cover fills slots `0-8` (top row) and `45-53` (bottom row) plus left/right columns. The issue is the left page (slot 11 = row 1, col 2) is 2 slots from the spine (col 4) while the right page (slot 15 = row 1, col 6) is also 2 slots from the spine. This is actually symmetric. Keep as-is but convert to ItemBuilder.

Replace `createPaneItem()` and `createPageItem()` with `ItemBuilder`.

- [ ] **Step 5: Migrate EngravingTableMenu.java**

Replace `createBackgroundPane()` with `fillAll()`. Use `ItemBuilder` throughout. Delete local `prettifyKey()` (already done in Task 3). Remove `Utils` import.

- [ ] **Step 6: Migrate ResearchGraphMenu.java**

Replace `Utils.getBlank()` loop with `fillAll()`. Use `ItemBuilder` for `createNodeItem()`, `createEdgeItem()`, and `button()`. Remove `Utils` import.

- [ ] **Step 7: Migrate ItemEditorMenu.java**

Replace `createBorderPane()` with `fillAll()`. Use `ItemBuilder` for `createHeaderItem()`. Remove `Utils` import if present.

- [ ] **Step 8: Verify compilation**

Run: `mvn compile -pl core -q` from `D:\Alkatraz\Alkatraz`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeBookMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeDetailMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/EquipmentMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/GrimorePageMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/EngravingTableMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/ItemEditorMenu.java
git commit -m "refactor(gui): migrate remaining menus to ItemBuilder, fix empty slot visibility"
```

---

### Task 9: Full Build Verification

**Files:** None (verification only)

- [ ] **Step 1: Full Maven build**

Run: `mvn clean package` from `D:\Alkatraz\Alkatraz`
Expected: BUILD SUCCESS across all modules

- [ ] **Step 2: Search for leftover Utils.getBlank() in GUI code**

Run: `grep -r "Utils.getBlank" core/src/main/java/me/nagasonic/alkatraz/gui/`
Expected: No results (all migrated)

- [ ] **Step 3: Search for leftover local prettifyKey definitions**

Run: `grep -r "private.*prettifyKey" core/src/main/java/me/nagasonic/alkatraz/gui/`
Expected: No results (all migrated to StringUtils)

- [ ] **Step 4: Search for leftover createPane/createBackgroundPane private helpers**

Run: `grep -rn "private.*createPane\|private.*createBackgroundPane\|private.*createBorderPane\|private.*createButton" core/src/main/java/me/nagasonic/alkatraz/gui/implementation/`
Expected: Only in editor sub-menus that haven't been migrated (LoreSubMenu, ComponentsSubMenu, AttributesSubMenu, RequirementsSubMenu, RecipeSubMenu — these are lower priority and can be done in a follow-up)

---

## Summary

| Task | What | Files Changed | Risk |
|------|------|---------------|------|
| 1 | ItemBuilder utility | 1 new | None |
| 2 | Menu base methods + GUIItemRegistry | 3 | Low |
| 3 | Deduplicate prettifyKey | 4 | None |
| 4 | SpellsMenu | 1 | Low |
| 5 | StatsMenu + reset confirmation | 1 | Medium (new UX) |
| 6 | WandTable, Progression, CircleUp | 3 | Low |
| 7 | Spell option menus | 4 | Low |
| 8 | Recipe, Equipment, Grimoire, Engraving, Research, Editor | 7 | Low |
| 9 | Full build verification | 0 | None |
