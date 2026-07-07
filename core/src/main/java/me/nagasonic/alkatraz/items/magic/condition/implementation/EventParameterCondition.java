package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;

import java.util.Map;

public final class EventParameterCondition implements Condition {

    private final String parameter;
    private final String value;

    public EventParameterCondition(String parameter, String value) {
        this.parameter = parameter;
        this.value = value;
    }

    @Override
    public boolean test(TriggerContext context) {
        Object actual = context.parameter(parameter);
        if (actual == null) return false;
        return value.equals(String.valueOf(actual));
    }

    public static Condition fromConfig(Map<String, Object> config) {
        String parameter = String.valueOf(config.get("parameter"));
        String value = String.valueOf(config.get("value"));
        return new EventParameterCondition(parameter, value);
    }
}
