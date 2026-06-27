package me.nagasonic.alkatraz.spells;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.dom.Permission;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SpellCastValidator {

    private SpellCastValidator() {}

    public static boolean canCast(Player player, ItemStack wand, Spell spell) {
        if (spell == null) return false;
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);

        if (wand != null) {
            int wandCircleLimit = NBT.get(wand, nbt -> (Integer) nbt.getInteger("circle_limit"));
            if (wandCircleLimit < spell.getRequiredCircleLevel()) {
                Utils.sendActionBar(player, "&cYou need a better wand to cast this.");
                return false;
            }
        }

        if (profile.getCircleLevel() < spell.getRequiredCircleLevel()) {
            Utils.sendActionBar(player, "&cToo low Magic Circle");
            return false;
        }

        if (!profile.hasDiscoveredSpell(spell) && !Permission.hasPermission(player, Permission.ALL_SPELLS)) {
            Utils.sendActionBar(player, "&cYou have not discovered this spell.");
            return false;
        }

        return true;
    }
}
