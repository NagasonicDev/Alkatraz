package me.nagasonic.alkatraz.api.magic.condition;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;

public interface Condition {

    boolean test(TriggerContext context);
}
