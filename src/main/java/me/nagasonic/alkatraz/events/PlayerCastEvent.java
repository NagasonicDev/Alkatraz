package me.nagasonic.alkatraz.events;

import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PlayerCastEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player caster;
    private final Spell spell;
    private final ItemStack wand;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public PlayerCastEvent(Player caster, Spell spell, ItemStack wand){
        this.caster = caster;
        this.spell = spell;
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

    public Player getCaster() {
        return caster;
    }

    public Spell getSpell() {
        return spell;
    }

    public ItemStack getWand() {
        return wand;
    }
}
