package me.nagasonic.alkatraz.api.magic.effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;

public interface Effect {

    void execute(TriggerContext context);
}
