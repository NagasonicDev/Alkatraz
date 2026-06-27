package me.nagasonic.alkatraz.progression.research;

import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.research.definition.ResearchCategory;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

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
        ProfileManager.getProfile(player, MagicProfile.class).setResearchStarted(node.getId(), true);
        return true;
    }

    public static boolean complete(Player player, ResearchNode node) {
        ResearchState state = getState(player, node);
        if (state != ResearchState.AVAILABLE && state != ResearchState.IN_PROGRESS) return false;
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.setResearchStarted(node.getId(), false);
        profile.setResearchCompleted(node.getId(), true);
        return true;
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
                    config.getBoolean(root + "visibility.hidden_until_available", false)
            );
            loaded.put(key, node);
        }
        return loaded;
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
