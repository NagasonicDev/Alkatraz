package me.nagasonic.alkatraz.events;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PlayerCastEvent extends CastEvent {
    private final Player caster;

    public PlayerCastEvent(Player caster, SpellProperties spell, ItemStack wand) {
        super(caster, spell, wand);
        this.caster = caster;
    }

    @Override
    public Player getCaster(){
        return caster;
    }
}
