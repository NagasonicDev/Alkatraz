package me.nagasonic.alkatraz.spells.domains;

import org.junit.jupiter.api.Test;

import static me.nagasonic.alkatraz.spells.domains.DomainGeometry.Claim;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure claim arbitration between two competing domain fronts. */
class DomainClaimTest {

    @Test
    void closerFrontWinsButExactTieBecomesNeutralBorder() {
        assertEquals(Claim.SELF, DomainGeometry.claim(2, 5, 32, 32, 0.5));
        assertEquals(Claim.OTHER, DomainGeometry.claim(5, 2, 32, 32, 0.5));
        assertEquals(Claim.BORDER, DomainGeometry.claim(5, 5, 32, 32, 0.5));
    }

    @Test
    void tileOutsideBothRadiiIsFreeAndOutsideSelfIsNotClaimed() {
        assertEquals(Claim.FREE, DomainGeometry.claim(40, 5, 32, 32, 0.5));
        assertEquals(Claim.FREE, DomainGeometry.claim(33, 50, 32, 32, 0.5));
    }
}
