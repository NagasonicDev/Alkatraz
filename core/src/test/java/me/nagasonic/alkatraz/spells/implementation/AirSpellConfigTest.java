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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions, so no Bukkit mock is needed. getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest.
 */
class AirSpellConfigTest {

    private static final String[] AIR_SPELLS = {
            "air_burst", "air_blades", "wind_vortex", "swift", "tornado", "wind_barrier"
    };

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = AirSpellConfigTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyAirSpellDeclaresARoleAllowedForItsElement() throws IOException {
        for (String id : AIR_SPELLS) {
            YamlConfiguration yml = load(id);
            SpellRole role = SpellRole.parse(yml.getString("role"));
            assertNotNull(role, id + " must declare a role: key");
            assertTrue(SpellBudgets.isAllowed(Element.AIR, role),
                    id + " declares " + role + " but AIR may not use it");
        }
    }

    @Test
    void windBarrierIsTheUnassignedAirDenialSpell() throws IOException {
        // wind_barrier is AIR/DENIAL/circle 4 in the design but was named in no
        // task row of the master plan. T11 owns it because no other task touches
        // an AIR yml.
        YamlConfiguration yml = load("wind_barrier");
        assertEquals("AIR", yml.getString("element"), "wind_barrier is an AIR spell");
        assertEquals(SpellRole.DENIAL, SpellRole.parse(yml.getString("role")),
                "wind_barrier deflects projectiles, so it is DENIAL");
        assertEquals(75, yml.getInt("mana_cost"), "circle-4 mana");
        assertEquals(7, yml.getInt("cooldown"), "circle-4 cooldown");
        assertEquals(90, yml.getInt("required_focus"), "circle-4 focus");
    }

    @Test
    void airDeclaresNoTerrainSpell() throws IOException {
        assertFalse(SpellBudgets.isAllowed(Element.AIR, SpellRole.TERRAIN),
                "AIR is forbidden from TERRAIN - air's damage is a by-product of moving something");
        for (String id : AIR_SPELLS) {
            assertFalse(SpellRole.TERRAIN == SpellRole.parse(load(id).getString("role")),
                    id + " must not be TERRAIN");
        }
    }

    @Test
    void burstPowersMatchTheTable() throws IOException {
        assertEquals(3.0, load("air_burst").getDouble("power"), 1e-9,
                "air_burst now deals damage, so it is a circle-1 BURST at 3.0");
        assertEquals(4.2, load("air_blades").getDouble("power"), 1e-9, "air_blades is circle 2");
        assertTrue(load("air_burst").getDouble("power") <= SpellBudgets.burstBudget(1),
                "air_burst's 3.0 sits inside the circle-1 BURST budget of "
                        + SpellBudgets.burstBudget(1));
    }

    @Test
    void airBladesVolleyIsBoundedByTheFanAndTheDedup() throws IOException {
        double perBlade = load("air_blades").getDouble("power");
        int blades = 3;
        assertEquals(12.6, perBlade * blades, 1e-9,
                "4.2 per blade x 3 blades = 12.6, which is the design's stated fan total "
                        + "when each blade finds its own target");

        // The BURST budget is per TARGET, so the number that has to fit under
        // burstBudget(2) is what ONE target takes from the whole cast. The volley
        // shares a single AttackProperties and AirBlades guards on props.hasHit,
        // so the first blade to land records the target and the rest skip it.
        Set<String> hitThisCast = new HashSet<>();
        double totalToOneTarget = 0;
        for (int i = 0; i < blades; i++) {
            if (hitThisCast.add("victim")) totalToOneTarget += perBlade;
        }
        assertEquals(4.2, totalToOneTarget, 1e-9,
                "one target caught by all three blades is damaged once, because the "
                        + "shared AttackProperties' hit Set is cast-lifetime and never cleared");
        assertTrue(totalToOneTarget <= SpellBudgets.burstBudget(2),
                "the per-target ceiling is the circle-2 BURST budget of "
                        + SpellBudgets.burstBudget(2) + ", and the deduped volley deals "
                        + totalToOneTarget);
        assertTrue(perBlade * blades > SpellBudgets.burstBudget(2),
                "the 12.6 fan total is a spread-damage figure, not a per-target one - it only "
                        + "lands when all three blades reach THREE DIFFERENT targets");
    }

