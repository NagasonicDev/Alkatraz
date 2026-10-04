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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * crown_of_dawn is light's circle-9 DENIAL capstone and the sharpest test of LIGHT's ban on
 * TERRAIN: it is literally a wall. The last method therefore asserts that no terrain key
 * exists and that the pure config record carries the same literals the yml does, including
 * the derived {@code power} and {@code hit_count} that fill the declared cap exactly.
 */
class CrownOfDawnTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = CrownOfDawnTest.class.getResourceAsStream("/spells/crown_of_dawn.yml")) {
            assertNotNull(in, "missing bundled resource /spells/crown_of_dawn.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsDenialAndAllowedForLight() throws IOException {
        YamlConfiguration yml = load();
        SpellRole role = SpellRole.parse(yml.getString("role"));
        assertNotNull(role, "crown_of_dawn must declare a role: key");
        assertEquals(SpellRole.DENIAL, role,
                "the capstone denies passage and executes; that is what DENIAL means");
        assertEquals("LIGHT", yml.getString("element"), "crown_of_dawn is light's circle-9 capstone");
        assertTrue(SpellBudgets.isAllowed(Element.LIGHT, role),
                role + " must be in LIGHT's allowlist");
    }

    @Test
    void incidentalDamageIsCappedAtTheCircleNineBurstBudget() throws IOException {
        YamlConfiguration yml = load();
        assertTrue(yml.isSet("denial_damage_cap"),
                "SpellBudgetTest.denialSpellsCapIncidentalDamageAtTheCircleBurstBudget "
                        + "requires an explicit cap on any DENIAL spell that damages");
        double cap = yml.getDouble("denial_damage_cap");
        assertEquals(90.0, cap, 1e-9, "the cap is the section 6 circle-9 BURST budget, verbatim");
        assertTrue(cap > 0.0, "a declared cap must be positive");
        assertTrue(cap <= SpellBudgets.burstBudget(9),
                "the cap must not exceed the circle-9 BURST budget of " + SpellBudgets.burstBudget(9));
        assertTrue(yml.getDouble("power") <= SpellBudgets.burstBudget(9),
                "the single power key must sit inside the burst budget on its own too");
    }

    @Test
    void powerTimesHitCountLandsExactlyOnTheDeclaredCap() throws IOException {
        YamlConfiguration yml = load();
        double power = yml.getDouble("power");
        int hitCount = Math.max(1, yml.getInt("hit_count"));
        double cap = yml.getDouble("denial_damage_cap");
        assertEquals(6.0, power, 1e-9, "90.0 / 15 = 6.0, derived from the cap, not invented");
        assertEquals(15, hitCount,
                "wall_duration 15.0s is 300 ticks, which is 15 twenty-tick passes");
        assertEquals(90.0, power * hitCount, 1e-9,
                "6.0 x 15 passes = exactly the declared denial_damage_cap");
        assertEquals(cap, power * hitCount, 1e-9,
                "the cap and the declared per-cast total must be the same number, or the "
                        + "cap is decoration rather than a ceiling");
    }

