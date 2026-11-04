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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions, so no Bukkit mock is needed: getResourceAsStream plus
 * YamlConfiguration, following {@code mobs/AlkatrazConfigPathsTest}.
 */
class TideOfTheDeepTest {

    private static YamlConfiguration yml() throws IOException {
        try (InputStream in = TideOfTheDeepTest.class.getResourceAsStream("/spells/tide_of_the_deep.yml")) {
            assertNotNull(in, "missing bundled resource /spells/tide_of_the_deep.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /**
     * The windows the yml's own cadence opens, cross-checked against {@code hit_count}. A
     * hand-edited copy that moves one without the other would under-report the spell to
     * {@code SpellBudgetTest}, so the two are asserted to agree rather than trusted.
     */
    private static int hitCount(YamlConfiguration y) {
        int windows = (int) Math.round(y.getDouble("duration_seconds") * 20.0
                / y.getInt("damage_interval_ticks"));
        assertEquals(y.getInt("hit_count"), windows,
                "hit_count must equal the number of damage windows, or SpellBudgetTest under-reports the spell");
        return windows;
    }

    @Test
    void roleIsParseableAndAllowedForWater() throws IOException {
        YamlConfiguration y = yml();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "tide_of_the_deep must declare a parseable role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.SUSTAIN, role,
                "the tide keeps acting for as long as the caster holds it - that is SUSTAIN");
        assertTrue(SpellBudgets.isAllowed(Element.WATER, role),
                "WATER owns SUSTAIN, DENIAL and BURST, so SUSTAIN must be allowed");
    }

    @Test
    void declaredDamageLandsExactlyOnTheCircleNineSustainBudget() throws IOException {
        YamlConfiguration y = yml();
        double total = y.getDouble("power") * hitCount(y);
        assertEquals(14.0, y.getDouble("power"), 1e-9, "14.0 per window x 20 windows = 280.0");
        assertEquals(20, hitCount(y), "60s at one window every 3s is 20 windows");
        assertEquals(280.0, total, 1e-9);
        assertEquals(SpellBudgets.sustainBudget(9), total, 1e-9);
        assertFalse(y.isSet("tide_ramp"),
                "the escalating ramp is replaced by a flat power over 20 windows");
    }

    @Test
    void theCasterNoLongerHasToStandInTheTide() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(8.0, y.getDouble("mend_radius"), 1e-9);
        assertEquals(0.5, y.getDouble("heal_ratio"), 1e-9);
        assertEquals(0, y.getInt("cooldown"));
        assertEquals(80, y.getInt("upkeep_mana_per_second"));
        assertEquals(60.0, y.getDouble("duration_seconds"), 1e-9);
        assertEquals(9, y.getInt("level"));
        assertEquals("LSSSR", y.getString("code"));
        assertEquals(4144, y.getInt("gui_custom_model_data"));
        assertEquals(600, y.getInt("mana_cost"));
        assertEquals(161, y.getInt("required_focus"));
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9);
    }

    @Test
    void ladderValuesMatchTheCircleNineTable() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(600, y.getInt("mana_cost"), "spec section 6 ladder, circle 9");
        assertEquals(0, y.getInt("cooldown"),
                "a toggle is paid for by upkeep_mana_per_second; a cooldown would block the second cast "
                        + "that turns it back off");
        assertEquals(161, y.getInt("required_focus"), "spec section 6 ladder, circle 9");
        // SpellBudgets.castTimeRange collapses circle 9 to the single point 40 ticks, because
        // CAST_MIN_TICKS and CAST_MAX_TICKS both hold 40 at index 9. The withdrawn section 6
        // ladder column said 3.0; 2.0 is the only value the collapsed window still admits.
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 9 to the single point 40 ticks, so 2.0 is "
                        + "the only legal circle-9 cast time");
        assertTrue(SpellBudgets.castTimeRange(9).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 9 allows " + SpellBudgets.castTimeRange(9).minTicks() + ".."
                        + SpellBudgets.castTimeRange(9).maxTicks() + " ticks");

        // The tide declares no channel and no wind_up_duration, so the collapsed cast time is
        // not added to anything: hit_count and the upkeep are budgeted against
        // duration_seconds, which is why the cast time moving does not move the 20 windows.
        assertFalse(y.isSet("wind_up_duration") || y.isSet("channel_seconds"),
                "nothing is stacked on top of cast_time, so the twenty windows of duration_seconds "
                        + "are unaffected by it");
        assertFalse(SpellBudgets.isPaidOverWindUp(9, (int) Math.round(y.getDouble("cast_time") * 20.0)),
                "a 40-tick circle-9 cast is exactly the wind-up, never over it");
    }

    @Test
    void levelIsCircleNine() throws IOException {
        assertEquals(9, yml().getInt("level"),
                "tide_of_the_deep is a circle-9 spell; Spell.loadCommonConfig reads required_circle from level");
    }

    /**
     * The cast is a three-way branch, and only one of the three outcomes shows the BossBar.
     * Reading the source is the cheap way to pin that down: driving it needs a live server,
     * and the thing worth protecting - that a foreign sustain cannot steal the slot, and that
     * the toggle-off refund matches what {@code Spell.cast} charged - is invisible to a
     * Bukkit-less test otherwise.
     */
    @Test
    void theToggleBranchesOnAllThreeStatesAndTheToggleOffRefundsWhatWasCharged() throws IOException {
        String source = sourceOf("TideOfTheDeep");
        String castAction = source.substring(source.indexOf("public void castAction("),
                source.indexOf("private boolean tideTick("));

        assertTrue(castAction.contains("SustainEffect.toggle(caster, upkeepManaPerSecond, this,"),
                "the tide must pass itself as owner, or a cast with a reloaded spell instance cannot "
                        + "close its own tide and every cast reads as BLOCKED");
        assertTrue(castAction.contains("SustainEffect.Toggle.BLOCKED")
                        && castAction.contains("SustainEffect.Toggle.CLOSED"),
                "both non-opening outcomes must be handled by name, not collapsed into 'not opened'");

        int blocked = castAction.indexOf("SustainEffect.Toggle.BLOCKED");
        int closed = castAction.indexOf("SustainEffect.Toggle.CLOSED");
        int shown = castAction.indexOf("bar.addPlayer(caster)");
        assertTrue(blocked < closed && closed < shown,
                "OPENED is the fall-through, so both BLOCKED and CLOSED must return before the bar is shown");

        assertTrue(castAction.contains("\"spells.sustain.blocked\""),
                "a BLOCKED cast changed nothing and must say so instead of looking like a silent no-op");

        assertTrue(castAction.contains("StatUtils.addMana(caster, getModifiedManaCost(caster))"),
                "Spell.cast charged getModifiedManaCost(p) (Spell.java:137,189), so the toggle-off refund "
                        + "must be the same number or a mana_cost modifier mints mana for free");
        assertFalse(source.contains("addMana(caster, getCost())"),
                "the bare getCost() is the un-modified 600 and is the bug this refund replaces");
    }

    /**
     * Reads one spell class from disk. The candidates cover being run from the module
     * directory (surefire's working directory) and from the repository root.
     */
    private static String sourceOf(String simpleName) throws IOException {
        String relative = "src/main/java/me/nagasonic/alkatraz/spells/implementation/" + simpleName + ".java";
        Path[] candidates = {
                Paths.get(relative),
                Paths.get("core").resolve(relative),
                Paths.get("..").resolve("core").resolve(relative),
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("could not locate " + simpleName + ".java from " + Path.of("").toAbsolutePath());
    }
}