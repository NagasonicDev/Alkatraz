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
 * Spec sections 5, 5.2, 4.1 and 12.1: cyclone_nest declares a role allowed for
 * AIR, its cost keys are the circle-7 ladder row, and its 20-tick cadence over one
 * nest's life stays inside the circle-7 SUSTAIN budget no matter how many nests it
 * places.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration. The pure
 * {@link CycloneNestConfig} record is exercised too, so the record's defaults and
 * the yml cannot drift apart.
 */
class CycloneNestTest {

    private static final String ID = "cyclone_nest";

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = CycloneNestTest.class.getResourceAsStream("/spells/" + ID + ".yml")) {
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
                "a field that keeps acting after the cast resolves is a SUSTAIN");
        assertTrue(SpellBudgets.isAllowed(Element.AIR, role),
                "AIR owns movement SUSTAIN; it is the movement kind, not the ticking kind");
    }

    @Test
    void declaredDamageTotalStaysInsideTheCircleSevenSustainBudget() throws IOException {
        YamlConfiguration y = load();
        // One nest's life only: the three nests share one cadence clock, so this is
        // the whole per-target ceiling and NOT three times it.
        double total = y.getDouble("power") * hitCount(y);
        assertEquals(72.0, total, 1e-9, "9.0 per application x 8 applications over one nest's life");
        assertTrue(total <= SpellBudgets.sustainBudget(7),
                ID + " deals " + total + " per target against the circle-7 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(7));
        assertFalse(y.isSet("terrain_budget"),
                "AIR is forbidden from TERRAIN, so cyclone_nest spends from no block budget at all");
    }

    @Test
    void costKeysMatchTheCircleSevenLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(240, y.getInt("mana_cost"), "circle-7 mana_cost from the spec section 6 ladder");
        assertEquals(13, y.getInt("cooldown"), "circle-7 cooldown from the spec section 6 ladder");
        assertEquals(144, y.getDouble("required_focus"), 1e-9,
                "circle-7 required_focus, lowered from 145 so SpellBudgetTest's 85%-of-max_focus "
                        + "ceiling of 0.85 * 170 = 144.5 holds");
        // The spec section 6 ladder lists 2.0s for circle 7, but SpellBudgets collapses
        // circles 6-9 to one legal cast time each and circle 7's is 25 ticks = 1.25s.
        assertEquals(1.25, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 7 to the single point 25 ticks, so 1.25 is "
                        + "the only legal circle-7 cast time");
        assertTrue(SpellBudgets.castTimeRange(7).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 7 allows " + SpellBudgets.castTimeRange(7).minTicks() + ".."
                        + SpellBudgets.castTimeRange(7).maxTicks() + " ticks");
    }

    @Test
    void levelIsSevenAndThePowerIsTheDocumentedDeviation() throws IOException {
        YamlConfiguration y = load();
        assertEquals(ID, y.getString("id"), "the id key must equal the filename stem");
        assertEquals(7, y.getInt("level"), ID + " is the circle-7 air SUSTAIN");
        assertEquals("LRSRL", y.getString("code"), "the cast code allocated to cyclone_nest");
        assertEquals(4151, y.getInt("gui_custom_model_data"),
                "4151 is cyclone_nest's allocated model slot and is never reused");
        assertEquals(9.0, y.getDouble("power"), 1e-9,
                "a documented deviation from the 18.9 ladder value, chosen so hit_count 8 fits "
                        + "sustainBudget(7)");
        assertEquals(3, y.getInt("nest_count"), "the cast places three tornadoes by default");
    }

    @Test
    void theCadenceIsTwentyTicksOverAFullNestLifeAndTheRecordAgrees() throws IOException {
        YamlConfiguration y = load();
        assertEquals(20L, SpellAttackContext.DAMAGE_INTERVAL_TICKS,
                "spec 4.1: a SUSTAIN field damages a target at most once every 20 ticks");
        assertEquals(8.0, y.getDouble("nest_lifetime"), 1e-9,
                "one nest lives 8 seconds, which at a 20-tick cadence is exactly 8 applications");
        assertEquals(8, hitCount(y),
                "hit_count is the number of 20-tick openings inside one nest's life");
        assertEquals((int) Math.round(y.getDouble("nest_lifetime") * 20.0), hitCount(y) * 20,
                "the declared life and the declared damage windows describe the same field");

        // The pure record must agree with the yml, and must supply a default for
        // every key so a missing key can never read as 0.0.
        CycloneNestConfig fromYml = CycloneNestConfig.from(y);
        assertEquals(3, fromYml.nestCount());
        assertEquals(8.0, fromYml.nestLifetime(), 1e-9);
        assertEquals(8, fromYml.hitsPerTarget(), "the record derives hit_count from nest_lifetime");
        assertEquals(160, fromYml.lifetimeTicks());
        assertEquals(8, fromYml.damageWindows(), "160 ticks at a 20-tick cadence opens 8 windows");
        assertEquals(72.0, fromYml.declaredTotal(y.getDouble("power")), 1e-9,
                "the record's declared total matches what the yml declares");

        CycloneNestConfig defaults = CycloneNestConfig.defaults();
        assertEquals(3, defaults.nestCount());
        assertEquals(8.0, defaults.nestLifetime(), 1e-9);
        assertEquals(2.5, defaults.nestRadius(), 1e-9);
        assertEquals(6.0, defaults.nestHeight(), 1e-9);
        assertEquals(0.55, defaults.nestPull(), 1e-9);
        assertEquals(0.18, defaults.nestWanderSpeed(), 1e-9);
        assertEquals(4.0, defaults.nestSpawnSpread(), 1e-9);
        assertEquals(defaults, CycloneNestConfig.fromConfig(null),
                "a null map falls back to exactly the documented defaults");
    }
}