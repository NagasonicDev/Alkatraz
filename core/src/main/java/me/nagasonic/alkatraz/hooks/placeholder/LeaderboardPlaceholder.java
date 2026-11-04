package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leaderboard placeholders backed by an asynchronously built snapshot.
 *
 * <p>PlaceholderAPI resolves on the main thread, so this handler never touches
 * disk on the request path. It serves the last completed snapshot and drives
 * the rebuild itself on an async repeating task.
 */
public class LeaderboardPlaceholder implements Placeholder {

    private static final String PROFILE_FILE = "magic.yml";
    private static final String PATH_ARCANE_KNOWLEDGE = "stats.doubles.arcaneKnowledge";
    private static final String PATH_LEGACY_EXPERIENCE = "stats.doubles.experience";
    private static final String PATH_CIRCLE_LEVEL = "stats.ints.circleLevel";
    private static final String UNKNOWN_NAME = "Unknown";

    private static class LeaderboardEntry {
        final UUID uuid;
        final String name;
        final double arcaneKnowledge;
        final int circleLevel;

        LeaderboardEntry(UUID uuid, String name, double arcaneKnowledge, int circleLevel) {
            this.uuid = uuid;
            this.name = name;
            this.arcaneKnowledge = arcaneKnowledge;
            this.circleLevel = circleLevel;
        }

        LeaderboardEntry withName(String newName) {
            return new LeaderboardEntry(uuid, newName, arcaneKnowledge, circleLevel);
        }
    }

    private final long refreshIntervalMs;
    private final int maxEntries;
    private final AtomicBoolean building = new AtomicBoolean(false);
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    private volatile List<LeaderboardEntry> cachedBoard = Collections.emptyList();
    private volatile boolean scheduled = false;
    private volatile BukkitTask refreshTask = null;

    public LeaderboardPlaceholder(long refreshIntervalMinutes, int maxEntries) {
        this.refreshIntervalMs = refreshIntervalMinutes * 60 * 1000;
        this.maxEntries = maxEntries;
    }

    @Override
    public String name() {
        return "leaderboard";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (!isLeaderboardParam(params)) return "";

        ensureRefreshScheduled();

        List<LeaderboardEntry> board = cachedBoard;

        if (params.equals("total_players")) {
            return String.valueOf(board.size());
        }

        if (params.equals("rank")) {
            UUID uuid = player.getUniqueId();
            for (int i = 0; i < board.size(); i++) {
                if (board.get(i).uuid.equals(uuid)) {
                    return String.valueOf(i + 1);
                }
            }
            return "0";
        }

        String[] parts = params.split("_", 3);
        if (parts.length < 3) return "";
        int index;
        try {
            index = Integer.parseInt(parts[1]) - 1;
        } catch (NumberFormatException e) {
            return "";
        }
        if (index < 0 || index >= board.size()) return "";
        LeaderboardEntry entry = board.get(index);
        String field = parts[2];
        return switch (field) {
            case "name" -> entry.name;
            case "uuid" -> entry.uuid.toString();
            case "ak" -> String.valueOf(entry.arcaneKnowledge);
            case "circle" -> String.valueOf(entry.circleLevel);
            default -> "";
        };
    }

    private static boolean isLeaderboardParam(String params) {
        return params.equals("total_players")
                || params.equals("rank")
                || params.startsWith("top_");
    }

    private synchronized void ensureRefreshScheduled() {
        if (scheduled && refreshTask != null) return;
        if (!Alkatraz.getInstance().isEnabled()) return;
        long periodTicks = Math.max(1L, refreshIntervalMs / 50L);
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                Alkatraz.getInstance(), this::refreshAsync, 0L, periodTicks);
        scheduled = true;
    }

    private void refreshAsync() {
        if (!building.compareAndSet(false, true)) return;
        try {
            List<LeaderboardEntry> entries = buildSnapshot();
            publish(entries);
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().log(java.util.logging.Level.WARNING, "[Alkatraz] Failed to refresh leaderboard snapshot", e);
        } finally {
            building.set(false);
        }
    }

    private List<LeaderboardEntry> buildSnapshot() {
        File playerDataDir = new File(Alkatraz.getInstance().getDataFolder(), "playerdata");
        if (!playerDataDir.exists() || !playerDataDir.isDirectory()) return Collections.emptyList();

        File[] uuidFolders = playerDataDir.listFiles(File::isDirectory);
        if (uuidFolders == null) return Collections.emptyList();

        List<LeaderboardEntry> entries = new ArrayList<>(uuidFolders.length);
        for (File folder : uuidFolders) {
            UUID uuid;
            try {
                uuid = UUID.fromString(folder.getName());
            } catch (IllegalArgumentException e) {
                continue;
            }
            File file = new File(folder, PROFILE_FILE);
            if (!file.isFile()) continue;

            double ak = 0;
            int circle = 0;
            try {
                YamlConfiguration config = new YamlConfiguration();
                config.load(file);
                ak = Math.max(
                        config.getDouble(PATH_ARCANE_KNOWLEDGE, 0),
                        config.getDouble(PATH_LEGACY_EXPERIENCE, 0));
                circle = config.getInt(PATH_CIRCLE_LEVEL, 0);
            } catch (Exception e) {
                continue;
            }

            entries.add(new LeaderboardEntry(uuid, nameCache.get(uuid), ak, circle));
        }

        entries.sort((a, b) -> Double.compare(b.arcaneKnowledge, a.arcaneKnowledge));
        if (entries.size() > maxEntries) {
            entries = new ArrayList<>(entries.subList(0, Math.max(0, maxEntries)));
        }
        return Collections.unmodifiableList(entries);
    }

    private void publish(List<LeaderboardEntry> entries) {
        cachedBoard = entries;
        boolean needsNames = false;
        for (LeaderboardEntry entry : entries) {
            if (entry.name == null) {
                needsNames = true;
                break;
            }
        }
        if (!needsNames) return;
        if (!Alkatraz.getInstance().isEnabled()) return;
        Bukkit.getScheduler().runTask(Alkatraz.getInstance(), () -> resolveNames(entries));
    }

    private void resolveNames(List<LeaderboardEntry> entries) {
        List<LeaderboardEntry> named = new ArrayList<>(entries.size());
        boolean changed = false;
        for (LeaderboardEntry entry : entries) {
            if (entry.name != null) {
                named.add(entry);
                continue;
            }
            String name = nameCache.get(entry.uuid);
            if (name == null) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.uuid);
                name = offline.getName() != null ? offline.getName() : UNKNOWN_NAME;
                nameCache.put(entry.uuid, name);
            }
            named.add(entry.withName(name));
            changed = true;
        }
        if (changed && cachedBoard == entries) {
            cachedBoard = Collections.unmodifiableList(named);
        }
    }
}
