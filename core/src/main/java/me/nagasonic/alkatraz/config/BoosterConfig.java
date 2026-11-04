package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Configuration and validation helpers for {@code boosters.yml}.
 *
 * <p>Kept core-only: no NMS, no Bukkit world/entity access.</p>
 */
public class BoosterConfig {

    public static final double MIN_MULTIPLIER = 0.1;
    public static final double MAX_MULTIPLIER = 100.0;
    public static final long MIN_DURATION_SECONDS = 1L;
    public static final long MAX_DURATION_SECONDS = 2_592_000L; // 30 days

    private static final Set<String> VALID_STACK_POLICIES =
            Set.of("replace", "stack", "strongest");
    private static final Set<String> VALID_SCOPES =
            Set.of("server", "world", "group", "player");

    private static final AtomicReference<YamlConfiguration> cached =
            new AtomicReference<>();

    protected BoosterConfig() {}

    public static void reload() {
        try {
            if (Alkatraz.getInstance() != null) {
                ConfigManager.reloadConfig("boosters.yml");
            }
        } catch (Exception ignored) {
        }
        try {
            cached.set(ConfigManager.getDefault("boosters.yml"));
        } catch (Exception ignored) {
            cached.set(null);
        }
    }

    public static YamlConfiguration getConfig() {
        if (cached.get() == null) reload();
        YamlConfiguration cfg = cached.get();
        if (cfg == null && Alkatraz.getInstance() != null) {
            try {
                cfg = ConfigManager.getConfig("boosters.yml").get();
                cached.set(cfg);
            } catch (Exception ignored) {
            }
        }
        return cfg;
    }

    public static YamlConfiguration getDefault() {
        return ConfigManager.getDefault("boosters.yml");
    }

    public static ConfigurationSection getBoosters() {
        YamlConfiguration cfg = getConfig();
        return cfg == null ? null : cfg.getConfigurationSection("boosters");
    }

    public static ConfigurationSection getBooster(String id) {
        ConfigurationSection section = getBoosters();
        return section == null ? null : section.getConfigurationSection(id);
    }

    public static ConfigurationSection getDefaults() {
        YamlConfiguration cfg = getConfig();
        return cfg == null ? null : cfg.getConfigurationSection("defaults");
    }

    public static boolean isValidStackPolicy(String policy) {
        return policy != null && VALID_STACK_POLICIES.contains(policy.toLowerCase(Locale.ROOT));
    }

    public static boolean isValidScope(String scope) {
        return scope != null && VALID_SCOPES.contains(scope.toLowerCase(Locale.ROOT));
    }

    public static boolean isValidMultiplier(double multiplier) {
        return Double.isFinite(multiplier)
                && multiplier >= MIN_MULTIPLIER && multiplier <= MAX_MULTIPLIER;
    }

    public static boolean isValidDuration(long seconds) {
        return seconds >= MIN_DURATION_SECONDS && seconds <= MAX_DURATION_SECONDS;
    }

    public static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.length() <= 64;
    }

    public static java.util.List<String> validate(ConfigurationSection section) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (section == null) {
            problems.add("booster section is null");
            return problems;
        }
        String name = section.getString("name", "");
        if (!isValidName(name)) problems.add("invalid name");
        String stackPolicy = section.getString("stack_policy",
                section.getString("stackPolicy", "strongest"));
        if (!isValidStackPolicy(stackPolicy)) problems.add("invalid stack policy '" + stackPolicy + "'");
        String scope = section.getString("scope", "server");
        if (!isValidScope(scope)) problems.add("invalid scope '" + scope + "'");
        double multiplier = section.getDouble("multiplier", 0.0);
        if (multiplier != 0.0 && !isValidMultiplier(multiplier)) problems.add("multiplier out of range");
        long duration = section.getLong("duration_seconds", section.getLong("duration", 0L));
        if (duration != 0 && !isValidDuration(duration)) problems.add("duration out of range");
        return problems;
    }

    // convenience aliases
    public static boolean isValidStack(String policy) { return isValidStackPolicy(policy); }
    public static boolean validateStackPolicy(String policy) { return isValidStackPolicy(policy); }
    public static boolean validateScope(String scope) { return isValidScope(scope); }
    public static boolean validateMultiplier(double multiplier) { return isValidMultiplier(multiplier); }
    public static boolean validateDurationSeconds(long seconds) { return isValidDuration(seconds); }
    public static boolean isMultiplierValid(double multiplier) { return isValidMultiplier(multiplier); }
    public static boolean isDurationValid(long seconds) { return isValidDuration(seconds); }
    public static Set<String> getValidStackPolicies() {
        return Collections.unmodifiableSet(new HashSet<>(VALID_STACK_POLICIES));
    }
    public static Set<String> getValidScopes() {
        return Collections.unmodifiableSet(new HashSet<>(VALID_SCOPES));
    }
    public static java.util.List<String> getBoosterIds() {
        ConfigurationSection section = getBoosters();
        if (section == null) return java.util.List.of();
        return new java.util.ArrayList<>(section.getKeys(false));
    }
}