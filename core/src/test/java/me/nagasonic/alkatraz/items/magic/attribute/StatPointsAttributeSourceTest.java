package me.nagasonic.alkatraz.items.magic.attribute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatPointsAttributeSourceTest {

    @Test
    void affinityIsPointsTimesPerPoint() {
        assertEquals(10.0, StatPointsAttributeSource.affinityFromPoints(5, 2.0));
        assertEquals(0.0, StatPointsAttributeSource.affinityFromPoints(0, 2.0));
    }

    @Test
    void resistanceIsPointsTimesPerPoint() {
        assertEquals(4.0, StatPointsAttributeSource.resistanceFromPoints(2, 2.0));
        assertEquals(0.0, StatPointsAttributeSource.resistanceFromPoints(0, 1.5));
    }
}