    @Test
    void ladderCostsMatchTheCircleNineRow() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(600, yml.getInt("mana_cost"), "section 6 circle-9 mana");
        assertEquals(20, yml.getInt("cooldown"), "section 6 circle-9 cooldown");
        assertEquals(161, yml.getInt("required_focus"), "section 6 circle-9 focus");
        // The spec section 6 ladder lists 3.0s for circle 9, but SpellBudgets collapses
        // circles 6-9 to one legal cast time each and circle 9's is 40 ticks = 2.0s.
        assertEquals(2.0, yml.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 9 to the single point 40 ticks, so 2.0 is "
                        + "the only legal circle-9 cast time");
        assertTrue(SpellBudgets.castTimeRange(9).contains((int) Math.round(yml.getDouble("cast_time") * 20.0)),
                "circle 9 allows " + SpellBudgets.castTimeRange(9).minTicks() + ".."
                        + SpellBudgets.castTimeRange(9).maxTicks() + " ticks");
    }

    @Test
    void levelIsCircleNineTheWallPlacesNoBlocksAndTheRecordMatchesTheYml() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(9, yml.getInt("level"), "crown_of_dawn is light's circle-9 capstone");
        assertEquals("SSSSL", yml.getString("code"), "the allocated cast code");
        assertEquals(4158, yml.getInt("gui_custom_model_data"),
                "the allocated custom model data value");
        for (String terrainish : new String[]{
                "terrain_budget", "block_type", "material", "blocks_placed"}) {
            assertFalse(yml.isSet(terrainish),
                    terrainish + " must not exist: a wall of light is not terrain, so "
                            + "crown_of_dawn places no blocks and spends from no "
                            + "TERRAIN budget");
        }
        assertEquals(60.0, yml.getDouble("barrier_hitpoints"), 1e-9,
                "the wall's BarrierProperties hitpoints");
        assertEquals(0.45, yml.getDouble("push_strength"), 1e-9,
                "the radial push that makes it a wall rather than a decoration");
        assertEquals(0.3, yml.getDouble("execution_threshold"), 1e-9,
                "anything below 30% health is executed, which is the denial itself");

        assertEquals(SpellAttackContext.DAMAGE_INTERVAL_TICKS, CrownOfDawnConfig.DAMAGE_INTERVAL_TICKS,
                "the record's local cadence constant must not drift from the context's");

        // The pure config record must carry the SAME literals the yml does, and its derived
        // counts must be the ones the yml declares.
        CrownOfDawnConfig defaults = CrownOfDawnConfig.defaults();
        assertEquals(defaults, CrownOfDawnConfig.fromConfig(Map.of()),
                "defaults() and fromConfig(Map.of()) are one set of literals");
        assertEquals(defaults, CrownOfDawnConfig.fromConfig(null),
                "a null map is the same set of literals");
        assertEquals(yml.getDouble("wall_duration"), defaults.wallDuration(), 1e-9, "wall_duration");
        assertEquals(yml.getDouble("wall_length"), defaults.wallLength(), 1e-9, "wall_length");
        assertEquals(yml.getDouble("wall_height"), defaults.wallHeight(), 1e-9, "wall_height");
        assertEquals(yml.getDouble("barrier_hitpoints"), defaults.barrierHitpoints(), 1e-9,
                "barrier_hitpoints");
        assertEquals(yml.getDouble("wall_half_thickness"), defaults.wallHalfThickness(), 1e-9,
                "wall_half_thickness");
        assertEquals(yml.getDouble("push_strength"), defaults.pushStrength(), 1e-9, "push_strength");
        assertEquals(yml.getDouble("execution_threshold"), defaults.executionThreshold(), 1e-9,
                "execution_threshold");
        assertEquals(yml.getDouble("segment_spacing"), defaults.segmentSpacing(), 1e-9, "segment_spacing");
        assertEquals(yml.getDouble("rise_ticks"), defaults.riseTicks(), 1e-9, "rise_ticks");
        assertEquals(yml.getDouble("denial_damage_cap"), defaults.denialDamageCap(), 1e-9,
                "denial_damage_cap");
        assertEquals(300, defaults.durationTicks(), "15.0 seconds is 300 ticks");
        assertEquals(10, defaults.riseTickCount(), "10 ticks to full height");
        assertEquals(18, defaults.segmentCount(), "9.0 blocks at 0.5 spacing is 18 segments");
        assertEquals(15, defaults.hitCount(), "300 ticks over the 20-tick interval is 15 passes");
        assertEquals(6.0, defaults.powerForCap(), 1e-9, "90.0 / 15 = 6.0");
        assertEquals(90.0, defaults.powerForCap() * defaults.hitCount(), 1e-9,
                "the derived power and hit count fill the declared cap exactly");
    }
}
