package me.nagasonic.alkatraz.events;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class CastEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final LivingEntity caster;
    private final Spell spell;
    private final ItemStack wand;
    private boolean cancelled = false;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public CastEvent(LivingEntity caster, Spell spell, ItemStack wand){
        this.caster = caster;
        this.spell = spell;
        this.wand = wand;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public Spell getSpell() {
        return spell;
    }

    public LivingEntity getCaster() {
        return caster;
    }

    public ItemStack getWand() {
        return wand;
    }
}
