# Localization System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract all hardcoded user-facing strings into a centralized `lang/english.lang` file with a `LangManager` that provides key-based lookup with placeholder interpolation and automatic color formatting.

**Architecture:** A new `LangManager` class loads a properties-style `.lang` file, caches entries in a `Map<String, String>`, and provides `get(key, placeholders...)` with `ColorFormat.format()` auto-applied. All ~650 hardcoded strings across menus, commands, spells, tutorial, and utility classes are migrated to use this system. YAML config files (spell display_name, research descriptions) fall back to lang file entries.

**Tech Stack:** Java 17, Bukkit/Spigot API, Maven, Properties-style file parsing, `ColorFormat.format()` integration.

## Global Constraints

- Java 17 (source/target)
- Maven build: `mvn clean package -pl core` from project root
- No unit tests exist — verification is compile-only
- Must preserve all existing behavior — this is a string extraction, not a feature change
- All GUI text goes through `ColorFormat.format()` (already the case; LangManager.get() wraps this)
- Package root: `me.nagasonic.alkatraz`
- Lang file format: `key=value` properties (no sections/headers, comments with `#`)
- Placeholder syntax: `%variable_name%` (named, order-independent)
- Fallback chain: requested language → english.lang (JAR resource) → raw key string

---

### Task 1: Create LangManager, english.lang, and Config Integration

**Files:**
- Create: `core/src/main/java/me/nagasonic/alkatraz/lang/LangManager.java`
- Create: `core/src/main/resources/lang/english.lang`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/Alkatraz.java` — add `LangManager` field, static getter, init in `onEnable()`, replace unused `language` variable
- Modify: `core/src/main/java/me/nagasonic/alkatraz/config/Configs.java` — add `LANGUAGE` enum value

**Interfaces:**
- Consumes: `Alkatraz.getPluginConfig()` for `language` key, `ColorFormat.format()` for auto-colorization
- Produces: `Alkatraz.getLangManager()` static getter returning `LangManager`, `lang.get(key, placeholders...)` and `lang.getRaw(key, placeholders...)`

- [ ] **Step 1: Create `LangManager.java`**

```java
package me.nagasonic.alkatraz.lang;

import me.nagasonic.alkatraz.util.ColorFormat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LangManager {
    private final Map<String, String> messages = new HashMap<>();
    private final String language;

    public LangManager(String language) {
        this.language = language;
        loadLanguage(language);
        // Always load english as fallback base
        if (!"english".equals(language)) {
            loadBundledLanguage("english");
        }
    }

    private void loadLanguage(String langName) {
        File langDir = new File("plugins/Alkatraz/lang");
        File langFile = new File(langDir, langName + ".lang");
        if (!langFile.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8))) {
            loadFromReader(reader);
        } catch (IOException e) {
            System.err.println("[Alkatraz] Failed to load lang file: " + langFile.getName());
            e.printStackTrace();
        }
    }

    private void loadBundledLanguage(String langName) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("lang/" + langName + ".lang");
        if (is == null) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            loadFromReader(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadFromReader(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            messages.put(key, value);
        }
    }

    public String get(String key, Object... placeholders) {
        String template = messages.get(key);
        if (template == null) {
            // Fallback: return the raw key so missing translations are visible
            return key;
        }
        String result = template;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = "%" + placeholders[i] + "%";
            String replacement = String.valueOf(placeholders[i + 1]);
            result = result.replace(placeholder, replacement);
        }
        return ColorFormat.format(result);
    }

    public String getRaw(String key, Object... placeholders) {
        String template = messages.get(key);
        if (template == null) {
            return key;
        }
        String result = template;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = "%" + placeholders[i] + "%";
            String replacement = String.valueOf(placeholders[i + 1]);
            result = result.replace(placeholder, replacement);
        }
        return result;
    }

    public String getLanguage() {
        return language;
    }
}
```

- [ ] **Step 2: Create `english.lang` with all ~650 keys**

Create `core/src/main/resources/lang/english.lang` with the following content (organized by section). This is the complete file — every string extracted from the codebase:

```properties
# ==============================
# Alkatraz — English Language File
# ==============================

# --- Common ---
common.back = &cBack
common.close = &cClose
common.confirm = &aConfirm
common.cancel = &cCancel
common.info = &eInfo
common.next_page = &aNext Page
common.previous_page = &aPrevious Page
common.page_indicator = &ePage %current% / %total%
common.empty = &8(empty)

# --- Menu Titles ---
menu.spells = &5Spells
menu.spell_options = &6%spell% - Options
menu.spell_option_values = &6%option% - Select Value
menu.stats = &eStats
menu.progression = &5Progression
menu.arcane_table = &5Arcane Table
menu.equipment = &8Equipment
menu.grimoire = &6Grimoire
menu.recipe_book = &6Recipe Book
menu.recipe_details = &6Recipe Details
menu.hotbar_config = &5Configure Spell Hotbar
menu.pooled_slot = &6%slot_name%
menu.research_library = &5Research Library
menu.research_entry = &5Research: %name%
menu.research_categories = &5Research Categories
menu.engraving_table = &8Engraving Table
menu.engraving_selector = &dEngraving Table
menu.trigger_select = &6Select Trigger Type
menu.item_editor = &8Item Editor
menu.rune_editor = &b&lRune Editor
menu.circle_up_confirm = &5&lConfirm Circle Up

# --- Spells Menu ---
spells.configure_hotbar = &5Configure Spell Hotbar
spells.configure_hotbar_lore = &7Assign spells to your wand hotbar slots.
spells.code = &bCode:
spells.mana_cost = &bMana Cost:
spells.cooldown = &bCooldown:
spells.cast_time = &bCast Time:
spells.element = &bElement:
spells.mastery = &bMastery:
spells.circle = &eCircle:
spells.has_options = &aHas Spell Options
spells.has_options_lore = &7Click to configure
spells.locked_name = &8???
spells.locked_lore = &7&oCircle: &7&oUnknown

