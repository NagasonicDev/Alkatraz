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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions, so no Bukkit mock is needed: getResourceAsStream plus
 * YamlConfiguration, following {@code mobs/AlkatrazConfigPathsTest}.
 */
class ThousandWavesTest {

    private static YamlConfiguration yml() throws IOException {
        try (InputStream in = ThousandWavesTest.class.getResourceAsStream("/spells/thousand_waves.yml")) {
            assertNotNull(in, "missing bundled resource /spells/thousand_waves.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForWater() throws IOException {
        YamlConfiguration y = yml();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "thousand_waves must declare a parseable role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.BURST, role,
                "every damage event is a discrete crest; there is no repeating tick, so this is a BURST");
        assertTrue(SpellBudgets.isAllowed(Element.WATER, role),
                "WATER owns SUSTAIN, DENIAL and BURST, so BURST must be allowed");
    }

    @Test
    void powerTimesHitCountStaysInsideTheCircleEightBurstBudget() throws IOException {
        YamlConfiguration y = yml();
        double declared = y.getDouble("power") * y.getInt("hit_count");

        assertEquals(22.7, y.getDouble("power"), 1e-9,
                "spec section 6 ladder, circle 8, verbatim - this spell has no deviations-table row");
        assertEquals(1, y.getInt("hit_count"),
                "a body stands in one lane and one crest sweeps that lane, so exactly one hit lands");
        assertEquals(22.7, declared, 1e-9, "22.7 x 1");
        assertTrue(declared <= SpellBudgets.burstBudget(8),
                "thousand_waves declares " + declared + " per cast against a budget of "
                        + SpellBudgets.burstBudget(8));
    }

    @Test
    void ladderValuesMatchTheCircleEightTable() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(450, y.getInt("mana_cost"), "spec section 6 ladder, circle 8");
        assertEquals(16, y.getInt("cooldown"), "spec section 6 ladder, circle 8");
        assertEquals(153, y.getInt("required_focus"), "spec section 6 ladder, circle 8");
        // SpellBudgets.castTimeRange collapses circle 8 to the single point 30 ticks, because
        // CAST_MIN_TICKS and CAST_MAX_TICKS both hold 30 at index 8. The withdrawn section 6
        // ladder column said 2.25; 1.5 is the only value the collapsed window still admits.
        assertEquals(1.5, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 8 to the single point 30 ticks, so 1.5 is "
                        + "the only legal circle-8 cast time");
        assertTrue(SpellBudgets.castTimeRange(8).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 8 allows " + SpellBudgets.castTimeRange(8).minTicks() + ".."
                        + SpellBudgets.castTimeRange(8).maxTicks() + " ticks");

        // cast_time and channel_seconds are declared SEPARATELY, exactly as sunfire_barrage,
        // warp and skeletal_knights do, so the collapsed cast time does not shorten the
        // channel. 2.0s of channel is 40 ticks and circle 8's paid-over ceiling is
        // 2 x windUpTicks(8) = 60, so this channel is within contract rather than a
        // declared paid-over - which is why it needs no written reason.
        assertEquals(2.0, y.getDouble("channel_seconds"), 1e-9,
                "the channel is the spell's own telegraph, declared apart from cast_time");
        assertFalse(SpellBudgets.isPaidOverWindUp(8, (int) Math.round(y.getDouble("channel_seconds") * 20.0)),
                "a 40-tick channel is under circle 8's 60-tick paid-over ceiling, so it is within "
                        + "contract and not a paid-over cast");
    }

    @Test
    void levelIsCircleEight() throws IOException {
        assertEquals(8, yml().getInt("level"),
                "thousand_waves is a circle-8 spell; Spell.loadCommonConfig reads required_circle from level");
    }

    /**
     * The proof behind {@code hit_count: 1}. Every identity the fan's geometry rests on,
     * asserted rather than assumed: the sectors close the circle, a lane is narrower than
     * its sector, one crest fills each lane exactly once, and the escalation still fits the
     * budget with all eight multipliers summed.
     */
    @Test
    void theFanTilesTheCircleExactlyOncePerTarget() throws IOException {
        YamlConfiguration y = yml();
        double range = y.getDouble("wave_range");
        double width = y.getDouble("wave_width");
        double sector = y.getDouble("wave_sector_degrees");
        int count = y.getInt("wave_count");
        int interval = y.getInt("wave_interval_ticks");

        assertEquals(360.0, count * sector, 1e-9,
                "wave_count x wave_sector_degrees must be 360 so the fan has no dead angle and no overlap");

        double laneAngle = Math.toDegrees(2.0 * Math.atan((width / 2.0) / range));
        assertEquals(35.64, laneAngle, 0.01,
                "2 * atan(4.5 / 14) is 35.6378 degrees. The spell file's 35.60 is a rounding slip and "
                        + "its own tolerance of 0.01 would have failed on it.");
        assertTrue(laneAngle <= sector,
                "lane angle " + laneAngle + " must fit inside the " + sector + " degree sector, or a "
                        + "body would be swept by two crests and hit_count: 1 would be a lie");

        assertEquals(range, interval * y.getDouble("wave_speed"), 1e-9,
                "wave_interval_ticks x wave_speed must equal wave_range, or a lane is empty for part of "
                        + "the release and doubly occupied for the rest");

        List<Double> escalation = y.getDoubleList("wave_escalation");
        assertEquals(count, escalation.size(), "one escalation multiplier per crest");
        double sum = 0.0;
        for (double multiplier : escalation) sum += multiplier;
        assertEquals(2.75, sum, 1e-9, "0.10 + 0.17 + 0.24 + 0.31 + 0.38 + 0.45 + 0.52 + 0.58");
        assertTrue(y.getDouble("power") * sum <= SpellBudgets.burstBudget(8),
                "the whole fan across eight bodies must still fit the circle-8 BURST budget, reads "
                        + (y.getDouble("power") * sum));
        assertTrue(escalation.get(escalation.size() - 1) > escalation.get(0),
                "later waves must hit harder, or the spell is not escalating");
    }
}