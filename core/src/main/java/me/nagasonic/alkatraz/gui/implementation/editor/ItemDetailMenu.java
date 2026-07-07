package me.nagasonic.alkatraz.gui.implementation.editor;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemDetailMenu extends Menu {

    private static final int PREVIEW_SLOT = 22;
    private static final int[] FIELD_SLOTS = {10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 23, 24, 25,
            28, 29, 30, 32, 33, 34,
            39, 40, 41, 43};

    final ItemDefinition definition;
    final String defKey;
    private final EditorSession session;
    private boolean needsSave;

    public ItemDetailMenu(Player viewer, ItemDefinition definition, String defKey) {
        super(viewer, ColorFormat.format("&8Edit: " + defKey), 45);
        this.definition = definition;
        this.defKey = defKey;
        this.session = EditorSession.get(viewer.getUniqueId());
        this.needsSave = false;
    }

    @Override
    protected void build() {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, createBackgroundPane());
        }

        inventory.setItem(PREVIEW_SLOT, createPreviewItem());

        inventory.setItem(10, createFieldItem(Material.NAME_TAG, "&eDisplay Name",
                "&7Current: &f" + getConfigString("display_name", definition.getKey().getKey()),
                "&7Click to edit", "edit_display_name"));
        inventory.setItem(11, createFieldItem(Material.WRITABLE_BOOK, "&eLore",
                "&7" + getConfigListSize("lore") + " line(s)", "&7Click to edit", "edit_lore"));
        inventory.setItem(12, createFieldItem(Material.BRICK, "&eMaterial",
                "&7Current: &f" + getConfigString("material", definition.visual().material().name()),
                "&7Click to edit", "edit_material"));
        inventory.setItem(13, createFieldItem(Material.LEATHER, "&eDye Color",
                "&7Current: &f" + getConfigString("dye_color", "none"),
                "&7Click to edit", "edit_dye_color"));
        inventory.setItem(14, createFieldItem(Material.REPEATER, "&eCustom Model Data",
                "&7Current: &f" + getConfigInt("custom_model_data", 0),
                "&7Click to edit", "edit_cmd"));
        inventory.setItem(15, createFieldItem(Material.SHIELD, "&eUnbreakable",
                "&7Current: &f" + getConfigBool("unbreakable", false),
                "&7Click to toggle", "toggle_unbreakable"));
        inventory.setItem(16, createFieldItem(Material.ENDER_EYE, "&eHide Attributes",
                "&7Current: &f" + getConfigBool("hide_attributes", true),
                "&7Click to toggle", "toggle_hide_attributes"));

        inventory.setItem(19, createFieldItem(Material.CRAFTING_TABLE, "&eComponents",
                "&7" + getConfigListSize("components") + " component(s)", "&7Click to edit", "edit_components"));
        inventory.setItem(20, createFieldItem(Material.GOLD_NUGGET, "&eAttributes",
                "&7" + getConfigSectionSize("attributes") + " attribute(s)", "&7Click to edit", "edit_attributes"));
        inventory.setItem(21, createFieldItem(Material.IRON_NUGGET, "&eVanilla Attributes",
                "&7" + getConfigSectionSize("vanilla_attributes") + " attribute(s)", "&7Click to edit", "edit_vanilla_attributes"));
        inventory.setItem(23, createFieldItem(Material.ENCHANTED_BOOK, "&eMax Engravings",
                "&7Current: &f" + getConfigInt("max_engravings", 1),
                "&7Click to edit", "edit_max_engravings"));
        inventory.setItem(24, createFieldItem(Material.PAPER, "&eSpell ID",
                "&7Current: &f" + getConfigString("spell_id", "none"),
                "&7Click to edit", "edit_spell_id"));
        inventory.setItem(25, createFieldItem(Material.REDSTONE, "&eTriggers",
                "&7" + getConfigListSize("triggers") + " trigger(s)", "&7Click to edit", "edit_triggers"));

        inventory.setItem(28, createFieldItem(Material.CRAFTING_TABLE, "&eRecipe Shape",
                "&7Configure the crafting grid", "&7Click to edit", "edit_recipe_shape"));
        inventory.setItem(29, createFieldItem(Material.HOPPER, "&eRecipe Ingredients",
                "&7Configure ingredient materials", "&7Click to edit", "edit_recipe_ingredients"));
        inventory.setItem(30, createFieldItem(Material.BOOK, "&eRecipe Requirements",
                "&7" + getRecipeRequirementsCount() + " requirement(s)", "&7Click to edit", "edit_requirements"));

        inventory.setItem(39, createActionItem(Material.LIME_DYE, "&a&lSave",
                "&7Save changes to config file",
                needsSave ? "&eYou have unsaved changes!" : "", "save"));
        inventory.setItem(40, createActionItem(Material.YELLOW_DYE, "&eReload",
                "&7Discard changes and reload from file", "", "reload"));
        inventory.setItem(41, createActionItem(Material.BARRIER, "&cBack",
                "&7Return to item list", "", "back"));
        inventory.setItem(43, createActionItem(Material.EMERALD, "&b&lGet Item",
                "&7Get a copy of this item", "", "get_item"));
    }

    private ItemStack createBackgroundPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&7"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPreviewItem() {
        MagicItemInstance instance = MagicItemInstance.createDefault(definition.getKey());
        ItemStack display = MagicItemStack.create(definition, instance);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();
            lore.add("");
            lore.add(ColorFormat.format("&8" + MagicKeys.format(definition.getKey())));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        setMenuData(display, "action", "preview");
        return display;
    }

    private ItemStack createFieldItem(Material material, String displayName, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format(displayName));
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                if (line != null && !line.isEmpty()) {
                    lore.add(ColorFormat.format(line));
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createActionItem(Material material, String displayName, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format(displayName));
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                if (line != null && !line.isEmpty()) {
                    lore.add(ColorFormat.format(line));
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        int slot = event.getRawSlot();
        if (slot == PREVIEW_SLOT) return true;

        if (slot == 15) { toggleUnbreakable(); return true; }
        if (slot == 16) { toggleHideAttributes(); return true; }
        if (slot == 39) { handleSave(); return true; }
        if (slot == 40) { handleReload(); return true; }
        if (slot == 41) { handleBack(); return true; }
        if (slot == 43) { handleGetItem(); return true; }

        if (slot == 10) { promptChat("edit_display_name", "Enter new display name (& color codes supported):"); return true; }
        if (slot == 11) { new LoreSubMenu(viewer, this).open(); return true; }
        if (slot == 12) { promptChat("edit_material", "Enter new material name (e.g. STICK, DIAMOND_SWORD):"); return true; }
        if (slot == 13) { promptChat("edit_dye_color", "Enter hex color code (e.g. FFCC88) or 'none' to remove:"); return true; }
        if (slot == 14) { promptChat("edit_cmd", "Enter custom model data number (or 0 for none):"); return true; }
        if (slot == 19) { new ComponentsSubMenu(viewer, this).open(); return true; }
        if (slot == 20) { new AttributesSubMenu(viewer, this, "attributes").open(); return true; }
        if (slot == 21) { new AttributesSubMenu(viewer, this, "vanilla_attributes").open(); return true; }
        if (slot == 23) { promptChat("edit_max_engravings", "Enter max engravings number:"); return true; }
        if (slot == 24) { promptChat("edit_spell_id", "Enter spell ID (or 'none' to remove):"); return true; }
        if (slot == 25) { viewer.sendMessage(ColorFormat.format("&cTriggers editing is not yet implemented in the GUI.")); return true; }
        if (slot == 28) { new RecipeSubMenu(viewer, this).open(); return true; }
        if (slot == 29) { new RecipeSubMenu(viewer, this).open(); return true; }
        if (slot == 30) { new RequirementsSubMenu(viewer, this).open(); return true; }

        return true;
    }

    private void promptChat(String action, String message) {
        if (session == null) {
            viewer.sendMessage(ColorFormat.format("&cError: No editing session found."));
            return;
        }
        viewer.closeInventory();
        session.setPendingChatAction(action);
        EditorChatHandler.prompt(viewer, message, this);
    }

    private void toggleUnbreakable() {
        boolean current = session.config().getBoolean("unbreakable", false);
        session.config().set("unbreakable", !current);
        needsSave = true;
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        refresh();
    }

    private void toggleHideAttributes() {
        boolean current = session.config().getBoolean("hide_attributes", true);
        session.config().set("hide_attributes", !current);
        needsSave = true;
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        refresh();
    }

    public void markNeedsSave() {
        this.needsSave = true;
    }

    private void handleSave() {
        session.save();
        needsSave = false;
        viewer.sendMessage(ColorFormat.format("&aSaved changes to &f" + defKey + "&a."));
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        Alkatraz.getInstance().getServer().dispatchCommand(
                Alkatraz.getInstance().getServer().getConsoleSender(), "alkatraz reload");
        refresh();
    }

    private void handleReload() {
        session.reload();
        needsSave = false;
        viewer.sendMessage(ColorFormat.format("&eReloaded &f" + defKey + " &efrom file."));
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        refresh();
    }

    private void handleBack() {
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        new ItemEditorMenu(viewer).open();
    }

    private void handleGetItem() {
        MagicItemInstance instance = MagicItemInstance.createDefault(definition.getKey());
        ItemStack stack = MagicItemStack.create(definition, instance);
        viewer.getInventory().addItem(stack)
                .values().forEach(leftover ->
                        viewer.getWorld().dropItemNaturally(viewer.getLocation(), leftover));
        viewer.sendMessage(ColorFormat.format("&aGiven you a copy of &f" + defKey + "&a."));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    private String getConfigString(String path, String fallback) {
        String val = session.config().getString(path);
        return val != null ? val : fallback;
    }

    private int getConfigInt(String path, int fallback) {
        return session.config().getInt(path, fallback);
    }

    private boolean getConfigBool(String path, boolean fallback) {
        return session.config().getBoolean(path, fallback);
    }

    private int getConfigListSize(String path) {
        List<?> list = session.config().getList(path);
        return list != null ? list.size() : 0;
    }

    private int getConfigSectionSize(String path) {
        var section = session.config().getConfigurationSection(path);
        if (section == null) return 0;
        return section.getKeys(false).size();
    }

    private int getRecipeRequirementsCount() {
        List<?> reqs = session.config().getList("requirements");
        if (reqs != null) return reqs.size();
        var sec = session.config().getConfigurationSection("requirements");
        if (sec != null) return sec.getKeys(false).size();
        return 0;
    }
}
