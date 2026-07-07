package me.nagasonic.alkatraz.api.events;

import me.nagasonic.alkatraz.api.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerSpellPrepareEvent extends SpellPrepareEvent {
    private final Player caster;

    public PlayerSpellPrepareEvent(Player caster, Spell spell, ItemStack wand) {
        super(caster, spell, wand);
        this.caster = caster;
    }

    @Override
    public Player getCaster() { return caster; }
}
