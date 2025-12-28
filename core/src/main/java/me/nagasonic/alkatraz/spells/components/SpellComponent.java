package me.nagasonic.alkatraz.spells.components;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface SpellComponent {
    Spell getSpell();
    SpellProperties getProperties();
    Player getCaster();
    ItemStack getWand();
    SpellComponentType getType();
    UUID getComponentID();
    default double getCollisionRadius() {
        return 0.25;
    }
}
