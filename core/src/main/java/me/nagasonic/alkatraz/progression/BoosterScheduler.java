package me.nagasonic.alkatraz.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.nagasonic.alkatraz.Alkatraz;
import java.util.concurrent.ConcurrentHashMap;

public class BoosterScheduler {
    public enum StackPolicy { REPLACE, STACK, IGNORE, REFRESH, STRONGEST }

    public static class Schedule {
        private final String id;
        private final String boosterId;
        private final BoosterManager.Scope scope;
        private final String scopeKey;
        private final String target;
        private final double multiplier;
        private final long durationMillis;
        private final long intervalMillis;
        private final StackPolicy stackPolicy;
        private final boolean enabled;
        private final long endAtMillis;
        private final long firstRunAtMillis;
        private volatile long nextRunAtMillis;
        private volatile long lastRunAtMillis;

        public Schedule(String id, String boosterId, BoosterManager.Scope scope, String scopeKey,
                        String target, double multiplier, long durationMillis, long intervalMillis,
                        StackPolicy stackPolicy, boolean enabled, long firstRunAtMillis, long endAtMillis) {
            this.id = (id == null || id.isBlank()) ? "schedule" : id;
            this.boosterId = (boosterId == null || boosterId.isBlank()) ? this.id : boosterId;
            this.scope = (scope == null) ? BoosterManager.Scope.SERVER : scope;
            this.scopeKey = (scopeKey == null) ? "" : scopeKey;
            this.target = (target == null) ? "" : target;
            this.multiplier = multiplier;
            this.durationMillis = durationMillis > 0 ? durationMillis : 3_600_000L;
            this.intervalMillis = intervalMillis >= 0 ? intervalMillis : 0L;
            this.stackPolicy = (stackPolicy == null) ? StackPolicy.STRONGEST : stackPolicy;
            this.enabled = enabled;
            long first = firstRunAtMillis > 0 ? firstRunAtMillis : System.currentTimeMillis();
            this.firstRunAtMillis = first;
            this.nextRunAtMillis = first;
            this.endAtMillis = endAtMillis > 0 ? endAtMillis : 0L;
        }

        public String id() { return id; }
        public String getId() { return id; }
        public String boosterId() { return boosterId; }
        public String getBoosterId() { return boosterId; }
        public BoosterManager.Scope scope() { return scope; }
        public BoosterManager.Scope getScope() { return scope; }
        public String scopeKey() { return scopeKey; }
        public String getScopeKey() { return scopeKey; }
        public String target() { return target; }
        public String getTarget() { return target; }
        public double multiplier() { return multiplier; }
        public double getMultiplier() { return multiplier; }
        public long durationMillis() { return durationMillis; }
        public long getDurationMillis() { return durationMillis; }
        public long intervalMillis() { return intervalMillis; }
        public long getIntervalMillis() { return intervalMillis; }
        public StackPolicy stackPolicy() { return stackPolicy; }
        public StackPolicy getStackPolicy() { return stackPolicy; }
        public boolean enabled() { return enabled; }
        public boolean isEnabled() { return enabled; }
        public long endAtMillis() { return endAtMillis; }
        public long getEndAtMillis() { return endAtMillis; }
        public long firstRunAtMillis() { return firstRunAtMillis; }
        public long getFirstRunAtMillis() { return firstRunAtMillis; }
        public long nextRunAtMillis() { return nextRunAtMillis; }
        public long getNextRunAtMillis() { return nextRunAtMillis; }
        public long lastRunAtMillis() { return lastRunAtMillis; }
        public long getLastRunAtMillis() { return lastRunAtMillis; }
        public void setNextRunAtMillis(long value) { this.nextRunAtMillis = value; }
        public void setLastRunAtMillis(long value) { this.lastRunAtMillis = value; }
        public boolean isExpiredSchedule(long nowMillis) { return endAtMillis > 0 && nowMillis > endAtMillis; }
        public boolean isDue(long nowMillis) { return enabled && nextRunAtMillis <= nowMillis && !isExpiredSchedule(nowMillis); }
    }

    public static final class ActiveScheduledBoost {
        private final String scheduleId;
        private final BoosterManager.Scope scope;
        private final String scopeKey;
        private final String target;
        private final double multiplier;
        private final long startedAtMillis;
        private final long expiresAtMillis;
        public ActiveScheduledBoost(String scheduleId, BoosterManager.Scope scope, String scopeKey, String target,
                                    double multiplier, long startedAtMillis, long expiresAtMillis) {
            this.scheduleId = scheduleId;
            this.scope = scope;
            this.scopeKey = scopeKey == null ? "" : scopeKey;
            this.target = target == null ? "" : target;
            this.multiplier = multiplier;
            this.startedAtMillis = startedAtMillis;
            this.expiresAtMillis = expiresAtMillis;
        }
        public String scheduleId() { return scheduleId; }
        public BoosterManager.Scope scope() { return scope; }
        public String scopeKey() { return scopeKey; }
        public String target() { return target; }
        public double multiplier() { return multiplier; }
        public long startedAtMillis() { return startedAtMillis; }
        public long expiresAtMillis() { return expiresAtMillis; }
        public boolean isExpired(long nowMillis) { return expiresAtMillis <= nowMillis; }
    }

