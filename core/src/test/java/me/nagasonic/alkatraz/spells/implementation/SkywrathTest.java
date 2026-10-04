package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
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
 * Spec sections 5, 5.2, 4.1 and 12.1: skywrath declares a role allowed for AIR
 * (SUSTAIN, not UTILITY, despite the flight grant), its cost keys are the
 * circle-9 ladder row, its 15-strike life lands EXACTLY on the circle-9 SUSTAIN
 * budget, and its flight lasts exactly as long as its storm.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration. The pure
 * {@link SkywrathConfig} record is exercised too, so its defaults and the yml
 * cannot drift apart.
 */
class SkywrathTest {

    private static final String ID = "skywrath";

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = SkywrathTest.class.getResourceAsStream("/spells/" + ID + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + ID + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** Mirrors SpellBudgetTest.hitCount: absent means one damage application per cast. */
    private static int hitCount(YamlConfiguration y) {
        return y.isSet("hit_count") ? Math.max(1, y.getInt("hit_count")) : 1;
    }

    @Test
    void roleIsParseableAndAllowedForAir() throws IOException {
        YamlConfiguration y = load();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, ID + " must declare a role: key or SpellBudgetTest cannot judge it");
        assertEquals(Element.AIR, Element.valueOf(y.getString("element")),
                ID + " is an AIR spell and must never declare TERRAIN");
        assertEquals(SpellRole.SUSTAIN, role,
                "skywrath is judged on its storm's damage over time; the flight is the delivery "
                        + "mechanism, and UTILITY would fail SpellBudgetTest's no-damage-key rule");
        assertTrue(SpellBudgets.isAllowed(Element.AIR, role),
                "AIR owns movement SUSTAIN; it is the movement kind, not the ticking kind");
    }

