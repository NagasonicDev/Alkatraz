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
 * Spec sections 5, 5.2, 4.1 and 12.1: crown_of_dawn declares SUSTAIN rather than DENIAL, its
 * thirty armour-sear openings land inside the circle-9 SUSTAIN budget, and the whole static
 * wall - the keys it existed for - is gone from the yml.
 */
class CrownOfDawnTest {

    private static final String ID = "crown_of_dawn";

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = CrownOfDawnTest.class.getResourceAsStream("/spells/crown_of_dawn.yml")) {
            assertNotNull(in, "missing bundled resource /spells/crown_of_dawn.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsSustainAndAllowedForLight() throws IOException {
        YamlConfiguration yml = load();
        SpellRole role = SpellRole.parse(yml.getString("role"));
        assertNotNull(role, "crown_of_dawn must declare a role: key");
        assertEquals(SpellRole.SUSTAIN, role,
                "crown_of_dawn now hovers the caster and burns armour for 60s, so its damage is "
                        + "damage-over-time and SUSTAIN is what judges it");
        assertEquals("LIGHT", yml.getString("element"), "crown_of_dawn is light's circle-9 capstone");
        assertTrue(SpellBudgets.isAllowed(Element.LIGHT, role),
                role + " must be in LIGHT's allowlist");
    }

    @Test
    void itsDamageLandsInsideTheCircleNineSustainBudget() throws IOException {
        YamlConfiguration y = load();
        double total = y.getDouble("power") * hitCount(y);
        assertEquals(8.0, y.getDouble("power"), 1e-9, "8.0 per opening x 30 openings = 240.0");
        assertEquals(30, hitCount(y), "60s at one opening every 2s is 30 openings");
        assertEquals(240.0, total, 1e-9);
        assertTrue(total <= SpellBudgets.sustainBudget(9),
                ID + " deals " + total + " against the circle-9 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(9));
    }

    /**
     * The cap is what makes the declared 8.0 x 30 = 240.0 true, so the yml has to carry one and
     * hit_count has to be exactly what fits inside it. Nothing enforced a lifetime before: at
     * 9400 mana left after the 600 cast, 60/s upkeep against 15/s regen is a net 45/s, so the
     * purse alone bought 208s = 104 openings = 832.0 - and SpellBudgetTest cannot see that,
     * because it only ever reads {@code power x hit_count} out of this file.
     */
    @Test
    void theDeclaredHitCountFitsInsideTheEnforcedDuration() throws IOException {
        YamlConfiguration y = load();
        double seconds = y.getDouble("duration_seconds");
        int interval = y.getInt("damage_interval_ticks");
        assertTrue(y.isSet("duration_seconds"),
                "an unenforced duration_seconds lets the sustain run until the mana stops it");
        assertEquals(60.0, seconds, 1e-9, "the cap the arithmetic above needs");
        assertTrue(hitCount(y) * interval <= Math.round(seconds * 20.0),
                hitCount(y) + " openings at every " + interval + " ticks do not fit inside "
                        + seconds + "s, so the crown deals more than it declares");
        assertEquals(Math.round(seconds * 20.0 / interval), hitCount(y),
                "hit_count must be exactly what duration_seconds allows");
    }

    @Test
    void theDenialBlockIsGoneAndItNeverPlacesBlocks() throws IOException {
        YamlConfiguration y = load();
        for (String gone : List.of("denial_damage_cap", "execution_threshold", "push_strength",
                "wall_duration", "wall_length", "wall_height", "barrier_hitpoints",
                "wall_half_thickness", "segment_spacing", "rise_ticks")) {
            assertFalse(y.isSet(gone), gone + " belonged to the static wall and must be deleted");
        }
        assertFalse(y.isSet("terrain_budget"), "crown_of_dawn places no blocks at all");
        assertEquals(10.0, y.getDouble("hover_radius"), 1e-9);
        assertEquals(6, y.getInt("hover_altitude"));
        assertEquals(60, y.getInt("upkeep_mana_per_second"));
        assertEquals(0, y.getInt("cooldown"), "a cooldown would block the toggle-off cast");
        assertEquals(9, y.getInt("level"));
        assertEquals("SSSSL", y.getString("code"));
        assertEquals(4158, y.getInt("gui_custom_model_data"));
        assertEquals(600, y.getInt("mana_cost"));
        assertEquals(161, y.getDouble("required_focus"), 1e-9);
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9);
    }

    /**
     * The openings {@code duration_seconds} actually allows, cross-checked against
     * {@code hit_count} the way TideOfTheDeepTest does it. A hand-edited copy that moves one
     * without the other would under-report the spell to SpellBudgetTest, which only ever reads
     * the declared pair. Absent {@code hit_count} means one damage application per cast.
     */
    private static int hitCount(YamlConfiguration y) {
        if (!y.isSet("hit_count")) return 1;
        int declared = Math.max(1, y.getInt("hit_count"));
        assertTrue(y.isSet("duration_seconds"),
                "crown_of_dawn must declare an enforced duration_seconds; without one the sustain "
                        + "runs until the mana stops it and blows the budget by 3x");
        int openings = (int) Math.round(y.getDouble("duration_seconds") * 20.0
                / y.getInt("damage_interval_ticks"));
        assertEquals(declared, openings,
                "hit_count must equal the openings duration_seconds allows, or SpellBudgetTest "
                        + "under-reports the spell");
        return openings;
    }
}
