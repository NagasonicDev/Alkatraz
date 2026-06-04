package me.nagasonic.alkatraz.spells.modifier;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Builds {@link AppliedModifier} instances from YAML impact sections.
 *
 * Supported {@code kind} values:
 * <ul>
 *   <li>{@code attribute} — vanilla attribute (see {@link AttributeAppliedModifier})</li>
 *   <li>{@code stat} — MagicProfile spell modifier (see {@link StatAppliedModifier})</li>
 * </ul>
 */
public final class AppliedModifierFactory {

    private AppliedModifierFactory() {}

    public static AppliedModifier fromConfig(ConfigurationSection section) {
        String kind = section.getString("kind", section.getString("modifier_kind", ""))
                .toLowerCase();
        String description = section.getString("description", "");

        return switch (kind) {
            case "attribute" -> parseAttribute(section, description);
            case "stat" -> parseStat(section, description);
            default -> throw new IllegalArgumentException(
                    "Unknown cast modifier kind '" + kind + "'. Use 'attribute' or 'stat'.");
        };
    }

    private static AppliedModifier parseAttribute(ConfigurationSection section, String description) {
        String attrName = section.getString("attribute", "");
        Attribute attribute = Attribute.valueOf(attrName.toUpperCase());

        double amount = section.getDouble("amount", 0);
        String opStr = section.getString("operation", "ADD_SCALAR").toUpperCase();
        AttributeModifier.Operation operation = parseOperation(opStr);

        if (description.isBlank()) {
            description = formatAttributeDescription(attribute, amount, operation);
        }
        return new AttributeAppliedModifier(attribute, amount, operation, description);
    }

    private static AppliedModifier parseStat(ConfigurationSection section, String description) {
        String stat = section.getString("stat", "");
        double value = section.getDouble("value", 0);
        if (description.isBlank()) {
            description = "+" + value + " " + stat;
        }
        return new StatAppliedModifier(stat, value, description);
    }

    private static AttributeModifier.Operation parseOperation(String opStr) {
        try {
            return AttributeModifier.Operation.valueOf(opStr);
        } catch (IllegalArgumentException e) {
            // MULTIPLY_SCALAR was renamed in newer API versions.
            if ("MULTIPLY_SCALAR_1".equals(opStr)) {
                return AttributeModifier.Operation.valueOf("MULTIPLY_SCALAR");
            }
            if ("MULTIPLY_SCALAR".equals(opStr)) {
                try {
                    return AttributeModifier.Operation.valueOf("MULTIPLY_SCALAR_1");
                } catch (IllegalArgumentException ignored) {
                    return AttributeModifier.Operation.valueOf("MULTIPLY_SCALAR");
                }
            }
            throw e;
        }
    }

    private static String formatAttributeDescription(Attribute attribute,
                                                       double amount,
                                                       AttributeModifier.Operation operation) {
        String name = attribute.name().toLowerCase().replace("generic_", "").replace('_', ' ');
        return switch (operation.name()) {
            case "ADD_NUMBER" -> (amount >= 0 ? "+" : "") + amount + " " + name;
            case "ADD_SCALAR", "MULTIPLY_SCALAR", "MULTIPLY_SCALAR_1" ->
                    (int) (amount * 100) + "% " + name;
            default -> amount + " " + name;
        };
    }
}
