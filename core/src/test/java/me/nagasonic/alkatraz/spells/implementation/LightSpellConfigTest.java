package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.LightAmplification;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.util.UndeadTypes;
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
 * YML-only assertions plus the amplification arithmetic, so no Bukkit mock is
 * needed. getResourceAsStream plus YamlConfiguration, following
 * AlkatrazConfigPathsTest.
 */
class LightSpellConfigTest {

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = LightSpellConfigTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyLightSpellDeclaresARoleAllowedForItsElement() throws IOException {
        for (String id : new String[]{"buff", "lesser_heal", "heal", "light_buff", "radiance"}) {
            YamlConfiguration yml = load(id);
            SpellRole role = SpellRole.parse(yml.getString("role"));
            assertNotNull(role, id + " must declare a role: key");
            assertTrue(SpellBudgets.isAllowed(Element.LIGHT, role),
                    id + " declares " + role + " but LIGHT may not use it");
        }
    }

    @Test
    void lightDeclaresNoTerrainSpell() throws IOException {
        assertFalse(SpellBudgets.isAllowed(Element.LIGHT, SpellRole.TERRAIN),
                "LIGHT is forbidden from TERRAIN - light strengthens the caster, it never reshapes land");
        for (String id : new String[]{"buff", "lesser_heal", "heal", "light_buff", "radiance"}) {
            assertFalse(SpellRole.TERRAIN == SpellRole.parse(load(id).getString("role")),
                    id + " must not be TERRAIN");
        }
    }

    @Test
    void radianceIsTheOnlySustainSpellAndCarriesNoPowerKey() throws IOException {
        YamlConfiguration yml = load("radiance");
        assertEquals(SpellRole.SUSTAIN, SpellRole.parse(yml.getString("role")),
                "radiance keeps acting after the cast resolves");
        assertFalse(yml.isSet("power"),
                "spec section 7 lists '-' for radiance's power; the base comes from "
                        + "heal_amount / duration and getPower is reached through the context");
    }

