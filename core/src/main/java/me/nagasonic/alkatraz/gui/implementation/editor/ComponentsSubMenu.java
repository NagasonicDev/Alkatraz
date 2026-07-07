package me.nagasonic.alkatraz.gui.implementation.editor;

import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
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

public class ComponentsSubMenu extends Menu {

    private static final int[] COMP_SLOTS = {10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int BACK_SLOT = 42;

    private final ItemDetailMenu parent;
    private final EditorSession session;

    public ComponentsSubMenu(Player viewer, ItemDetailMenu parent) {
        super(viewer, ColorFormat.format("&8Edit Components"), 45);
        this.parent = parent;
        this.session = EditorSession.get(viewer.getUniqueId());
    }

    @Override
    protected void build() {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, createBackgroundPane());
        }

        List<String> currentComps = session.config().getStringList("components");
        var allTypes = MagicItemRegistries.COMPONENT_TYPES.values();
        int idx = 0;
        for (var type : allTypes) {
            if (idx >= COMP_SLOTS.length) break;
            String key = MagicKeys.format(type.getKey());
            boolean enabled = currentComps.contains(key);
            inventory.setItem(COMP_SLOTS[idx], createCompItem(key, enabled));
            idx++;
        }

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

    private ItemStack createCompItem(String key, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format((enabled ? "&a" : "&7") + key));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format(enabled ? "&aEnabled" : "&7Disabled"));
            lore.add(ColorFormat.format("&7Click to toggle"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        setMenuData(item, "comp_key", key);
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
            parent.markNeedsSave();
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            new ItemDetailMenu(viewer, parent.definition, parent.defKey).open();
            return true;
        }

        String compKey = getStringData(clicked, "comp_key");
        if (compKey != null) {
            toggleComponent(compKey);
            return true;
        }

        return true;
    }

    private void toggleComponent(String key) {
        List<String> current = new ArrayList<>(session.config().getStringList("components"));
        if (current.contains(key)) {
            current.remove(key);
        } else {
            current.add(key);
        }
        session.config().set("components", current);
        parent.markNeedsSave();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        refresh();
    }
}
