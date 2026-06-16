package me.nagasonic.alkatraz.items.magic.condition;

import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.items.magic.attribute.AttributeService;
import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.NamespacedKey;

import java.util.Locale;
import java.util.Map;

final class CompareAttributeCondition implements Condition {

    private final NamespacedKey attribute;
    private final Comparison comparison;
    private final double value;

    CompareAttributeCondition(NamespacedKey attribute, Comparison comparison, double value) {
        this.attribute = attribute;
        this.comparison = comparison;
        this.value = value;
    }

    @Override
    public boolean test(TriggerContext context) {
        if (context.actor() == null) {
            return false;
        }
        AttributeService attributes = MagicItemServices.get().attributes();
        double current = attributes.get(context.actor(), attribute, context);
        return comparison.compare(current, value);
    }

    enum Comparison {
        GREATER_THAN,
        GREATER_OR_EQUAL,
        LESS_THAN,
        LESS_OR_EQUAL,
        EQUAL;

        static Comparison fromString(String raw) {
            return Comparison.valueOf(raw.toUpperCase(Locale.ROOT));
        }

        boolean compare(double current, double expected) {
            return switch (this) {
                case GREATER_THAN -> current > expected;
                case GREATER_OR_EQUAL -> current >= expected;
                case LESS_THAN -> current < expected;
                case LESS_OR_EQUAL -> current <= expected;
                case EQUAL -> Double.compare(current, expected) == 0;
            };
        }
    }

    static Condition fromConfig(Map<String, Object> config) {
        NamespacedKey attribute = MagicKeys.require(String.valueOf(config.get("attribute")));
        Comparison comparison = Comparison.fromString(String.valueOf(config.getOrDefault("comparison", "GREATER_OR_EQUAL")));
        double value = Double.parseDouble(String.valueOf(config.get("value")));
        return new CompareAttributeCondition(attribute, comparison, value);
    }
}
