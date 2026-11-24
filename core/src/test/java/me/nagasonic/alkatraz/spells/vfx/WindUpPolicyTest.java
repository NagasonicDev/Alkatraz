package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The pure policy half of the {@link WindUp} contract: which spells owe a wind-up,
 * how the counter advances, and how mastery shortens it. No server, no scheduler,
 * no particles - {@link WindUp#begin} on a non-{@code Player} caster resolves nothing
 * through {@code ProfileManager}, so this runs on a bare JVM.
 */
class WindUpPolicyTest {

    /** Minimal Spell stand-in; the abstract methods are never called by the policy layer. */
    private static final class StubSpell extends Spell {
        StubSpell(String id, int requiredCircle, int maxMastery) {
            super("stub");
            this.id = id;
            this.requiredCircle = requiredCircle;
            this.maxMastery = maxMastery;
            this.element = Element.LIGHT;
        }

        @Override
        public void loadConfiguration() {
        }

        @Override
        public void castAction(org.bukkit.entity.Player p, ItemStack wand) {
        }

        @Override
        public void mobCastAction(Mob caster, ItemStack wand) {
        }

        @Override
        public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
            return 0;
        }

        @Override
        public ItemStack getSpellBook() {
            return null;
        }
    }

    private static StubSpell spell(String id, int circle) {
        return new StubSpell(id, circle, 5);
    }

    private static LivingEntity caster() {
        return mock(LivingEntity.class);
    }

    // ---------------------------------------------------------------- guard

    @Test
    void circlesZeroThroughFiveNeverWindUp() {
        for (int circle = 0; circle <= 5; circle++) {
            assertFalse(WindUp.requiresWindUp(spell("plain", circle)),
                    "circle " + circle + " is below the wind-up threshold");
        }
    }

    @Test
    void plainHighCircleSpellsWindUp() {
        for (int circle = 6; circle <= 9; circle++) {
            assertTrue(WindUp.requiresWindUp(spell("plain", circle)),
                    "circle " + circle + " owes a wind-up");
        }
    }

    @Test
    void theExemptionListIsEmpty() {
        assertTrue(WindUp.EXEMPT_FROM_WIND_UP.isEmpty(),
                "the 0.9.2 rework removed every wind-up exemption");
    }

    @Test
    void everyCircleSixAndAboveSpellRequiresWindUp() {
        for (int circle = 6; circle <= 9; circle++) {
            assertTrue(WindUp.requiresWindUp(spell("any_spell", circle)),
                    "circle " + circle + " owes a wind-up, whatever the spell id");
        }
    }

    @Test
    void theExemptionListIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> WindUp.EXEMPT_FROM_WIND_UP.put("plain", 6),
                "the exemption list must reject new entries so the contract cannot drift");
        assertThrows(UnsupportedOperationException.class,
                () -> WindUp.EXEMPT_FROM_WIND_UP.remove("plain"),
                "the exemption list must reject removals so an exemption cannot be quietly added");
        assertTrue(WindUp.requiresWindUp(spell("plain", 6)),
                "a circle 6 spell is still not exempt after the rejected mutation");
    }

    @Test
    void intensityScalesWithCircle() {
        float six = WindUp.intensityScale(6);
        float nine = WindUp.intensityScale(9);
        assertEquals(1.0f, six, 1e-6f, "circle 6 is the reference intensity");
        assertTrue(nine > six,
                "circle 9 wind-up must be visibly more intense than circle 6, got " + six + " vs " + nine);
        assertTrue(WindUp.intensityScale(4) <= six,
                "a sub-threshold circle is never larger than the circle-6 baseline");
        assertEquals(nine, WindUp.intensityScale(99), 1e-6f,
                "intensity clamps at the circle-9 ceiling");
    }

    // ------------------------------------------------------------- counting

    @Test
    void progressStartsAtZeroAndIsMonotonic() {
        WindUp windUp = WindUp.begin(spell("plain", 6), caster(), 40);
        double previous = -1.0;
        for (int i = 0; i < 40; i++) {
            assertTrue(windUp.progress() >= previous,
                    "progress must never fall, tick " + i);
            previous = windUp.progress();
            windUp.tick();
        }
    }

    @Test
    void progressStaysInsideTheUnitInterval() {
        WindUp windUp = WindUp.begin(spell("plain", 7), caster(), 10);
        for (int i = 0; i < 25; i++) {
            double p = windUp.progress();
            assertTrue(p >= 0.0 && p <= 1.0,
                    "progress must clamp to [0,1], tick " + i + " gave " + p);
            windUp.tick();
        }
    }

    @Test
    void isCompleteFlipsExactlyAtTheEnd() {
        WindUp windUp = WindUp.begin(spell("plain", 6), caster(), 20);
        assertFalse(windUp.isComplete(), "a fresh wind-up is not complete");
        for (int i = 0; i < 19; i++) {
            windUp.tick();
            assertFalse(windUp.isComplete(),
                    "still running after " + (i + 1) + " of 20 ticks");
        }
        assertEquals(1, windUp.remainingTicks(), "one tick left before the final tick");
        windUp.tick();
        assertTrue(windUp.isComplete(), "complete exactly on the 20th tick");
        assertEquals(0, windUp.remainingTicks(), "nothing remains once complete");
        assertEquals(1.0, windUp.progress(), 1e-9, "a complete wind-up reads full progress");
    }

    @Test
    void remainingTicksCountsDownAndFloorsAtZero() {
        WindUp windUp = WindUp.begin(spell("plain", 6), caster(), 5);
        assertEquals(5, windUp.remainingTicks(), "a fresh wind-up has every tick left");
        windUp.tick();
        assertEquals(4, windUp.remainingTicks(), "one tick consumed");
        for (int i = 0; i < 10; i++) {
            windUp.tick();
        }
        assertEquals(0, windUp.remainingTicks(), "remaining never goes negative");
    }

    @Test
    void aNonPositiveDurationCompletesImmediatelyWithoutDividingByZero() {
        WindUp windUp = WindUp.begin(spell("plain", 6), caster(), 0);
        assertEquals(1.0, windUp.progress(), 1e-9, "a zero-tick wind-up is instantly complete");
        assertTrue(windUp.isComplete(), "a zero-tick wind-up is instantly complete");
        assertEquals(0, windUp.remainingTicks(), "a zero-tick wind-up leaves nothing");
    }

    // -------------------------------------------------------------- mastery

    @Test
    void maxMasteryShortensTheWindUpByAQuarter() {
        assertEquals(30, WindUp.effectiveTicks(40, 5, 5),
                "at max mastery a 40-tick wind-up becomes 30");
        assertEquals(15, WindUp.effectiveTicks(20, 9, 5),
                "mastery above the cap still counts as max mastery");
    }

    @Test
    void belowMaxMasteryTheWindUpIsUnshortened() {
        assertEquals(40, WindUp.effectiveTicks(40, 4, 5),
                "one point short of max mastery pays the full duration");
        assertEquals(40, WindUp.effectiveTicks(40, 0, 5),
                "no mastery pays the full duration");
    }

    @Test
    void anUnmasterableSpellNeverShortens() {
        assertEquals(40, WindUp.effectiveTicks(40, 0, 0),
                "maximum_mastery 0 means no mastery track, so no shortening");
        assertEquals(40, WindUp.effectiveTicks(40, 3, 0),
                "maximum_mastery 0 means no mastery track, so no shortening");
    }

    @Test
    void effectiveTicksNeverRoundsAWindUpAwayToNothing() {
        assertTrue(WindUp.effectiveTicks(1, 5, 5) >= 1,
                "a one-tick wind-up must survive the mastery discount");
    }

    // ------------------------------------------------------------ framework

    @Test
    void frameworkWindUpSpansTheActualCastDelay() {
        WindUp windUp = WindUp.forFrameworkCast(spell("plain", 6), mock(Player.class), 42);
        assertEquals(42, windUp.remainingTicks(),
                "the framework wind-up is the computed cast delay, not the ladder ceiling");
        for (int i = 0; i < 41; i++) {
            windUp.tick();
            assertFalse(windUp.isComplete(), "still charging after " + (i + 1) + " ticks");
        }
        windUp.tick();
        assertTrue(windUp.isComplete(), "the payload is due exactly when the delay ends");
    }

    @Test
    void frameworkWindUpNeverHasZeroTicks() {
        assertEquals(1, WindUp.forFrameworkCast(spell("plain", 7), mock(Player.class), 0)
                .remainingTicks(), "a zero-delay cast still gets one wind-up tick");
    }
}