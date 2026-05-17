package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Buff extends Spell {

    public Buff(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {

    }

    @Override
    public void castAction(Player p, ItemStack wand) {

    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {

    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return 0;
    }

    @Override
    public ItemStack getSpellBook() {
        return null;
    }
}
