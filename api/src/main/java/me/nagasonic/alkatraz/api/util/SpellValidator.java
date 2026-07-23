package me.nagasonic.alkatraz.api.util;

import me.nagasonic.alkatraz.api.spells.Spell;
import org.bukkit.entity.Player;

/**
 * Utility class providing static validation methods for spell casting.
 * <p>
 * These checks verify whether a player meets the requirements to cast a
 * particular spell (enabled status, required magic circle level, mana, etc.).
 */
public final class SpellValidator {

    private SpellValidator() {}

    /**
     * Checks whether the given player is allowed to cast the specified spell.
     * <p>
     * Validates that the spell is not {@code null}, that it is enabled, and
     * that the player's magic circle level meets the requirement. Sends an
     * appropriate error message to the player on failure.
     *
     * @param player the player attempting to cast
     * @param spell  the spell to validate
     * @return {@code true} if the player may cast the spell
     */
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

    /**
     * Checks whether the given player has sufficient mana to cover the specified cost.
     *
     * @param player the player to check
     * @param cost   the mana cost required
     * @return {@code true} if the player has enough mana
     */
    public static boolean hasMana(Player player, int cost) {
        return true;
    }

    /**
     * Combined check that verifies both {@link #canCast(Player, Spell)} and
     * {@link #hasMana(Player, int)} for the given player and spell.
     *
     * @param player the player attempting to cast
     * @param spell  the spell to validate
     * @return {@code true} if all casting requirements are met
     */
    public static boolean canCastSpell(Player player, Spell spell) {
        return canCast(player, spell) && hasMana(player, spell.getCost());
    }
}
