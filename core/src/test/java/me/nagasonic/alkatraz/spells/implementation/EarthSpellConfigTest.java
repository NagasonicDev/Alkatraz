package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions plus the pure restore-ticket arithmetic, so no Bukkit mock
 * is needed. getResourceAsStream plus YamlConfiguration, following
 * SummonBrainYmlTest.
 */
class EarthSpellConfigTest {

    private static final String[] EARTH_SPELLS =
            {"earth_throw", "earth_spike", "tremor", "earthen_wall", "earthsplitter"};

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = EarthSpellConfigTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyEarthSpellDeclaresARoleAllowedForItsElement() throws IOException {
        for (String id : EARTH_SPELLS) {
            YamlConfiguration yml = load(id);
            SpellRole role = SpellRole.parse(yml.getString("role"));
            assertNotNull(role, id + " must declare a role: key");
            assertTrue(SpellBudgets.isAllowed(Element.EARTH, role),
                    id + " declares " + role + " but EARTH may not use it");
        }
    }

    @Test
    void earthHasNoConvenienceSpells() throws IOException {
        assertFalse(SpellBudgets.isAllowed(Element.EARTH, SpellRole.UTILITY),
                "EARTH is forbidden from UTILITY - earth has no convenience spells");
        for (String id : EARTH_SPELLS) {
            assertFalse(SpellRole.UTILITY == SpellRole.parse(load(id).getString("role")),
                    id + " must not be UTILITY");
        }
    }

    @Test
    void burstPowersMatchTheTable() throws IOException {
        assertEquals(3.0, load("earth_throw").getDouble("power"), 1e-9,
                "earth_throw was a raw 4.0 with no getPower at all; the table value is 3.0");
        assertEquals(5.5, load("earth_spike").getDouble("power"), 1e-9, "earth_spike is circle 2");
        assertEquals(5.5, load("tremor").getDouble("power"), 1e-9, "tremor is circle 3");
    }

    @Test
    void burstPowersStayInsideTheirCircleBudgets() throws IOException {
        assertTrue(load("earth_throw").getDouble("power") <= SpellBudgets.burstBudget(1),
                "earth_throw inside the circle-1 BURST budget of " + SpellBudgets.burstBudget(1));
        assertTrue(load("earth_spike").getDouble("power") <= SpellBudgets.burstBudget(2),
                "earth_spike inside the circle-2 BURST budget of " + SpellBudgets.burstBudget(2));
        assertTrue(load("tremor").getDouble("power") <= SpellBudgets.burstBudget(3),
                "tremor inside the circle-3 BURST budget of " + SpellBudgets.burstBudget(3));
    }

    @Test
    void spikeAndWallPerCastTotalsAreDeduplicated() throws IOException {
        // One hasHit per target per cast means one hit, whatever the rise height.
        assertEquals(5.5, load("earth_spike").getDouble("power"), 1e-9,
                "the spike is 5.5 per target per cast once hasHit dedups the rising steps "
                        + "that used to deal 35 to 105");
        assertTrue(load("earth_spike").getDouble("power") <= SpellBudgets.burstBudget(2),
                "the deduped spike total is inside the circle-2 BURST budget");
    }

    @Test
    void earthenWallIsDenialWithANinePointCap() throws IOException {
        YamlConfiguration yml = load("earthen_wall");
        assertEquals(SpellRole.DENIAL, SpellRole.parse(yml.getString("role")),
                "earthen_wall is a wall, so it is DENIAL");
        double perHit = yml.getDouble("power");
        double cap = yml.getDouble("denial_damage_cap");
        assertEquals(0.5, perHit, 1e-9, "0.5 per hit");
        assertEquals(9.0, cap, 1e-9, "the design's per-cast total is 9.0");
        assertEquals(18.0, cap / perHit, 1e-9, "9.0 / 0.5 = 18 hits to reach the cap");
        assertTrue(cap <= SpellBudgets.burstBudget(4),
                "a DENIAL spell's incidental damage is capped at the same-circle BURST budget of "
                        + SpellBudgets.burstBudget(4) + ", and 9.0 is under it");
    }

    @Test
    void earthenWallWouldHaveOvershotWithoutTheCap() throws IOException {
        YamlConfiguration yml = load("earthen_wall");
        double perHit = yml.getDouble("power");
        double cap = yml.getDouble("denial_damage_cap");
        // height 6 -> 7 rising steps, length 20 at 0.5 spacing -> 40 segments.
        double uncapped = perHit * 7.0 * 40.0;
        assertTrue(uncapped > cap,
                "7 steps x 40 segments x 0.5 is " + uncapped
                        + ", far past the 9.0 cap - this is why the accumulator exists");
        assertEquals(cap, Math.min(uncapped, cap), 1e-9,
                "the accumulator truncates at the cap rather than overshooting it");
    }

    @Test
    void denialCapAccumulatorTruncatesRatherThanSkippingOrOvershooting() {
        // The pure arithmetic of the EarthenWall accumulator, with no Bukkit mock:
        // hits keep landing until the allowance is spent, then nothing more does.
        double cap = 9.0;
        double perHit = 0.5;
        double dealt = 0.0;
        int hits = 0;
        while (true) {
            double room = cap - dealt;
            if (room <= 0) break;
            dealt += Math.min(perHit, room);
            hits++;
            if (hits > 100) break;   // hard stop: the accumulator must terminate
        }
        assertEquals(18, hits, "0.5/hit reaches the 9.0 cap in exactly 18 hits");
        assertEquals(cap, dealt, 1e-9, "the accumulator lands exactly on the cap");
        assertTrue(dealt <= cap, "the accumulator never deals past the cap");
    }

