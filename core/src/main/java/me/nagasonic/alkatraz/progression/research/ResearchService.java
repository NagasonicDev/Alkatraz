package me.nagasonic.alkatraz.progression.research;

import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.research.definition.ResearchCategory;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import me.nagasonic.alkatraz.progression.research.definition.ResearchObjective;
import me.nagasonic.alkatraz.progression.research.definition.ResearchReward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ResearchService {

    private static final String CONFIG_NAME = "research.yml";
    private static Map<String, ResearchCategory> categories = Map.of();
    private static Map<String, ResearchNode> nodes = Map.of();
    private static Map<String, List<ResearchNode>> nodesByCategory = Map.of();
    private static Map<String, List<ResearchNode>> childrenByNode = Map.of();

    private ResearchService() {}

    public static void initialize() {
        reload();
        ResearchProgressRegistry.register("alkatraz", (player, researchId) ->
                ProfileManager.getProfile(player, MagicProfile.class).hasCompletedResearch(researchId));
    }

    public static void reload() {
        YamlConfiguration config = ConfigManager.reloadConfig(CONFIG_NAME).get();
        Map<String, ResearchCategory> loadedCategories = loadCategories(config);
        Map<String, ResearchNode> loadedNodes = loadNodes(config);

        validateAcyclic(loadedNodes);

        categories = Collections.unmodifiableMap(loadedCategories);
        nodes = Collections.unmodifiableMap(loadedNodes);
        nodesByCategory = Collections.unmodifiableMap(indexByCategory(loadedNodes));
        childrenByNode = Collections.unmodifiableMap(indexChildren(loadedNodes));
    }

    public static List<ResearchCategory> getCategories() {
        return categories.values().stream()
                .sorted(Comparator.comparing(ResearchCategory::getDisplayName))
                .toList();
    }

    public static List<ResearchNode> getNodes(String category) {
        return nodesByCategory.getOrDefault(category, List.of());
    }

    public static Optional<ResearchNode> getNode(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(nodes.get(id.toLowerCase()));
    }

    public static List<ResearchNode> getChildren(String researchId) {
        return childrenByNode.getOrDefault(researchId.toLowerCase(), List.of());
    }

    public static ResearchState getState(Player player, ResearchNode node) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile.hasCompletedResearch(node.getId())) return ResearchState.COMPLETED;
        if (profile.hasStartedResearch(node.getId())) return ResearchState.IN_PROGRESS;
        if (meetsParents(profile, node)) return ResearchState.AVAILABLE;
        return node.isHiddenUntilAvailable() ? ResearchState.HIDDEN : ResearchState.LOCKED;
    }

    public static boolean start(Player player, ResearchNode node) {
        ResearchState state = getState(player, node);
        if (state != ResearchState.AVAILABLE) return false;
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        int cost = node.getResearchPointsCost();
        if (cost > 0 && profile.getResearchPoints() < cost) return false;
        if (cost > 0) profile.addResearchPoints(-cost);
        profile.setResearchStarted(node.getId(), true);
        return true;
    }

    public static boolean complete(Player player, ResearchNode node) {
        ResearchState state = getState(player, node);
        if (state != ResearchState.AVAILABLE && state != ResearchState.IN_PROGRESS) return false;
        if (!objectivesComplete(player, node)) return false;
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.setResearchStarted(node.getId(), false);
        profile.setResearchCompleted(node.getId(), true);
        applyRewards(player, node);
        return true;
    }

    public static boolean objectivesComplete(Player player, ResearchNode node) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        for (ResearchObjective objective : node.getObjectives()) {
            if (profile.getResearchObjectiveProgress(node.getId(), objective.getId()) < objective.getAmount()) {
                return false;
            }
        }
        return true;
    }

    public static int getObjectiveProgress(Player player, ResearchNode node, ResearchObjective objective) {
        return ProfileManager.getProfile(player, MagicProfile.class)
                .getResearchObjectiveProgress(node.getId(), objective.getId());
    }

    public static void recordObjectiveEvent(Player player, String event, Map<String, Object> context) {
        if (player == null || event == null) return;
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        String key = event.toLowerCase();
        for (ResearchNode node : nodes.values()) {
            ResearchState state = getState(player, node);
            if (state != ResearchState.IN_PROGRESS) continue;
            for (ResearchObjective objective : node.getObjectives()) {
                if (!objective.getEvent().equals(key) || !matches(objective, context)) continue;
                profile.addResearchObjectiveProgress(node.getId(), objective.getId(), 1, objective.getAmount());
            }
        }
    }

    public static void applyRewards(Player player, ResearchNode node) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        boolean profileRewardsApplied = profile.hasAppliedResearchRewards(node.getId());
        for (ResearchReward reward : node.getRewards()) {
            applyReward(player, profile, node.getId(), reward, profileRewardsApplied);
        }
        if (!profileRewardsApplied) {
            profile.setResearchRewardsApplied(node.getId(), true);
        }
    }

    public static void applyCompletedRewards(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        for (String researchId : profile.getCompletedResearchIds()) {
            getNode(researchId).ifPresent(node -> applyRewards(player, node));
        }
    }

    private static boolean meetsParents(MagicProfile profile, ResearchNode node) {
        for (String parent : node.getParents()) {
            if (!profile.hasCompletedResearch(parent)) return false;
        }
        return true;
    }

    private static Map<String, ResearchCategory> loadCategories(YamlConfiguration config) {
        Map<String, ResearchCategory> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("categories");
        if (section == null) return loaded;

        for (String id : section.getKeys(false)) {
            String root = "categories." + id + ".";
            Material icon = material(config.getString(root + "icon"), Material.BOOK);
            loaded.put(id.toLowerCase(), new ResearchCategory(
                    id.toLowerCase(),
                    config.getString(root + "display_name", id),
                    icon
            ));
        }
        return loaded;
    }

    private static Map<String, ResearchNode> loadNodes(YamlConfiguration config) {
        Map<String, ResearchNode> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("nodes");
        if (section == null) return loaded;

        for (String id : section.getKeys(false)) {
            String key = id.toLowerCase();
            String root = "nodes." + id + ".";
            List<String> parents = config.getStringList(root + "parents").stream()
                    .map(String::toLowerCase)
                    .toList();
            ResearchNode node = new ResearchNode(
                    key,
                    config.getString(root + "display_name", id),
                    config.getStringList(root + "description"),
                    config.getString(root + "category", "general").toLowerCase(),
                    material(config.getString(root + "icon"), Material.PAPER),
                    config.getInt(root + "position.x"),
                    config.getInt(root + "position.y"),
                    parents,
                    config.getStringList(root + "unlocks"),
                    loadObjectives(config.getConfigurationSection(root + "objectives")),
                    loadRewards(config.getConfigurationSection(root + "rewards")),
                    config.getBoolean(root + "visibility.hidden_until_available", false),
                    config.getInt(root + "research_points_cost", 0)
            );
            loaded.put(key, node);
        }
        return loaded;
    }

    private static List<ResearchObjective> loadObjectives(ConfigurationSection section) {
        List<ResearchObjective> objectives = new ArrayList<>();
        if (section == null) return objectives;
        for (String id : section.getKeys(false)) {
            String root = id + ".";
            ConfigurationSection filters = section.getConfigurationSection(root + "filters");
            objectives.add(new ResearchObjective(
                    id,
                    section.getString(root + "display_name", id),
                    section.getString(root + "event", "manual"),
                    section.getInt(root + "amount", 1),
                    filters == null ? Map.of() : new HashMap<>(filters.getValues(false))
            ));
        }
        return objectives;
    }

    private static List<ResearchReward> loadRewards(ConfigurationSection section) {
        List<ResearchReward> rewards = new ArrayList<>();
        if (section == null) return rewards;
        for (String id : section.getKeys(false)) {
            String root = id + ".";
            rewards.add(new ResearchReward(
                    section.getString(root + "type", "magic_stat"),
                    section.getString(root + "target", ""),
                    section.getDouble(root + "amount", 0),
                    section.getString(root + "operation", "add"),
                    section.getString(root + "display", "")
            ));
        }
        return rewards;
    }

    private static boolean matches(ResearchObjective objective, Map<String, Object> context) {
        Map<String, Object> filters = objective.getFilters();
        if (filters.isEmpty()) return true;
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            String key = filter.getKey().toLowerCase();
            Object expected = filter.getValue();
            Object actual = context.get(key);
            if (!matchesFilter(key, expected, actual, context)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesFilter(String key, Object expected, Object actual, Map<String, Object> context) {
        if (key.startsWith("min_")) return number(context.get(key.substring(4))) >= number(expected);
        if (key.startsWith("max_")) return number(context.get(key.substring(4))) <= number(expected);
        if (expected instanceof List<?> list) {
            for (Object value : list) {
                if (matchesValue(value, actual)) return true;
            }
            return false;
        }
        return matchesValue(expected, actual);
    }

    private static boolean matchesValue(Object expected, Object actual) {
        if (expected == null || String.valueOf(expected).equalsIgnoreCase("any")) return true;
        if (actual == null) return false;
        return String.valueOf(expected).equalsIgnoreCase(String.valueOf(actual));
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0D;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private static void applyReward(
            Player player,
            MagicProfile profile,
            String researchId,
            ResearchReward reward,
            boolean profileRewardsApplied
    ) {
        if ("vanilla_attribute".equals(reward.getType())) {
            applyVanillaAttribute(player, researchId, reward);
            return;
        }
        if (profileRewardsApplied) return;
        if ("command".equals(reward.getType())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    reward.getTarget().replace("%player%", player.getName()));
            return;
        }
        profile.addMagicStat(reward.getTarget(), reward.getAmount(), reward.getOperation());
    }

    private static void applyVanillaAttribute(Player player, String researchId, ResearchReward reward) {
        try {
            Attribute attribute = Attribute.valueOf(reward.getTarget().toUpperCase());
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) return;
            UUID id = UUID.nameUUIDFromBytes(("alkatraz:research:" + researchId + ":" + reward.getTarget())
                    .getBytes(StandardCharsets.UTF_8));
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.getUniqueId().equals(id)) return;
            }
            instance.addModifier(new AttributeModifier(
                    id,
                    "Alkatraz Research " + researchId,
                    reward.getAmount(),
                    AttributeModifier.Operation.ADD_NUMBER
            ));
        } catch (IllegalArgumentException ignored) {
            // Invalid attributes are ignored so one bad config entry does not break research completion.
        }
    }

    private static Map<String, List<ResearchNode>> indexByCategory(Map<String, ResearchNode> loadedNodes) {
        Map<String, List<ResearchNode>> index = new HashMap<>();
        for (ResearchNode node : loadedNodes.values()) {
            index.computeIfAbsent(node.getCategory(), ignored -> new ArrayList<>()).add(node);
        }
        for (List<ResearchNode> value : index.values()) {
            value.sort(Comparator.comparingInt(ResearchNode::getY).thenComparingInt(ResearchNode::getX));
        }
        return index;
    }

    private static Map<String, List<ResearchNode>> indexChildren(Map<String, ResearchNode> loadedNodes) {
        Map<String, List<ResearchNode>> index = new HashMap<>();
        for (ResearchNode node : loadedNodes.values()) {
            for (String parent : node.getParents()) {
                index.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(node);
            }
        }
        return index;
    }

    private static void validateAcyclic(Map<String, ResearchNode> loadedNodes) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (ResearchNode node : loadedNodes.values()) {
            visit(node.getId(), loadedNodes, visiting, visited);
        }
    }

    private static void visit(String id, Map<String, ResearchNode> loadedNodes, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) {
            throw new IllegalStateException("Research graph contains a cycle at '" + id + "'");
        }

        ResearchNode node = loadedNodes.get(id);
        if (node != null) {
            for (ResearchNode child : childrenOf(id, loadedNodes)) {
                visit(child.getId(), loadedNodes, visiting, visited);
            }
        }

        visiting.remove(id);
        visited.add(id);
    }

    private static List<ResearchNode> childrenOf(String id, Map<String, ResearchNode> loadedNodes) {
        List<ResearchNode> children = new ArrayList<>();
        for (ResearchNode node : loadedNodes.values()) {
            if (node.getParents().contains(id)) children.add(node);
        }
        return children;
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
