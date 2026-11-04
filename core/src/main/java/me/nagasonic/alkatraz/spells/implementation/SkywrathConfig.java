package me.nagasonic.alkatraz.spells.implementation;

import org.bukkit.configuration.file.YamlConfiguration;
import java.util.Map;

/** Pure configuration for Skywrath's damaging storm sustain. */
public record SkywrathConfig(double stormDuration, double stormRadius,
                             double stormHeight, double strikeIntervalSeconds,
                             double upkeepManaPerSecond) {
    public static SkywrathConfig defaults() { return fromConfig(Map.of()); }
    public static SkywrathConfig from(YamlConfiguration config) {
        return config == null ? defaults() : fromConfig(config.getValues(false));
    }
    public static SkywrathConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new SkywrathConfig(number(map.get("storm_duration"), 24.0),
                number(map.get("storm_radius"), 8.0), number(map.get("storm_height"), 12.0),
                number(map.get("strike_interval_seconds"), 2.4),
                number(map.get("upkeep_mana_per_second"), 70.0));
    }
    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
    public int stormTicks() { return Math.max(1, (int) Math.round(stormDuration * 20.0)); }
    public int strikeIntervalTicks() { return Math.max(1, (int) Math.round(strikeIntervalSeconds * 20.0)); }
    public int damageIntervalTicks() { return strikeIntervalTicks(); }
    public int hitsPerTarget() {
        return strikeIntervalSeconds <= 0 ? 1 : Math.max(1, (int) Math.round(stormDuration / strikeIntervalSeconds));
    }
    public int damageWindows() { return (stormTicks() + strikeIntervalTicks() - 1) / strikeIntervalTicks(); }
    public double declaredTotal(double power) { return power * hitsPerTarget(); }
}
