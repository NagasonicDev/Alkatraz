package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;

import java.util.Map;

public final class AlwaysCondition implements Condition {

    public static final AlwaysCondition INSTANCE = new AlwaysCondition();

    public AlwaysCondition() {}

    @Override
    public boolean test(TriggerContext context) {
        return true;
    }

    public static Condition fromConfig(Map<String, Object> config) {
        return INSTANCE;
    }
}
