package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
    void theThreeNamedExemptionsAreRejected() {
        assertFalse(WindUp.requiresWindUp(spell("waystone_recall", 7)),
                "waystone_recall's instantness is its identity");
        assertFalse(WindUp.requiresWindUp(spell("echo_cast", 8)),
                "echo_cast bypasses Spell.cast and each replayed spell carries its own wind-up");
        assertFalse(WindUp.requiresWindUp(spell("arcane_resonance", 9)),
                "arcane_resonance amplifies other casts rather than acting");
    }

    @Test
    void warpAndSkeletalKnightsAreNotExempt() {
        assertTrue(WindUp.requiresWindUp(spell("warp", 6)),
                "warp keeps its 6-second channel as a declared paid-over");
        assertTrue(WindUp.requiresWindUp(spell("skeletal_knights", 6)),
                "skeletal_knights keeps its 6-second channel as a declared paid-over");
    }

    @Test
    void anExemptionOnlyAppliesAtItsOwnCircle() {
        assertTrue(WindUp.requiresWindUp(spell("waystone_recall", 6)),
                "waystone_recall is exempt at circle 7 only");
        assertTrue(WindUp.requiresWindUp(spell("echo_cast", 9)),
                "echo_cast is exempt at circle 8 only");
        assertTrue(WindUp.requiresWindUp(spell("arcane_resonance", 8)),
                "arcane_resonance is exempt at circle 9 only");
    }

    @Test
    void theExemptionListIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> WindUp.EXEMPT_FROM_WIND_UP.put("plain", 6),
                "the exemption list must reject new entries so the contract cannot drift");
        assertThrows(UnsupportedOperationException.class,
                () -> WindUp.EXEMPT_FROM_WIND_UP.remove("warp"),
                "the exemption list must reject removals so warp cannot be quietly exempted");
        assertTrue(WindUp.requiresWindUp(spell("warp", 6)),
                "warp is still not exempt after the rejected mutation");
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
}