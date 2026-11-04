package me.nagasonic.alkatraz.spells.util;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SustainEffectTest {

    @Test
    void stopIsIdempotentAndSafeBeforeAnythingWasToggled() {
        UUID id = UUID.randomUUID();
        assertFalse(SustainEffect.isActive(id), "a fresh uuid is never active");
        SustainEffect.stop(id);
        SustainEffect.stop(id);
        assertFalse(SustainEffect.isActive(id));
        assertEquals(0, SustainEffect.activeCount());
    }

    @Test
    void aNullCasterIsIgnoredRatherThanThrowing() {
        // The death and quit hooks can fire with a caster the server has already dropped.
        SustainEffect.stop((Player) null);
        assertEquals(SustainEffect.Toggle.BLOCKED,
                SustainEffect.toggle(null, 0.0, null, (p, t) -> true, p -> { }),
                "a null caster must not open the slot");
        assertEquals(0, SustainEffect.activeCount());
    }
}