package me.nagasonic.alkatraz.gui.implementation.editor;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AttributesSubMenu extends Menu {

    private static final int[] ATTR_SLOTS = {10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int ADD_SLOT = 38;
    private static final int BACK_SLOT = 42;

    private final ItemDetailMenu parent;
    private final EditorSession session;
    private final String sectionPath;
    private final List<String> keys;
    private int offset;

    public AttributesSubMenu(Player viewer, ItemDetailMenu parent, String sectionPath) {
        super(viewer, ColorFormat.format("&8Edit: " + sectionPath), 45);
        this.parent = parent;
        this.session = EditorSession.get(viewer.getUniqueId());
        this.sectionPath = sectionPath;
        ConfigurationSection sec = session.config().getConfigurationSection(sectionPath);
        this.keys = new ArrayList<>(sec != null ? sec.getKeys(false) : List.of());
        this.offset = 0;
    }

    @Override
    protected void build() {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, createBackgroundPane());
        }

        int end = Math.min(offset + ATTR_SLOTS.length, keys.size());
        for (int i = offset; i < end; i++) {
            int slotIndex = i - offset;
            if (slotIndex < ATTR_SLOTS.length) {
                inventory.setItem(ATTR_SLOTS[slotIndex], createAttrItem(keys.get(i), i));
            }
        }

        if (offset > 0) {
            inventory.setItem(36, createNavItem(Material.ARROW, "&ePrevious", "prev"));
        }
        if (end < keys.size()) {
            inventory.setItem(44, createNavItem(Material.ARROW, "&eNext", "next"));
        }

        inventory.setItem(ADD_SLOT, createAddButton());
        inventory.setItem(BACK_SLOT, createBackButton());
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

    private ItemStack createAttrItem(String key, int index) {
        ConfigurationSection sec = session.config().getConfigurationSection(sectionPath);
        double value = sec != null ? sec.getDouble(key, 0) : 0;

        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&e" + key));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7Value: &f" + value));
            lore.add("");
            lore.add(ColorFormat.format("&eLeft-click to edit value"));
            lore.add(ColorFormat.format("&cRight-click to delete"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        setMenuData(item, "attr_index", index);
        return item;
    }

    private ItemStack createAddButton() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&a&lAdd Attribute"));
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", "add");
        return item;
    }

    private ItemStack createNavItem(Material mat, String name, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format(name));
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", action);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&cBack"));
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", "back");
        return item;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            saveAndReturn();
            return true;
        }

        if (slot == ADD_SLOT) {
            handleAdd();
            return true;
        }

        if (slot == 36 && offset > 0) {
            offset = Math.max(0, offset - ATTR_SLOTS.length);
            refresh();
            return true;
        }
        if (slot == 44 && offset + ATTR_SLOTS.length < keys.size()) {
            offset += ATTR_SLOTS.length;
            refresh();
            return true;
        }

        for (int i = 0; i < ATTR_SLOTS.length; i++) {
            if (slot == ATTR_SLOTS[i]) {
                int idx = offset + i;
                if (idx < keys.size()) {
                    if (event.isRightClick()) {
                        handleDelete(idx);
                    } else {
                        handleEdit(idx);
                    }
                }
                return true;
            }
        }

        return true;
    }

    private void handleAdd() {
        viewer.closeInventory();
        parent.markNeedsSave();
        session.setPendingChatAction("add_attr:" + sectionPath);
        EditorChatHandler.prompt(viewer, "Enter new attribute as key:value (e.g. spell_power:5):", parent);
    }

    private void handleEdit(int index) {
        String key = keys.get(index);
        viewer.closeInventory();
        parent.markNeedsSave();
        session.setPendingChatAction("edit_attr:" + sectionPath + ":" + key);
        EditorChatHandler.prompt(viewer, "Enter new value for &f" + key + ":", parent);
    }

    private void handleDelete(int index) {
        String key = keys.get(index);
        session.config().set(sectionPath + "." + key, null);
        keys.remove(index);
        parent.markNeedsSave();
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        refresh();
    }

    private void saveAndReturn() {
        parent.markNeedsSave();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        new ItemDetailMenu(viewer, parent.definition, parent.defKey).open();
    }
}
