package me.nagasonic.alkatraz.progression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressionMultiplierServiceTest {
    @BeforeEach
    void setUp() {
        ProgressionMultiplierService.reset();
    }

    @AfterEach
    void cleanup() {
        ProgressionMultiplierService.reset();
    }

    @Test
    void globalDefaultIsOne() {
        assertEquals(1.0, ProgressionMultiplierService.getGlobalMultiplier(), 1e-9);
        assertEquals(1.0, ProgressionMultiplierService.resolve((String) null, (String) null), 1e-9);
    }

    @Test
    void globalBoostApplies() {
        ProgressionMultiplierService.setGlobalMultiplier(2.0);
        assertEquals(2.0, ProgressionMultiplierService.resolve((String) null, (String) null), 1e-9);
        assertEquals(10.0, ProgressionMultiplierService.apply(5.0, (String) null, (String) null), 1e-9);
    }

    @Test
    void worldAndGroupCompose() {
        ProgressionMultiplierService.setWorldMultiplier("world", 2.0);
        ProgressionMultiplierService.setGroupMultiplier("group", 3.0);
        assertEquals(6.0, ProgressionMultiplierService.resolve("world", "group"), 1e-9);
    }
}