# --- Stats Menu ---
stats.circle_level = &eCircle Level
stats.arcane_knowledge = &bArcane Knowledge
stats.arcane_knowledge_lore = &7Current: &f%current%&7 / &f%max%
stats.research_points = &dResearch Points
stats.research_points_lore = &7Available: &f%current%
stats.stat_points = &aStat Points
stats.stat_points_lore = &7Available: &f%current%
stats.mana = &bMana
stats.mana_lore = &7Max: &f%current%
stats.mana_regen = &bMana Regen
stats.mana_regen_lore = &7Per second: &f%current%
stats.affinity = &eAffinity: &f%value%
stats.resistance = &eResistance: &f%value%
stats.element_fire = &6Fire
stats.element_water = &3Water
stats.element_earth = &aEarth
stats.element_air = &fAir
stats.element_light = &eLight
stats.element_dark = &5Dark
stats.invested_points = &eInvested Points:
stats.bonus = &eBonus:
stats.invest_click = &eClick to invest &61 &epoint.
stats.invest_insufficient = &cYou don't have any stat points available!
stats.invest_wrong_player = &cYou can only modify your own stats!
stats.invest_success = &aInvested 1 point into %element%!
stats.reset_button = &d&lReset Stats
stats.reset_button_lore = &7Reset all stat investments.
stats.reset_confirm_title = &e&lAre you sure?
stats.reset_confirm_lore = &7This action is NOT UNDOABLE
stats.reset_confirm_yes = &a&lConfirm Reset
stats.reset_confirm_no = &c&lCancel
stats.reset_success = &aStats reset! Refunded %points% stat points.
stats.reset_no_tokens = &cYou don't have any reset tokens!
stats.reset_no_stats = &cYou have no invested stats to reset!

# --- Progression Menu ---
progression.title = &dCircle Progression
progression.current_circle = &7Current Circle: &f%circle%
progression.legend_green = &aGreen &7= Completed
progression.legend_yellow = &eYellow &7= Working towards
progression.legend_red = &cRed &7= Locked
progression.status_completed = &a&lCOMPLETED
progression.status_working = &e&lWORKING TOWARDS
progression.status_locked = &c&lLOCKED
progression.already_mastered = &7You have already mastered this circle.
progression.requirements_header = &e&lRequirements:
progression.rewards_header = &e&lRewards:
progression.reward_stat_points = &a  +%amount% Stat Points
progression.reward_max_mana = &a  +%amount% Max Mana
progression.reward_mana_regen = &a  +%amount% Mana Regen/s
progression.click_to_advance = &e&lClick to advance!
progression.requirement_met = &a✔
progression.requirement_unmet = &c✘

# --- Arcane Table ---
arcane.research = &dResearch Library
arcane.research_lore = &7Unlock new knowledge
arcane.progression = &dProgression
arcane.progression_lore = &7Advance your circle
arcane.engineering = &dMagic Engineering
arcane.engineering_lore = &7Modify your equipment
arcane.choose_path = &7Choose your path:

# --- Equipment Menu ---
equipment.slot_ring = Ring
equipment.slot_necklace = Necklace
equipment.slot_bracelet = Bracelet
equipment.slot_pendant = Pendant
equipment.empty_slot = &a%s% (Empty)
equipment.empty_slot_lore = &7Click then click an item in your inventory to equip it.
equipment.click_unequip = &eClick to &cunequip
equipment.equip_wrong_slot = &cThat item cannot be equipped in this slot.
equipment.equip_instructions = &eClick an item in your inventory to equip it in the %slot%.

# --- Grimoire ---
grimoire.previous_pages = &fPrevious Pages
grimoire.next_pages = &fNext Pages
grimoire.page_empty = &6Page %page% &7- &fEmpty
grimoire.page_empty_lore = &7Click to assign a spell.
grimoire.page_unknown = &6Page %page% &7- &cUnknown
grimoire.page_spell_left_click = &eLeft-click &7to cast this spell.
grimoire.page_spell_right_click = &cRight-click &7to clear this page.
grimoire.spell_select_title = &5Select a Spell
grimoire.spell_select_back = &cBack to Grimoire
grimoire.spell_select_assign = &eClick to assign to Page %page%
grimoire.spell_select_assigned = &aAssigned %spell% to Page %page%.

# --- Recipe Book ---
recipes.title = &6&lRecipe Book
recipes.browse_lore = &7Browse all available magic item recipes.
recipes.ingredients_header = &7&m---&r &6Ingredients &7&m---
recipes.has_requirements = &cRequirements
recipes.click_details = &eClick for details
recipes.back_to_recipes = &fBack to Recipes
recipes.unknown = &cUnknown

# --- Spell Options ---
spell_options.configure = &7Configure spell options
spell_options.circle = &eCircle: &f%circle%
spell_options.back_to_spells = &cBack to Spells
spell_options.click_configure = &eClick to configure
spell_options.description = &7%description%

# --- Spell Option Values ---
option_values.select_value = &7Select a value below
option_values.effects_header = &eEffects:
option_values.click_select = &eClick to select
option_values.locked = &c&lLOCKED
option_values.requirements_header = &cRequirements:
option_values.selected = &aSelected:
option_values.select_failed = &cFailed to select this option!

# --- Hotbar Config ---
hotbar.back_to_spells = &cBack to Spells
hotbar.currently_selected = &bCurrently selected
hotbar.click_assign_lore = &7Click a spell below to assign it.
hotbar.slot_header = &6Slot %slot%
hotbar.slot_empty = &8Spell Slot %slot% &7(empty)
hotbar.slot_empty_lore = &7Configure in the Spells menu.
hotbar.left_click_select = &eLeft-click &7to select this slot.
hotbar.right_click_clear = &cRight-click &7to clear.
hotbar.already_assigned = &7Already assigned to a slot.
hotbar.click_assign = &eClick to assign to Slot %slot%

# --- Pooled Slots ---
pooled.back_to_options = &cBack to Options
pooled.selected_assignment = &bSelected for assignment
pooled.click_assign_lore = &7Click a value below to assign it.
pooled.right_click_clear = &cRight-click &7to clear.
pooled.slot_locked = &c&lSLOT LOCKED
pooled.locked = &c&lLOCKED
pooled.already_in_slot = &7Already in a slot.
pooled.click_assign_slot = &eClick to assign to %name%
pooled.already_assigned = &cThat option is already assigned to a slot.

# --- Research ---
research.back_to_graph = &fBack to Graph
research.back_to_arcane = &fBack to Arcane Table
research.pan_up = &fPan Up
research.pan_down = &fPan Down
research.pan_left = &fPan Left
research.pan_right = &fPan Right
research.categories = &6%category% &7Categories
research.research_points = &bResearch Points: &f%points%
research.unknown = &8Unknown Research
research.click_inspect = &eClick to inspect
research.link_completed = &aCompleted
research.link_active = &eActive Link
research.link_inactive = &7Inactive Link
research.entry_requirements = &eRequirements
research.entry_tasks = &bResearch Tasks
research.entry_rewards = &aRewards
research.entry_unlocks = &dUnlocks
research.entry_start = &eStart Research
research.entry_complete = &bComplete Research
research.entry_completed = &aCompleted
research.entry_locked = &cLocked
research.entry_hidden = &8Hidden
research.no_prior = &aNo prior research required.
research.no_tasks = &aNo tasks required.
research.insufficient_points = &cYou need %points% Research Points
research.linked_research = &dLinked Research
research.objective = &7- %description% &e(%current%/%target%)

