package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;

import java.util.Map;

public final class PermissionCondition implements Condition {
    private final String permission;

    public PermissionCondition(String permission) {
        this.permission = permission;
    }

    @Override
    public boolean test(TriggerContext context) {
        return context.playerActor().map(player -> player.hasPermission(permission)).orElse(false);
    }

    public static Condition fromConfig(Map<String, Object> config) {
        return new PermissionCondition(String.valueOf(config.getOrDefault("permission", "")));
    }
}
