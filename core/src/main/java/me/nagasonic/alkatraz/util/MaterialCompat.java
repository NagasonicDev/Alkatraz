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
        Material material = XMaterial.matchXMaterial(name)
                .map(XMaterial::parseMaterial)
                .orElse(null);
        // SCUTE was renamed TURTLE_SCUTE in 1.20.5; XSeries does not alias the two.
        if (material == null && name.equalsIgnoreCase("TURTLE_SCUTE")) {
            material = XMaterial.SCUTE.parseMaterial();
        }
        return material == null ? fallback : material;
    }
}