    private static final Map<String, Schedule> SCHEDULES = new ConcurrentHashMap<>();
    private static final Map<String, ActiveScheduledBoost> ACTIVE = new ConcurrentHashMap<>();

    protected BoosterScheduler() {}

    public static boolean register(Schedule schedule) {
        if (schedule == null) return false;
        SCHEDULES.put(schedule.id(), schedule);
        return true;
    }
    public static boolean schedule(Schedule schedule) { return register(schedule); }
    public static boolean addSchedule(Schedule schedule) { return register(schedule); }
    public static boolean add(Schedule schedule) { return register(schedule); }
    public static boolean put(Schedule schedule) { return register(schedule); }

    public static boolean removeSchedule(String id) {
        if (id == null) return false;
        Schedule removed = SCHEDULES.remove(id);
        if (removed != null) ACTIVE.remove(id);
        return removed != null;
    }
    public static boolean unregister(String id) { return removeSchedule(id); }
    public static boolean remove(String id) { return removeSchedule(id); }
    public static boolean delete(String id) { return removeSchedule(id); }

    public static Schedule getSchedule(String id) { return (id == null) ? null : SCHEDULES.get(id); }
    public static List<Schedule> getSchedules() { return new ArrayList<>(SCHEDULES.values()); }
    public static List<Schedule> listSchedules() { return getSchedules(); }
    public static List<Schedule> list() { return getSchedules(); }

    public static List<ActiveScheduledBoost> getActive() { return new ArrayList<>(ACTIVE.values()); }
    public static List<ActiveScheduledBoost> listActive() { return getActive(); }
    public static List<ActiveScheduledBoost> activeBoosts() { return getActive(); }

    public static void clearSchedules() { SCHEDULES.clear(); }
    public static void clearActive() { ACTIVE.clear(); }
    public static void clear() { SCHEDULES.clear(); ACTIVE.clear(); }
    public static void reset() { clear(); }
    public static void shutdown() { clear(); }

    public static void tick() { tick(System.currentTimeMillis()); }

    public static void tick(long nowMillis) {
        expireBoosts(nowMillis);
        for (Schedule schedule : new ArrayList<>(SCHEDULES.values())) {
            if (!schedule.enabled()) continue;
            if (schedule.isExpiredSchedule(nowMillis)) continue;
            if (schedule.nextRunAtMillis() <= 0) schedule.setNextRunAtMillis(nowMillis);
            if (schedule.nextRunAtMillis() > nowMillis) continue;
            fireIfAllowed(schedule, nowMillis);
            advanceNextRun(schedule, nowMillis);
        }
    }

    private static void fireIfAllowed(Schedule schedule, long nowMillis) {
        List<ActiveScheduledBoost> overlapping = new ArrayList<>();
        for (ActiveScheduledBoost b : ACTIVE.values()) {
            if (b.scope() == schedule.scope()
                    && b.scopeKey().equals(schedule.scopeKey())
                    && b.target().equals(schedule.target())
                    && !b.isExpired(nowMillis)) {
                overlapping.add(b);
            }
        }

        boolean fire = true;
        switch (schedule.stackPolicy()) {
            case IGNORE -> { if (!overlapping.isEmpty()) fire = false; }
            case STRONGEST -> {
                for (ActiveScheduledBoost b : overlapping) {
                    if (b.multiplier() >= schedule.multiplier()) fire = false;
                }
                if (fire && !overlapping.isEmpty()) {
                    for (ActiveScheduledBoost b : overlapping) {
                        if (b.multiplier() < schedule.multiplier()) {
                            ACTIVE.remove(b.scheduleId());
                            BoosterManager.stop(scopeToken(b.scope()), b.scopeKey().isBlank() ? b.target() : b.scopeKey());
                        }
                    }
                }
            }
            case REPLACE, REFRESH -> {
                for (ActiveScheduledBoost b : overlapping) {
                    ACTIVE.remove(b.scheduleId());
                    BoosterManager.stop(scopeToken(b.scope()), b.scopeKey().isBlank() ? b.target() : b.scopeKey());
                }
            }
            case STACK -> { }
        }

        if (!fire) return;

        long duration = schedule.durationMillis();
        String targetKey = schedule.scopeKey().isBlank() ? (schedule.target().isBlank() ? schedule.boosterId() : schedule.target()) : schedule.scopeKey();
        boolean ok = BoosterManager.start(scopeToken(schedule.scope()), targetKey, schedule.multiplier(), duration);
        if (ok) {
            ACTIVE.put(schedule.id(), new ActiveScheduledBoost(
                    schedule.id(), schedule.scope(),
                    schedule.scopeKey().isBlank() ? schedule.target() : schedule.scopeKey(),
                    schedule.target(), schedule.multiplier(), nowMillis, nowMillis + duration));
            schedule.setLastRunAtMillis(nowMillis);
        }
    }

