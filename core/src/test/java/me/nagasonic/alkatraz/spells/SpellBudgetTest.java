package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec sections 12.1 and 5: every bundled spell declares a role, that role is inside
 * its circle's budget at DEFAULT options, and that role is allowed for the spell's
 * element.
 *
 * <p>Zero Bukkit mocks. getResourceAsStream plus YamlConfiguration, following
 * AlkatrazConfigPathsTest.
 *
 * <p>The roster is discovered, not hardcoded, so this test polices the 38 existing
 * spells today and all 68 once wave 5 lands. A spell with no {@code role:} key fails
 * the build.
 */
class SpellBudgetTest {

    /** The 85% focus ceiling from spec section 6.2, against the shipped max_focus table. */
    private static final int[] MAX_FOCUS = {110, 110, 120, 130, 140, 150, 160, 170, 180, 190};

    /** Spell ids that are allowed to exceed their element's blanket role rules. */
    private static final List<String> NONE_DAMAGE_LEGAL = List.of("magic_missile");

    /**
     * Spec section 9 deviations: spells whose per-cast damage is SCALED rather than
     * COUNTED, or which spend from more than one budget. Each entry names the exact
     * yml keys the wave-5 brief mandates, so the test and the implementation read the
     * same contract. Ids that have not landed yet are skipped, not vacuously passed.
     */
    private record Deviation(String id, String contract) {
    }

    private static final List<Deviation> DEVIATIONS = List.of(
            new Deviation("inferno_pilgrimage",
                    "three discrete bursts of power, declared as hit_count: 3 - never a DoT aura"),
            new Deviation("undertow",
                    "damage scales with drag distance and is capped by drag_damage_cap"),
            new Deviation("oblivion",
                    "damage scales with the target's MISSING health and is capped by burst_damage_cap"),
            new Deviation("fault_line",
                    "declares SUSTAIN and is charged against BOTH the SUSTAIN and TERRAIN budgets"),
            // The six the spell-writing briefs discovered and the deviations table records:
            // a SUSTAIN or multi-projectile spell divides its budget across applications,
            // so the per-hit ladder value would overshoot.
            new Deviation("tide_of_the_deep",
                    "power: 14.4, not the circle-9 ladder's 28.6 - 10 damage windows x 14.4 = 144.0"),
            new Deviation("cyclone_nest",
                    "power: 9.0, not the circle-7 ladder's 18.9 - 8 damage windows x 9.0 = 72.0"),
            new Deviation("stormcaller",
                    "power: 8.0, not the circle-8 ladder's 22.7 - 12 damage windows x 8.0 = 96.0"),
            new Deviation("skywrath",
                    "power: 9.6, not the circle-9 ladder's 28.6 - 15 damage windows x 9.6 = 144.0"),
            new Deviation("everdrain",
                    "hit_count: 4 - power stays on the circle-7 ladder at 18.9, so the budget fixes the duration"),
            new Deviation("umbral_volley",
                    "power: 16.0 and hit_count: 3 - 18.9 x 3 = 56.7 overshoots the circle-7 BURST budget")
    );

    private static List<String> roster;

