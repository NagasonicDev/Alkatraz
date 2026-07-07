package me.nagasonic.alkatraz.gui.implementation.editor;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class LoreSubMenu extends Menu {

    private static final int[] LORE_SLOTS = {10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int ADD_SLOT = 38;
    private static final int BACK_SLOT = 42;

    private final ItemDetailMenu parent;
    private final EditorSession session;
    private List<String> lore;

    public LoreSubMenu(Player viewer, ItemDetailMenu parent) {
        super(viewer, ColorFormat.format("&8Edit Lore"), 45);
        this.parent = parent;
        this.session = EditorSession.get(viewer.getUniqueId());
        this.lore = new ArrayList<>(session.config().getStringList("lore"));
    }

    @Override
    protected void build() {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, createBackgroundPane());
        }

        for (int i = 0; i < LORE_SLOTS.length && i < lore.size(); i++) {
            inventory.setItem(LORE_SLOTS[i], createLoreLineItem(i));
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

    private ItemStack createLoreLineItem(int index) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&eLine " + (index + 1)));
            List<String> lineLore = new ArrayList<>();
            lineLore.add(ColorFormat.format("&7" + lore.get(index)));
            lineLore.add("");
            lineLore.add(ColorFormat.format("&eLeft-click to edit"));
            lineLore.add(ColorFormat.format("&cRight-click to delete"));
            meta.setLore(lineLore);
            item.setItemMeta(meta);
        }
        setMenuData(item, "lore_index", index);
        return item;
    }

    private ItemStack createAddButton() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&a&lAdd Line"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7Click to add a new lore line"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", "add");
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

        for (int i = 0; i < LORE_SLOTS.length && i < lore.size(); i++) {
            if (slot == LORE_SLOTS[i]) {
                if (event.isRightClick()) {
                    handleDelete(i);
                } else {
                    handleEdit(i);
                }
                return true;
            }
        }

        return true;
    }

    private void handleAdd() {
        viewer.closeInventory();
        parent.markNeedsSave();
        session.setPendingChatAction("add_lore_line");
        EditorChatHandler.prompt(viewer, "Enter new lore line text:", parent, () -> new LoreSubMenu(viewer, parent).open());
    }

    private void handleEdit(int index) {
        viewer.closeInventory();
        parent.markNeedsSave();
        session.setPendingChatAction("edit_lore_line_" + index);
        EditorChatHandler.prompt(viewer, "Enter new text for line " + (index + 1) + ":", parent, () -> new LoreSubMenu(viewer, parent).open());
    }

    private void handleDelete(int index) {
        lore.remove(index);
        session.config().set("lore", lore);
        parent.markNeedsSave();
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        refresh();
    }

    private void saveAndReturn() {
        session.config().set("lore", lore);
        parent.markNeedsSave();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        new ItemDetailMenu(viewer, parent.definition, parent.defKey).open();
    }

    public List<String> lore() { return lore; }
}
