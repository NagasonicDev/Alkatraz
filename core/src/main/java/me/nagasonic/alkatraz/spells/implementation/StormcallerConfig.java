package me.nagasonic.alkatraz.spells.implementation;

import org.bukkit.configuration.file.YamlConfiguration;
import java.util.Map;

/** Pure configuration for Stormcaller's channelled flight utility. */
public record StormcallerConfig(double channelDuration, double flightDuration,
                                double flightSpeed, double moveCancelThreshold) {
    public static StormcallerConfig defaults() { return fromConfig(Map.of()); }
    public static StormcallerConfig from(YamlConfiguration config) {
        return config == null ? defaults() : fromConfig(config.getValues(false));
    }
    public static StormcallerConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new StormcallerConfig(number(map.get("channel_duration"), 2.25),
                number(map.get("flight_duration"), 12.0), number(map.get("flight_speed"), 0.15),
                number(map.get("move_cancel_threshold"), 0.30));
    }
    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
    public int flightTicks() { return Math.max(1, (int) Math.round(flightDuration * 20.0)); }
    public int channelTicks(int mastery, int maxMastery) {
        return channelTicksOf(channelDuration, mastery, maxMastery);
    }
    public static int channelTicksOf(double seconds, int mastery, int maxMastery) {
        double multiplier = maxMastery > 0 && mastery >= maxMastery ? 0.75 : 1.0;
        return Math.max(1, (int) Math.round(seconds * 20.0 * multiplier));
    }
}
