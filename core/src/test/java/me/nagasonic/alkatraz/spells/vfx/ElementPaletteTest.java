package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.api.Element;
import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Locks the approved Element -&gt; RGB palette and, just as importantly, the
 * zero-allocation guarantee of {@link ElementPalette.Palette#coreDust()} and friends.
 *
 * <p>Zero Bukkit mocks on purpose: {@link ElementPalette} must never touch
 * {@code MinecraftVersion}'s static initialiser, so if this file starts needing a
 * mocked server the design has been broken.
 */
class ElementPaletteTest {

    private static void assertRgb(String label, Color actual, int r, int g, int b) {
        assertEquals(r, actual.getRed(), label + " red");
        assertEquals(g, actual.getGreen(), label + " green");
        assertEquals(b, actual.getBlue(), label + " blue");
    }

    private static double luminance(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

    @Test
    void fireUsesOrangeCoreGoldAccentAndCharredDeep() {
        ElementPalette.Palette p = ElementPalette.of(Element.FIRE);
        assertRgb("FIRE core", p.core(), 255, 140, 0);
        assertRgb("FIRE accent", p.accent(), 255, 210, 74);
        assertRgb("FIRE deep", p.deep(), 140, 47, 0);
    }

    @Test
    void waterUsesBlueCoreSkyAccentAndAbyssDeep() {
        ElementPalette.Palette p = ElementPalette.of(Element.WATER);
        assertRgb("WATER core", p.core(), 47, 127, 212);
        assertRgb("WATER accent", p.accent(), 127, 212, 255);
        assertRgb("WATER deep", p.deep(), 16, 56, 107);
    }

    @Test
    void earthUsesSiennaCoreTanAccentAndSoilDeep() {
        ElementPalette.Palette p = ElementPalette.of(Element.EARTH);
        assertRgb("EARTH core", p.core(), 160, 82, 45);
        assertRgb("EARTH accent", p.accent(), 217, 143, 95);
        assertRgb("EARTH deep", p.deep(), 78, 40, 17);
    }

    @Test
    void airUsesWhiteBlueCorePureWhiteAccentAndPaleSteelDeep() {
        ElementPalette.Palette p = ElementPalette.of(Element.AIR);
        assertRgb("AIR core", p.core(), 234, 246, 255);
        assertRgb("AIR accent", p.accent(), 255, 255, 255);
        assertRgb("AIR deep", p.deep(), 159, 196, 216);
    }

    @Test
    void lightUsesPaleYellowCoreCreamAccentAndAntiqueGoldDeep() {
        ElementPalette.Palette p = ElementPalette.of(Element.LIGHT);
        assertRgb("LIGHT core", p.core(), 255, 255, 135);
        assertRgb("LIGHT accent", p.accent(), 255, 253, 224);
        assertRgb("LIGHT deep", p.deep(), 212, 184, 58);
    }

    @Test
    void darkUsesVioletCoreMagentaAccentAndNearBlackDeep() {
        ElementPalette.Palette p = ElementPalette.of(Element.DARK);
        assertRgb("DARK core", p.core(), 106, 13, 173);
        assertRgb("DARK accent", p.accent(), 177, 76, 224);
        assertRgb("DARK deep", p.deep(), 43, 4, 56);
    }

    @Test
    void noneUsesNeutralGreySoItNeverReadsAsAnElement() {
        ElementPalette.Palette p = ElementPalette.of(Element.NONE);
        assertRgb("NONE core", p.core(), 180, 180, 180);
        assertRgb("NONE accent", p.accent(), 234, 234, 234);
        assertRgb("NONE deep", p.deep(), 85, 95, 102);
    }

    @Test
    void ofNullElementFallsBackToNoneInsteadOfThrowing() {
        ElementPalette.Palette p = ElementPalette.of((Element) null);
        assertSame(Element.NONE, p.element(), "a null element must resolve to NONE, not blow up a spell cast");
    }

    @Test
    void ofUnknownElementNameFallsBackToNoneInsteadOfThrowing() {
        ElementPalette.Palette p = ElementPalette.of("NOT_AN_ELEMENT");
        assertSame(Element.NONE, p.element(), "a typo in a spell yml must not throw during cast");
    }

    @Test
    void ofNullElementNameFallsBackToNoneInsteadOfThrowing() {
        ElementPalette.Palette p = ElementPalette.of((String) null);
        assertSame(Element.NONE, p.element(), "a missing element key must not throw during cast");
    }

    @Test
    void ofElementNameParsesEveryElementCaseInsensitively() {
        assertSame(Element.FIRE, ElementPalette.of("fire").element(), "yml element keys are lowercase");
        assertSame(Element.WATER, ElementPalette.of("WATER").element(), "uppercase yml keys must also parse");
        assertSame(Element.EARTH, ElementPalette.of("Earth").element(), "display-name spelling must also parse");
    }

    @Test
    void coreDustIsTheSameInstanceOnEveryCallForEveryElementAndShade() {
        for (Element element : Element.values()) {
            ElementPalette.Palette p = ElementPalette.of(element);
            assertSame(p.coreDust(), p.coreDust(), element + " coreDust must be pre-built, not allocated per tick");
            assertSame(p.accentDust(), p.accentDust(), element + " accentDust must be pre-built, not allocated per tick");
            assertSame(p.deepDust(), p.deepDust(), element + " deepDust must be pre-built, not allocated per tick");
            assertEquals(1.0f, p.coreDust().getSize(), element + " core dust is the biggest/brighest");
            assertEquals(0.6f, p.accentDust().getSize(), element + " accent dust is mid detail");
            assertEquals(0.4f, p.deepDust().getSize(), element + " deep dust is the smallest");
            assertEquals(p.core(), p.coreDust().getColor(), element + " coreDust must carry core's colour");
            assertEquals(p.accent(), p.accentDust().getColor(), element + " accentDust must carry accent's colour");
            assertEquals(p.deep(), p.deepDust().getColor(), element + " deepDust must carry deep's colour");
        }
    }

    @Test
    void paletteInstanceIsSharedAcrossLookupsSoNoMapHitAllocates() {
        assertSame(ElementPalette.of(Element.FIRE), ElementPalette.of(Element.FIRE),
                "repeated lookups must hand back the cached palette instance");
        assertSame(ElementPalette.of(Element.FIRE).coreDust(), ElementPalette.of(Element.FIRE).coreDust(),
                "cached palettes must also share their pre-built dust");
    }

    @Test
    void theThreeShadesOfEachElementAreMutuallyDistinct() {
        for (Element element : Element.values()) {
            ElementPalette.Palette p = ElementPalette.of(element);
            assertNotEquals(p.core(), p.accent(), element + " core and accent must differ");
            assertNotEquals(p.accent(), p.deep(), element + " accent and deep must differ");
            assertNotEquals(p.core(), p.deep(), element + " core and deep must differ");
        }
    }

    @Test
    void coreIsAtLeastAsBrightAsDeepSoDeepIsNeverUsedForTheBrightestEffect() {
        for (Element element : Element.values()) {
            ElementPalette.Palette p = ElementPalette.of(element);
            assertEquals(true, luminance(p.core()) >= luminance(p.deep()),
                    element + " core luminance " + luminance(p.core()) + " must be >= deep luminance " + luminance(p.deep()));
        }
    }

    }