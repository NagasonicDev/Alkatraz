package me.nagasonic.alkatraz.util;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;

/**
 * Cross-version material resolution using XSeries.
 * Handles material renames (e.g. CHAIN→IRON_CHAIN in 1.21.9) automatically.
 */
public final class MaterialCompat {

    private MaterialCompat() {}

    public static Material resolve(String name) {
        return resolve(name, null);
    }

    public static Material resolve(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        return XMaterial.matchXMaterial(name)
                .map(XMaterial::parseMaterial)
                .orElse(fallback);
    }
}
