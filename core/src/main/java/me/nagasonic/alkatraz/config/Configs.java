package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;

public enum Configs {
    CHECK_UPDATES,
    VERBOSE,
    DEFAULT_STAT_POINTS,
    DEFAULT_RESET_TOKENS,
    CIRCLE_TICKS,
    AFFINITY_PER_POINT,
    RESISTANCE_PER_POINT,
    FIRST_JOIN_TUTORIAL,
    LANGUAGE;

    private volatile Object value;
    private static volatile boolean loaded = false;

    public Object get(){
        if (this.value == null && !loaded) {
            reload();
        }
        return this.value;
    }

    public static void reload() {
        var config = Alkatraz.getPluginConfig();
        if (config == null) {
            loaded = true;
            return;
        }
        CHECK_UPDATES.value = config.getBoolean("check_updates");
        VERBOSE.value = VerbosityLevel.fromString(config.getString("verbose"));
        DEFAULT_STAT_POINTS.value = config.getInt("default_stat_points");
        DEFAULT_RESET_TOKENS.value = config.getInt("default_reset_tokens");
        CIRCLE_TICKS.value = config.getLong("circle_ticks");
        AFFINITY_PER_POINT.value = config.getInt("affinity_per_point");
        RESISTANCE_PER_POINT.value = config.getInt("resistance_per_point");
        FIRST_JOIN_TUTORIAL.value = config.getBoolean("first_join_tutorial");
        LANGUAGE.value = config.getString("language", "english");
        loaded = true;
    }
}
