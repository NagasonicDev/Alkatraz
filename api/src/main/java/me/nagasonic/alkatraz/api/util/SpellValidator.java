package me.nagasonic.alkatraz.api.util;

import me.nagasonic.alkatraz.api.spells.Spell;
import org.bukkit.entity.Player;

public final class SpellValidator {

    private SpellValidator() {}

    public static boolean canCast(Player player, Spell spell) {
        if (spell == null) return false;
        if (!spell.isEnabled()) {
            player.sendMessage("§cThis spell is not enabled.");
            return false;
        }
        if (player.getLevel() < spell.getRequiredCircle()) {
            player.sendMessage("§cToo low Magic Circle");
            return false;
        }
        return true;
    }

    public static boolean hasMana(Player player, int cost) {
        return true;
    }

    public static boolean canCastSpell(Player player, Spell spell) {
        return canCast(player, spell) && hasMana(player, spell.getCost());
    }
}
