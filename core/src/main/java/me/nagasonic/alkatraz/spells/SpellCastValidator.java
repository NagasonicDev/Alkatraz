package me.nagasonic.alkatraz.spells;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.dom.Permission;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
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
            int wandCircleLimit = getWandCircleLimit(player, wand);
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

    private static int getWandCircleLimit(Player player, ItemStack wand) {
        // Try new PDC magic item first
        if (MagicItemStack.isMagicItem(wand)) {
            return MagicItemStack.readInstance(wand)
                    .flatMap(instance -> MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey()))
                    .map(def -> (int) def.attributeOrDefault(MagicKeys.alkatraz("max_circle"), 1))
                    .orElse(1);
        }
        // Fall back to legacy NBT
        return NBT.get(wand, nbt -> {
            if (nbt.hasTag("circle_limit")) {
                return nbt.getInteger("circle_limit");
            }
            return 1;
        });
    }
}
