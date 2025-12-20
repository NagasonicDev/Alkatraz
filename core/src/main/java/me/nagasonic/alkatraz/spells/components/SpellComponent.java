package me.nagasonic.alkatraz.spells.components;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface SpellComponent {
    Spell getSpell();
    Player getCaster();
    ItemStack getWand();
    SpellComponentType getType();
    UUID getComponentID();
}