# --- Engraving ---
engraving.engravings_header = &7Engravings: &f%current%&7/&f%max%
engraving.unequip_click = &eClick to unequip this engraving
engraving.empty_slot = &7Empty Slot
engraving.locked_slot = &8Locked Slot
engraving.back = &cBack
engraving.invalid_item = &cThis is not a valid magic item.
engraving.select_item = &dSelect an Item
engraving.select_item_lore = &7Click an item in your inventory.
engraving.rune_installed = &a%rune% installed
engraving.rune_empty = &7No rune installed

# --- Circle Up Confirmation ---
circleup.reward_stat_points = &a+%amount% Stat Points
circleup.reward_max_mana = &a+%amount% Max Mana
circleup.reward_mana_regen = &a+%amount% Mana Regen/s
circleup.about_to_advance = &7You are about to advance to the
circleup.confirm_question = &7Are you sure you want to proceed?
circleup.confirm_yes = &a&lConfirm Circle Up
circleup.confirm_no = &c&lCancel
circleup.requirements_met = &aRequirements Met
circleup.requirements_not_met = &cYou no longer meet the requirements!

# --- Editor ---
editor.rune_editor_title = &b&lRune Editor
editor.item_editor_title = &6&lItem Editor
editor.switch_to_items = &7Click to switch to Items view
editor.switch_to_runes = &7Click to switch to Runes view
editor.click_edit_item = &7Click to edit this item
editor.not_supported = &eRune editing is not yet supported in the editor.
editor.display_name = &eDisplay Name
editor.lore = &eLore
editor.material = &eMaterial
editor.dye_color = &eDye Color
editor.custom_model_data = &eCustom Model Data
editor.unbreakable = &eUnbreakable
editor.hide_attributes = &eHide Attributes
editor.components = &eComponents
editor.attributes = &eAttributes
editor.vanilla_attributes = &eVanilla Attributes
editor.max_engravings = &eMax Engravings
editor.spell_id = &eSpell ID
editor.triggers = &eTriggers
editor.recipe_shape = &eRecipe Shape
editor.recipe_ingredients = &eRecipe Ingredients
editor.recipe_requirements = &eRecipe Requirements
editor.save = &a&lSave
editor.reload = &eReload
editor.get_item = &b&lGet Item
editor.enter_display_name = Enter new display name...
editor.enter_material = Enter new material name...
editor.line = &eLine %line%
editor.add_line = &a&lAdd Line
editor.attr_key = &e%key%
editor.attr_value = &7Value:
editor.attr_edit = &eLeft-click to edit value
editor.attr_delete = &cRight-click to delete
editor.add_attribute = &a&lAdd Attribute
editor.component_enabled = &aEnabled
editor.component_disabled = &7Disabled
editor.component_click_toggle = &7Click to toggle
editor.add_component = &a&lAdd Component
editor.recipe_empty = &7Empty
editor.recipe_char = &e'%char%'
editor.recipe_item = &6Item: &f%key%
editor.save_recipe = &a&lSave Recipe
editor.requirement_display = &eRequirement %num% (%type%)
editor.add_requirement = &a&lAdd Requirement
editor.chat_prompt = &7[Editor] &7Type your input in chat, or type &ccancel &7to abort.
editor.chat_cancelled = &cCancelled.

# --- Commands ---
commands.reload_success = &aReloaded configs.
commands.no_permission = &cYou do not have permission to use this command.
commands.player_only = &cOnly players can use this command.
commands.usage_header = &cUsage:
commands.spell_not_found = &cThere is no spell named &f%name%&c.
commands.item_not_found = &cThere is no item named &f%name%&c.
commands.gave_item = &aGave %item% to %player%.
commands.ak_usage = &cUsage: /alkatraz arcaneknowledge set|add <player> <amount>
commands.ak_negative = &cArcane Knowledge cannot be negative.
commands.ak_set = &aSet Arcane Knowledge of %player% to %amount%.
commands.ak_add = &aAdded %amount% Arcane Knowledge to %player%.
commands.ak_invalid_op = &cPlease choose a valid operator: set/add.
commands.give_usage = &cUsage: /alkatraz give <item> [player]
commands.reload_usage = &cUsage: /alkatraz reload
commands.main_usage = &cPlease add an argument.
commands.cast_mode_set = &aCast mode set to %mode%.
commands.converted_wands = &aConverted %count% legacy wand(s).
commands.spawnmob_usage = &cUsage: /alkatraz spawnmob <mob> [player]
commands.spawnmob_success = &aSpawned %mob% at %player%.
commands.spells_usage = &cUsage: /spells [<player>]
commands.recipes_permission = &cYou don't have permission to view recipes.
commands.cast_no_session = &cNo active grimoire session.
commands.cast_invalid_token = &cInvalid cast token.

# --- Spell Casting ---
spells.cast.too_low_circle = &cToo low Magic Circle
spells.cast.cannot_cast_now = &cYou cannot cast right now
spells.cast.not_enough_mana = &cNot Enough Mana
spells.cast.please_wait = &cPlease wait %time%s
spells.cast.casted = Casted:
spells.cast.need_tool = &cYou need a better tool to cast this.
spells.cast.not_discovered = &cYou have not discovered this spell.
spells.cast.buffs_applied = &bBuffs applied to %target%!
spells.cast.debuffs_applied = &5Debuffs applied to %target%!
spells.cast.buffs_faded = &eThe buff on you has faded.
spells.cast.debuffs_faded = &5The debuff on you has faded.
spells.cast.blessed_by = &eYou have been blessed by %spell%!

# --- Spellbook ---
spellbook.corrupted = &cThis spellbook appears to be corrupted!
spellbook.already_known = &eYou already know %spell%!
spellbook.requirement_not_met = &cRequirement not met:
spellbook.discovered = &aYou have discovered %spell%!
spellbook.view_spells_hint = &7Use &e/spells &7to view your discovered spells.
spellbook.requirements_header = Requirements:
spellbook.effects_header = Effects:
spellbook.empty = &cThis spellbook appears to be empty!
spellbook.selection_failed = &cFailed to select a spell!
spellbook.selection_not_found = &cSelected spell not found!
spellbook.transformed = &aThe spellbook transforms into %spell%!
spellbook.random_lore = &7Random Spellbook
spellbook.element_lore = &7%element% Spellbook

