package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link ChannelRegistry}'s bookkeeping. The task-cancelling side sits behind a
 * {@link Runnable} seam rather than a raw Bukkit task id, so none of this needs a
 * live scheduler - the registry is exercised exactly as a server would drive it.
 */
class ChannelRegistryTest {

    private static final String INTERRUPTED = "spells.cast.interrupted";

    private final AtomicInteger cancellations = new AtomicInteger();

    private static final class StubSpell extends Spell {
        StubSpell(String id, int requiredCircle) {
            super("stub");
            this.id = id;
            this.requiredCircle = requiredCircle;
            this.maxMastery = 5;
            this.element = Element.FIRE;
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

    private WindUp begin(UUID caster) {
        WindUp windUp = WindUp.begin(new StubSpell("plain", 6), mock(LivingEntity.class), 40);
        ChannelRegistry.register(caster, windUp, cancellations::incrementAndGet);
        return windUp;
    }

    private static UUID uuid() {
        return UUID.randomUUID();
    }

    @BeforeEach
    void resetRegistry() {
        ChannelRegistry.shutdown();
    }

    @AfterEach
    void clearRegistry() {
        ChannelRegistry.shutdown();
    }

    @Test
    void aFreshRegistryHoldsNoChannels() {
        assertEquals(0, ChannelRegistry.activeChannels(),
                "a registry with nothing registered reports none");
        assertFalse(ChannelRegistry.isChanneling(uuid()),
                "an unknown caster is not channelling");
    }

    @Test
    void registeringMakesTheCasterChannelling() {
        UUID caster = uuid();
        assertEquals(0, ChannelRegistry.activeChannels(), "nothing registered yet");

        begin(caster);

        assertTrue(ChannelRegistry.isChanneling(caster), "the caster is now channelling");
        assertEquals(1, ChannelRegistry.activeChannels(), "one active channel");
    }

    @Test
    void unregisteringStopsTheCasterChannelling() {
        UUID caster = uuid();
        begin(caster);

        ChannelRegistry.unregister(caster);

        assertFalse(ChannelRegistry.isChanneling(caster), "the caster is no longer channelling");
        assertEquals(0, ChannelRegistry.activeChannels(), "the channel is gone");
    }

    @Test
    void unregisteringAnUnknownCasterIsHarmless() {
        ChannelRegistry.unregister(uuid());
        assertEquals(0, ChannelRegistry.activeChannels(), "unregister never invents a channel");
    }

    @Test
    void reRegisteringReplacesTheEarlierWindUp() {
        UUID caster = uuid();
        begin(caster);
        begin(caster);

        assertEquals(1, ChannelRegistry.activeChannels(),
                "one caster can only hold one wind-up at a time");
    }

    @Test
    void cancelAllInterruptsEveryChannelAndRunsEveryCanceller() {
        begin(uuid());
        begin(uuid());
        begin(uuid());

        ChannelRegistry.cancelAll(INTERRUPTED);

        assertEquals(0, ChannelRegistry.activeChannels(), "cancelAll empties the registry");
        assertEquals(3, cancellations.get(),
                "each registered wind-up had its underlying task cancelled exactly once");
    }

    @Test
    void cancelAllOnAnEmptyRegistryDoesNothing() {
        ChannelRegistry.cancelAll(INTERRUPTED);
        assertEquals(0, cancellations.get(), "there was no task to cancel");
    }

    @Test
    void cancelAllInterruptsTheWindUpItself() {
        UUID caster = uuid();
        WindUp windUp = begin(caster);

        ChannelRegistry.cancelAll(INTERRUPTED);

        assertTrue(windUp.isCancelled(),
                "an interrupted wind-up reports itself cancelled so it cannot fire its payload");
    }

    @Test
    void unregisterDoesNotCancelTheUnderlyingTask() {
        UUID caster = uuid();
        begin(caster);

        ChannelRegistry.unregister(caster);

        assertEquals(0, cancellations.get(),
                "unregister only forgets the channel; the owner still owns its own task");
    }

    @Test
    void shutdownCancelsEveryChannelAndEmptiesTheRegistry() {
        begin(uuid());
        begin(uuid());

        ChannelRegistry.shutdown();

        assertEquals(0, ChannelRegistry.activeChannels(), "shutdown leaves nothing behind");
        assertEquals(2, cancellations.get(),
                "shutdown cancels every task so nothing survives a plugin reload");
    }

    @Test
    void shutdownIsIdempotent() {
        begin(uuid());

        ChannelRegistry.shutdown();
        ChannelRegistry.shutdown();

        assertEquals(1, cancellations.get(), "a second shutdown cancels nothing new");
        assertEquals(0, ChannelRegistry.activeChannels(), "the registry stays empty");
    }

    @Test
    void theRegistryIsABukkitListener() {
        assertTrue(org.bukkit.event.Listener.class.isAssignableFrom(ChannelRegistry.class),
                "ChannelRegistry must be registrable as a Bukkit listener");
        assertNotNull(ChannelRegistry.INSTANCE, "the single shared registry instance");
    }
}