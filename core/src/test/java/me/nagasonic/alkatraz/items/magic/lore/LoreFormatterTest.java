package me.nagasonic.alkatraz.items.magic.lore;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeType;
import me.nagasonic.alkatraz.api.magic.definition.ItemVisual;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerType;
import me.nagasonic.alkatraz.items.magic.config.SetBonusConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreFormatterTest {

    @BeforeEach
    void setUp() {
        MagicItemRegistries.ATTRIBUTE_TYPES.register(new AttributeType(MagicKeys.alkatraz("fire_affinity"), 0, "Fire Affinity"));
        MagicItemRegistries.ATTRIBUTE_TYPES.register(new AttributeType(MagicKeys.alkatraz("max_mana"), 0, "Maximum Mana"));
        MagicItemRegistries.ATTRIBUTE_TYPES.register(new AttributeType(MagicKeys.alkatraz("spell_power"), 0, "Spell Power"));
        MagicItemRegistries.ATTRIBUTE_TYPES.register(new AttributeType(MagicKeys.alkatraz("cast_time_multiplier"), 0, "Cast Time Multiplier"));
        MagicItemRegistries.TRIGGER_TYPES.register(new TriggerType(
                MagicKeys.alkatraz("on_damage_dealt"), "When the holder deals damage to an entity"));
    }

    @AfterEach
    void tearDown() {
        MagicItemRegistries.clearAll();
    }

    private static Map<String, Object> cond(String type, Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> effect(String type, Object... kv) {
        return cond(type, kv);
    }

    private static EngravingDefinition def(String key, List<String> triggers,
                                           Map<NamespacedKey, Double> attributes,
                                           List<Map<String, Object>> conditions,
                                           List<Map<String, Object>> effects) {
        return def(key, triggers, attributes, conditions, effects, List.of());
    }

    private static EngravingDefinition def(String key, List<String> triggers,
                                           Map<NamespacedKey, Double> attributes,
                                           List<Map<String, Object>> conditions,
                                           List<Map<String, Object>> effects,
                                           List<String> allowedItemTypes) {
        Map<String, Object> staticConfig = new LinkedHashMap<>();
        staticConfig.put("triggers", triggers);
        staticConfig.put("conditions", conditions);
        staticConfig.put("effects", effects);
        return new EngravingDefinition(
                MagicKeys.alkatraz(key),
                ItemVisual.of(Material.FIRE_CHARGE, key, List.of()),
                attributes,
                List.of(),
                List.of(),
                allowedItemTypes,
                staticConfig);
    }

    @Test
    void formatNumberHandlesIntegralsFractionsAndNegatives() {
        assertEquals("2", LoreFormatter.formatNumber(2.0));
        assertEquals("0.5", LoreFormatter.formatNumber(0.5));
        assertEquals("-0.02", LoreFormatter.formatNumber(-0.02));
    }

    @Test
    void attributeLinesRenderDisplayNameAndSign() {
        Map<NamespacedKey, Double> attributes = new LinkedHashMap<>();
        attributes.put(MagicKeys.alkatraz("fire_affinity"), 10.0);
        attributes.put(MagicKeys.alkatraz("max_mana"), 20.0);
        assertEquals(List.of(
                "&7Fire Affinity: &a+10",
                "&7Maximum Mana: &a+20"), LoreFormatter.attributeLines(attributes));
    }

    @Test
    void attributeLinesRenderNegativeValuesInRed() {
        Map<NamespacedKey, Double> attributes = new LinkedHashMap<>();
        attributes.put(MagicKeys.alkatraz("cast_time_multiplier"), -0.02);
        assertEquals(List.of("&7Cast Time Multiplier: &c-0.02"),
                LoreFormatter.attributeLines(attributes));
    }

    @Test
    void passiveEngravingShowsAttributesOnly() {
        EngravingDefinition def = def("fire_rune", List.of(),
                Map.of(MagicKeys.alkatraz("fire_affinity"), 10.0),
                List.of(), List.of(effect("ignite", "duration_ticks", 60)));
        List<String> lines = LoreFormatter.engravingBlock(def, MagicKeys.alkatraz("passive"));
        assertEquals(List.of(
                "",
                "&7&m---&r &6Fire Rune &7&m---",
                "&7Fire Affinity: &a+10"), lines);
    }

    @Test
    void triggeredEngravingShowsTriggerConditionsAndEffects() {
        EngravingDefinition def = def("explosive_rune", List.of("on_damage_dealt"),
                Map.of(),
                List.of(cond("cooldown", "seconds", 3), cond("circle_level", "min_circle", 2)),
                List.of(effect("explosion", "power", 2.5, "set_fire", true)));
        List<String> lines = LoreFormatter.engravingBlock(def, MagicKeys.alkatraz("on_damage_dealt"));
        assertEquals(List.of(
                "",
                "&7&m---&r &6Explosive Rune &7&m---",
                "&7Trigger: &fOn Damage Dealt",
                "&8&oWhen the holder deals damage to an entity",
                "",
                "&7Conditions:",
                "&7  &f• &rCooldown of 3s",
                "&7  &f• &rRequires Circle Level 2",
                "",
                "&7Effects:",
                "&7  &f• &rCreates an explosion of power &f2.5 (&9ignites fire&7)"), lines);
    }

    @Test
    void hiddenEffectsAndConditionsAreFiltered() {
        EngravingDefinition def = def("test_rune", List.of("on_damage_dealt"),
                Map.of(),
                List.of(cond("permission", "permission", "alkatraz.admin"),
                        cond("event_parameter", "parameter", "x", "value", "y"),
                        cond("always"),
                        cond("mana", "amount", 10)),
                List.of(effect("command", "command", "say hi"),
                        effect("heal", "amount", 2)));
        List<String> lines = LoreFormatter.engravingBlock(def, MagicKeys.alkatraz("on_damage_dealt"));
        String joined = String.join("\n", lines);
        assertFalse(joined.contains("permission"));
        assertFalse(joined.contains("event_parameter"));
        assertFalse(joined.contains("Always"));
        assertFalse(joined.contains("command"));
        assertTrue(joined.contains("Heals you for &f2 &9HP"));
        assertTrue(joined.contains("Requires at least &f10 &7mana"));
    }

    @Test
    void runeBlockShowsTriggersAttributesConditionsAndEffects() {
        EngravingDefinition def = def("fire_rune", List.of("on_damage_dealt", "on_kill"),
                Map.of(MagicKeys.alkatraz("fire_affinity"), 10.0),
                List.of(cond("random", "chance", 0.5)),
                List.of(effect("ignite", "duration_ticks", 60)));
        List<String> lines = LoreFormatter.runeBlock(def);
        assertEquals(List.of(
                "",
                "&7Triggers: &fOn Damage Dealt&7, &fOn Kill",
                "&7Fire Affinity: &a+10",
                "",
                "&7Conditions:",
                "&7  &f• &r50% chance to activate",
                "",
                "&7Effects:",
                "&7  &f• &rIgnites the target for &f3 &9seconds"), lines);
    }

    @Test
    void passiveRuneBlockShowsPassiveTrigger() {
        EngravingDefinition def = def("mana_well_rune", List.of(),
                Map.of(MagicKeys.alkatraz("max_mana"), 15.0),
                List.of(), List.of());
        List<String> lines = LoreFormatter.runeBlock(def);
        assertEquals(List.of(
                "",
                "&7Trigger: &fPassive",
                "&7Maximum Mana: &a+15"), lines);
    }

    @Test
    void setBonusBlockShowsAllThresholds() {
        Map<Integer, List<SetBonusConfig.BonusEntry>> bonuses = new LinkedHashMap<>();
        bonuses.put(2, List.of(
                new SetBonusConfig.BonusEntry(MagicKeys.alkatraz("fire_affinity"), 2),
                new SetBonusConfig.BonusEntry(MagicKeys.alkatraz("max_mana"), 15)));
        bonuses.put(4, List.of(
                new SetBonusConfig.BonusEntry(MagicKeys.alkatraz("fire_affinity"), 5),
                new SetBonusConfig.BonusEntry(MagicKeys.alkatraz("spell_power"), 0.5),
                new SetBonusConfig.BonusEntry(MagicKeys.alkatraz("max_mana"), 30)));
        SetBonusConfig.SetBonusData data = new SetBonusConfig.SetBonusData("ember", bonuses);
        List<String> lines = LoreFormatter.setBonusBlock("ember", data);
        assertEquals(List.of(
                "",
                "&7&m---&r &6Set Bonus: Ember &7&m---",
                "&f(2) Pieces:",
                "&7  Fire Affinity: &a+2",
                "&7  Maximum Mana: &a+15",
                "&f(4) Pieces:",
                "&7  Fire Affinity: &a+5",
                "&7  Spell Power: &a+0.5",
                "&7  Maximum Mana: &a+30"), lines);
    }

    @Test
    void setBonusBlockWithNullDataReturnsEmpty() {
        assertTrue(LoreFormatter.setBonusBlock("ember", null).isEmpty());
    }

    @Test
    void runeBlockShowsApplicableItemTypes() {
        EngravingDefinition def = def("fire_rune", List.of("on_damage_dealt"),
                Map.of(MagicKeys.alkatraz("fire_affinity"), 10.0),
                List.of(),
                List.of(effect("ignite", "duration_ticks", 60)),
                List.of("sword", "axe", "wand"));
        List<String> lines = LoreFormatter.runeBlock(def);
        assertEquals(List.of(
                "",
                "&7Triggers: &fOn Damage Dealt",
                "&7Applies to: &fSword&7, &fAxe&7, &fWand",
                "&7Fire Affinity: &a+10",
                "",
                "&7Effects:",
                "&7  &f• &rIgnites the target for &f3 &9seconds"), lines);
    }

    @Test
    void runeBlockOmitsAppliesToWhenNoAllowedItemTypes() {
        EngravingDefinition def = def("mana_well_rune", List.of(),
                Map.of(MagicKeys.alkatraz("max_mana"), 15.0),
                List.of(), List.of());
        List<String> lines = LoreFormatter.runeBlock(def);
        assertTrue(lines.stream().noneMatch(line -> line.contains("Applies to")));
    }

    @Test
    void healEffectUsesConfigAmount() {
        EngravingDefinition def = def("arcane_power_rune", List.of("on_spell_cast"),
                Map.of(),
                List.of(cond("circle_level", "min_circle", 2)),
                List.of(effect("heal", "amount", 2)));
        List<String> lines = LoreFormatter.engravingBlock(def, MagicKeys.alkatraz("on_spell_cast"));
        assertTrue(String.join("\n", lines).contains("Heals you for &f2 &9HP"));
    }
}
