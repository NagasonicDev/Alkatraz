package me.nagasonic.alkatraz.progression;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.arcane.ArcaneKnowledgeRegistry;
import me.nagasonic.alkatraz.progression.arcane.ArcaneKnowledgeSource;
import me.nagasonic.alkatraz.progression.circle.CircleDefinition;
import me.nagasonic.alkatraz.progression.circle.CircleLevel;
import me.nagasonic.alkatraz.progression.requirement.ProgressionRequirement;
import me.nagasonic.alkatraz.progression.requirement.ProgressionRequirementRegistry;
import me.nagasonic.alkatraz.progression.requirement.RequirementContext;
import me.nagasonic.alkatraz.progression.requirement.implementation.ArcaneKnowledgeRequirement;
import me.nagasonic.alkatraz.progression.requirement.implementation.ResearchRequirement;
import me.nagasonic.alkatraz.progression.requirement.implementation.SpellMasteryRequirement;
import me.nagasonic.alkatraz.progression.research.ResearchPointService;
import me.nagasonic.alkatraz.progression.research.ResearchProgressRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central progression entry point. Content is loaded from configuration;
 * advancement logic only evaluates registered requirements.
 */
public final class ProgressionService {

    private static final Map<Integer, CircleDefinition> circles = new LinkedHashMap<>();
    private static boolean autoAdvance;

    private ProgressionService() {}

    public static void initialize() {
        registerBuiltIns();
        reload();
    }

    public static void reload() {
        ArcaneKnowledgeRegistry.clear();
        circles.clear();

        YamlConfiguration config = ConfigManager.getConfig("progression.yml").get();
        autoAdvance = config.getBoolean("circle.auto_advance", true);
        loadArcaneKnowledge(config.getConfigurationSection("arcane_knowledge.sources"));
        loadCircles(config.getConfigurationSection("circle.levels"));
        ResearchPointService.reload();
    }

    public static void addArcaneKnowledge(OfflinePlayer player, String sourceId) {
        addArcaneKnowledge(player, sourceId, 0);
    }

    public static void addArcaneKnowledge(OfflinePlayer player, String sourceId, int circle) {
        ArcaneKnowledgeRegistry.get(sourceId)
                .filter(ArcaneKnowledgeSource::isEnabled)
                .ifPresent(source -> addArcaneKnowledge(player, source.getAmount(circle)));
    }

    public static void addArcaneKnowledge(OfflinePlayer player, double amount) {
        if (player == null || amount == 0) return;
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        profile.setArcaneKnowledge(Math.max(0, profile.getArcaneKnowledge() + amount));
        showArcaneKnowledgeBar(player, profile);
    }

    public static boolean canAdvance(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        int target = profile.getCircleLevel() + 1;
        return canAdvance(player, target);
    }

    public static boolean canAdvance(Player player, int targetCircle) {
        if (player == null || !CircleLevel.isValid(targetCircle)) return false;
        CircleDefinition definition = circles.get(targetCircle);
        if (definition == null) return false;

        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        if (profile.getCircleLevel() >= targetCircle) return false;

        RequirementContext context = new RequirementContext(player, profile, targetCircle);
        for (ProgressionRequirement requirement : definition.getRequirements()) {
            if (!requirement.isMet(context)) {
                return false;
            }
        }
        return true;
    }

    public static boolean advance(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        int target = profile.getCircleLevel() + 1;
        if (!canAdvance(player, target)) return false;

        applyCircleDefinition(profile, target);
        ResearchPointService.addPoints(player, "circle_up_bonus");
        player.sendMessage(
                ColorFormat.format("&e&lCIRCLE UP!"),
                ColorFormat.format("&bReached the " + StringUtils.toOrdinal(target) + " circle."),
                ColorFormat.format("&bYou are now able to use spells up to the " + StringUtils.toOrdinal(target) + " rank.")
        );
        return true;
    }

