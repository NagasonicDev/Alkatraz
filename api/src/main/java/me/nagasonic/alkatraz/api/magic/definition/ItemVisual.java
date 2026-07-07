package me.nagasonic.alkatraz.api.magic.definition;

import org.bukkit.Color;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ItemVisual(
        Material material,
        String displayName,
        List<String> lore,
        int customModelData,
        boolean unbreakable,
        boolean hideAttributes,
        @Nullable Color dyeColor
) {
    public static ItemVisual of(Material material, String displayName, List<String> lore) {
        return new ItemVisual(material, displayName, lore, 0, false, true, null);
    }
}