# --- Tutorial ---
tutorial.welcome_title = &d&lAlkatraz
tutorial.welcome_subtitle = &fYour magical journey begins...
tutorial.step1_title = &e&lStep 1: The Grimoire
tutorial.step1_subtitle = &7Your book of spells
tutorial.step1_chat = &eLeft-click &7with the Grimoire to open it. This is where you'll manage your spells.
tutorial.step2_title = &e&lStep 2: Magic Circle
tutorial.step2_subtitle = &7Your progression system
tutorial.step2_chat = &7Invest stat points using the &e/spells &7command. Then click &eCircle Up &7to advance!
tutorial.step3_title = &e&lStep 3: Arcane Table
tutorial.step3_subtitle = &7Research & Engineering
tutorial.step3_chat = &7Use &e/wandtable &7to access the Arcane Table for research and equipment modification.
tutorial.step4_title = &e&lStep 4: Spell Options
tutorial.step4_subtitle = &7Customize your spells
tutorial.step4_chat = &7Click a spell in &e/spells &7to configure its options and hotbar assignment.
tutorial.step5_title = &e&lStep 5: Equipment
tutorial.step5_subtitle = &7Magic items
tutorial.step5_chat = &7Equip magic items in your inventory to gain their effects.
tutorial.done_title = &a&lYou're Ready!
tutorial.done_subtitle = &7Type &e/spells &7to begin your journey.

# --- Progression Messages ---
progression.circle_up_title = &e&lCIRCLE UP!
progression.circle_up_subtitle = &bReached the %ordinal% circle.
progression.circle_up_spell_rank = &bYou are now able to use spells up to the %rank% rank.
progression.circle_up_ak = &bArcane Knowledge: %current%/%max%

# --- MagicItemStack ---
magic_item.element_header = Elements:
magic_item.element_fire = &6Fire: %value%
magic_item.element_water = &3Water: %value%
magic_item.element_earth = &aEarth: %value%
magic_item.element_air = &fAir: %value%
magic_item.element_light = &eLight: %value%
magic_item.element_dark = &5Dark: %value%
magic_item.trigger_header = Triggers:

# --- Spellbook Factory ---
spellbook_factory.random_name = &7Random Spellbook
spellbook_factory.element_name = &7%element% Spellbook

# --- No Config ---
pooled.no_effects = &cNo effects configured! Open the spell options menu.
```

- [ ] **Step 3: Modify `Configs.java`**

Add `LANGUAGE` to the enum and load it:

```java
// In the enum declaration, add:
LANGUAGE;

// In reload(), add:
LANGUAGE.value = config.getString("language", "english");
```

- [ ] **Step 4: Modify `Alkatraz.java`**

Replace the unused `String lang = ...` variable with LangManager initialization:

```java
// Add field (after guiItemRegistry):
private static me.nagasonic.alkatraz.lang.LangManager langManager = null;

// In onLoad(), replace line 90:
String lang = pluginConfig.getString("language", "en-us");
// With:
// (nothing — language is read in onEnable after config is ready)

// In onEnable(), after guiItemRegistry.init() (around line 115), add:
logVeryHigh("Initializing LangManager...");
langManager = new me.nagasonic.alkatraz.lang.LangManager(
    pluginConfig.getString("language", "english"));

// Add static getter:
public static me.nagasonic.alkatraz.lang.LangManager getLangManager() {
    return langManager;
}
```

- [ ] **Step 5: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/lang/LangManager.java \
        core/src/main/resources/lang/english.lang \
        core/src/main/java/me/nagasonic/alkatraz/config/Configs.java \
        core/src/main/java/me/nagasonic/alkatraz/Alkatraz.java
git commit -m "feat(lang): add LangManager, english.lang, and config integration"
```

---

### Task 2: Migrate GUI Common — Menu Base, GUIItemRegistry

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/PagedMenu.java` — replace `nextPage`, `previousPage` title strings
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/GUIItemRegistry.java` — replace display names for all registered items

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key)` from Task 1
- Produces: No new public interfaces — purely string replacement

- [ ] **Step 1: Modify PagedMenu.java**

Replace the hardcoded page navigation strings. In the `open()` method where page indicators are set:

Find and replace:
- `"&ePage " + currentPage + " / " + totalPages` → `Alkatraz.getLangManager().get("common.page_indicator", "current", currentPage, "total", totalPages)`

In the item names for page buttons (if any display names use hardcoded text).

- [ ] **Step 2: Modify GUIItemRegistry.java**

Replace all hardcoded display names in `createBackButton()`, `createNextPageButton()`, `createPrevPageButton()`, `createConfirmButton()`, `createCancelButton()`, `createInfoButton()`, `createCloseButton()`:

For example, in `createBackButton()`:
Find: `ColorFormat.format("&cBack")`
Replace: `Alkatraz.getLangManager().get("common.back")`

Do this for all 7 button factory methods, replacing with corresponding keys: `common.back`, `common.next_page`, `common.previous_page`, `common.confirm`, `common.cancel`, `common.info`, `common.close`.

- [ ] **Step 3: Remove unused imports if any**

After changes, remove `ColorFormat` import from `GUIItemRegistry` if it's no longer used directly (it's still used indirectly via LangManager).

- [ ] **Step 4: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/PagedMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/GUIItemRegistry.java
git commit -m "refactor(lang): migrate PagedMenu and GUIItemRegistry strings to LangManager"
```

---

### Task 3: Migrate SpellsMenu, GrimoirePageMenu, GrimoireSpellSelectMenu

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellsMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/GrimorePageMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/GrimoreSpellSelectMenu.java`

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key, placeholders...)` from Task 1
- Produces: No new public interfaces

- [ ] **Step 1: Migrate SpellsMenu.java**

Replace all hardcoded strings:
- Menu title: `ColorFormat.format("&5Spells")` → `Alkatraz.getLangManager().get("menu.spells")`
- `"&5Configure Spell Hotbar"` → `lang.get("spells.configure_hotbar")`
- `"&7Assign spells to your wand hotbar slots."` → `lang.get("spells.configure_hotbar_lore")`
- `"&bCode:"` → `lang.get("spells.code")`
- `"&bMana Cost:"` → `lang.get("spells.mana_cost")`
- `"&bCooldown:"` → `lang.get("spells.cooldown")`
- `"&bCast Time:"` → `lang.get("spells.cast_time")`
- `"&bElement:"` → `lang.get("spells.element")`
- `"&bMastery:"` → `lang.get("spells.mastery")`
- `"&eCircle:"` → `lang.get("spells.circle")`
- `"&aHas Spell Options"` → `lang.get("spells.has_options")`
- `"&7Click to configure"` → `lang.get("spells.has_options_lore")`
- `"&8???"` → `lang.get("spells.locked_name")`
- `"&7&oCircle:"` → `lang.get("spells.locked_lore")`

