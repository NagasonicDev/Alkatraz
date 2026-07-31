package me.nagasonic.alkatraz.util;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import org.bukkit.inventory.ItemStack;

public final class WandUtils {

    private WandUtils() {}

    /**
     * Checks if an ItemStack is a wand — either a legacy NBT wand or a new PDC magic item.
     */
    public static boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (isLegacyWand(item)) return true;
        return MagicItemStack.isMagicItem(item) && MagicItemStack.isWandDefinition(item);
    }

    /**
     * Alias for backward compatibility with code that specifically checks both.
     */
    public static boolean isWandOrMagicItem(ItemStack stack) {
        return isWand(stack);
    }

    /**
     * Checks if an ItemStack is a legacy NBT wand (has "wand" boolean tag).
     */
    public static boolean isLegacyWand(ItemStack item) {
        if (item == null) return false;
        Boolean result = NBT.get(item, nbt -> (Boolean) nbt.getBoolean("wand"));
        return result != null && result;
    }

    /**
     * Checks if an ItemStack is a grimoire.
     */
    public static boolean isGrimoire(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return MagicItemStack.isMagicItem(item) && MagicItemStack.isGrimoireDefinition(item);
    }
}
