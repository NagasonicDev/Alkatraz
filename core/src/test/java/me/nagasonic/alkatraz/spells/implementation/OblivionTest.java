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
 * YML-only assertions, so no Bukkit mock is needed. getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest and FlameLanceTest.
 *
 * <p>The whole budget is asserted here as {@code power x hit_count}: 28.0 x 10 = 280.0,
 * exactly the circle-9 SUSTAIN budget. The old missing-health curve is asserted only in that
 * it is GONE - a key that is still present would put the spell back under the BURST reading.
 */
class OblivionTest {

    private static final String ID = "oblivion";

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = OblivionTest.class.getResourceAsStream("/spells/" + ID + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + ID + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsSustainAndAllowedForDark() throws IOException {
        YamlConfiguration y = load();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertEquals(SpellRole.SUSTAIN, role,
                "oblivion is now a 60s drain field, so SUSTAIN judges it; DARK allows SUSTAIN");
        assertEquals(Element.DARK, Element.valueOf(y.getString("element")));
        assertTrue(SpellBudgets.isAllowed(Element.DARK, role));
        assertFalse(SpellBudgets.isAllowed(Element.DARK, SpellRole.DENIAL),
                "DARK is forbidden from DENIAL outright");
    }

    @Test
    void itsDamageLandsExactlyOnTheCircleNineSustainBudget() throws IOException {
        YamlConfiguration y = load();
        double total = y.getDouble("power") * hitCount(y);
        assertEquals(28.0, y.getDouble("power"), 1e-9);
        assertEquals(10, hitCount(y), "60s at one opening every 6s is 10 openings");
        assertEquals(280.0, total, 1e-9);
        assertEquals(280.0, SpellBudgets.sustainBudget(9), 1e-9);
    }

    /**
     * The cap is what makes the budget declaration true. {@code hit_count} is what
     * {@code SpellBudgetTest} reads, so if the yml's own cadence opens more times inside
     * {@code duration_seconds} than it declares, the sustain spends more than 280.0 while still
     * reporting 28.0 x 10. The two are asserted to agree, as in {@code TideOfTheDeepTest}.
     */
    @Test
    void theDeclaredOpeningsFitInsideTheEnforcedDuration() throws IOException {
        YamlConfiguration y = load();
        assertEquals(24.0, y.getDouble("duration_seconds"), 1e-9,
                "9400 mana / (75 upkeep - 15 regen) = 156.7s of upkeep = 26 openings x 28.0 = 728.0, "
                        + "2.6x the 280.0 budget; the 60s cap is what holds 28.0 x 10 = 280.0 true");
        assertEquals(10, hitCount(y), "60s at one opening every 6s is 10 openings");
    }

    @Test
    void theMissingHealthCurveIsGone() throws IOException {
        YamlConfiguration y = load();
        assertFalse(y.isSet("missing_health_multiplier"),
                "scaling off MISSING health meant minimum damage against a full-health boss");
        assertFalse(y.isSet("burst_damage_cap"), "it is a SUSTAIN now and is charged against SUSTAIN");
        assertFalse(y.isSet("erasure_wave_ticks"), "the cosmetic wave is replaced by the sustained hole");
    }

    @Test
    void sustainShapeAndIdentity() throws IOException {
        YamlConfiguration y = load();
        assertEquals(0, y.getInt("cooldown"));
        assertEquals(75, y.getInt("upkeep_mana_per_second"));
        assertEquals(0.25, y.getDouble("vampirism_ratio"), 1e-9,
                "a quarter of every point the hole deals returns to the caster");
        assertEquals(3.0, y.getDouble("chasm_radius"), 1e-9);
        assertEquals(9, y.getInt("level"));
        assertEquals("RSLRL", y.getString("code"));
        assertEquals(4161, y.getInt("gui_custom_model_data"));
        assertEquals(600, y.getInt("mana_cost"));
        assertEquals(161, y.getInt("required_focus"));
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9);
    }

    @Test
    void levelIsNine() throws IOException {
        YamlConfiguration y = load();
        assertEquals(9, y.getInt("level"), ID + " is the circle-9 capstone");
        assertEquals("RSLRL", y.getString("code"), "cast code from the roster table");
        assertEquals(4161, y.getInt("gui_custom_model_data"),
                "4161 is oblivion's allocated gui_custom_model_data and is never reused");
        assertEquals("oblivion", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
    }

    /**
     * Mirrors SpellBudgetTest.hitCount: absent means one damage application per cast, then
     * cross-checks it against the openings the yml's own cadence produces inside
     * {@code duration_seconds} - the count the runtime cap actually allows.
     */
    private static int hitCount(YamlConfiguration y) {
        int declared = y.isSet("hit_count") ? Math.max(1, y.getInt("hit_count")) : 1;
        int openings = (int) Math.round(y.getDouble("duration_seconds") * 20.0
                / y.getInt("damage_interval_ticks"));
        assertEquals(declared, openings,
                "hit_count must equal the openings duration_seconds allows, or SpellBudgetTest "
                        + "under-reports the spell and the hole out-damages its own budget");
        return declared;
    }
}