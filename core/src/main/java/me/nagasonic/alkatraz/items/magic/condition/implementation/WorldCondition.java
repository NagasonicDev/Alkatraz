package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;

import java.util.List;
import java.util.Map;

public final class WorldCondition implements Condition {
    private final List<String> worlds;

    @SuppressWarnings("unchecked")
    public WorldCondition(List<String> worlds) {
        this.worlds = worlds;
    }

    @Override
    public boolean test(TriggerContext context) {
        if (context.actor() == null) return false;
        return worlds.contains(context.actor().getWorld().getName());
    }

    @SuppressWarnings("unchecked")
    public static Condition fromConfig(Map<String, Object> config) {
        Object raw = config.getOrDefault("worlds", List.of());
        List<String> worlds = raw instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return new WorldCondition(worlds);
    }
}