Add a local helper at top of class for brevity:
```java
private static me.nagasonic.alkatraz.lang.LangManager lang() {
    return me.nagasonic.alkatraz.Alkatraz.getLangManager();
}
```

- [ ] **Step 2: Migrate GrimoirePageMenu.java**

Replace:
- Title: → `lang.get("menu.grimoire")`
- `"&fPrevious Pages"` → `lang.get("grimoire.previous_pages")`
- `"&fNext Pages"` → `lang.get("grimoire.next_pages")`
- `"&6Page " + n + " &7- &fEmpty"` → `lang.get("grimoire.page_empty", "page", n)`
- `"&7Click to assign a spell."` → `lang.get("grimoire.page_empty_lore")`
- `"&6Page " + n + " &7- &cUnknown"` → `lang.get("grimoire.page_unknown", "page", n)`
- `"&eLeft-click &7to cast this spell."` → `lang.get("grimoire.page_spell_left_click")`
- `"&cRight-click &7to clear this page."` → `lang.get("grimoire.page_spell_right_click")`

- [ ] **Step 3: Migrate GrimoireSpellSelectMenu.java**

Replace:
- Title: → `lang.get("menu.spell_select")`
- `"&5Select a Spell"` → `lang.get("grimoire.spell_select_title")`
- `"&cBack to Grimoire"` → `lang.get("grimoire.spell_select_back")`
- `"&eClick to assign to Page " + pageNumber` → `lang.get("grimoire.spell_select_assign", "page", pageNumber)`
- `"&aAssigned " + spell.getDisplayName() + " to Page " + pageNumber` → `lang.get("grimoire.spell_select_assigned", "spell", spell.getDisplayName(), "page", pageNumber)`

- [ ] **Step 4: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellsMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/GrimorePageMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/GrimoreSpellSelectMenu.java
git commit -m "refactor(lang): migrate SpellsMenu, GrimoirePageMenu, GrimoireSpellSelectMenu"
```

---

### Task 4: Migrate StatsMenu, ProgressionMenu, CircleUpConfirmationMenu, WandTableSelectionMenu

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/StatsMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/ProgressionMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/CircleUpConfirmationMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/WandTableSelectionMenu.java`

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key, placeholders...)` from Task 1
- Produces: No new public interfaces

- [ ] **Step 1: Migrate StatsMenu.java**

Replace all hardcoded strings with lang keys from the `stats.*` section. Key replacements:
- Title → `menu.stats`
- `"&eCircle Level"` → `stats.circle_level`
- `"&bArcane Knowledge"` → `stats.arcane_knowledge`
- Lore with `%current%`/`%max%` → `stats.arcane_knowledge_lore` with placeholders
- All stat element names → `stats.element_fire`, `stats.element_water`, etc.
- `"&d&lReset Stats"` → `stats.reset_button`
- Confirmation dialog strings → `stats.reset_confirm_*`
- Error messages → `stats.invest_*`, `stats.reset_*`

- [ ] **Step 2: Migrate ProgressionMenu.java**

Replace:
- Title → `menu.progression`
- `"&dCircle Progression"` → `progression.title`
- `"&7Current Circle: &f" + ...` → `progression.current_circle` with `%circle%`
- Legend strings → `progression.legend_*`
- Status strings → `progression.status_*`
- Reward strings → `progression.reward_*`
- `"&e&lClick to advance!"` → `progression.click_to_advance`
- Requirement checkmarks → `progression.requirement_met`/`progression.requirement_unmet`

- [ ] **Step 3: Migrate CircleUpConfirmationMenu.java**

Replace:
- Title → `menu.circle_up_confirm`
- All reward strings → `circleup.reward_*`
- Confirmation text → `circleup.about_to_advance`, `circleup.confirm_question`
- Button labels → `circleup.confirm_yes`, `circleup.confirm_no`

- [ ] **Step 4: Migrate WandTableSelectionMenu.java**

Replace:
- Title → `menu.arcane_table`
- `"&dArcane Table"` → `arcane.research` etc.
- `"&7Choose your path:"` → `arcane.choose_path`
- Button names/lore → `arcane.research`, `arcane.progression`, `arcane.engineering`

- [ ] **Step 5: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/StatsMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/ProgressionMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/CircleUpConfirmationMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/WandTableSelectionMenu.java
git commit -m "refactor(lang): migrate StatsMenu, ProgressionMenu, CircleUpConfirmation, WandTable"
```

---

### Task 5: Migrate SpellOptionsMenu, SpellOptionValuesMenu, HotbarSpellSelectionMenu, PooledSlotSelectionMenu

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellOptionsMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellOptionValuesMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/HotbarSpellSelectionMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/options/PooledSlotSelectionMenu.java`

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key, placeholders...)` from Task 1
- Produces: No new public interfaces

- [ ] **Step 1: Migrate SpellOptionsMenu.java**

Replace:
- Title (dynamic) → `menu.spell_options` with `%spell%`
- `"&7Configure spell options"` → `spell_options.configure`
- `"&eCircle:"` → `spell_options.circle` with `%circle%`
- `"&cBack to Spells"` → `spell_options.back_to_spells`
- `"&eClick to configure"` → `spell_options.click_configure`

- [ ] **Step 2: Migrate SpellOptionValuesMenu.java**

Replace:
- Title (dynamic) → `menu.spell_option_values` with `%option%`
- `"&7Select a value below"` → `option_values.select_value`
- `"&eEffects:"` → `option_values.effects_header`
- `"&eClick to select"` → `option_values.click_select`
- `"&c&lLOCKED"` → `option_values.locked`
- `"&cRequirements:"` → `option_values.requirements_header`
- `"&aSelected:"` → `option_values.selected`
- `"&cFailed to select this option!"` → `option_values.select_failed`

- [ ] **Step 3: Migrate HotbarSpellSelectionMenu.java**

Replace:
- Title → `menu.hotbar_config`
- `"&cBack to Spells"` → `hotbar.back_to_spells`
- `"&bCurrently selected"` → `hotbar.currently_selected`
- Slot headers → `hotbar.slot_header` with `%slot%`
- Empty slot display → `hotbar.slot_empty` with `%slot%`
- Assignment messages → `hotbar.click_assign` with `%slot%`

- [ ] **Step 4: Migrate PooledSlotSelectionMenu.java**