    @BeforeAll
    static void discoverRoster() {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "the spells resource directory is not enumerable on this classpath");
        roster = BundledSpellRoster.spellIds();
    }

    /**
     * The per-cast damage total this spell may deal to ONE target at default options.
     *
     * <p>{@code power} alone is never enough: half the roster damages more than once
     * per cast. See the class javadoc for the three mechanisms.
     */
    private static double declaredTotal(String id, YamlConfiguration y) {
        return switch (id) {
            // Multi-projectile BURSTs that predate the hit_count key. Their whole volley
            // shares ONE AttackProperties and guards every hit with hasHit/hit, so the
            // fan's N blades land on N DIFFERENT targets and a single target still takes
            // exactly one power. See AirBlades.java:275 and FlamingVolley.java:247 - the
            // guard is there precisely so the per-target total is one power, not three.
            case "air_blades", "flaming_volley" -> y.getDouble("power");

            // SUSTAIN fields that predate the hit_count key: one damage window every
            // SpellAttackContext.DAMAGE_INTERVAL_TICKS for the whole field life.
            case "whirlpool" -> y.getDouble("power") * 8.0;
            case "tornado" -> y.getDouble("power") * 5.0;
            case "wind_vortex" -> y.getDouble("power") * 5.0;

            // Scaled damage models from spec section 9.
            case "undertow" -> Math.min(
                    y.getDouble("drag_damage_per_block") * y.getDouble("max_drag_distance"),
                    y.getDouble("drag_damage_cap"));
            case "oblivion" -> Math.min(
                    y.getDouble("power") * y.getDouble("missing_health_multiplier"),
                    y.getDouble("burst_damage_cap"));

            // DENIAL spells whose incidental damage is explicitly capped. fire_wall is
            // the odd one out: it declares the per-application damage on power and
            // carries no denial_damage_cap at all, so the cap falls back to power.
            case "fire_wall", "earthen_wall", "crown_of_dawn" -> y.isSet("denial_damage_cap")
                    ? y.getDouble("denial_damage_cap")
                    : y.getDouble("power");

            // radiance and shadow_realm carry NO power key (spec section 7 lists '-'),
            // so their ceiling is the circle SUSTAIN budget itself.
            case "radiance", "shadow_realm" -> SpellBudgets.sustainBudget(y.getInt("level"));

            // TERRAIN is judged on blocks; DENIAL-without-damage deals none.
            case "earthsplitter", "meteor_shower", "barrier", "wind_barrier" -> 0.0;

            default -> y.isSet("power") ? y.getDouble("power") * hitCount(y) : 0.0;
        };
    }

    /** Mandated on every BURST/SUSTAIN yml that damages one target more than once per cast. */
    private static int hitCount(YamlConfiguration y) {
        return y.isSet("hit_count") ? Math.max(1, y.getInt("hit_count")) : 1;
    }

    private static int circleOf(YamlConfiguration y) {
        return y.getInt("level");
    }

    /**
     * The ceiling a SUSTAIN spell of that circle is judged against.
     *
     * <p>The spec section 5 budget table has no SUSTAIN column at circles 0-2, which
     * {@link SpellBudgets#sustainBudget} reports as 0 - "no contract", not "zero
     * damage permitted". wind_vortex at circle 2 is the only such spell today. Falling
     * back to the same circle's BURST budget keeps the assertion live on those circles
     * instead of skipping it, and it is strictly the looser bound, so nothing a wave-5
     * spell declares there can hide behind it.
     */
    private static double sustainCeiling(int circle) {
        double sustain = SpellBudgets.sustainBudget(circle);
        return sustain > 0.0 ? sustain : SpellBudgets.burstBudget(circle);
    }

    @Test
    void everyBundledSpellDeclaresAParseableRole() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            assertNotNull(SpellRole.parse(y.getString("role")),
                    id + " must declare a role: key, or SpellBudgetTest cannot judge it");
        }
    }

    @Test
    void everySpellRoleIsAllowedForItsElement() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            Element element = Element.valueOf(y.getString("element"));
            SpellRole role = SpellRole.parse(y.getString("role"));
            assertNotNull(role, id + " must declare a parseable role");
            boolean allowed = SpellBudgets.isAllowed(element, role)
                    || SpellBudgets.isGrandfatheredBurst(id, element, role);
            assertTrue(allowed, id + " declares " + role + " but " + element + " may not use it");
        }
    }

    @Test
    void burstSpellsStayInsideTheirCircleBudget() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            if (SpellRole.BURST != SpellRole.parse(y.getString("role"))) continue;
            int circle = circleOf(y);
            double total = declaredTotal(id, y);
            assertTrue(total <= SpellBudgets.burstBudget(circle),
                    id + " deals " + total + " per cast, over the circle-" + circle
                            + " BURST budget of " + SpellBudgets.burstBudget(circle));
        }
    }

    @Test
    void sustainSpellsStayInsideTheirCircleBudget() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            if (SpellRole.SUSTAIN != SpellRole.parse(y.getString("role"))) continue;
            int circle = circleOf(y);
            double total = declaredTotal(id, y);
            double ceiling = sustainCeiling(circle);
            assertTrue(total <= ceiling,
                    id + " deals " + total + " over its life, over the circle-" + circle
                            + " SUSTAIN ceiling of " + ceiling);
        }
    }

    @Test
    void terrainSpellsCarryTheCircleTerrainBudget() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            if (SpellRole.TERRAIN != SpellRole.parse(y.getString("role"))) continue;
            int circle = circleOf(y);
            assertTrue(y.isSet("terrain_budget"),
                    id + " is TERRAIN and must declare terrain_budget, or it has no block cap at all");
            assertEquals(SpellBudgets.terrainBudget(circle), y.getInt("terrain_budget"),
                    id + " spends from the circle-" + circle + " TERRAIN budget");
        }
    }

    @Test
    void denialSpellsCapIncidentalDamageAtTheCircleBurstBudget() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            if (SpellRole.DENIAL != SpellRole.parse(y.getString("role"))) continue;
            int circle = circleOf(y);
            double cap = SpellBudgets.burstBudget(circle);
            // Spec section 4.2 caps a DENIAL spell's incidental damage at the same-circle
            // BURST budget. The spell declares that damage either as an explicit
            // denial_damage_cap (earthen_wall) or as the per-application power key
            // (fire_wall); either way it is judged against the same ceiling.
            String key = y.isSet("denial_damage_cap") ? "denial_damage_cap" : "power";
            if (!y.isSet(key)) {
                // A DENIAL spell that deals no incidental damage carries no damage key
                // at all - barrier and wind_barrier are both like this.
                assertFalse(y.isSet("power") && y.isSet("denial_damage_cap"),
                        id + " is DENIAL, deals no damage, and so must carry neither power "
                                + "nor denial_damage_cap");
                continue;
            }
            assertTrue(y.getDouble(key) > 0.0,
                    id + " declares " + key + ", so it must be positive");
            assertTrue(y.getDouble(key) <= cap,
                    "spec section 4.2 caps a DENIAL spell's incidental damage at the same-circle "
                            + "BURST budget; " + id + " declares " + key + " = "
                            + y.getDouble(key) + " against a cap of " + cap);
        }
    }

    @Test
    void utilitySpellsCarryNoDamageKey() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            if (SpellRole.UTILITY != SpellRole.parse(y.getString("role"))) continue;
            assertFalse(y.isSet("power"),
                    id + " is UTILITY - judged on nothing - so a power key on it is dead config");
        }
    }

    @Test
    void singlePowerKeyNeverExceedsItsCircleBurstBudget() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            if (!y.isSet("power")) continue;
            int circle = circleOf(y);
            assertTrue(y.getDouble("power") <= SpellBudgets.burstBudget(circle),
                    id + "'s single power key is " + y.getDouble("power")
                            + ", already over the circle-" + circle + " BURST budget before any "
                            + "hit count is applied");
        }
    }

    @Test
    void legacyMultiHitSpellsLandExactlyOnTheirDesignTotals() throws IOException {
        assertEquals(4.2, declaredTotal("air_blades", BundledSpellRoster.load("air_blades")), 1e-9,
                "one power to one target: the fan's three blades share one AttackProperties and "
                        + "hasHit guard, so they land on three different targets");
        assertEquals(4.0, declaredTotal("flaming_volley", BundledSpellRoster.load("flaming_volley")), 1e-9,
                "4.0 per arrow, and one shared AttackProperties means a target takes it once, not three times");
        assertEquals(22.0, declaredTotal("whirlpool", BundledSpellRoster.load("whirlpool")), 1e-9,
                "2.75 per hit x 8 hits = 22, exactly the circle-4 SUSTAIN budget");
        assertEquals(36.0, declaredTotal("tornado", BundledSpellRoster.load("tornado")), 1e-9,
                "7.2 per hit x 5 hits = 36, exactly the circle-5 SUSTAIN budget");
        assertEquals(2.5, declaredTotal("wind_vortex", BundledSpellRoster.load("wind_vortex")), 1e-9,
                "0.5 per hit x 5 hits; circle 2 has no SUSTAIN budget of its own");
    }

    @Test
    void documentedDeviationsFromSpecSectionNine() throws IOException {
        boolean anyLanded = false;
        for (Deviation deviation : DEVIATIONS) {
            if (!BundledSpellRoster.has(deviation.id())) continue;
            anyLanded = true;
            YamlConfiguration y = BundledSpellRoster.load(deviation.id());
            SpellRole role = SpellRole.parse(y.getString("role"));
            assertNotNull(role, deviation.id() + " must declare a role");

            switch (deviation.id()) {
                case "inferno_pilgrimage" -> {
                    assertEquals(SpellRole.BURST, role,
                            "three discrete bursts is a BURST, not a SUSTAIN - fire never sustains");
                    assertEquals(3, hitCount(y),
                            "inferno_pilgrimage must declare hit_count: 3 so its total is assertable");
                    assertEquals(48.0, declaredTotal(deviation.id(), y), 1e-9,
                            "3 pulses x 16 = 48, exactly the circle-7 BURST budget");
                }
                case "undertow" -> {
                    assertTrue(y.isSet("drag_damage_per_block") && y.isSet("max_drag_distance")
                                    && y.isSet("drag_damage_cap"),
                            "undertow's damage scales with drag distance, so all three keys must exist");
                    assertEquals(34.1, y.getDouble("drag_damage_cap"), 1e-9,
                            "the cap is the circle-6 BURST budget");
                    assertEquals(SpellBudgets.burstBudget(6), y.getDouble("drag_damage_cap"), 1e-9,
                            "and it traces back to the ladder rather than being invented");
                    assertTrue(declaredTotal("undertow", y) <= SpellBudgets.burstBudget(6),
                            "the dragged distance can never push undertow over its budget");
                }
                case "oblivion" -> {
                    assertTrue(y.isSet("missing_health_multiplier") && y.isSet("burst_damage_cap"),
                            "oblivion scales with missing health, so both keys must exist");
                    assertEquals(90.0, y.getDouble("burst_damage_cap"), 1e-9,
                            "the cap is the circle-9 BURST budget");
                    assertTrue(declaredTotal("oblivion", y) <= SpellBudgets.burstBudget(9),
                            "a full-health target deals base power and a 1-health target deals the cap");
                }
                case "fault_line" -> {
                    assertEquals(SpellRole.SUSTAIN, role,
                            "fault_line declares SUSTAIN - the crack keeps acting after the cast");
                    assertTrue(y.isSet("terrain_budget"),
                            "fault_line spends from the TERRAIN budget as well, so it declares one");
                    assertTrue(y.getInt("terrain_budget") <= SpellBudgets.terrainBudget(7),
                            "its terrain spend is charged against the circle-7 TERRAIN budget of "
                                    + SpellBudgets.terrainBudget(7));
                    assertTrue(declaredTotal("fault_line", y) <= sustainCeiling(7),
                            "and its damage against the circle-7 SUSTAIN ceiling of "
                                    + sustainCeiling(7));
                }
                // The four SUSTAIN fields whose per-hit power is the ladder value divided by
                // the number of damage windows. Each asserts BOTH that the declared value is
                // the divided one and that the product still lands inside the budget, so a
                // later "harmonisation" back to the ladder value fails the build.
                case "tide_of_the_deep" -> {
                    assertEquals(14.4, y.getDouble("power"), 1e-9,
                            "tide_of_the_deep's power is the circle-9 ladder's 28.6 divided by its 10 windows");
                    assertEquals(10, hitCount(y), "one damage window per second of the 10 s ramp");
                    assertEquals(SpellBudgets.sustainBudget(9), declaredTotal("tide_of_the_deep", y), 1e-9,
                            "14.4 x 10 = 144.0, exactly the circle-9 SUSTAIN budget");
                }
                case "cyclone_nest" -> {
                    assertEquals(9.0, y.getDouble("power"), 1e-9,
                            "cyclone_nest's power is the circle-7 ladder's 18.9 halved for 8 windows");
                    assertEquals(8, hitCount(y), "one tornado's life is 8 damage windows");
                    assertTrue(declaredTotal("cyclone_nest", y) <= SpellBudgets.sustainBudget(7),
                            "9.0 x 8 = 72.0 inside the circle-7 SUSTAIN budget of "
                                    + SpellBudgets.sustainBudget(7) + "; the three nests SHARE that budget");
                }
                case "stormcaller" -> {
                    assertEquals(8.0, y.getDouble("power"), 1e-9,
                            "stormcaller's power is the circle-8 ladder's 22.7 divided by its 12 windows");
                    assertEquals(12, hitCount(y), "one damage window per second of the 12 s storm");
                    assertTrue(declaredTotal("stormcaller", y) <= SpellBudgets.sustainBudget(8),
                            "8.0 x 12 = 96.0 inside the circle-8 SUSTAIN budget of "
                                    + SpellBudgets.sustainBudget(8));
                }
                case "skywrath" -> {
                    assertEquals(9.6, y.getDouble("power"), 1e-9,
                            "skywrath's power is the circle-9 ladder's 28.6 divided by its 15 windows");
                    assertEquals(15, hitCount(y), "one damage window per second of the 15 s storm");
                    assertEquals(SpellBudgets.sustainBudget(9), declaredTotal("skywrath", y), 1e-9,
                            "9.6 x 15 = 144.0, exactly the circle-9 SUSTAIN budget");
                }
                case "everdrain" -> {
                    // The other axis of the same constraint: power stays on the ladder, so the
                    // budget fixes hit_count instead. A fifth window would be 18.9 x 5 = 94.5.
                    assertEquals(18.9, y.getDouble("power"), 1e-9,
                            "everdrain keeps the circle-7 ladder power; only its life is reduced");
                    assertEquals(4, hitCount(y),
                            "76 / 18.9 = 4.02, so the budget allows four damage windows and not five");
                    assertEquals(y.getDouble("duration_seconds"), 4.0, 1e-9,
                            "hit_count must equal duration_seconds per the SUSTAIN cadence rule");
                    assertTrue(declaredTotal("everdrain", y) <= SpellBudgets.sustainBudget(7),
                            "18.9 x 4 = 75.6 inside the circle-7 SUSTAIN budget of "
                                    + SpellBudgets.sustainBudget(7));
                }
                case "umbral_volley" -> {
                    assertEquals(16.0, y.getDouble("power"), 1e-9,
                            "the circle-7 ladder's 18.9 x 3 darts = 56.7 overshoots burstBudget(7)");
                    assertEquals(3, hitCount(y), "the default dart count; one target can take all three");
                    assertEquals(SpellBudgets.burstBudget(7), declaredTotal("umbral_volley", y), 1e-9,
                            "16.0 x 3 = 48.0, exactly the circle-7 BURST budget");
                }
                default -> throw new IllegalStateException("unhandled deviation " + deviation.id());
            }
        }
        Assumptions.assumeTrue(anyLanded,
                "the ten spec section 9 deviation spells all arrive in wave 5");
    }

    @Test
    void focusCapsStayAtOrBelowEightyFivePercentOfMaxFocus() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            int circle = Math.max(0, Math.min(9, circleOf(y)));
            if (circle < 6) continue;   // spec section 6.2 caps circles 6-9 only
            double ceiling = 0.85 * MAX_FOCUS[circle];
            assertTrue(y.getDouble("required_focus") <= ceiling,
                    id + " requires " + y.getDouble("required_focus") + " focus, over the 85% ceiling of "
                            + ceiling + " at circle " + circle
                            + " - one full-health hit would make it uncastable");
        }
    }

    @Test
    void magicMissileIsTheOnlyNoneSpellWithADamageRole() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            if (Element.NONE != Element.valueOf(y.getString("element"))) continue;
            SpellRole role = SpellRole.parse(y.getString("role"));
            if (SpellRole.BURST != role && SpellRole.SUSTAIN != role) continue;
            assertTrue(NONE_DAMAGE_LEGAL.contains(id)
                            && SpellBudgets.isGrandfatheredBurst(id, Element.NONE, role),
                    id + " is a NONE " + role + "; none never deals elemental damage and "
                            + "magic_missile at circle 0 is the sole grandfathered exception");
        }
    }
}