package me.nagasonic.alkatraz.items.magic.definition;

import org.bukkit.Material;

import java.util.List;

/**
 * Immutable visual presentation data for an item definition.
 */
public record ItemVisual(
        Material material,
        String displayName,
        List<String> lore,
        int customModelData,
        boolean unbreakable,
        boolean hideAttributes
) {
    public static ItemVisual of(Material material, String displayName, List<String> lore) {
        return new ItemVisual(material, displayName, lore, 0, false, true);
    }
}