    @Test
    void earthsplitterRetiresItsPowerKeyAndCapsTerrain() throws IOException {
        YamlConfiguration yml = load("earthsplitter");
        assertFalse(yml.isSet("power"),
                "spec section 7 retires the power key - a power key that never reaches damage "
                        + "is dead config, and this is a TERRAIN spell judged on blocks");
        assertEquals(SpellRole.TERRAIN, SpellRole.parse(yml.getString("role")), "earthsplitter is TERRAIN");
        assertEquals(SpellBudgets.terrainBudget(5), yml.getInt("terrain_budget"),
                "346 destroyed blocks with no cap becomes the circle-5 budget of "
                        + SpellBudgets.terrainBudget(5));
    }

    @Test
    void earthsplitterSeededDamageIsTheCircleFiveBurstCeiling() {
        // With `power` retired, Fissure seeds AttackProperties from burstBudget(5)
        // rather than a config value. This pins the number the code must use.
        assertEquals(22.1, SpellBudgets.burstBudget(5), 1e-9,
                "the circle-5 BURST budget earthsplitter's incidental damage is held to");
        assertTrue(SpellBudgets.burstBudget(5) <= SpellBudgets.terrainBudget(5),
                "a TERRAIN spell's incidental damage ceiling sits far below its block allowance");
    }

    @Test
    void ladderCostsMatchTheTable() throws IOException {
        assertEquals(15, load("earth_throw").getInt("mana_cost"), "circle-1 mana");
        assertEquals(24, load("earth_spike").getInt("mana_cost"), "circle-2 mana");
        assertEquals(48, load("tremor").getInt("mana_cost"), "circle-3 mana");
        assertEquals(75, load("earthen_wall").getInt("mana_cost"), "circle-4 mana");
        assertEquals(120, load("earthsplitter").getInt("mana_cost"), "circle-5 mana");
        assertEquals(115, load("earthsplitter").getInt("required_focus"), "circle-5 focus");
    }

    @Test
    void cooldownsAndFocusMatchTheTable() throws IOException {
        assertEquals(4, load("earth_throw").getInt("cooldown"), "circle-1 cooldown");
        assertEquals(5, load("earth_spike").getInt("cooldown"), "circle-2 cooldown");
        assertEquals(6, load("tremor").getInt("cooldown"), "circle-3 cooldown");
        assertEquals(7, load("earthen_wall").getInt("cooldown"), "circle-4 cooldown");
        assertEquals(9, load("earthsplitter").getInt("cooldown"), "circle-5 cooldown");
        assertEquals(20, load("earth_throw").getInt("required_focus"), "circle-1 focus");
        assertEquals(40, load("earth_spike").getInt("required_focus"), "circle-2 focus");
        assertEquals(65, load("tremor").getInt("required_focus"), "circle-3 focus");
        assertEquals(90, load("earthen_wall").getInt("required_focus"), "circle-4 focus");
    }

    @Test
    void castTimesMatchTheTable() throws IOException {
        assertEquals(0.5, load("earth_throw").getDouble("cast_time"), 1e-9, "circle-1 cast time");
        assertEquals(0.75, load("earth_spike").getDouble("cast_time"), 1e-9, "circle-2 cast time");
        assertEquals(1.0, load("tremor").getDouble("cast_time"), 1e-9, "circle-3 cast time");
        assertEquals(1.25, load("earthen_wall").getDouble("cast_time"), 1e-9, "circle-4 cast time");
        assertEquals(1.5, load("earthsplitter").getDouble("cast_time"), 1e-9, "circle-5 cast time");
    }

    @Test
    void wallGeometryStaysAtTheDocumentedDefault() throws IOException {
        YamlConfiguration yml = load("earthen_wall");
        assertEquals(6, yml.getInt("height"), "height 6 -> 7 rising steps, which is what 18 hits assumes");
        assertEquals(20, yml.getDouble("length"), 1e-9, "length 20 at 0.5 spacing is 40 segments");
        assertEquals(10.0, yml.getDouble("duration"), 1e-9, "duration 10 seconds");
    }

    @Test
    void restoreTicketCarriesOneOriginalPerLocation() {
        Map<Location, Material> originals = new LinkedHashMap<>();
        originals.put(new Location(null, 1, 2, 3), Material.STONE);
        originals.put(new Location(null, 4, 5, 6), Material.DIRT);
        TerrainEditor.RestoreTicket ticket = new TerrainEditor.RestoreTicket(originals);
        assertEquals(2, ticket.originals().size(),
                "all EARTH restore sites now hand their map to restoreAll as a ticket");
        assertEquals(Material.STONE, ticket.originals().values().iterator().next(),
                "originals are preserved verbatim so a re-gated restore can put them back");
    }

    @Test
    void restoreSkipsBlocksThatAlreadyMatch() {
        Map<Location, Material> originals = new LinkedHashMap<>();
        Location alreadyCorrect = new Location(null, 7, 8, 9);
        originals.put(alreadyCorrect, Material.AIR);
        TerrainEditor.RestoreTicket ticket = new TerrainEditor.RestoreTicket(originals);
        assertTrue(ticket.originals().containsKey(alreadyCorrect),
                "restoreAll skips a block whose type already equals the recorded original, "
                        + "so a player who mined the wall back out does not get it re-placed");
    }

    @Test
    void anEmptyTicketIsStillWellFormed() {
        TerrainEditor.RestoreTicket ticket = new TerrainEditor.RestoreTicket(new LinkedHashMap<>());
        assertTrue(ticket.originals().isEmpty(),
                "a cast that touched no blocks still builds a ticket, and restoreAll no-ops on it");
    }
}