    private static void advanceNextRun(Schedule schedule, long nowMillis) {
        if (schedule.intervalMillis() <= 0) {
            schedule.setNextRunAtMillis(Long.MAX_VALUE);
            return;
        }
        long next = schedule.nextRunAtMillis() + schedule.intervalMillis();
        while (next <= nowMillis) next += schedule.intervalMillis();
        if (schedule.endAtMillis() > 0 && next > schedule.endAtMillis()) next = Long.MAX_VALUE;
        schedule.setNextRunAtMillis(next);
    }

    private static void expireBoosts(long nowMillis) {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, ActiveScheduledBoost> e : ACTIVE.entrySet()) {
            if (e.getValue().isExpired(nowMillis)) expired.add(e.getKey());
        }
        for (String id : expired) {
            ActiveScheduledBoost b = ACTIVE.remove(id);
            if (b == null) continue;
            boolean others = ACTIVE.values().stream().anyMatch(x ->
                    x.scope() == b.scope() && x.scopeKey().equals(b.scopeKey()) && x.target().equals(b.target())
                            && !x.isExpired(nowMillis));
            if (!others) {
                BoosterManager.stop(scopeToken(b.scope()), b.scopeKey().isBlank() ? b.target() : b.scopeKey());
            }
        }
    }

    private static String scopeToken(BoosterManager.Scope scope) {
        if (scope == null) return "server";
        return switch (scope) {
            case PLAYER -> "player";
            case WORLD -> "world";
            case GROUP -> "group";
            case GLOBAL -> "global";
            case SERVER -> "server";
        };
    }

    public static void loadFromConfig(YamlConfiguration config) {
        clear();
        if (config == null) return;
        ConfigurationSection root = null;
        try { root = config.getConfigurationSection("schedules"); } catch (Exception e) { Alkatraz.logDebug("Failed to read schedules: " + e.getMessage()); }
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            try {
                Schedule schedule = parseSchedule(id, sec, System.currentTimeMillis());
                if (schedule != null) register(schedule);
            } catch (Exception e) { Alkatraz.logDebug("Failed to parse booster schedule " + id + ": " + e.getMessage()); }
        }
    }

    public static void loadConfig() {
        try { loadFromConfig(me.nagasonic.alkatraz.config.BoosterConfig.getConfig()); } catch (Exception e) { Alkatraz.logDebug("Failed to load booster config: " + e.getMessage()); }
    }

    public static void reload() { loadConfig(); }

    private static Schedule parseSchedule(String id, ConfigurationSection sec, long now) {
        String scopeStr = sec.getString("scope", "server");
        BoosterManager.Scope scope;
        try {
            scope = BoosterManager.Scope.valueOf(scopeStr.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            scope = BoosterManager.Scope.SERVER;
        }
        String scopeKey = sec.getString("scope_key", sec.getString("world", sec.getString("group", "")));
        String target = sec.getString("target", sec.getString("kind", ""));
        String boosterId = sec.getString("booster_id", sec.getString("booster", id));
        double multiplier = sec.getDouble("multiplier", sec.getDouble("amount", 1.0));
        long durationSec = sec.getLong("duration_seconds", sec.getLong("duration", 3600L));
        long intervalSec = sec.getLong("interval_seconds", sec.getLong("interval", sec.getLong("period_seconds", 0L)));
        long duration = durationSec * 1000L;
        long interval = intervalSec * 1000L;
        String policyStr = sec.getString("stack_policy", sec.getString("stackPolicy", "strongest"));
        StackPolicy policy;
        try { policy = StackPolicy.valueOf(policyStr.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { policy = StackPolicy.STRONGEST; }
        boolean enabled = sec.getBoolean("enabled", true);
        long endAt = sec.getLong("end_at", sec.getLong("until", 0L));
        long firstRun = sec.getLong("first_run_at", sec.getLong("start_at", 0L));
        if (firstRun == 0) firstRun = now;
        if (firstRun > 0 && firstRun < 1_000_000_000_000L) firstRun = firstRun * 1000L;
        if (endAt > 0 && endAt < 1_000_000_000_000L) endAt = endAt * 1000L;
        return new Schedule(id, boosterId, scope, scopeKey, target, multiplier, duration, interval,
                policy, enabled, firstRun, endAt);
    }

    public static YamlConfiguration toYaml() { return BoosterManager.toYaml(); }
    public static void saveActive(YamlConfiguration config) { BoosterManager.saveActive(config); }
    public static void saveActive(File file) { BoosterManager.saveActive(file); }
    public static void saveActive(String path) { BoosterManager.saveActive(path); }
    public static void loadActive(YamlConfiguration config) { BoosterManager.loadActive(config); }
    public static void loadActive(File file) { BoosterManager.loadActive(file); }
    public static void loadActive(String path) { BoosterManager.loadActive(path); }

    public static final String PAUSE_SEMANTICS = "wall-clock-no-pause; missed-occurrences-skipped";
}
