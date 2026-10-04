package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.api.Element;
import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The single source of truth for turning an {@link Element} into particle colours.
 *
 * <p>Why three shades per element instead of one: a flat single-colour blob is what makes
 * two unrelated spells read as the same effect on screen. A bright {@code core} read against a
 * dark {@code deep} edge keeps an effect legible at distance and gives each element a
 * recognisable internal structure - {@code accent} supplies the secondary detail in between.
 *
 * <p>Why every {@code DustOptions} is pre-built: the cast-circle implementations run once per
 * tick per casting player. Rebuilding a {@link Color} plus {@link Particle.DustOptions} on each of
 * those ticks is pure garbage. Each palette builds its three dust instances exactly once in a
 * {@code static final} map and hands the same instance back forever, so a spell that asks for
 * {@link Palette#coreDust()} every tick allocates nothing at all.
 *
 * <p>Deliberately depends on nothing but {@link Color}, {@link Particle.DustOptions} and
 * {@link Element}. It must never reference {@code Utils} particle constants or {@code ParticleCaster}:
 * those sit behind {@code MinecraftVersion}'s static initialiser, which needs a live server and
 * NPEs in a unit test.
 */
public final class ElementPalette {

    /** Largest and brightest dust size, reserved for the element's identity colour. */
    private static final float CORE_SIZE = 1.0f;
    /** Mid detail dust size, for the secondary colour. */
    private static final float ACCENT_SIZE = 0.6f;
    /** Smallest dust size, for edges, ground rings and shadow. */
    private static final float DEEP_SIZE = 0.4f;

    private static final Map<Element, Palette> PALETTES;

    static {
        Map<Element, Palette> built = new EnumMap<>(Element.class);
        built.put(Element.FIRE, new Palette(Element.FIRE, 255, 140, 0, 255, 210, 74, 140, 47, 0));
        built.put(Element.WATER, new Palette(Element.WATER, 47, 127, 212, 127, 212, 255, 16, 56, 107));
        built.put(Element.EARTH, new Palette(Element.EARTH, 160, 82, 45, 217, 143, 95, 78, 40, 17));
        built.put(Element.AIR, new Palette(Element.AIR, 234, 246, 255, 255, 255, 255, 159, 196, 216));
        built.put(Element.LIGHT, new Palette(Element.LIGHT, 255, 255, 135, 255, 253, 224, 212, 184, 58));
        built.put(Element.DARK, new Palette(Element.DARK, 106, 13, 173, 177, 76, 224, 43, 4, 56));
        // NONE stays neutral grey on purpose so it never reads as a real element.
        built.put(Element.NONE, new Palette(Element.NONE, 180, 180, 180, 234, 234, 234, 85, 95, 102));
        PALETTES = Collections.unmodifiableMap(built);
    }

    private ElementPalette() {
    }

    /**
     * Returns the palette for the given element.
     *
     * @param element the element; {@code null} resolves to {@link Element#NONE}'s palette
     * @return the shared, immutable palette instance for that element
     */
    public static Palette of(Element element) {
        Palette palette = (element == null) ? null : PALETTES.get(element);
        return (palette == null) ? PALETTES.get(Element.NONE) : palette;
    }

    /**
     * Returns the palette for an element named in a string (spell yml, research node, recipe).
     * Parsing is case-insensitive and tolerates surrounding whitespace. Anything unrecognised -
     * including {@code null} - falls back to {@link Element#NONE}'s palette rather than throwing,
     * because a typo in a config file must not break a spell mid-cast.
     *
     * @param elementName the element name, e.g. {@code "fire"}
     * @return the shared, immutable palette instance for that element
     */
    public static Palette of(String elementName) {
        if (elementName == null) {
            return PALETTES.get(Element.NONE);
        }
        String key = elementName.trim().toUpperCase(Locale.ROOT);
        for (Element candidate : Element.values()) {
            if (candidate.name().equals(key) || candidate.getColorlessName().toUpperCase(Locale.ROOT).equals(key)) {
                return PALETTES.get(candidate);
            }
        }
        return PALETTES.get(Element.NONE);
    }

    /**
     * An immutable element colour scheme with pre-built dust.
     *
     * <p>Instances are created once during {@code ElementPalette}'s static initialisation and are
     * shared; never construct one from a spell.
     */
    public static final class Palette {

        private final Element element;
        private final Color core;
        private final Color accent;
        private final Color deep;
        private final Particle.DustOptions coreDust;
        private final Particle.DustOptions accentDust;
        private final Particle.DustOptions deepDust;

        private Palette(Element element,
                         int coreR, int coreG, int coreB,
                         int accentR, int accentG, int accentB,
                         int deepR, int deepG, int deepB) {
            this.element = element;
            this.core = Color.fromRGB(coreR, coreG, coreB);
            this.accent = Color.fromRGB(accentR, accentG, accentB);
            this.deep = Color.fromRGB(deepR, deepG, deepB);
            this.coreDust = new Particle.DustOptions(this.core, CORE_SIZE);
            this.accentDust = new Particle.DustOptions(this.accent, ACCENT_SIZE);
            this.deepDust = new Particle.DustOptions(this.deep, DEEP_SIZE);
        }

        /**
         * @return the element this palette belongs to; never {@code null}
         */
        public Element element() {
            return element;
        }

        /**
         * @return the brightest shade, used for the spell's identity effect
         */
        public Color core() {
            return core;
        }

        /**
         * @return the secondary shade, used for detail inside the core
         */
        public Color accent() {
            return accent;
        }

        /**
         * @return the darkest shade, used for edges, ground rings and shadow
         */
        public Color deep() {
            return deep;
        }

        /**
         * @return the pre-built core dust; the same instance on every call
         */
        public Particle.DustOptions coreDust() {
            return coreDust;
        }

        /**
         * @return the pre-built accent dust; the same instance on every call
         */
        public Particle.DustOptions accentDust() {
            return accentDust;
        }

        /**
         * @return the pre-built deep dust; the same instance on every call
         */
        public Particle.DustOptions deepDust() {
            return deepDust;
        }
    }
}