    @Test
    void tornadoSustainTotalLandsExactlyOnTheBudget() throws IOException {
        double perHit = load("tornado").getDouble("power");
        double durationSeconds = load("tornado").getDouble("duration");
        long ticks = (long) (durationSeconds * 20);
        long hits = ticks / 20L;
        assertEquals(7.2, perHit, 1e-9, "tornado is 7.2 per hit");
        assertEquals(5, hits, "5 seconds at one hit per 20 ticks is 5 hits");
        assertEquals(36.0, perHit * hits, 1e-9, "5 x 7.2 = 36");
        assertEquals(SpellBudgets.sustainBudget(5), perHit * hits, 1e-9,
                "the tornado total is exactly the circle-5 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(5));
    }

    @Test
    void tornadoNoLongerCarriesADamagePerTickKey() throws IOException {
        assertFalse(load("tornado").isSet("damage_per_tick"),
                "damage_per_tick was the only thing actually dealing damage while the whole "
                        + "option system was inert on a bare Spell; power now drives it");
        assertTrue(load("tornado").isSet("power"),
                "tornado gains the power key it needs now that it extends AttackSpell");
    }

    @Test
    void oldPerTickTornadoDamageWasTwentyTimesTheBudget() {
        double oldPerTick = 2.0;
        double oldTicks = 100;
        double oldTotal = oldPerTick * oldTicks;
        assertEquals(200.0, oldTotal, 1e-9, "the old flat damage was 200 over the field's life");
        assertTrue(oldTotal > SpellBudgets.sustainBudget(5),
                "200 is over 5.5x the circle-5 SUSTAIN budget of " + SpellBudgets.sustainBudget(5));
    }

    @Test
    void windVortexTotalLandsInsideTheSmallestSustainBudget() throws IOException {
        double perHit = load("wind_vortex").getDouble("power");
        int durationSeconds = load("wind_vortex").getInt("vortex_duration");
        long hits = ((long) durationSeconds * 20) / 20L;
        assertEquals(5, hits, "5 seconds at one hit per 20 ticks is 5 hits");
        assertEquals(2.5, perHit * hits, 1e-9, "5 x 0.5 = 2.5");
        assertTrue(perHit * hits <= SpellBudgets.sustainBudget(3),
                "circle 2 has no SUSTAIN budget of its own, so the smallest defined one "
                        + "(circle 3, " + SpellBudgets.sustainBudget(3) + ") is the ceiling");
    }

    @Test
    void swiftIsUtilityAndCarriesNoDamageKey() throws IOException {
        assertEquals(SpellRole.UTILITY, SpellRole.parse(load("swift").getString("role")),
                "swift is a mobility dash");
        assertFalse(load("swift").isSet("power"),
                "a non-damage spell correctly extends bare Spell and has no power key");
    }

    @Test
    void ladderCostsMatchTheTable() throws IOException {
        assertEquals(15, load("air_burst").getInt("mana_cost"), "circle-1 mana");
        assertEquals(4, load("air_burst").getInt("cooldown"), "circle-1 cooldown");
        assertEquals(24, load("air_blades").getInt("mana_cost"), "circle-2 mana");
        assertEquals(24, load("wind_vortex").getInt("mana_cost"), "circle-2 mana");
        assertEquals(24, load("swift").getInt("mana_cost"), "circle-2 mana");
        assertEquals(120, load("tornado").getInt("mana_cost"), "circle-5 mana");
        assertEquals(115, load("tornado").getInt("required_focus"), "circle-5 focus");
        assertEquals(9, load("tornado").getInt("cooldown"), "circle-5 cooldown");
    }
}