Replace:
- `"&cBack to Options"` → `pooled.back_to_options`
- `"&bSelected for assignment"` → `pooled.selected_assignment`
- `"&7Click a value below to assign it."` → `pooled.click_assign_lore`
- `"&cRight-click &7to clear."` → `pooled.right_click_clear`
- `"&c&lSLOT LOCKED"` → `pooled.slot_locked`
- `"&c&lLOCKED"` → `pooled.locked`
- `"&7Already in a slot."` → `pooled.already_in_slot`

- [ ] **Step 5: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellOptionsMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/SpellOptionValuesMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/HotbarSpellSelectionMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/options/PooledSlotSelectionMenu.java
git commit -m "refactor(lang): migrate spell option menus and hotbar config"
```

---

### Task 6: Migrate EquipmentMenu, RecipeBookMenu, RecipeDetailMenu, Engraving Menus

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/EquipmentMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeBookMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeDetailMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/EngravingTableMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/EngravingTableMenuSelector.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/TriggerSelectionMenu.java`

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key, placeholders...)` from Task 1
- Produces: No new public interfaces

- [ ] **Step 1: Migrate EquipmentMenu.java**

Replace:
- Title → `menu.equipment`
- Slot name strings → `equipment.slot_ring`, `equipment.slot_necklace`, `equipment.slot_bracelet`, `equipment.slot_pendant`
- Empty slot display → `equipment.empty_slot` with `%slot%`
- `"&eClick to &cunequip"` → `equipment.click_unequip`
- Error messages → `equipment.equip_wrong_slot`, `equipment.equip_instructions`

- [ ] **Step 2: Migrate RecipeBookMenu.java**

Replace:
- Title → `menu.recipe_book`
- `"&6&lRecipe Book"` → `recipes.title`
- `"&7Browse all available magic item recipes."` → `recipes.browse_lore`
- `"&7&m---&r &6Ingredients &7&m---"` → `recipes.ingredients_header`
- `"&cRequirements"` → `recipes.has_requirements`
- `"&eClick for details"` → `recipes.click_details`

- [ ] **Step 3: Migrate RecipeDetailMenu.java**

Replace:
- Title → `menu.recipe_details`
- `"&fBack to Recipes"` → `recipes.back_to_recipes`
- `"&cUnknown"` → `recipes.unknown`
- `"&7&m---&r &cRequirements &7&m---"` → `recipes.ingredients_header` (or a separate key)

- [ ] **Step 4: Migrate EngravingTableMenu.java**

Replace:
- Title → `menu.engraving_table`
- `"&dSelect an Item"` → `engraving.select_item`
- `"&7Engravings: " + current + "/" + max` → `engraving.engravings_header` with `%current%`/`%max%`
- Slot strings → `engraving.empty_slot`, `engraving.locked_slot`
- `"&eClick to unequip this engraving"` → `engraving.unequip_click`
- `"&cBack"` → `common.back`
- `"&cThis is not a valid magic item."` → `engraving.invalid_item`

- [ ] **Step 5: Migrate EngravingTableMenuSelector.java**

Replace:
- Title → `menu.engraving_selector`
- Selection lore → `engraving.select_item_lore`

- [ ] **Step 6: Migrate TriggerSelectionMenu.java**

Replace:
- Title → `menu.trigger_select`
- `"&cBack"` → `common.back`
- Trigger display names via lang keys

- [ ] **Step 7: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/EquipmentMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeBookMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/RecipeDetailMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/EngravingTableMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/EngravingTableMenuSelector.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/engraving/TriggerSelectionMenu.java
git commit -m "refactor(lang): migrate EquipmentMenu, Recipes, Engraving menus"
```

---

### Task 7: Migrate Research Menus

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchEntryMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchCategoriesMenu.java`

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key, placeholders...)` from Task 1
- Produces: No new public interfaces

- [ ] **Step 1: Migrate ResearchGraphMenu.java**

Replace:
- Title → `menu.research_library`
- Pan button labels → `research.pan_*`
- `"&fBack to Arcane Table"` → `research.back_to_arcane`
- `" &7Categories"` → `research.categories` with `%category%`
- `"&bResearch Points:"` → `research.research_points` with `%points%`
- `"&8Unknown Research"` → `research.unknown`
- `"&eClick to inspect"` → `research.click_inspect`
- Link status strings → `research.link_completed`, `research.link_active`, `research.link_inactive`

- [ ] **Step 2: Migrate ResearchEntryMenu.java**

Replace:
- Title → `menu.research_entry` with `%name%`
- `"&eRequirements"` → `research.entry_requirements`
- `"&bResearch Tasks"` → `research.entry_tasks`
- `"&aRewards"` → `research.entry_rewards`
- `"&dUnlocks"` → `research.entry_unlocks`
- `"&eStart Research"` → `research.entry_start`
- `"&bComplete Research"` → `research.entry_complete`
- Status strings → `research.entry_completed`, `research.entry_locked`, `research.entry_hidden`
- `"&aNo prior research required."` → `research.no_prior`
- `"&aNo tasks required."` → `research.no_tasks`
- `"&cYou need " + points + " Research Points"` → `research.insufficient_points` with `%points%`
- `"&fBack to Graph"` → `research.back_to_graph`
- Objective display → `research.objective` with `%description%`, `%current%`, `%target%`

- [ ] **Step 3: Migrate ResearchCategoriesMenu.java**

Replace:
- Title → `menu.research_categories`
- `"&fBack to Graph"` → `research.back_to_graph`
- Category display names → use lang keys or keep dynamic (depending on whether categories are defined in YAML)

