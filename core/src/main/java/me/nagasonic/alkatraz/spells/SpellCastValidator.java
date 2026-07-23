package me.nagasonic.alkatraz.spells;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.dom.Permission;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SpellCastValidator {

    private SpellCastValidator() {}

    private static LangManager lang() {
        return me.nagasonic.alkatraz.Alkatraz.getLangManager();
    }

    public static boolean canCast(Player player, ItemStack wand, Spell spell) {
        if (spell == null) {
            me.nagasonic.alkatraz.Alkatraz.logDebug("[Grimoire] SpellCastValidator: spell is null, returning false");
            return false;
        }
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        me.nagasonic.alkatraz.Alkatraz.logDebug("[Grimoire] SpellCastValidator: player=" + player.getName() + " spell=" + spell.getId() 
            + " requiredCircle=" + spell.getRequiredCircleLevel() + " profileCircle=" + (profile != null ? profile.getCircleLevel() : "null"));

        if (wand != null) {
            int toolCircleLimit = getToolCircleLimit(player, wand);
            me.nagasonic.alkatraz.Alkatraz.logDebug("[Grimoire] SpellCastValidator: toolCircleLimit=" + toolCircleLimit);
            if (toolCircleLimit < spell.getRequiredCircleLevel()) {
                Utils.sendActionBar(player, lang().get("spells.cast.need_tool"));
                me.nagasonic.alkatraz.Alkatraz.logDebug("[Grimoire] SpellCastValidator: FAILED tool circle limit too low");
                return false;
            }
        }

        if (profile.getCircleLevel() < spell.getRequiredCircleLevel()) {
            Utils.sendActionBar(player, lang().get("spells.cast.too_low_circle"));
            me.nagasonic.alkatraz.Alkatraz.logDebug("[Grimoire] SpellCastValidator: FAILED player circle level too low");
            return false;
        }

        if (!profile.hasDiscoveredSpell(spell) && !Permission.hasPermission(player, Permission.ALL_SPELLS)) {
            Utils.sendActionBar(player, lang().get("spells.cast.not_discovered"));
            me.nagasonic.alkatraz.Alkatraz.logDebug("[Grimoire] SpellCastValidator: FAILED spell not discovered");
            return false;
        }

        me.nagasonic.alkatraz.Alkatraz.logDebug("[Grimoire] SpellCastValidator: PASSED all checks");
        return true;
    }

    private static int getToolCircleLimit(Player player, ItemStack wand) {
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
