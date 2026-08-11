package me.nagasonic.alkatraz.items.magic.lore;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeType;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.config.SetBonusConfig;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the detail lore sections shown on magic items, runes, and installed
 * engravings. All methods return raw {@code &}-code strings; callers must apply
 * {@link me.nagasonic.alkatraz.util.ColorFormat#format(String)} before use.
 *
 * <p>Debug/admin-only content is deliberately excluded: {@code command} effects,
 * {@code permission} and {@code event_parameter} conditions, and {@code always}
 * conditions are never rendered.</p>
 */
public final class LoreFormatter {

    private static final String HEADER_FORMAT = "&7&m---&r &6%s &7&m---";

    private LoreFormatter() {}

    public static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return StringUtils.trimTrailingZeroes(String.format(Locale.ROOT, "%.2f", value));
    }

    public static List<String> attributeLines(Map<NamespacedKey, Double> attributes) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<NamespacedKey, Double> entry : attributes.entrySet()) {
            double value = entry.getValue();
            if (value == 0) continue;
            String displayName = MagicItemRegistries.ATTRIBUTE_TYPES.get(entry.getKey())
                    .map(AttributeType::displayName)
                    .orElse(StringUtils.prettifyKey(entry.getKey().getKey()));
            lines.add(value > 0
                    ? "&7" + displayName + ": &a+" + formatNumber(value)
                    : "&7" + displayName + ": &c" + formatNumber(value));
        }
        return lines;
    }

    /**
     * Full detail block for an installed engraving.
     *
     * @param definition           the engraving definition
     * @param installedTriggerKey  the trigger key stored on the item instance
     */
    public static List<String> engravingBlock(EngravingDefinition definition, NamespacedKey installedTriggerKey) {
        List<String> lines = new ArrayList<>();
        boolean passive = isPassive(definition, installedTriggerKey);
        lines.add("");
        lines.add(String.format(HEADER_FORMAT, StringUtils.prettifyKey(definition.getKey().getKey())));
        if (!passive && installedTriggerKey != null) {
            lines.add("&7Trigger: &f" + StringUtils.prettifyKey(installedTriggerKey.getKey()));
            MagicItemRegistries.TRIGGER_TYPES.get(installedTriggerKey)
                    .ifPresent(trigger -> lines.add("&8&o" + trigger.description()));
        }
        lines.addAll(attributeLines(definition.attributes()));
        if (!passive) {
            addSections(lines, definition);
        }
        return lines;
    }

    /**
     * Full detail block for a physical rune item.
     */
    public static List<String> runeBlock(EngravingDefinition definition) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        List<String> triggers = triggerKeys(definition.staticConfig());
        if (triggers.isEmpty()) {
            lines.add("&7Trigger: &fPassive");
        } else {
            lines.add("&7Triggers: &f" + triggers.stream()
                    .map(StringUtils::prettifyKey)
                    .collect(Collectors.joining("&7, &f")));
        }
        lines.addAll(appliesToLines(definition));
        lines.addAll(attributeLines(definition.attributes()));
        addSections(lines, definition);
        return lines;
    }

    /**
     * Set-bonus section for a single set piece, showing every threshold.
     *
     * @param setName the prettified set name (e.g. "ember")
     * @param data    the loaded set data, or {@code null} if the set is unknown
     */
    public static List<String> setBonusBlock(String setName, SetBonusConfig.SetBonusData data) {
        List<String> lines = new ArrayList<>();
        if (data == null) {
            return lines;
        }
        lines.add("");
        lines.add(String.format(HEADER_FORMAT, "Set Bonus: " + StringUtils.prettifyKey(setName)));
        List<Integer> thresholds = new ArrayList<>(data.bonuses.keySet());
        thresholds.sort(Integer::compareTo);
        for (int threshold : thresholds) {
            lines.add("&f(" + threshold + ") Pieces:");
            for (SetBonusConfig.BonusEntry bonus : data.bonuses.get(threshold)) {
                String displayName = MagicItemRegistries.ATTRIBUTE_TYPES.get(bonus.attribute)
                        .map(AttributeType::displayName)
                        .orElse(StringUtils.prettifyKey(bonus.attribute.getKey()));
                lines.add(bonus.value > 0
                        ? "&7  " + displayName + ": &a+" + formatNumber(bonus.value)
                        : "&7  " + displayName + ": &c" + formatNumber(bonus.value));
            }
        }
        return lines;
    }

    private static boolean isPassive(EngravingDefinition definition, NamespacedKey installedTriggerKey) {
        if (installedTriggerKey != null && installedTriggerKey.equals(MagicKeys.alkatraz("passive"))) {
            return true;
        }
        return triggerKeys(definition.staticConfig()).isEmpty();
    }

    private static List<String> appliesToLines(EngravingDefinition definition) {
        List<String> types = definition.allowedItemTypes();
        if (types.isEmpty()) {
            return List.of();
        }
        return List.of("&7Applies to: &f" + types.stream()
                .map(StringUtils::prettifyKey)
                .collect(Collectors.joining("&7, &f")));
    }

    private static void addSections(List<String> lines, EngravingDefinition definition) {
        List<String> conditionLines = visibleLines(conditionLines(configList(definition.staticConfig(), "conditions")));
        if (!conditionLines.isEmpty()) {
            lines.add("");
            lines.add("&7Conditions:");
            lines.addAll(conditionLines);
        }
        List<String> effectLines = visibleLines(effectLines(configList(definition.staticConfig(), "effects")));
        if (!effectLines.isEmpty()) {
            lines.add("");
            lines.add("&7Effects:");
            lines.addAll(effectLines);
        }
    }

    private static List<String> visibleLines(List<String> raw) {
        return raw.stream().filter(line -> line != null).collect(Collectors.toList());
    }

    private static List<String> conditionLines(List<Map<String, Object>> conditions) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> condition : conditions) {
            String line = conditionLine(condition);
            if (line != null) {
                lines.add("&7  &f• &r" + line);
            }
        }
        return lines;
    }

    private static List<String> effectLines(List<Map<String, Object>> effects) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> effect : effects) {
            String line = effectLine(effect);
            if (line != null) {
                lines.add("&7  &f• &r" + line);
            }
        }
        return lines;
    }

    private static String conditionLine(Map<String, Object> condition) {
        String type = String.valueOf(condition.get("type"));
        switch (type) {
            case "always", "permission", "event_parameter":
                return null;
            case "cooldown":
                return "Cooldown of " + formatNumber(doubleValue(condition, "seconds", 1.0)) + "s";
            case "circle_level":
                return "Requires Circle Level " + condition.getOrDefault("min_circle", 1);
            case "mana":
                return "Requires " + comparisonWord(String.valueOf(condition.getOrDefault("comparison", "GREATER_OR_EQUAL")))
                        + " &f" + formatNumber(doubleValue(condition, "amount", 10.0)) + " &7mana";
            case "random":
                return formatNumber(doubleValue(condition, "chance", 1.0) * 100) + "% chance to activate";
            case "compare_attribute":
                String attr = String.valueOf(condition.get("attribute"));
                String display = MagicItemRegistries.ATTRIBUTE_TYPES.get(MagicKeys.require(attr))
                        .map(AttributeType::displayName)
                        .orElse(StringUtils.prettifyKey(MagicKeys.require(attr).getKey()));
                return "Requires " + comparisonWord(String.valueOf(condition.getOrDefault("comparison", "GREATER_OR_EQUAL")))
                        + " &f" + formatNumber(doubleValue(condition, "value", 0.0)) + " &7" + display;
            case "spell_element":
                return "Requires &f" + StringUtils.prettifyKey(String.valueOf(condition.get("element"))) + " &7spells";
            case "world":
                Object raw = condition.get("worlds");
                List<String> worlds = raw instanceof List<?> list
                        ? list.stream().map(String::valueOf).collect(Collectors.toList())
                        : List.of();
                return "Only works in &f" + String.join("&7, &f", worlds);
            case "arcane_knowledge":
                return "Requires &f" + formatNumber(doubleValue(condition, "amount", 0.0)) + " &7Arcane Knowledge";
            case "has_discovered_spell":
                return "Requires discovering &f" + StringUtils.prettifyKey(String.valueOf(condition.getOrDefault("spell_id", "")));
            default:
                return null;
        }
    }

    private static String effectLine(Map<String, Object> effect) {
        String type = String.valueOf(effect.get("type"));
        switch (type) {
            case "command":
                return null;
            case "ignite":
                return "Ignites the target for &f" + formatNumber(ticksToSeconds(intValue(effect, "duration_ticks", 60))) + " &9seconds";
            case "heal":
                return "Heals you for &f" + formatNumber(doubleValue(effect, "amount", 2.0)) + " &9HP";
            case "explosion":
                StringBuilder explosion = new StringBuilder("Creates an explosion of power &f")
                        .append(formatNumber(doubleValue(effect, "power", 2.0)));
                if (boolValue(effect, "set_fire", false)) {
                    explosion.append(" (&9ignites fire&7)");
                }
                if (!boolValue(effect, "break_blocks", true)) {
                    explosion.append(" (&9no block damage&7)");
                }
                return explosion.toString();
            case "damage":
                StringBuilder damage = new StringBuilder("Deals &f")
                        .append(formatNumber(doubleValue(effect, "damage", 1.0)))
                        .append(" &9damage");
                if (boolValue(effect, "bypass_armor", false)) {
                    damage.append(" (&9ignores armor&7)");
                }
                return damage.toString();
            case "apply_potion":
                String potion = StringUtils.prettifyKey(String.valueOf(effect.getOrDefault("effect", "SPEED")));
                int amplifier = intValue(effect, "amplifier", 0);
                String target = "self".equalsIgnoreCase(String.valueOf(effect.get("target"))) ? "you" : "the target";
                return "Applies &f" + potion + " " + StringUtils.toRoman(amplifier + 1)
                        + " &9for &f" + formatNumber(ticksToSeconds(intValue(effect, "duration_ticks", 100)))
                        + " &9seconds to " + target;
            case "teleport":
                return "Teleports you &f" + formatNumber(doubleValue(effect, "distance", 5.0)) + " &9blocks forward";
            case "particle":
                return "Spawns &f" + StringUtils.prettifyKey(String.valueOf(effect.getOrDefault("particle", "FLAME"))) + " &9particles";
            case "play_sound":
                return "Plays the sound &f" + StringUtils.prettifyKey(String.valueOf(effect.get("sound")));
            case "message":
                return "Displays a message";
            default:
                return null;
        }
    }

    private static double ticksToSeconds(int ticks) {
        return ticks / 20.0;
    }

    private static double doubleValue(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int intValue(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean boolValue(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value != null ? Boolean.parseBoolean(String.valueOf(value)) : fallback;
    }

    private static String comparisonWord(String comparison) {
        return switch (comparison) {
            case "GREATER_THAN" -> "more than";
            case "LESS_THAN" -> "less than";
            case "LESS_OR_EQUAL" -> "at most";
            case "EQUAL" -> "exactly";
            default -> "at least";
        };
    }

    private static List<String> triggerKeys(Map<String, Object> staticConfig) {
        Object raw = staticConfig.get("triggers");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> configList(Map<String, Object> staticConfig, String key) {
        Object raw = staticConfig.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((k, v) -> converted.put(String.valueOf(k), v));
                result.add(converted);
            }
        }
        return result;
    }
}
