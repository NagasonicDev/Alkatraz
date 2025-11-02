package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;

public enum Configs {
    CHECK_UPDATES(Alkatraz.getPluginConfig().getBoolean("check_updates")),
    DEBUG(Alkatraz.getPluginConfig().getBoolean("debug")),
    DEFAULT_STAT_POINTS(Alkatraz.getPluginConfig().getInt("default_stat_points")),
    DEFAULT_RESET_TOKENS(Alkatraz.getPluginConfig().getInt("default_reset_tokens")),
    CIRCLE_TICKS(Alkatraz.getPluginConfig().getLong("circle_ticks")),
    AFFINITY_PER_POINT(Alkatraz.getPluginConfig().getInt("affinity_per_point")),
    RESISTANCE_PER_POINT(Alkatraz.getPluginConfig().getInt("resistance_per_point"));

    private Object value;
    Configs(Object value){
        this.value = value;
    }

    public Object get(){
        return this.value;
    }
}
