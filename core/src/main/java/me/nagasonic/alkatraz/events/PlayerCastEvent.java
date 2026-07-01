package me.nagasonic.alkatraz.events;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerCastEvent extends CastEvent {
    private final Player caster;

    public PlayerCastEvent(Player caster, Spell spell, ItemStack wand) {
        super(caster, spell, wand);
        this.caster = caster;
    }

    @Override
    public Player getCaster(){
        return caster;
    }
}
