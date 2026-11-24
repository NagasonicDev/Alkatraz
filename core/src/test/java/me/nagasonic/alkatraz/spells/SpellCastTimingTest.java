package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The advertised cast time is the real cast time: wand and mastery discounts
 * shorten the payload delay instead of being clamped back to the configured
 * value. A spell with no mastery track gets no hidden discount.
 */
class SpellCastTimingTest {

    private static final class StubSpell extends Spell {
        StubSpell(double castTime) {
            this(castTime, 5);
        }

        StubSpell(double castTime, int maxMastery) {
            super("stub");
            this.castTime = castTime;
            this.maxMastery = maxMastery;
            this.element = Element.LIGHT;
        }

        @Override
        public void loadConfiguration() {
        }

        @Override
        public void castAction(Player p, ItemStack wand) {
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

    private static MagicProfile profile(Spell spell, int mastery) {
        MagicProfile profile = mock(MagicProfile.class);
        when(profile.getSpellMastery(spell)).thenReturn(mastery);
        return profile;
    }

    @Test
    void neutralWandMatchesTheConfiguredCastTime() {
        Spell spell = new StubSpell(3.5);
        assertEquals(70L, spell.calculateFinalCastTime(profile(spell, 0), 3.5f));
    }

    @Test
    void fastWandShortensTheDelayAsAdvertised() {
        Spell spell = new StubSpell(3.5);
        // 0.6x wand: 42 ticks instead of the configured 70.
        assertEquals(42L, spell.calculateFinalCastTime(profile(spell, 0), 2.1f));
    }

    @Test
    void maxMasteryShortensTheDelayByAQuarter() {
        Spell spell = new StubSpell(3.5);
        // 70 * 0.75 = 52.5, truncated to 52 ticks as before.
        assertEquals(52L, spell.calculateFinalCastTime(profile(spell, 5), 3.5f));
    }

    @Test
    void slowWandKeepsItsLongerDelay() {
        Spell spell = new StubSpell(3.5);
        assertEquals(105L, spell.calculateFinalCastTime(profile(spell, 0), 5.25f));
    }

    @Test
    void aSpellWithNoMasteryTrackGetsNoDiscount() {
        Spell spell = new StubSpell(3.5, 0);
        assertEquals(70L, spell.calculateFinalCastTime(profile(spell, 0), 3.5f));
    }
}
