package me.nagasonic.alkatraz.spells.domains;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure radial-claim rules: rings, contested fronts, opposing dampening. */
class DomainGeometryTest {

    @Test
    void firstRingContainsEightNeighboursButNotTheOrigin() {
        List<DomainGeometry.Tile> ring = DomainGeometry.ring(0, 0, 0, 1);

        assertEquals(8, ring.size());
        assertTrue(ring.contains(new DomainGeometry.Tile(1, 0, 0)));
        assertFalse(ring.contains(new DomainGeometry.Tile(0, 0, 0)));
    }

    @Test
    void exactDistanceTieIsContestedButACloserFrontIsNot() {
        assertTrue(DomainGeometry.isContested(5.0, 5.0, 0.35));
        assertFalse(DomainGeometry.isContested(5.0, 6.0, 0.35));
    }

    @Test
    void opposingDampenAppliesOnlyInsideTheBorderBand() {
        assertEquals(0.4, DomainGeometry.dampen(1.0, 3.0, 0.4));
        assertEquals(0.4, DomainGeometry.dampen(3.0, 3.0, 0.4));
        assertEquals(1.0, DomainGeometry.dampen(4.0, 3.0, 0.4));
    }
}
