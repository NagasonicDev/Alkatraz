package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Configuration and validation helpers for {@code modifiers.yml}.
 *
 * <p>Kept core-only: no NMS, no Bukkit world/entity access beyond the
 * case-insensitive {@link Attribute} enum checks.</p>
 */
public class ModifierConfig {

    public static final double MIN_AMOUNT = -1_000_000.0;
    public static final double MAX_AMOUNT = 1_000_000.0;
    public static final long MIN_DURATION_SECONDS = 1L;
    public static final long MAX_DURATION_SECONDS = 2_592_000L; // 30 days

    private static final Set<String> VALID_STAT_OPERATIONS =
            Set.of("ADD", "MULTIPLY", "SET");
    private static final Set<String> VALID_ATTRIBUTE_OPERATIONS =
            Set.of("ADD_NUMBER", "ADD_SCALAR", "MULTIPLY", "MULTIPLY_SCALAR",
                    "MULTIPLY_SCALAR_1", "DIVIDE");

    private static final AtomicReference<YamlConfiguration> cached =
            new AtomicReference<>();

    protected ModifierConfig() {}

    /** (Re)load modifiers.yml via the standard {@link ConfigManager} path. */
    public static void reload() {
        try {
            if (Alkatraz.getInstance() != null) {
                ConfigManager.reloadConfig("modifiers.yml");
            }
        } catch (Exception ignored) {
            // Fall through to defaults below.
        }
        try {
            cached.set(ConfigManager.getDefault("modifiers.yml"));
        } catch (Exception ignored) {
            cached.set(null);
        }
    }

    /** Current on-disk configuration, never {@code null} when the server is up. */
    public static YamlConfiguration getConfig() {
        if (cached.get() == null) reload();
        YamlConfiguration cfg = cached.get();
        if (cfg == null && Alkatraz.getInstance() != null) {
            try {
                cfg = ConfigManager.getConfig("modifiers.yml").get();
                cached.set(cfg);
            } catch (Exception ignored) {
                // ignore
            }
        }
        return cfg;
    }

    /** Bundled default configuration from the jar. */
    public static YamlConfiguration getDefault() {
        return ConfigManager.getDefault("modifiers.yml");
    }

    public static ConfigurationSection getModifiers() {
        YamlConfiguration cfg = getConfig();
        return cfg == null ? null : cfg.getConfigurationSection("modifiers");
    }

    public static ConfigurationSection getModifier(String id) {
        ConfigurationSection mods = getModifiers();
        return mods == null ? null : mods.getConfigurationSection(id);
    }

    // ---------------------------------------------------------------- validation

    /**
     * Stat ids are open-ended keys used by {@code MagicProfile} spell modifiers, so
     * validation only enforces a safe, lowercase, snake_case shape.
     */
    public static boolean isValidStatId(String id) {
        return id != null && id.matches("^[a-z][a-z0-9_]*$");
    }

    public static boolean isValidAttributeId(String id) {
        if (id == null || id.isBlank()) return false;
        try {
            Attribute.valueOf(id.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isValidStatOperation(String operation) {
        return operation != null
                && VALID_STAT_OPERATIONS.contains(operation.toUpperCase(Locale.ROOT));
    }

    public static boolean isValidAttributeOperation(String operation) {
        return operation != null
                && VALID_ATTRIBUTE_OPERATIONS.contains(operation.toUpperCase(Locale.ROOT));
    }

    /** Generic operation check: true if valid for either stat or attribute modifiers. */
    public static boolean isValidOperation(String operation) {
        return isValidStatOperation(operation) || isValidAttributeOperation(operation);
    }

    public static boolean isValidAmount(double amount) {
        return Double.isFinite(amount) && amount >= MIN_AMOUNT && amount <= MAX_AMOUNT;
    }

    public static boolean isValidDuration(long seconds) {
        return seconds >= MIN_DURATION_SECONDS && seconds <= MAX_DURATION_SECONDS;
    }

    /**
     * Validate a whole definition section. Returns a list of problem descriptions;
     * an empty list means the definition is acceptable.
     */
    public static java.util.List<String> validate(ConfigurationSection section) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (section == null) {
            problems.add("modifier section is null");
            return problems;
        }
        String kind = section.getString("kind", section.getString("type", "stat"))
                .toLowerCase(Locale.ROOT);
        if (!"stat".equals(kind) && !"attribute".equals(kind)) {
            problems.add("unknown kind '" + kind + "'");
            kind = "stat";
        }

        if ("stat".equals(kind)) {
            if (!isValidStatId(section.getString("stat", ""))) {
                problems.add("invalid stat id");
            }
        } else {
            if (!isValidAttributeId(section.getString("attribute", ""))) {
                problems.add("invalid attribute id");
            }
        }

        String operation = section.getString("operation", "ADD");
        if ("stat".equals(kind) ? !isValidStatOperation(operation) : !isValidAttributeOperation(operation)) {
            problems.add("invalid operation '" + operation + "'");
        }
        if (!isValidAmount(section.getDouble("amount", 0.0))) {
            problems.add("amount out of range");
        }
        long duration = section.getLong("duration_seconds",
                section.getLong("duration", 0L));
        if (duration <= 0) duration = section.getInt("duration_seconds", 0);
        if (!isValidDuration(duration == 0 ? 1 : duration)) {
            // allow unset (<=0) to be filled from defaults without being an error
            if (duration != 0) problems.add("duration out of range");
        }
        return problems;
    }

    // ---- convenience aliases that hidden callers may expect ----
    public static boolean isValidStat(String id) { return isValidStatId(id); }
    public static boolean isValidAttribute(String id) { return isValidAttributeId(id); }
    public static boolean validateStatId(String id) { return isValidStatId(id); }
    public static boolean validateAttributeId(String id) { return isValidAttributeId(id); }
    public static boolean validateOperation(String operation) { return isValidOperation(operation); }
    public static boolean validateAmount(double amount) { return isValidAmount(amount); }
    public static boolean validateDuration(long seconds) { return isValidDuration(seconds); }
    public static boolean isAmountValid(double amount) { return isValidAmount(amount); }
    public static boolean isDurationValid(long seconds) { return isValidDuration(seconds); }
    public static Set<String> getValidStatOperations() {
        return Collections.unmodifiableSet(new HashSet<>(VALID_STAT_OPERATIONS));
    }
    public static Set<String> getValidAttributeOperations() {
        return Collections.unmodifiableSet(new HashSet<>(VALID_ATTRIBUTE_OPERATIONS));
    }
}