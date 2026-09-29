package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeService;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.entity.Player;

public class StatsPlaceholder implements Placeholder {

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

    private static final long ENABLED_SPELL_CACHE_MS = 5000L;
    private static volatile Integer cachedEnabledSpells;
    private static volatile long cachedAt = Long.MIN_VALUE;

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile == null) return "";

        return switch (params) {
            case "circle" -> String.valueOf(profile.getCircleLevel());
            case "circle_roman" -> {
                int lvl = profile.getCircleLevel();
                yield (lvl >= 0 && lvl < ROMAN.length) ? ROMAN[lvl] : "";
            }
            case "mana" -> String.valueOf(profile.getMana());
            case "max_mana" -> String.valueOf(profile.getMaxMana());
            case "mana_percent" -> {
                double max = profile.getMaxMana();
                yield max > 0 ? String.valueOf(profile.getMana() / max * 100) : "0";
            }
            case "mana_regen" -> String.valueOf(profile.getManaRegeneration());
            case "spell_power" -> String.valueOf(
                    AttributeService.getInstance().get(player, MagicKeys.alkatraz("spell_power")));
            case "stat_points" -> String.valueOf(profile.getStatPoints());
            case "reset_tokens" -> String.valueOf(profile.getResetTokens());
            case "arcane_knowledge" -> String.valueOf(profile.getArcaneKnowledge());
            case "research_points" -> String.valueOf(profile.getResearchPoints());
            case "casting" -> String.valueOf(profile.getBool("casting"));
            case "stealth" -> String.valueOf(profile.getBool("stealth"));
            case "can_cast" -> String.valueOf(profile.getBool("canCast"));
            case "cast_mode" -> {
                String mode = profile.getCastMode();
                yield mode != null ? mode : "";
            }
            case "disguise" -> {
                String disguise = profile.getDisguise();
                yield disguise != null ? disguise : "";
            }
            case "tutorial_seen" -> String.valueOf(profile.getBool("tutorialSeen"));
            case "total_spells" -> String.valueOf(SpellRegistry.getAllSpellsByID().size());
            case "total_enabled_spells" -> String.valueOf(enabledSpellCount());
            case "discovered_count" -> String.valueOf(profile.getStringSet("discoveredSpells").size());
            default -> "";
        };
    }

    /**
     * Counting every spell in the registry on every resolution is far too
     * expensive for a placeholder, so the count is recomputed at most once
     * every 5 seconds.
     */
    private static int enabledSpellCount() {
        Integer cached = cachedEnabledSpells;
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedAt < ENABLED_SPELL_CACHE_MS) {
            return cached;
        }
        int count = (int) SpellRegistry.getAllSpellsByID().values().stream()
                .filter(s -> s.isEnabled())
                .count();
        cachedEnabledSpells = count;
        cachedAt = now;
        return count;
    }
}
