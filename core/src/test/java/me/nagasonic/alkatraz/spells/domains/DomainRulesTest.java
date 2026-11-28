package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.api.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opposing-element rules used by domain borders. */
class DomainRulesTest {

    @Test
    void onlyConfiguredElementPairsAreOpposing() {
        assertTrue(DomainRules.isOpposing(Element.FIRE, Element.WATER));
        assertTrue(DomainRules.isOpposing(Element.EARTH, Element.AIR));
        assertTrue(DomainRules.isOpposing(Element.DARK, Element.LIGHT));
        assertFalse(DomainRules.isOpposing(Element.WATER, Element.FIRE) == false);
        assertFalse(DomainRules.isOpposing(Element.FIRE, Element.EARTH));
        assertFalse(DomainRules.isOpposing(Element.FIRE, Element.FIRE));
    }

    @Test
    void dampeningAppliesOnlyInsideTheBorderBand() {
        assertEquals(0.4, DomainRules.dampen(Element.FIRE, Element.WATER, 2.0, 3.0, 0.4));
        assertEquals(1.0, DomainRules.dampen(Element.FIRE, Element.WATER, 4.0, 3.0, 0.4));
        assertEquals(1.0, DomainRules.dampen(Element.FIRE, Element.EARTH, 1.0, 3.0, 0.4));
    }
}
