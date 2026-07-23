package me.nagasonic.alkatraz.api.magic.definition;

import org.bukkit.Color;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Defines the visual appearance of a magic item, including its material, display name,
 * lore, custom model data, and optional dye color.
 *
 * @param material       the Bukkit material used for this item
 * @param displayName    the display name shown to players
 * @param lore           the lore lines displayed below the name
 * @param customModelData the custom model data value for resource pack model selection
 * @param unbreakable    whether the item appears unbreakable
 * @param hideAttributes whether vanilla attribute modifiers are hidden from the tooltip
 * @param dyeColor       the optional dye color applied to the item, or {@code null}
 */
public record ItemVisual(
        Material material,
        String displayName,
        List<String> lore,
        int customModelData,
        boolean unbreakable,
        boolean hideAttributes,
        @Nullable Color dyeColor
) {
    /**
     * Creates a simple visual with default settings (no custom model data, not unbreakable,
     * hidden attributes, no dye color).
     *
     * @param material    the Bukkit material
     * @param displayName the display name
     * @param lore        the lore lines
     * @return a new {@link ItemVisual} with the given parameters and defaults
     */
    public static ItemVisual of(Material material, String displayName, List<String> lore) {
        return new ItemVisual(material, displayName, lore, 0, false, true, null);
    }
}
