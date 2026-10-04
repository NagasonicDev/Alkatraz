package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * light_bolt is the LIGHT starter and the element's circle-1 gap. Nothing about the undead
 * roster is configurable, because {@code AttackSpell.getPower} already applies light's
 * anti-dark amplification to every LIGHT spell - the fifth method exists to stop that from
 * being "helped" with a hand-rolled multiplier.
 */
class LightBoltTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = LightBoltTest.class.getResourceAsStream("/spells/light_bolt.yml")) {
            assertNotNull(in, "missing bundled resource /spells/light_bolt.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForLight() throws IOException {
        YamlConfiguration yml = load();
        SpellRole role = SpellRole.parse(yml.getString("role"));
        assertNotNull(role, "light_bolt must declare a role: key");
        assertEquals(SpellRole.BURST, role, "a circle-1 basic projectile is a BURST");
        assertEquals("LIGHT", yml.getString("element"),
                "light_bolt is light's circle-1 gap filler");
        assertTrue(SpellBudgets.isAllowed(Element.LIGHT, role),
                role + " must be in LIGHT's allowlist");
    }

    @Test
    void powerTimesHitCountFitsTheCircleOneBurstBudget() throws IOException {
        YamlConfiguration yml = load();
        double power = yml.getDouble("power");
        int hitCount = yml.isSet("hit_count") ? Math.max(1, yml.getInt("hit_count")) : 1;
        assertEquals(1, hitCount, "one bolt kills on contact, so no hit_count key is declared");
        assertEquals(3.0, power, 1e-9, "the section 6 circle-1 ladder power");
        assertEquals(3.0, power * hitCount, 1e-9, "3.0 x 1 hit per cast");
        assertTrue(power * hitCount <= SpellBudgets.burstBudget(1),
                "3.0 must sit inside the circle-1 BURST budget of " + SpellBudgets.burstBudget(1));
    }

    @Test
    void ladderCostsMatchTheCircleOneRow() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(15, yml.getInt("mana_cost"), "section 6 circle-1 mana");
        assertEquals(4, yml.getInt("cooldown"), "section 6 circle-1 cooldown");
        assertEquals(20, yml.getInt("required_focus"), "section 6 circle-1 focus");
        // Circle 1 is BELOW the collapse: its window is the 5-15 tick range, not a single
        // point, so the ladder's 0.5s survives untouched at 10 ticks.
        assertEquals(0.5, yml.getDouble("cast_time"), 1e-9,
                "0.5s is 10 ticks, inside circle 1's open " + SpellBudgets.castTimeRange(1).minTicks()
                        + "-" + SpellBudgets.castTimeRange(1).maxTicks() + " tick cast-time window; only "
                        + "circles 6-9 collapse to one legal value");
        assertTrue(SpellBudgets.castTimeRange(1).contains((int) Math.round(yml.getDouble("cast_time") * 20.0)),
                "circle 1 allows " + SpellBudgets.castTimeRange(1).minTicks() + ".."
                        + SpellBudgets.castTimeRange(1).maxTicks() + " ticks");
    }

    @Test
    void levelIsCircleOne() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(1, yml.getInt("level"), "light_bolt fills LIGHT's circle-1 gap");
        assertEquals("LLLRR", yml.getString("code"),
                "three leading L/L/L shared with fireball, then R/R");
        assertEquals(4154, yml.getInt("gui_custom_model_data"),
                "the allocated custom model data value");
    }

    @Test
    void theUndeadBonusIsNotConfiguredBecauseGetPowerAppliesIt() throws IOException {
        YamlConfiguration yml = load();
        for (String forbidden : new String[]{
                "undead_multiplier", "undead_bonus", "light_amplification",
                "anti_dark_multiplier", "affinity_gap_cap"}) {
            assertFalse(yml.isSet(forbidden),
                    forbidden + " must not exist: AttackSpell.getPower applies light's "
                            + "anti-dark amplification for every LIGHT spell automatically, "
                            + "and a config key here would double it");
        }
        assertTrue(yml.isSet("projectile_speed") && yml.isSet("projectile_range")
                        && yml.isSet("projectile_hit_radius"),
                "the three spell-specific keys are the only ad-hoc reads, and each has "
                        + "an explicit default in loadConfiguration()");
    }
}
