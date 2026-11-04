package me.nagasonic.alkatraz.progression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoosterManagerTest {

    @AfterEach
    void cleanup() {
        BoosterManager.clear();
        ProgressionMultiplierService.reset();
    }

    @Test
    void activationSetsWorldMultiplier() {
        assertTrue(BoosterManager.start("world", "w", 2.0));
        assertEquals(2.0, ProgressionMultiplierService.getWorldMultiplier("w"), 1e-9);
        assertTrue(BoosterManager.isActive("world", "w"));
        assertEquals(1, BoosterManager.list("world").size());
    }

    @Test
    void expiryProcessorRemovesExpiredBoosters() {
        assertTrue(BoosterManager.start("world", "w", 2.0, 50L));
        long future = System.currentTimeMillis() + 100L;
        BoosterManager.processExpired(future);
        assertFalse(BoosterManager.isActive("world", "w"));
        assertEquals(1.0, ProgressionMultiplierService.getWorldMultiplier("w"), 1e-9);
        assertEquals(0, BoosterManager.count());
    }

    @Test
    void stopRestoresMultiplier() {
        ProgressionMultiplierService.setWorldMultiplier("w", 1.5);
        assertTrue(BoosterManager.start("world", "w", 3.0));
        assertEquals(3.0, ProgressionMultiplierService.getWorldMultiplier("w"), 1e-9);
        assertTrue(BoosterManager.stop("world", "w"));
        assertEquals(1.5, ProgressionMultiplierService.getWorldMultiplier("w"), 1e-9);
    }

    @Test
    void clearRemovesAll() {
        BoosterManager.start("world", "w", 2.0);
        BoosterManager.start("group", "g", 3.0);
        BoosterManager.clear();
        assertEquals(0, BoosterManager.count());
        assertEquals(0, BoosterManager.list().size());
    }
}
