package me.nagasonic.alkatraz.items.magic.adapter;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MagicDamageListenerTest {

    @Test
    void explosionBonusStartsAtZero() {
        assertEquals(0.0, MagicDamageListener.explosionBonus(UUID.randomUUID()));
    }

    @Test
    void registeredExplosionBonusIsVisible() {
        UUID playerId = UUID.randomUUID();
        MagicDamageListener.registerExplosionBonus(playerId, 5.0);
        assertEquals(5.0, MagicDamageListener.explosionBonus(playerId));
    }

    @Test
    void clearRemovesRegisteredExplosionBonus() {
        UUID playerId = UUID.randomUUID();
        MagicDamageListener.registerExplosionBonus(playerId, 5.0);
        MagicDamageListener.clearExplosionBonus(playerId);
        assertEquals(0.0, MagicDamageListener.explosionBonus(playerId));
    }
}