- [ ] **Step 4: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchEntryMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchCategoriesMenu.java
git commit -m "refactor(lang): migrate research menus to LangManager"
```

---

### Task 8: Migrate Editor Menus (8 files)

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/ItemEditorMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/ItemDetailMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/LoreSubMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/AttributesSubMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/ComponentsSubMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/RecipeSubMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/RequirementsSubMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/EditorChatHandler.java`

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key, placeholders...)` from Task 1
- Produces: No new public interfaces

- [ ] **Step 1: Migrate ItemEditorMenu.java**

Replace:
- Title → `menu.item_editor`
- `"&b&lRune Editor"` → `editor.rune_editor_title`
- `"&6&lItem Editor"` → `editor.item_editor_title`
- View toggle lore → `editor.switch_to_items`, `editor.switch_to_runes`
- `"&7Click to edit this item"` → `editor.click_edit_item`
- `"&eRune editing is not yet supported in the editor."` → `editor.not_supported`

- [ ] **Step 2: Migrate ItemDetailMenu.java**

Replace all ~30 field label strings:
- `"&eDisplay Name"` → `editor.display_name`
- `"&eLore"` → `editor.lore`
- `"&eMaterial"` → `editor.material`
- ... (all editor.* keys)
- Chat prompts for input → `editor.enter_display_name`, `editor.enter_material`
- Action buttons → `editor.save`, `editor.reload`, `editor.get_item`

- [ ] **Step 3: Migrate LoreSubMenu.java**

Replace: `editor.line`, `editor.add_line`, `common.back`

- [ ] **Step 4: Migrate AttributesSubMenu.java**

Replace: `editor.attr_key`, `editor.attr_value`, `editor.attr_edit`, `editor.attr_delete`, `editor.add_attribute`

- [ ] **Step 5: Migrate ComponentsSubMenu.java**

Replace: `editor.component_enabled`, `editor.component_disabled`, `editor.component_click_toggle`, `editor.add_component`, `common.back`

- [ ] **Step 6: Migrate RecipeSubMenu.java**

Replace: `editor.recipe_empty`, `editor.recipe_char`, `editor.recipe_item`, `editor.save_recipe`, `common.back`

- [ ] **Step 7: Migrate RequirementsSubMenu.java**

Replace: `editor.requirement_display`, `editor.add_requirement`, `common.back`

- [ ] **Step 8: Migrate EditorChatHandler.java**

Replace: `editor.chat_prompt`, `editor.chat_cancelled`

- [ ] **Step 9: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/ItemEditorMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/ItemDetailMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/LoreSubMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/AttributesSubMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/ComponentsSubMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/RecipeSubMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/RequirementsSubMenu.java \
        core/src/main/java/me/nagasonic/alkatraz/gui/implementation/editor/EditorChatHandler.java
git commit -m "refactor(lang): migrate editor menus to LangManager"
```

---

### Task 9: Migrate Commands (4 files)

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/commands/AlkatrazCommand.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/commands/SpellsCommand.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/commands/RecipesCommand.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/commands/CastCommand.java`

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key, placeholders...)` from Task 1
- Produces: No new public interfaces

- [ ] **Step 1: Migrate AlkatrazCommand.java**

Replace all ~50 command strings:
- Usage messages → `commands.main_usage`, `commands.reload_usage`, `commands.give_usage`, `commands.ak_usage`
- Error messages → `commands.spell_not_found`, `commands.item_not_found`, `commands.ak_negative`, `commands.ak_invalid_op`
- Success messages → `commands.reload_success`, `commands.gave_item`, `commands.ak_set`, `commands.ak_add`
- `NO_PERMISSION` constant → `commands.no_permission`

- [ ] **Step 2: Migrate SpellsCommand.java**

Replace: `commands.player_only`, `commands.no_permission`, `commands.spells_usage`

- [ ] **Step 3: Migrate RecipesCommand.java**

Replace: `commands.player_only`, `commands.recipes_permission`

- [ ] **Step 4: Migrate CastCommand.java**

Replace: `commands.player_only`, `commands.cast_no_session`, `commands.cast_invalid_token`

- [ ] **Step 5: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/commands/AlkatrazCommand.java \
        core/src/main/java/me/nagasonic/alkatraz/commands/SpellsCommand.java \
        core/src/main/java/me/nagasonic/alkatraz/commands/RecipesCommand.java \
        core/src/main/java/me/nagasonic/alkatraz/commands/CastCommand.java
git commit -m "refactor(lang): migrate command strings to LangManager"
```

---

### Task 10: Migrate Spells System

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/Spell.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/SpellCastValidator.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/spellbooks/Spellbook.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/spellbooks/RandomSpellbook.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/spellbooks/SpellbookFactory.java`
- Modify: ~30 spell implementation files in `core/src/main/java/me/nagasonic/alkatraz/spells/implementation/`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/components/PooledModifierSpellSupport.java`

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key, placeholders...)` from Task 1
- Produces: No new public interfaces

**Note:** Each spell implementation file has 2-3 book display strings (name, description lore) and 1-3 error/action messages. The book strings are used in `getBookDisplay()` and are formatted with ColorFormat. The error/action messages are sent via `player.sendMessage()` or `Utils.sendActionBar()`.

- [ ] **Step 1: Migrate Spell.java (base class)**

Replace:
- `"&cToo low Magic Circle"` → `spells.cast.too_low_circle`
- `"&cYou cannot cast right now"` → `spells.cast.cannot_cast_now`
- `"&cNot Enough Mana"` → `spells.cast.not_enough_mana`
- `"&cPlease wait"` → `spells.cast.please_wait`
- `"Casted:"` → `spells.cast.casted`

- [ ] **Step 2: Migrate SpellCastValidator.java**

Replace:
- `"&cYou need a better tool to cast this."` → `spells.cast.need_tool`
- `"&cToo low Magic Circle"` → `spells.cast.too_low_circle`
- `"&cYou have not discovered this spell."` → `spells.cast.not_discovered`

- [ ] **Step 3: Migrate Spellbook.java**

Replace:
- `"&cThis spellbook appears to be corrupted!"` → `spellbook.corrupted`
- `"&eYou already know"` → `spellbook.already_known` with `%spell%`
- `"&cRequirement not met:"` → `spellbook.requirement_not_met`
- `"&aYou have discovered"` → `spellbook.discovered` with `%spell%`
- `"&7Use &e/spells &7to view your discovered spells."` → `spellbook.view_spells_hint`
- `"Requirements:"` → `spellbook.requirements_header`
- `"Effects:"` → `spellbook.effects_header`

- [ ] **Step 4: Migrate RandomSpellbook.java**

Replace all 6 strings with corresponding `spellbook.*` keys.

- [ ] **Step 5: Migrate SpellbookFactory.java**

Replace: `spellbook_factory.random_name`, `spellbook_factory.element_lore`

- [ ] **Step 6: Migrate PooledModifierSpellSupport.java**

Replace: `"&cNo effects configured! Open the spell options menu."` → `pooled.no_effects`

- [ ] **Step 7: Migrate ~30 spell implementation files**

For each spell file (e.g., Fireball.java, Tsunami.java, etc.), replace:
- Book display name strings (in `getBookDisplay()`) with lang keys
- Error messages (in `onCast()` or validation) with lang keys
- Action messages (buff/debuff notifications) with lang keys

**Important:** Each spell has unique strings. The plan cannot enumerate all ~90 strings here. The implementer must:
1. Read each spell file
2. Find all `ColorFormat.format(...)` or `Utils.chat(...)` or `player.sendMessage(...)` calls with hardcoded strings
3. Replace with `Alkatraz.getLangManager().get("spells.spellname.key")` calls
4. Add corresponding entries to `english.lang`

For spells where the book name/description comes from YAML config, add those to the YAML Override section of english.lang with a comment explaining they override the YAML value.

