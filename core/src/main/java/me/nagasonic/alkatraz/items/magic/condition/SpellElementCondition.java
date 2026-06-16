package me.nagasonic.alkatraz.items.magic.condition;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;

import java.util.Locale;
import java.util.Map;

final class SpellElementCondition implements Condition {

    private final String element;

    SpellElementCondition(String element) {
        this.element = element.toUpperCase(Locale.ROOT);
    }

    @Override
    public boolean test(TriggerContext context) {
        Object spellElement = context.parameter("spell_element");
        if (spellElement == null) {
            return false;
        }
        return element.equals(String.valueOf(spellElement).toUpperCase(Locale.ROOT));
    }

    static Condition fromConfig(Map<String, Object> config) {
        return new SpellElementCondition(String.valueOf(config.get("element")));
    }
}
