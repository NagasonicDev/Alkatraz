package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.api.Element;

import java.util.Set;

/** Fixed opposing-element rules for domain borders. */
public final class DomainRules {

    private static final Set<String> OPPOSING = Set.of(
            pair(Element.FIRE, Element.WATER),
            pair(Element.EARTH, Element.AIR),
            pair(Element.DARK, Element.LIGHT));

    private DomainRules() {
    }

    public static boolean isOpposing(Element a, Element b) {
        return a != null && b != null && OPPOSING.contains(pair(a, b));
    }

    /** Effects are dampened only when an opposing domain's border band is reached. */
    public static double dampen(Element a, Element b, double distanceToBorder,
                                double band, double factor) {
        return isOpposing(a, b) && distanceToBorder <= band ? factor : 1.0;
    }

    private static String pair(Element a, Element b) {
        return a.name().compareTo(b.name()) <= 0
                ? a.name() + ":" + b.name()
                : b.name() + ":" + a.name();
    }
}
