package me.nagasonic.alkatraz.spells.implementation;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code dispel} YAML contract, read straight off the bundled resource with
 * {@code getResourceAsStream} + {@link YamlConfiguration} — no Bukkit mocks, the
 * same shape as {@code AlkatrazConfigPathsTest}.
 *
 * <p>Every ladder number asserted here is spec section 6's circle-3 row
 * verbatim. The point of the test is that nobody can quietly retune the roster's
 * anti-magic tool without failing the build.
 */
class DispelTest {

    private YamlConfiguration spell;
    private YamlConfiguration options;

    @BeforeEach
    void loadConfigs() {
        try (InputStream in = getClass().getResourceAsStream("/spells/dispel.yml")) {
            assertNotNull(in, "dispel.yml must ship in the jar");
            spell = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("could not read dispel.yml", ex);
        }
        try (InputStream in = getClass().getResourceAsStream("/spells/dispel_options.yml")) {
            assertNotNull(in, "dispel_options.yml must ship in the jar");
            options = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("could not read dispel_options.yml", ex);
        }
    }

    @Test
    void isLightUtilityAtCircleThree() {
        assertEquals("dispel", spell.getString("id"), "the yml id and the registry id must agree");
        assertEquals("LIGHT", spell.getString("element"),
                "LIGHT is the anti-magic counter; NONE contradicts its own pillar and DARK forbids defensive options");
        assertEquals(3, spell.getInt("level"), "circle 3 is where the first real opposition appears");
        assertEquals("UTILITY", spell.getString("role"),
                "UTILITY has no budget axis, which is exactly why the bursts are particles only");
    }

    @Test
    void carriesNoPowerKeyBecauseItIsUtility() {
        assertFalse(spell.contains("power"),
                "SpellBudgetTest.utilitySpellsCarryNoDamageKey fails the build on a power key "
                        + "belonging to a UTILITY spell, and a dispel deals no damage at all — "
                        + "the bursts are particles");
    }

    @Test
    void everySectionSixCircleThreeValueIsVerbatim() {
        assertEquals(48, spell.getInt("mana_cost"), "section 6 circle 3 mana, verbatim");
        assertEquals(6L, spell.getLong("cooldown"), "section 6 circle 3 cooldown, verbatim");
        assertEquals(65.0, spell.getDouble("required_focus"), 1e-9,
                "section 6 circle 3 focus, verbatim; the 0.85 x 130 ceiling is 110.5 so 65 is comfortable");
        assertEquals(1.0, spell.getDouble("cast_time"), 1e-9, "section 6 circle 3 cast time, verbatim");
        assertEquals(100, spell.getInt("maximum_mastery"),
                "100 mastery makes the adept threshold of 50 exactly half, which is what the tier gates on");
    }

    @Test
    void theIdentityKeysMatchTheAllocation() {
        assertEquals("SLLLS", spell.getString("code"),
                "spec 10.1: SLLLS has exactly one Hamming-1 neighbour, tremor SLLRS, also circle 3");
        assertEquals(4168, spell.getInt("gui_custom_model_data"),
                "4168 is allocated to dispel; 4137-4165 are the 29 others and 4166/4167 are the collision repairs");
        assertEquals("SPECTRAL_ARROW", spell.getString("gui_item"), "the GUI item from spec 9.8");
        assertEquals("YELLOW", spell.getString("mastery_bar_color"), "the bar colour from spec 9.8");
        assertTrue(spell.getBoolean("enabled"), "the spell ships enabled");
    }

    @Test
    void theDispelSpecificKeysArePresent() {
        assertEquals(24.0, spell.getDouble("target_range"), 1e-9,
                "target_range feeds Utils.getTargetEntity, which takes an int — Dispel casts it");
        assertEquals(4, spell.getInt("vanilla_potion_limit"),
                "the vanilla fallback is capped so one dispel cannot sweep a player's whole effect bar");
        assertTrue(spell.getBoolean("illusion_reset"),
                "illusion_reset is true: stripping Stealth and Disguise is the spell's headline");
    }

    @Test
    void theAdeptTierGrantsExactlyOneExtraBandAtHalfMastery() {
        // The option's values are a MAP of named values, not a list of maps - the shape
        // every *_options.yml in the roster uses - so getMapList("options.tier.values")
        // is empty and the tier has to be read as a section.
        ConfigurationSection values = options.getConfigurationSection("options.tier.values");
        assertNotNull(values, "the tier option must declare values");
        assertEquals(List.of("novice", "adept"), new ArrayList<>(values.getKeys(false)),
                "the tier option has exactly these two values, in this order; novice is the "
                        + "default_index 0 starting tier");
        assertEquals("adept", values.getString("adept.value"),
                "the value token the profile stores must equal the section key, or the selected "
                        + "value cannot be read back");
        assertEquals("novice", values.getString("novice.value"), "same for the starting tier");
        assertTrue(values.getMapList("options.tier.values.novice.impacts").isEmpty(),
                "novice carries no impacts on purpose: Dispel's own circle is always the base, so an "
                        + "impact here would give a zero-mastery player the band for free");

        List<Map<?, ?>> requirements = options.getMapList("options.tier.values.adept.requirements");
        assertNotNull(requirements, "the adept tier must gate on a requirement");
        assertEquals(1, requirements.size(), "the adept tier gates on exactly one requirement");
        Map<?, ?> requirement = requirements.get(0);
        assertEquals("number_stat", requirement.get("type"), "the gate is a number_stat on mastery");
        assertEquals("mastery_dispel", requirement.get("stat"),
                "mastery_<spell id> is the dynamic stat Spell.getSpellMastery writes");
        assertEquals(50, ((Number) requirement.get("minimum")).intValue(),
                "half of maximum_mastery 100 — the threshold at which the extra band unlocks");

        List<Map<?, ?>> impacts = options.getMapList("options.tier.values.adept.impacts");
        assertNotNull(impacts, "the adept tier must grant an impact");
        assertEquals(1, impacts.size(), "the adept tier grants exactly one impact");
        Map<?, ?> impact = impacts.get(0);
        assertEquals("stat_modifier", impact.get("type"), "a stat_modifier, read by getModifiedStat");
        assertEquals("dispel_bands", impact.get("stat"), "the stat Dispel reads the band from");
        assertEquals(1.0, ((Number) impact.get("value")).doubleValue(), 1e-9,
                "exactly one extra band — DispelBands.dispelBands clamps the bonus to {0, 1} anyway");
        assertEquals("ADD", impact.get("modifier"),
                "ADD is required: Spell.getModifiedStat only sums ADD, so SET or "
                        + "MULTIPLY here would silently change what the mastery band means");
    }
}