- [ ] **Step 8: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/spells/
git commit -m "refactor(lang): migrate spells system to LangManager"
```

---

### Task 11: Migrate Tutorial, ProgressionService, Utility Classes

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/tutorial/FirstJoinTutorial.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/progression/ProgressionService.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/items/magic/itemstack/MagicItemStack.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/components/SpellHotbarManager.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/components/WandComponentHandler.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/util/UpdateChecker.java`

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key, placeholders...)` from Task 1
- Produces: No new public interfaces

- [ ] **Step 1: Migrate FirstJoinTutorial.java**

Replace all ~40 tutorial messages with `tutorial.*` keys. This file has:
- Title/subtitle strings → `tutorial.welcome_title`, `tutorial.welcome_subtitle`
- Step titles → `tutorial.step1_title`, `tutorial.step2_title`, etc.
- Step subtitles → `tutorial.step1_subtitle`, etc.
- Chat messages → `tutorial.step1_chat`, etc.
- Done message → `tutorial.done_title`, `tutorial.done_subtitle`

- [ ] **Step 2: Migrate ProgressionService.java**

Replace:
- `"&e&lCIRCLE UP!"` → `progression.circle_up_title`
- `"&bReached the "` → `progression.circle_up_subtitle` with `%ordinal%`
- `"&bYou are now able to use spells up to the "` → `progression.circle_up_spell_rank` with `%rank%`
- `"&bArcane Knowledge: "` → `progression.circle_up_ak` with `%current%`/`%max%`

- [ ] **Step 3: Migrate MagicItemStack.java**

Replace element section header and individual element display lines with `magic_item.*` keys.

- [ ] **Step 4: Migrate SpellHotbarManager.java**

Replace slot display strings with `hotbar.slot_empty` and `hotbar.slot_empty_lore`.

- [ ] **Step 5: Migrate WandComponentHandler.java**

Replace any user-facing action bar messages with lang keys.

- [ ] **Step 6: Migrate UpdateChecker.java**

Replace the update notification message string (if user-facing).

- [ ] **Step 7: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/tutorial/FirstJoinTutorial.java \
        core/src/main/java/me/nagasonic/alkatraz/progression/ProgressionService.java \
        core/src/main/java/me/nagasonic/alkatraz/items/magic/itemstack/MagicItemStack.java \
        core/src/main/java/me/nagasonic/alkatraz/spells/components/SpellHotbarManager.java \
        core/src/main/java/me/nagasonic/alkatraz/spells/components/WandComponentHandler.java \
        core/src/main/java/me/nagasonic/alkatraz/util/UpdateChecker.java
git commit -m "refactor(lang): migrate tutorial, progression, and utility strings"
```

---

### Task 12: YAML String Integration

**Files:**
- Modify: `core/src/main/resources/lang/english.lang` — add YAML override keys
- Modify: `core/src/main/java/me/nagasonic/alkatraz/spells/Spell.java` — override `getBookDisplay()` name/description from lang if available
- Modify: `core/src/main/java/me/nagasonic/alkatraz/progression/research/ResearchService.java` — override research display_name/description from lang if available

**Interfaces:**
- Consumes: `Alkatraz.getLangManager().get(key)` (returns raw key if not found — lang takes precedence)
- Produces: No new public interfaces

**Concept:** For YAML-configured strings (spell display_name, spell description, research node names, etc.), the lang file can optionally override them. The pattern:
1. Try `lang.get("override.spells.fireball.name")` — if key exists, use it
2. Fall back to YAML value

This is a soft override: if the lang key doesn't exist, the YAML value is used unchanged.

- [ ] **Step 1: Add YAML override keys to english.lang**

Add a section at the bottom of english.lang with a comment explaining the override system:

```properties
# ==============================
# YAML Overrides (optional)
# Uncomment or add keys here to override YAML-defined display strings.
# If a key is missing, the YAML value is used unchanged.
# Format: override.<type>.<id>.<field>
# ==============================
# Example: override.spells.fireball.name = &6Fireball
# Example: override.spells.fireball.description = &7A small ball of fire...
```

Leave all override keys commented out by default (YAML values are the default).

- [ ] **Step 2: Add lang fallback to Spell.getBookDisplay()**

In `Spell.java`, the `getBookDisplay()` method constructs book pages using the spell's YAML `display_name` and `description`. Modify it to first check the lang file:

```java
// Before:
String name = ColorFormat.format(config.getString("display_name"));
// After:
String name = Alkatraz.getLangManager().get("override.spells." + getId() + ".name");
// If get() returns the raw key (no override exists), fall back to YAML:
if (name.equals("override.spells." + getId() + ".name")) {
    name = ColorFormat.format(config.getString("display_name"));
}
```

Same pattern for description lines.

- [ ] **Step 3: Add lang fallback to ResearchService (if applicable)**

If research node display names are user-facing and loaded from `research.yml`, apply the same pattern: check lang override first, fall back to YAML.

- [ ] **Step 4: Verify build**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/lang/english.lang \
        core/src/main/java/me/nagasonic/alkatraz/spells/Spell.java \
        core/src/main/java/me/nagasonic/alkatraz/progression/research/ResearchService.java
git commit -m "feat(lang): add YAML string override system via lang file"
```

---

### Task 13: Final Build Verification + Sweep

**Files:**
- No new files — verification only

**Interfaces:**
- Consumes: All prior tasks
- Produces: Green build, sweep report

- [ ] **Step 1: Full clean build**

Run: `mvn clean package -pl core`
Expected: BUILD SUCCESS

- [ ] **Step 2: Sweep for remaining hardcoded strings**

Run grep searches to find any remaining hardcoded `&` color-coded strings in Java files that should have been migrated:

```bash
# Search for ColorFormat.format with hardcoded &-coded strings in GUI files
grep -rn "ColorFormat.format(\"&" core/src/main/java/me/nagasonic/alkatraz/gui/
grep -rn "ColorFormat.format(\"&" core/src/main/java/me/nagasonic/alkatraz/commands/
grep -rn "ColorFormat.format(\"&" core/src/main/java/me/nagasonic/alkatraz/spells/
grep -rn "ColorFormat.format(\"&" core/src/main/java/me/nagasonic/alkatraz/tutorial/
```

Expected: Very few or zero results (some may remain for color-only formatting in NBT data or non-user-facing contexts).

- [ ] **Step 3: Verify lang file key count**

Count the number of non-comment, non-empty lines in english.lang:
```bash
grep -c "^[^#].*=" core/src/main/resources/lang/english.lang
```
Expected: ~650+ keys

- [ ] **Step 4: Commit (if any sweep fixes)**

```bash
git commit -m "chore(lang): final sweep for remaining hardcoded strings"
```