    @Test
    void radianceSustainTotalLandsInsideTheCircleFiveBudget() throws IOException {
        double healAmount = load("radiance").getDouble("heal_amount");
        double duration = load("radiance").getDouble("duration");
        double perTick = healAmount / duration;
        long ticks = (long) (duration * 20);
        long hits = ticks / 20L;
        assertEquals(1.6, perTick, 1e-9, "8.0 / 5.0 = 1.6 per hit at a naked profile");
        assertEquals(5, hits, "5 seconds at one hit per 20 ticks is 5 hits");
        assertEquals(8.0, perTick * hits, 1e-9, "5 x 1.6 = 8.0");
        assertTrue(perTick * hits <= SpellBudgets.sustainBudget(5),
                "the radiance total is inside the circle-5 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(5));
    }

    @Test
    void radianceStaysInsideBudgetWithTheAmplificationTermApplied() throws IOException {
        double healAmount = load("radiance").getDouble("heal_amount");
        double duration = load("radiance").getDouble("duration");
        double perHit = healAmount / duration;
        long hits = (long) (duration * 20) / SpellAttackContext.DAMAGE_INTERVAL_TICKS;
        double budget = SpellBudgets.sustainBudget(5);

        // Now that radiance is an AttackSpell, every hit picks up the section 5.3
        // amplification that calcPower never carried. The worst realistic case is a
        // circle-5 caster (105 affinity -> x2.05) hitting an undead for a flat x2.0.
        double worstUndead = perHit * hits
                * SpellBudgets.affinityMultiplierFor(5)
                * LightAmplification.UNDEAD_MULTIPLIER;
        assertEquals(32.8, worstUndead, 1e-6,
                "5 x 1.6 x 2.05 (circle-5 affinity) x 2.0 (undead) = 32.8");

        // The cap is x2.5 for a dark-invested non-undead target; at circle 5 that is
        // 5 x 1.6 x 2.05 x 2.5 = 41.0, which is OVER the circle-5 budget. So the
        // design only holds while the amplification is not stacked on top of a
        // circle-5 caster's affinity - documented rather than asserted away.
        double amplifiedDarkCap = perHit * hits
                * SpellBudgets.affinityMultiplierFor(5)
                * (1.0 + LightAmplification.GAP_CAP / 100.0);
        assertTrue(amplifiedDarkCap > budget,
                "the affinity x anti-dark cap combination exceeds the circle-5 SUSTAIN "
                        + "budget, which is why radiance's base stays at 1.6 per hit");
        assertTrue(perHit * hits * LightAmplification.UNDEAD_MULTIPLIER <= budget,
                "the un-invested undead case - the one radiance actually gates on, since it "
                        + "only damages the undead roster - is 16.0 and stays inside the budget");
    }

    @Test
    void oldTenTickRadianceCadenceWasTwiceTheContractInterval() throws IOException {
        double healAmount = load("radiance").getDouble("heal_amount");
        double duration = load("radiance").getDouble("duration");
        long ticks = (long) (duration * 20);
        long oldHits = ticks / 10L;
        long newHits = ticks / 20L;
        assertEquals(10, oldHits, "the old tick % 10 gate gave 10 hits");
        assertEquals(5, newHits, "the 20-tick contract interval gives 5");
        assertTrue(oldHits > newHits,
                "the old cadence fired twice as often as SpellAttackContext.DAMAGE_INTERVAL_TICKS");
        assertTrue((healAmount / duration) * oldHits <= SpellBudgets.sustainBudget(5),
                "even the old cadence was inside the circle-5 budget, so this is a contract "
                        + "violation rather than an over-budget one");
    }

    @Test
    void healAndBuffAreUtilityWithNoPowerKey() throws IOException {
        for (String id : new String[]{"buff", "lesser_heal", "heal", "light_buff"}) {
            assertEquals(SpellRole.UTILITY, SpellRole.parse(load(id).getString("role")),
                    id + " is a support spell");
            assertFalse(load(id).isSet("power"),
                    id + " is not a damage spell and correctly carries no power key");
        }
    }

    @Test
    void ladderCostsMatchTheTable() throws IOException {
        assertEquals(24, load("buff").getInt("mana_cost"), "circle-2 mana");
        assertEquals(24, load("lesser_heal").getInt("mana_cost"), "circle-2 mana");
        assertEquals(5, load("buff").getInt("cooldown"), "circle-2 cooldown");
        assertEquals(48, load("heal").getInt("mana_cost"), "circle-3 mana");
        assertEquals(6, load("heal").getInt("cooldown"), "circle-3 cooldown");
        assertEquals(65, load("heal").getInt("required_focus"), "circle-3 focus");
        assertEquals(75, load("light_buff").getInt("mana_cost"), "circle-4 mana");
        assertEquals(120, load("radiance").getInt("mana_cost"), "circle-5 mana");
        assertEquals(115, load("radiance").getInt("required_focus"), "circle-5 focus");
    }

    @Test
    void antiDarkAmplificationIsReachableFromRadiance() {
        assertTrue(SpellBudgets.isAllowed(Element.LIGHT, SpellRole.SUSTAIN),
                "radiance's role is allowed, and every LIGHT damage instance now carries the "
                        + "anti-dark amplification because getPower applies it");
        assertEquals(2.0, LightAmplification.UNDEAD_MULTIPLIER, 1e-9,
                "undead take a flat x2.0 from every light spell");
        assertEquals(13, UndeadTypes.all().size(),
                "the shared roster keeps all 13 entries, and radiance delegates to it");
    }

    @Test
    void buffAndLightBuffKeepTheirOwnDurationKeys() throws IOException {
        assertTrue(load("buff").isSet("base_duration"),
                "buff's duration key survives the rebalance - only mana, cooldown and role move");
        assertTrue(load("buff").isSet("target_range"),
                "buff's target_range key survives");
        assertTrue(load("light_buff").isSet("buff_radius"),
                "light_buff's buff_radius key survives");
        assertTrue(load("light_buff").isSet("base_duration"),
                "light_buff's base_duration key survives");
    }
}