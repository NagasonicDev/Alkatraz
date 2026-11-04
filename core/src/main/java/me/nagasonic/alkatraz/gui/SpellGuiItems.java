package me.nagasonic.alkatraz.gui;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Shared spell icon construction for menus and the live wand hotbar. */
public final class SpellGuiItems {
    private SpellGuiItems() {}

    /** Returns a copy of the configured spell icon with its resource-pack model assigned. */
    public static ItemStack create(Spell spell) {
        ItemStack item = spell.getGuiItem().clone();
        int customModelData = spell.getGuiCustomModelData();
        if (customModelData > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                item.setItemMeta(meta);
            }
        }
        return item;
    }
}
