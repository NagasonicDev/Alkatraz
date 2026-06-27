package me.nagasonic.alkatraz.configuration.requirement;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.research.ResearchProgressRegistry;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.BooleanStatRequirement;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.CompositeRequirement;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.OptionValueRequirement;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.PermissionRequirement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RequirementFactory {

    @FunctionalInterface
    public interface Builder {
        Requirement build(Spell spell, ConfigurationSection section);
    }

    private static final Map<String, Builder> REGISTRY = new HashMap<>();

    static {
        register("number_stat", (spell, s) -> new NumberStatRequirement<>(
                s.getString("stat", ""),
                s.getDouble("minimum", 0),
                s.getString("description", "Requires " + s.getString("stat", "") + " >= " + s.getDouble("minimum", 0))
        ));

        register("boolean_stat", (spell, s) -> new BooleanStatRequirement(
                s.getString("stat", ""),
                s.getBoolean("requires", true),
                s.getString("description", "Requires " + s.getString("stat", "") + " = " + s.getBoolean("requires", true))
        ));

        register("permission", (spell, s) -> new PermissionRequirement(
                s.getString("permission", ""),
                s.getString("description", "Requires permission: " + s.getString("permission", ""))
        ));

        register("option_value", (spell, s) -> new OptionValueRequirement(
                s.getString("option", ""),
                s.getString("value", ""),
                s.getString("description", "Requires " + s.getString("option", "") + " to be " + s.getString("value", ""))
        ));

        register("composite", (spell, s) -> {
            String description = s.getString("description", "Multiple requirements");
            List<Map<?, ?>> nested = s.getMapList("requirements");
            ValueRequirement[] children = nested.stream()
                    .map(raw -> create(spell, toSection(raw)))
                    .filter(ValueRequirement.class::isInstance)
                    .map(ValueRequirement.class::cast)
                    .toArray(ValueRequirement[]::new);
            return new CompositeRequirement(description, children);
        });

        register("arcane_knowledge", (spell, s) -> simple(
                s.getString("description", "Requires " + s.getDouble("amount", 0) + " Arcane Knowledge"),
                player -> ProfileManager.getProfile(player, MagicProfile.class).getArcaneKnowledge() >= s.getDouble("amount", 0)
        ));

        register("research", (spell, s) -> simple(
                s.getString("description", "Requires research: " + s.getString("research", "")),
                player -> ResearchProgressRegistry.hasCompleted(
                        player,
                        s.getString("provider", "alkatraz"),
                        s.getString("research", "")
                )
        ));

        register("spell_mastery", (spell, s) -> {
            String spellId = s.getString("spell", "");
            int mastery = s.getInt("mastery", s.getInt("minimum", 0));
            return simple(
                    s.getString("description", "Requires " + spellId + " mastery " + mastery),
                    player -> {
                        Spell requiredSpell = SpellRegistry.getSpell(spellId);
                        return requiredSpell != null
                                && ProfileManager.getProfile(player, MagicProfile.class).getSpellMastery(requiredSpell) >= mastery;
                    }
            );
        });

        register("spell_discovered", (spell, s) -> {
            String spellId = s.getString("spell", "");
            return simple(
                    s.getString("description", "Requires discovered spell: " + spellId),
                    player -> {
                        Spell requiredSpell = SpellRegistry.getSpell(spellId);
                        return requiredSpell != null
                                && ProfileManager.getProfile(player, MagicProfile.class).hasDiscoveredSpell(requiredSpell);
                    }
            );
        });

        register("mastered_spell_count", (spell, s) -> {
            int circle = s.getInt("circle", s.getInt("level", -1));
            int count = s.getInt("count", 1);
            int mastery = s.getInt("mastery", -1);
            return simple(
                    s.getString("description", "Requires " + count + " mastered circle " + circle + " spell(s)"),
                    player -> masteredSpellCount(ProfileManager.getProfile(player, MagicProfile.class), circle, mastery) >= count
            );
        });
    }

    public static void register(String type, Builder builder) {
        if (type == null || type.isBlank() || builder == null) {
            throw new IllegalArgumentException("Requirement type and builder are required");
        }
        REGISTRY.put(type.toLowerCase(), builder);
    }

    public static Requirement create(Spell spell, ConfigurationSection section) {
        String type = section.getString("type", "").toLowerCase();
        Builder builder = REGISTRY.get(type);
        if (builder == null) {
            throw new IllegalArgumentException("Unknown requirement type '" + type + "'. Registered types: " + REGISTRY.keySet());
        }
        return builder.build(spell, section);
    }

    public static boolean isRegistered(String type) {
        return type != null && REGISTRY.containsKey(type.toLowerCase());
    }

    public static ConfigurationSection toSection(Map<?, ?> map) {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("requirement");
        map.forEach((key, value) -> section.set(String.valueOf(key), value));
        return section;
    }

    private static int masteredSpellCount(MagicProfile profile, int circle, int mastery) {
        int total = 0;
        for (Spell candidate : SpellRegistry.getAllSpellsByID().values()) {
            if (circle >= 0 && candidate.getRequiredCircleLevel() != circle && candidate.getLevel() != circle) continue;
            int requiredMastery = mastery >= 0 ? mastery : candidate.getMaxMastery();
            if (profile.getSpellMastery(candidate) >= requiredMastery) {
                total++;
            }
        }
        return total;
    }

    private static ValueRequirement simple(String description, java.util.function.Predicate<org.bukkit.entity.Player> predicate) {
        return new ValueRequirement() {
            @Override
            public boolean isMet(org.bukkit.entity.Player player) {
                return predicate.test(player);
            }

            @Override
            public String getDescription() {
                return description;
            }
        };
    }

    private RequirementFactory() {}
}
