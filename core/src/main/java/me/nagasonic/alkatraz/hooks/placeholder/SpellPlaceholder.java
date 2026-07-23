package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class SpellPlaceholder implements Placeholder {

    @Override
    public String name() {
        return "spells";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile == null) return "";

        // Mastery: _percent and _max suffixes must be checked BEFORE plain mastery_
        if (params.startsWith("mastery_")) {
            if (params.endsWith("_percent")) {
                String spellId = params.substring(8, params.length() - 8);
                Spell spell = SpellRegistry.getSpell(spellId);
                if (spell == null) return "";
                int mastery = profile.getSpellMastery(spell);
                int max = spell.getMaxMastery();
                return max > 0 ? String.valueOf((double) mastery / max * 100) : "0";
            }
            if (params.endsWith("_max")) {
                String spellId = params.substring(8, params.length() - 4);
                Spell spell = SpellRegistry.getSpell(spellId);
                if (spell == null) return "";
                return String.valueOf(spell.getMaxMastery());
            }
            String spellId = params.substring(8);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell == null) return "";
            return String.valueOf(profile.getSpellMastery(spell));
        }

        // Cooldown: _ready suffix must be checked BEFORE plain cooldown_
        if (params.startsWith("cooldown_")) {
            if (params.endsWith("_ready")) {
                String spellId = params.substring(9, params.length() - 6);
                return String.valueOf(isReady(profile, spellId));
            }
            if (params.startsWith("cooldown_time_")) {
                String spellId = params.substring(14);
                Spell spell = SpellRegistry.getSpell(spellId);
                if (spell == null) return "";
                return String.valueOf(spell.getCooldown());
            }
            String spellId = params.substring(9);
            return String.valueOf(getRemainingCooldown(profile, spellId));
        }

        // has_, name_, element_, circle_, mana_cost_, code_
        if (params.startsWith("has_")) {
            String spellId = params.substring(4);
            return String.valueOf(profile.getAllDiscoveredSpellTypes().contains(spellId.toLowerCase()));
        }

        if (params.startsWith("name_")) {
            String spellId = params.substring(5);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell == null) return "";
            return spell.getDisplayName();
        }

        if (params.startsWith("element_")) {
            String spellId = params.substring(8);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell == null) return "";
            return spell.getElement().name();
        }

        if (params.startsWith("circle_")) {
            String spellId = params.substring(7);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell == null) return "";
            return String.valueOf(spell.getRequiredCircleLevel());
        }

        if (params.startsWith("mana_cost_")) {
            String spellId = params.substring(10);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell == null) return "";
            return String.valueOf(spell.getCost());
        }

        if (params.startsWith("code_")) {
            String spellId = params.substring(5);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell == null) return "";
            return spell.getCode();
        }

        return "";
    }

    private boolean isReady(MagicProfile profile, String spellId) {
        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) return true;
        Long cooldownSet = profile.getCooldown(spell);
        if (cooldownSet == null) return true;
        long elapsed = System.currentTimeMillis() - cooldownSet;
        return TimeUnit.MILLISECONDS.toSeconds(elapsed) >= spell.getCooldown();
    }

    private long getRemainingCooldown(MagicProfile profile, String spellId) {
        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) return 0;
        Long cooldownSet = profile.getCooldown(spell);
        if (cooldownSet == null) return 0;
        long elapsed = System.currentTimeMillis() - cooldownSet;
        long remaining = spell.getCooldown() - TimeUnit.MILLISECONDS.toSeconds(elapsed);
        return Math.max(0, remaining);
    }
}