    @Test
    void declaredDamageTotalLandsExactlyOnTheCircleNineSustainBudget() throws IOException {
        YamlConfiguration y = load();
        double total = y.getDouble("power") * hitCount(y);
        assertEquals(15, hitCount(y), "the T21 brief mandates hit_count: 15 for skywrath");
        assertEquals(144.0, total, 1e-9, "9.6 per strike x 15 strikes = 144.0");
        assertEquals(144.0, SpellBudgets.sustainBudget(9), 1e-9,
                "the circle-9 SUSTAIN budget, which the total lands on exactly");
        assertTrue(total <= SpellBudgets.sustainBudget(9),
                ID + " deals " + total + " per target against the circle-9 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(9));
        assertFalse(y.isSet("terrain_budget"),
                "AIR is forbidden from TERRAIN, so skywrath spends from no block budget at all");
    }

    @Test
    void costKeysMatchTheCircleNineLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(600, y.getInt("mana_cost"), "circle-9 mana_cost from the spec section 6 ladder");
        assertEquals(20, y.getInt("cooldown"), "circle-9 cooldown from the spec section 6 ladder");
        assertEquals(161, y.getDouble("required_focus"), 1e-9,
                "circle-9 required_focus, lowered from 162 so that "
                        + "SpellBudgetTest.focusCapsStayAtOrBelowEightyFivePercentOfMaxFocus holds: its "
                        + "ceiling is 0.85 * maxFocus(9) = 161.5, and 161 <= 161.5.");
        // The spec section 6 ladder lists 3.0s for circle 9, but SpellBudgets collapses
        // circles 6-9 to one legal cast time each and circle 9's is 40 ticks = 2.0s.
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 9 to the single point 40 ticks, so 2.0 is "
                        + "the only legal circle-9 cast time");
        assertTrue(SpellBudgets.castTimeRange(9).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 9 allows " + SpellBudgets.castTimeRange(9).minTicks() + ".."
                        + SpellBudgets.castTimeRange(9).maxTicks() + " ticks");
    }

    @Test
    void levelIsNineAndThePowerIsTheDocumentedDeviation() throws IOException {
        YamlConfiguration y = load();
        assertEquals(ID, y.getString("id"), "the id key must equal the filename stem");
        assertEquals(9, y.getInt("level"), ID + " is the circle-9 air capstone");
        assertEquals("SLRSL", y.getString("code"), "the cast code allocated to skywrath");
        assertEquals(4153, y.getInt("gui_custom_model_data"),
                "4153 is skywrath's allocated model slot and is never reused");
        assertEquals(9.6, y.getDouble("power"), 1e-9,
                "a documented deviation from the 28.6 ladder value, chosen so hit_count 15 lands "
                        + "exactly on sustainBudget(9)");
        assertEquals(15.0, y.getDouble("storm_duration"), 1e-9,
                "hit_count is storm_duration in seconds, per the SUSTAIN cadence rule");
    }

    @Test
    void theCadenceIsTwentyTicksAndTheFlightLastsExactlyAsLongAsTheStorm() throws IOException {
        YamlConfiguration y = load();
        assertEquals(20L, SpellAttackContext.DAMAGE_INTERVAL_TICKS,
                "spec 4.1: a SUSTAIN field damages a target at most once every 20 ticks");
        assertEquals(20, Math.round(y.getDouble("strike_interval_seconds") * 20.0),
                "the yml's declared strike interval must be exactly the mandated 20 ticks");
        assertEquals(300, (int) Math.round(y.getDouble("storm_duration") * 20.0),
                "a 15 second storm is 300 ticks");
        assertEquals(15, hitCount(y), "hit_count is the number of 20-tick openings in the storm's life");
        assertEquals(300, hitCount(y) * 20, "the declared life and the declared windows agree");

        assertTrue(y.getBoolean("flight_allow"),
                "skywrath grants flight by default - it is the capstone's whole identity");
        assertEquals(1.0, y.getDouble("flight_ticks_per_storm_tick"), 1e-9,
                "1.0 means flight for exactly the storm's duration and not a tick longer");
        assertEquals(0.15, y.getDouble("flight_speed"), 1e-9);
        assertTrue(y.getDouble("flight_speed") <= 1.0, "Player#setFlySpeed rejects anything above 1.0");

        SkywrathConfig fromYml = SkywrathConfig.from(y);
        assertEquals(15.0, fromYml.stormDuration(), 1e-9);
        assertEquals(15, fromYml.hitsPerTarget(), "the record derives hit_count from storm_duration");
        assertEquals(300, fromYml.stormTicks());
        assertEquals(15, fromYml.damageWindows(), "300 ticks at a 20-tick cadence opens 15 windows");
        assertEquals(20, fromYml.strikeIntervalTicks(), "the cadence gap is the mandated 20 ticks");
        assertEquals(300, fromYml.flightTicks(), "flight lasts exactly stormTicks() and no longer");
        assertTrue(fromYml.flightTicks() <= fromYml.stormTicks(),
                "the grant can never outlive the storm - the implementation clamps to this invariant");
        assertEquals(300, fromYml.flightTicksWithin(fromYml.stormTicks()));
        assertEquals(144.0, fromYml.declaredTotal(y.getDouble("power")), 1e-9,
                "the record's declared total matches what the yml declares");

        SkywrathConfig defaults = SkywrathConfig.defaults();
        assertEquals(15.0, defaults.stormDuration(), 1e-9);
        assertEquals(8.0, defaults.stormRadius(), 1e-9);
        assertEquals(12.0, defaults.stormHeight(), 1e-9);
        assertEquals(1.0, defaults.strikeIntervalSeconds(), 1e-9);
        assertTrue(defaults.flightAllow(), "a missing flight_allow must default to true, not false");
        assertEquals(1.0, defaults.flightTicksPerStormTick(), 1e-9);
        assertEquals(0.15, defaults.flightSpeed(), 1e-9);
        assertEquals(defaults, SkywrathConfig.fromConfig(null),
                "a null map falls back to exactly the documented defaults");
    }
}