    public static void advanceWhileEligible(Player player) {
        while (advance(player)) {
            if (ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class).getCircleLevel() >= 9) {
                return;
            }
        }
    }

    public static CircleDefinition getCircleDefinition(int circle) {
        return circles.get(circle);
    }

    private static void registerBuiltIns() {
        ProgressionRequirementRegistry.clear();
        ResearchProgressRegistry.clear();
        ProgressionRequirementRegistry.register("arcane_knowledge", ArcaneKnowledgeRequirement::fromConfig);
        ProgressionRequirementRegistry.register("research", ResearchRequirement::fromConfig);
        ProgressionRequirementRegistry.register("spell_mastery", SpellMasteryRequirement::fromConfig);
        ResearchProgressRegistry.register("alkatraz", (player, researchId) ->
                ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class).hasResearch(researchId));
    }

    private static void loadArcaneKnowledge(ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection source = section.getConfigurationSection(key);
            if (source == null) continue;
            ArcaneKnowledgeRegistry.register(new ArcaneKnowledgeSource(
                    key,
                    source.getDouble("amount", 0),
                    source.getBoolean("enabled", true),
                    parseCircleAmounts(source.getConfigurationSection("circle_amounts"))
            ));
        }
    }

    private static Map<Integer, Double> parseCircleAmounts(ConfigurationSection section) {
        Map<Integer, Double> amounts = new LinkedHashMap<>();
        if (section == null) return amounts;
        for (String key : section.getKeys(false)) {
            amounts.put(Integer.parseInt(key), section.getDouble(key));
        }
        return amounts;
    }

    private static void loadCircles(ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            int circle = Integer.parseInt(key);
            if (!CircleLevel.isValid(circle)) continue;
            ConfigurationSection circleSection = section.getConfigurationSection(key);
            if (circleSection == null) continue;
            List<ProgressionRequirement> requirements = parseRequirements(circleSection.getMapList("requirements"));
            circles.put(circle, new CircleDefinition(
                    circle,
                    requirements,
                    circleSection.getInt("stat_points", 0),
                    circleSection.getDouble("max_mana", 100),
                    circleSection.getDouble("mana_regeneration", 1)
            ));
        }
    }

    private static List<ProgressionRequirement> parseRequirements(List<Map<?, ?>> rawRequirements) {
        List<ProgressionRequirement> requirements = new ArrayList<>();
        for (Map<?, ?> raw : rawRequirements) {
            requirements.add(ProgressionRequirementRegistry.create(toStringMap(raw)));
        }
        return List.copyOf(requirements);
    }

    private static void applyCircleDefinition(MagicProfile profile, int circle) {
        CircleDefinition previous = circles.get(profile.getCircleLevel());
        CircleDefinition next = circles.get(circle);
        if (next == null) return;

        double previousMaxMana = previous != null ? previous.getMaxMana() : 100;
        double previousManaRegen = previous != null ? previous.getManaRegeneration() : 1;

        profile.setMaxMana(profile.getMaxMana() + (next.getMaxMana() - previousMaxMana));
        profile.setManaRegeneration(profile.getManaRegeneration() + (next.getManaRegeneration() - previousManaRegen));
        profile.setCircleLevel(circle);
        profile.setStatPoints(profile.getStatPoints() + next.getStatPoints());
    }

    private static void showArcaneKnowledgeBar(OfflinePlayer player, MagicProfile profile) {
        if (!player.isOnline()) return;

        BossBar bar = profile.getArcaneKnowledgeBar();
        String max = profile.getCircleLevel() < 9
                ? String.valueOf(nextArcaneKnowledgeRequirement(profile.getCircleLevel() + 1))
                : "MAX";
        String title = ColorFormat.format("&bArcane Knowledge: " + profile.getArcaneKnowledge() + "/" + max);
        double progress = profile.getCircleLevel() < 9
                ? profile.getArcaneKnowledge() / Math.max(1, nextArcaneKnowledgeRequirement(profile.getCircleLevel() + 1))
                : 1;

        if (bar == null) {
            bar = Bukkit.createBossBar(title, BarColor.WHITE, BarStyle.SOLID);
            profile.setArcaneKnowledgeBar(bar);
        } else {
            bar.removePlayer(player.getPlayer());
            bar.setTitle(title);
        }
        bar.setProgress(Math.max(0, Math.min(1, progress)));
        bar.addPlayer(player.getPlayer());

        int prevTask = profile.getArcaneKnowledgeBarTaskId();
        if (prevTask != -1) {
            Bukkit.getScheduler().cancelTask(prevTask);
        }
        BossBar finalBar = bar;
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
            if (player.isOnline()) {
                finalBar.removePlayer(player.getPlayer());
            }
        }, 100L);
        profile.setArcaneKnowledgeBarTaskId(taskId);
    }

    private static double nextArcaneKnowledgeRequirement(int circle) {
        CircleDefinition definition = circles.get(circle);
        if (definition == null) return 0;
        for (ProgressionRequirement requirement : definition.getRequirements()) {
            if (requirement instanceof ArcaneKnowledgeRequirement arcaneRequirement) {
                return arcaneRequirement.getAmount();
            }
        }
        return 0;
    }

    private static Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
