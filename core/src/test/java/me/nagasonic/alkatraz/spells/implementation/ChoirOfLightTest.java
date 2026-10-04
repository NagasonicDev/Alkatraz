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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * choir_of_light is light's circle-7 burst and the element's hardest hitter. Two facts carry
 * the whole design and are asserted together in the last method: {@code hit_count} equals the
 * default shard count because a shard lands exactly one hit on its own locked target, and
 * {@link ChoirOfLightConfig}'s shipped defaults carry the same literals the yml does.
 */
class ChoirOfLightTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = ChoirOfLightTest.class.getResourceAsStream("/spells/choir_of_light.yml")) {
            assertNotNull(in, "missing bundled resource /spells/choir_of_light.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForLight() throws IOException {
        YamlConfiguration yml = load();
        SpellRole role = SpellRole.parse(yml.getString("role"));
        assertNotNull(role, "choir_of_light must declare a role: key");
        assertEquals(SpellRole.BURST, role,
                "the choir lands once per target and stops - seeking is not a SUSTAIN");
        assertEquals("LIGHT", yml.getString("element"), "choir_of_light is a light spell");
        assertTrue(SpellBudgets.isAllowed(Element.LIGHT, role),
                role + " must be in LIGHT's allowlist");
    }

    @Test
    void powerTimesHitCountFitsTheCircleSevenBurstBudget() throws IOException {
        YamlConfiguration yml = load();
        double power = yml.getDouble("power");
        int hitCount = Math.max(1, yml.getInt("hit_count"));
        assertEquals(18.9, power, 1e-9, "the section 6 circle-7 ladder power");
        assertEquals(2, hitCount, "48.0 / 18.9 = 2.54, so the default volley cannot exceed two hits");
        assertEquals(37.8, power * hitCount, 1e-9, "18.9 x 2 shards at default options");
        assertTrue(power * hitCount <= SpellBudgets.burstBudget(7),
                "37.8 must sit inside the circle-7 BURST budget of " + SpellBudgets.burstBudget(7));
    }

    @Test
    void ladderCostsMatchTheCircleSevenRow() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(240, yml.getInt("mana_cost"), "section 6 circle-7 mana");
        assertEquals(13, yml.getInt("cooldown"), "section 6 circle-7 cooldown");
        assertEquals(144, yml.getInt("required_focus"), "section 6 circle-7 focus");
        // The spec section 6 ladder lists 2.0s for circle 7, but SpellBudgets collapses
        // circles 6-9 to one legal cast time each and circle 7's is 25 ticks = 1.25s.
        assertEquals(1.25, yml.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 7 to the single point 25 ticks, so 1.25 is "
                        + "the only legal circle-7 cast time");
        assertTrue(SpellBudgets.castTimeRange(7).contains((int) Math.round(yml.getDouble("cast_time") * 20.0)),
                "circle 7 allows " + SpellBudgets.castTimeRange(7).minTicks() + ".."
                        + SpellBudgets.castTimeRange(7).maxTicks() + " ticks");
    }

    @Test
    void levelIsCircleSeven() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(7, yml.getInt("level"), "choir_of_light is light's circle-7 burst");
        assertEquals("RSSRL", yml.getString("code"), "the allocated cast code");
        assertEquals(4156, yml.getInt("gui_custom_model_data"),
                "the allocated custom model data value");
    }

    @Test
    void hitCountEqualsTheDefaultShardCountAndTheConfigCarriesTheYmlLiterals() throws IOException {
        YamlConfiguration yml = load();
        int shardCount = yml.getInt("shard_count");
        int hitCount = yml.getInt("hit_count");
        assertEquals(2, shardCount, "the default volley is two shards");
        assertEquals(shardCount, hitCount,
                "each shard is one discrete damage application inside the cast, so "
                        + "hit_count must equal the default shard count");
        assertTrue(yml.getDouble("shard_speed") > 0.0, "shards must actually travel");
        assertTrue(yml.getDouble("shard_range") > 0.0, "shards must have a range");
        assertTrue(yml.getDouble("orbit_duration") >= 0.0,
                "the orbit is a wind-up, so zero is legal but negative is not");
        assertTrue(yml.getDouble("orbit_radius") > 0.0,
                "an orbit radius of zero would stack every shard on the caster");

        // The pure config record must carry the SAME literals the yml does, or a missing key
        // would silently fall back to a different number than the file advertises.
        ChoirOfLightConfig defaults = ChoirOfLightConfig.defaults();
        assertEquals(defaults, ChoirOfLightConfig.fromConfig(Map.of()),
                "defaults() and fromConfig(Map.of()) are one set of literals");
        assertEquals(defaults, ChoirOfLightConfig.fromConfig(null),
                "a null map is the same set of literals");
        assertEquals(shardCount, defaults.shardCount(), "shard_count");
        assertEquals(yml.getDouble("shard_speed"), defaults.shardSpeed(), 1e-9, "shard_speed");
        assertEquals(yml.getDouble("shard_range"), defaults.shardRange(), 1e-9, "shard_range");
        assertEquals(yml.getDouble("shard_lifetime"), defaults.shardLifetimeSeconds(), 1e-9,
                "shard_lifetime");
        assertEquals(yml.getDouble("orbit_duration"), defaults.orbitSeconds(), 1e-9, "orbit_duration");
        assertEquals(yml.getDouble("lock_range"), defaults.lockRange(), 1e-9, "lock_range");
        assertEquals(yml.getDouble("spread_degrees"), defaults.spreadDegrees(), 1e-9, "spread_degrees");
        assertEquals(yml.getDouble("orbit_radius"), defaults.orbitRadius(), 1e-9, "orbit_radius");
        assertEquals(10, defaults.orbitTicks(0, 100), "0.5s of wind-up is ten ticks");
        assertEquals(8, defaults.orbitTicks(100, 100), "maxed mastery cuts the wind-up to 75%");
        assertEquals(50, defaults.lifetimeTicks(), "2.5s of seeking");
        assertEquals(2, defaults.safeShardCount(), "the default volley is inside the clamp");
    }
}
