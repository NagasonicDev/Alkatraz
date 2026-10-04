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
 * Spec sections 5, 5.2, 4.1 and 12.1: stormcaller declares a role allowed for AIR,
 * its cost keys are the circle-8 ladder row, and its 20-tick cadence over the
 * storm's life stays inside the circle-8 SUSTAIN budget.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration. The pure
 * {@link StormcallerConfig} record is exercised too, so its defaults and the yml
 * cannot drift apart.
 */
class StormcallerTest {

    private static final String ID = "stormcaller";

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = StormcallerTest.class.getResourceAsStream("/spells/" + ID + ".yml")) {
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
                "a storm that keeps striking after the cast resolves is a SUSTAIN");
        assertTrue(SpellBudgets.isAllowed(Element.AIR, role),
                "AIR owns movement SUSTAIN; it is the movement kind, not the ticking kind");
    }

    @Test
    void declaredDamageTotalStaysInsideTheCircleEightSustainBudget() throws IOException {
        YamlConfiguration y = load();
        double total = y.getDouble("power") * hitCount(y);
        assertEquals(96.0, total, 1e-9, "8.0 per strike x 12 strikes over the storm's 12 second life");
        assertTrue(total <= SpellBudgets.sustainBudget(8),
                ID + " deals " + total + " per target against the circle-8 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(8));
        assertFalse(y.isSet("terrain_budget"),
                "AIR is forbidden from TERRAIN, so stormcaller spends from no block budget at all");
    }

    @Test
    void costKeysMatchTheCircleEightLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(450, y.getInt("mana_cost"), "circle-8 mana_cost from the spec section 6 ladder");
        assertEquals(16, y.getInt("cooldown"), "circle-8 cooldown from the spec section 6 ladder");
        assertEquals(153, y.getDouble("required_focus"), 1e-9,
                "circle-8 required_focus from the spec section 6 ladder");
        assertTrue(y.getDouble("required_focus") <= 0.85 * 180,
                "153 sits exactly on SpellBudgetTest's 85% ceiling at circle 8 and must not drift above it");
        // The spec section 6 ladder lists 2.25s for circle 8, but SpellBudgets collapses
        // circles 6-9 to one legal cast time each and circle 8's is 30 ticks = 1.5s.
        assertEquals(1.5, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 8 to the single point 30 ticks, so 1.5 is "
                        + "the only legal circle-8 cast time");
        assertTrue(SpellBudgets.castTimeRange(8).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 8 allows " + SpellBudgets.castTimeRange(8).minTicks() + ".."
                        + SpellBudgets.castTimeRange(8).maxTicks() + " ticks");
    }

    @Test
    void levelIsEightAndThePowerIsTheDocumentedDeviation() throws IOException {
        YamlConfiguration y = load();
        assertEquals(ID, y.getString("id"), "the id key must equal the filename stem");
        assertEquals(8, y.getInt("level"), ID + " is the circle-8 air SUSTAIN");
        assertEquals("RSSLS", y.getString("code"), "the cast code allocated to stormcaller");
        assertEquals(4152, y.getInt("gui_custom_model_data"),
                "4152 is stormcaller's allocated model slot and is never reused");
        assertEquals(8.0, y.getDouble("power"), 1e-9,
                "a documented deviation from the 22.7 ladder value, chosen so hit_count 12 fits "
                        + "sustainBudget(8)");
        assertEquals(2.25, y.getDouble("channel_duration"), 1e-9,
                "the channel is a real commitment and costs real time before the storm exists");
        assertEquals(0.30, y.getDouble("move_cancel_threshold"), 1e-9,
                "the caster may shuffle but not run; WindUp.moveCancelThreshold reads this key");
    }

    @Test
    void theCadenceIsTwentyTicksOverTheWholeStormAndTheRecordAgrees() throws IOException {
        YamlConfiguration y = load();
        assertEquals(20L, SpellAttackContext.DAMAGE_INTERVAL_TICKS,
                "spec 4.1: a SUSTAIN field damages a target at most once every 20 ticks");
        assertEquals(20, Math.round(y.getDouble("strike_interval_seconds") * 20.0),
                "the yml's declared strike interval must be exactly the mandated 20 ticks");
        assertEquals(12.0, y.getDouble("storm_duration"), 1e-9,
                "a 12 second storm at a 20-tick cadence is exactly 12 applications");
        assertEquals(12, hitCount(y), "hit_count is the number of 20-tick openings in the storm's life");
        assertEquals((int) Math.round(y.getDouble("storm_duration") * 20.0), hitCount(y) * 20,
                "the declared life and the declared damage windows describe the same field");

        StormcallerConfig fromYml = StormcallerConfig.from(y);
        assertEquals(2.25, fromYml.channelDuration(), 1e-9);
        assertEquals(12.0, fromYml.stormDuration(), 1e-9);
        assertEquals(12, fromYml.hitsPerTarget(), "the record derives hit_count from storm_duration");
        assertEquals(240, fromYml.stormTicks());
        assertEquals(12, fromYml.damageWindows(), "240 ticks at a 20-tick cadence opens 12 windows");
        assertEquals(20, fromYml.strikeIntervalTicks(), "the cadence gap is the mandated 20 ticks");
        assertEquals(45, fromYml.channelTicks(0, 100), "2.25s is 45 ticks at zero mastery");
        assertEquals(34, fromYml.channelTicks(100, 100), "max mastery discounts the channel by 0.75x");
        assertEquals(96.0, fromYml.declaredTotal(y.getDouble("power")), 1e-9,
                "the record's declared total matches what the yml declares");

        StormcallerConfig defaults = StormcallerConfig.defaults();
        assertEquals(2.25, defaults.channelDuration(), 1e-9);
        assertEquals(12.0, defaults.stormDuration(), 1e-9);
        assertEquals(6.0, defaults.stormRadius(), 1e-9);
        assertEquals(9.0, defaults.stormHeight(), 1e-9);
        assertEquals(1.0, defaults.strikeIntervalSeconds(), 1e-9);
        assertEquals(0.30, defaults.moveCancelThreshold(), 1e-9);
        assertEquals(defaults, StormcallerConfig.fromConfig(null),
                "a null map falls back to exactly the documented defaults");
    }
}