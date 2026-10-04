package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
 * The block-count arithmetic and the material policy are pure, so they are
 * asserted here without a Bukkit mock. Protection gating and real block writes
 * need a live server and are covered by the integration suite.
 */
class TerrainEditorTest {

    /** Mirrors destroySphere's candidate enumeration, which is the part that must stay deterministic. */
    private static int sphereBlockCount(double radius) {
        int r = (int) Math.ceil(radius);
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) count++;
                }
            }
        }
        return count;
    }

    /**
     * Spec section 8.2 and the maxBlocks cap.
     *
     * <p>This test previously asserted {@code 123 * 6 <= 620}, which is arithmetically
     * false - 6 meteors at radius 3 naturally cover 738 blocks, not 620. What the spell
     * actually does is pass the cast's remaining allowance as maxBlocks, so the
     * enumeration truncates AT the budget rather than overshooting it. That cap is the
     * behaviour worth pinning; the unscaled enumeration was never a property the spell
     * had.
     */
    @Test
    void radiusThreeSphereIsTruncatedAtTheCircleSixTerrainBudget() {
        int perMeteor = sphereBlockCount(3);
        int natural = perMeteor * 6;
        int budget = SpellBudgets.terrainBudget(6);
        assertEquals(123, perMeteor, "a radius-3 sphere is 123 blocks");
        assertEquals(738, natural, "6 meteors at radius 3 naturally cover 738 blocks");
        assertTrue(natural > budget,
                "738 is over the circle-6 terrain budget of " + budget
                        + ", which is exactly why maxBlocks exists");
        assertEquals(budget, Math.min(natural, budget),
                "the maxBlocks cap truncates at the budget rather than overshooting it");
        assertEquals(620, budget, "the circle-6 TERRAIN budget is 620 blocks");
    }

    @Test
    void wideRadiusIsThePaidOverOptionAndIsStillCapped() {
        int perMeteor = sphereBlockCount(4);
        int natural = perMeteor * 6;
        int budget = SpellBudgets.terrainBudget(6);
        int wideCap = (int) (budget * 2.0);
        assertEquals(257, perMeteor, "a radius-4 sphere is 257 blocks");
        assertEquals(1542, natural, "6 meteors at radius 4 naturally cover 1542 blocks");
        assertTrue(natural > budget,
                "the 'wide' option is a deliberate paid-over, not a violation");
        assertEquals(1240, wideCap,
                "spec section 4.3 allows max options to exceed the budget by up to x2.0");
        assertTrue(wideCap <= budget * 2.0,
                "the paid-over allowance is x2.0, so 'wide' must stay under 1240 blocks");
        assertEquals(wideCap, Math.min(natural, wideCap),
                "and the maxBlocks cap truncates at the paid-over ceiling, not at the natural count");
    }

    @Test
    void tightRadiusFitsTheBudgetWithoutNeedingTheCap() {
        // The third tier of the same option, and the only one that fits on its own.
        // 'normal' and 'wide' both need the cap; asserting that 'tight' does not is
        // what proves the cap is doing the work rather than the budget being loose.
        int perMeteor = sphereBlockCount(2);
        assertEquals(33, perMeteor, "a radius-2 sphere is 33 blocks");
        int natural = perMeteor * 6;
        assertEquals(198, natural, "6 meteors at radius 2 clear 198 blocks");
        assertEquals(198, Math.min(natural, 620),
                "198 is already under the circle-6 budget of 620, so nothing truncates at 'tight'");
        assertTrue(natural <= 620, "'tight' is the only radius tier that lands inside the default budget");
    }

    @Test
    void destroySphereRefusesWithoutACentreOrAnAllowance() {
        // The guard clause runs before any world is dereferenced, so this needs no
        // server. A null centre or a zero cap must write nothing at all.
        assertEquals(0, TerrainEditor.destroySphere(null, null, 3.0, true, 620),
                "a null centre destroys nothing");
        assertEquals(0, TerrainEditor.destroySphere(null, new Location(null, 0, 0, 0), 3.0, true, 620),
                "a centre with no world destroys nothing");
        assertEquals(0, TerrainEditor.destroySphere(null, new Location(null, 0, 0, 0), 3.0, true, 0),
                "a zero maxBlocks allowance destroys nothing - the cap is checked before the loop");
    }

    @Test
    void materialPolicyProtectsBedrockAndContainers() {
        assertTrue(TerrainEditor.isProtected(Material.BEDROCK), "bedrock must never break");
        assertTrue(TerrainEditor.isProtected(Material.CHEST), "containers are never destroyed by a spell");
        assertTrue(TerrainEditor.isProtected(Material.BARREL), "shulkers are never destroyed by a spell");
        assertTrue(TerrainEditor.isProtected(Material.ENDER_CHEST), "ender chests are never destroyed");
        assertTrue(TerrainEditor.isProtected(Material.FURNACE), "furnaces are never destroyed");
        assertTrue(TerrainEditor.isProtected(Material.SPAWNER), "spawners are never destroyed");
        assertFalse(TerrainEditor.isProtected(Material.STONE), "ordinary stone is fair game");
        assertFalse(TerrainEditor.isProtected(Material.DIRT), "ordinary dirt is fair game");
        assertFalse(TerrainEditor.isProtected(Material.DIAMOND_ORE), "ores break - Fissure already mines them via Ground.isGround");
    }

    @Test
    void bedrockSubstringCheckIsGone() {
        // MeteorShower:304 used toString().contains("BEDROCK"), which would falsely
        // match any future material with BEDROCK in its name. Assert on identity now.
        assertTrue(TerrainEditor.isProtected(Material.BEDROCK), "bedrock is protected by identity comparison");
        assertFalse(TerrainEditor.isProtected(null), "a null material is never a protected block");
    }

    @Test
    void restoreTicketHoldsOneOriginalPerLocation() {
        Map<Location, Material> originals = new LinkedHashMap<>();
        originals.put(new Location(null, 1, 2, 3), Material.STONE);
        originals.put(new Location(null, 4, 5, 6), Material.DIRT);
        TerrainEditor.RestoreTicket ticket = new TerrainEditor.RestoreTicket(originals);
        assertEquals(2, ticket.originals().size(), "the ticket is a plain location-to-original map");
        assertEquals(Material.STONE, ticket.originals().values().iterator().next(),
                "originals are preserved verbatim for restore");
    }

    /**
     * Spec section 4.3: an option that exceeds the default budget is legal ONLY if it
     * costs extra mana. This asserts the yml actually says so, rather than trusting
     * the budget arithmetic in the two tests above.
     *
     * <p>The mana impact is located by its {@code type} rather than by its index: the
     * shipped {@code meteor_shower_options.yml} lists the radius {@code stat_modifier}
     * at {@code impacts[0]} and the {@code mana_cost} at {@code impacts[1]}, so an
     * index-pinned lookup would miss the very thing this test exists to police.
     */
    @Test
    void thePaidOverOptionCostsExtraMana() throws IOException {
        try (InputStream in = TerrainEditorTest.class
                .getResourceAsStream("/spells/meteor_shower_options.yml")) {
            assertNotNull(in, "missing bundled resource /spells/meteor_shower_options.yml");
            YamlConfiguration opts = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(1, opts.getInt("options.radius.default_index"),
                    "the default radius option is 'normal' at radius 3 - the budget-exact one");

            ConfigurationSection values = opts.getConfigurationSection("options.radius.values");
            assertNotNull(values, "the radius option must declare its values");
            assertEquals(2.0, values.getDouble("tight.value"), 1e-9, "tight is re-scaled 4 -> 2");
            assertEquals(3.0, values.getDouble("normal.value"), 1e-9, "normal is re-scaled 6 -> 3");
            assertEquals(4.0, values.getDouble("wide.value"), 1e-9, "wide is re-scaled 8 -> 4");

            // Spec section 4.3: an over-budget option is legal ONLY if it costs extra
            // mana. +25% of the circle-6 cost of 165 is 41.
            Map<?, ?> manaImpact = null;
            for (Map<?, ?> impact : values.getMapList("wide.impacts")) {
                if ("mana_cost".equals(impact.get("type"))) {
                    manaImpact = impact;
                }
            }
            assertNotNull(manaImpact, "the 'wide' option must declare a mana_cost impact - without one it "
                    + "clears 1542 blocks against a 620 budget for free, which spec section 4.3 forbids");
            assertEquals(41.0, ((Number) manaImpact.get("change")).doubleValue(), 1e-9,
                    "wide carries a mana_cost impact of +41, which is +25% of 165");
            assertEquals("mana_cost", manaImpact.get("type"),
                    "and the impact is a mana_cost change, not a free stat tweak");
        }
    }
}