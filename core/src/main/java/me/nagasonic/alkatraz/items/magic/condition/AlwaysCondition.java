package me.nagasonic.alkatraz.items.magic.condition;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;

import java.util.Map;

final class AlwaysCondition implements Condition {

    static final AlwaysCondition INSTANCE = new AlwaysCondition();

    private AlwaysCondition() {}

    @Override
    public boolean test(TriggerContext context) {
        return true;
    }

    static Condition fromConfig(Map<String, Object> config) {
        return INSTANCE;
    }
}
