package me.nagasonic.alkatraz.events;

import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class CastEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final LivingEntity caster;
    private final SpellProperties props;
    private final ItemStack wand;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public CastEvent(LivingEntity caster, SpellProperties spell, ItemStack wand){
        this.caster = caster;
        this.props = spell;
        this.wand = wand;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancel) {

    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public LivingEntity getCaster() {
        return caster;
    }

    public SpellProperties getProps() {
        return props;
    }

    public ItemStack getWand() {
        return wand;
    }
}
