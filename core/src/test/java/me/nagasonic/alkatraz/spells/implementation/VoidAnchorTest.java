package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

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
 * <p>This spell reads its configuration inline - it deliberately has <b>no</b>
 * config record, because T23's brief names records for {@code UmbralVolleyConfig}
 * and {@code OblivionConfig} only. Everything asserted here therefore comes straight
 * off the yml.
 */
class VoidAnchorTest {

    private static final String ID = "void_anchor";
    private static final double EPS = 1e-9;

    private static YamlConfiguration yml() throws Exception {
        try (InputStream in = VoidAnchorTest.class.getResourceAsStream("/spells/" + ID + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + ID + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsUtilityAndAllowedForDark() throws Exception {
        YamlConfiguration y = yml();
        Element element = Element.valueOf(y.getString("element"));
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, ID + " must declare a parseable role: key");
        assertEquals(Element.DARK, element, ID + " is a dark spell");
        assertEquals(SpellRole.UTILITY, role,
                ID + " is the circle-8 necromancy summon and declares role: UTILITY");
        assertTrue(SpellBudgets.isAllowed(element, role),
                ID + " declares " + role + " but DARK allows only {UTILITY, SUSTAIN, BURST}");
        assertFalse(role == SpellRole.DENIAL, "dark has no defensive option at any circle");
    }

    @Test
    void carriesNoDamageKeyBecauseItsDamageComesFromTheMinionsBrain() throws Exception {
        YamlConfiguration y = yml();
        assertFalse(y.isSet("power"),
                ID + " is UTILITY - judged on nothing - so a power key on it is dead config");
        assertFalse(y.isSet("hit_count"),
                ID + " deals no damage through the spell pipeline, so there is no hit count");
        assertTrue(y.isSet("horror_power"),
                "the minion's damage must be an explicit horror_power, not an implicit one");
        assertTrue(y.getDouble("horror_power") > 0.0
                        && y.getDouble("horror_power") <= SpellBudgets.burstBudget(8),
                "horror_power is a mob attribute spent on vanilla cooldowns, not a per-cast damage "
                        + "line, and it must still be sane");
        assertEquals(10.0, y.getDouble("horror_power"), EPS,
                "the precedent chain is zombie_power 2.0 at circle 4, knight_power 6.0 at circle 6, "
                        + "horror_power 10.0 at circle 8 - one servant, each individually far stronger");
        assertFalse(y.isSet("denial_damage_cap"),
                "denial_damage_cap belongs to DENIAL spells that deal incidental damage");
        assertFalse(y.isSet("terrain_budget"),
                "terrain_budget belongs to TERRAIN spells; this one reshapes nothing");
    }

    @Test
    void ladderValuesMatchTheCircleEightRow() throws Exception {
        YamlConfiguration y = yml();
        assertEquals(450, y.getInt("mana_cost"), "section 6 ladder circle-8 mana");
        assertEquals(16, y.getInt("cooldown"), "section 6 ladder circle-8 cooldown");
        assertEquals(153, y.getInt("required_focus"), "section 6 ladder circle-8 focus");
        assertTrue(y.getInt("required_focus") <= 0.85 * 180,
                "circle-8 focus must sit at or below the 85% ceiling of the 180 maxFocus - 153 is "
                        + "exactly 0.85 x 180 and must not be raised");
        assertEquals(1.5, y.getDouble("cast_time"), EPS,
                "SpellBudgets.castTimeRange(8) collapses to the single point 30 ticks = 1.5s, so 1.5 "
                        + "is the only legal circle-8 cast time");
        assertTrue(SpellBudgets.castTimeRange(8).contains(30),
                "1.5s is 30 ticks, exactly the circle-8 cast-time window");
    }

    @Test
    void levelIsEight() throws Exception {
        YamlConfiguration y = yml();
        assertEquals(8, y.getInt("level"), ID + " is a circle-8 spell");
        assertEquals("SLSSS", y.getString("code"), "cast code from the roster table");
        assertEquals(4160, y.getInt("gui_custom_model_data"),
                "4160 is void_anchor's allocated gui_custom_model_data and is never reused");
        assertEquals("void_anchor", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
    }

    @Test
    void theChannelIsExactlyTheCastTimeAndTheLashIsNotADamageCadence() throws Exception {
        YamlConfiguration y = yml();
        assertEquals(30, y.getInt("channel_ticks"),
                "the channel is 1.5s x 20 = 30 ticks");
        assertEquals(Math.round(y.getDouble("cast_time") * 20.0), y.getInt("channel_ticks"),
                "the channel must be exactly the cast time, or a cast pays for a wind-up it never had");
        assertEquals(20, y.getInt("lash_interval_ticks"),
                "the lash runs once per second, which is why this is UTILITY and not SUSTAIN");
        assertEquals(1, y.getInt("horror_count"), "one bound horror at circle 8");
        assertEquals(30, y.getInt("horror_duration"), "thirty seconds of anchored servant");
        assertEquals(4.0, y.getDouble("anchor_leash"), EPS,
                "four blocks of slack before the servant is dragged back to its anchor");
        assertEquals(6.0, y.getDouble("lash_range"), EPS, "the lash reaches six blocks");
        assertEquals(10, y.getInt("lash_hold_ticks"), "a victim is held for half a second");
        assertEquals(0.45, y.getDouble("anchor_pull_strength"), EPS, "the pull strength");
        assertFalse(y.isSet("damage_per_tick"),
                ID + " deals no damage of its own, so a per-tick damage key would be dead config");
        assertFalse(y.getStringList("description").isEmpty(), "description must be a non-empty list");
    }
}