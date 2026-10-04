package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import org.bukkit.NamespacedKey;

public final class MagicDamage {

    private static final NamespacedKey MAGIC_DAMAGE_KEY = MagicKeys.alkatraz("magic_damage");

    private MagicDamage() {
    }

    public static double of(MagicItemInstance instance) {
        if (instance == null) return 0.0;
        return MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey())
                .map(def -> def.attributes().getOrDefault(MAGIC_DAMAGE_KEY, 0.0))
                .orElse(0.0);
    }
}
