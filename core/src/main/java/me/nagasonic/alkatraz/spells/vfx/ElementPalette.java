package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.api.Element;
import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** How far {@code accent} is blended from the base colour toward white. */
    private static final double ACCENT_BLEND = 0.35;
    /** How far {@code deep} is blended from the base colour toward black. */
    private static final double DEEP_BLEND = 0.5;

    private static final Map<Element, Palette> PALETTES;

    /**
     * Per-spell palettes built by {@link #of(Color)}, keyed on the packed RGB int.
     *
     * <p>Written only during a spell's own emission, so it is bounded by the number of distinct
     * colours declared in source, not by how often a particle is dispatched.
     */
    private static final Map<Integer, Palette> CUSTOM = new ConcurrentHashMap<>();

    static {
        Map<Element, Palette> built = new EnumMap<>(Element.class);
        built.put(Element.FIRE, new Palette(Element.FIRE, rgb(255, 140, 0), rgb(255, 210, 74), rgb(140, 47, 0)));
        built.put(Element.WATER, new Palette(Element.WATER, rgb(47, 127, 212), rgb(127, 212, 255), rgb(16, 56, 107)));
        built.put(Element.EARTH, new Palette(Element.EARTH, rgb(160, 82, 45), rgb(217, 143, 95), rgb(78, 40, 17)));
        built.put(Element.AIR, new Palette(Element.AIR, rgb(234, 246, 255), rgb(255, 255, 255), rgb(159, 196, 216)));
        built.put(Element.LIGHT, new Palette(Element.LIGHT, rgb(255, 255, 135), rgb(255, 253, 224), rgb(212, 184, 58)));
        built.put(Element.DARK, new Palette(Element.DARK, rgb(106, 13, 173), rgb(177, 76, 224), rgb(43, 4, 56)));
        // NONE stays neutral grey on purpose so it never reads as a real element.
        built.put(Element.NONE, new Palette(Element.NONE, rgb(180, 180, 180), rgb(234, 234, 234), rgb(85, 95, 102)));
        PALETTES = Collections.unmodifiableMap(built);
    }

    private ElementPalette() {
    }

    private static int rgb(int r, int g, int b) {
        return Color.fromRGB(r, g, b).asRGB();
    }

    /**
     * Returns a palette built around a single caller-supplied colour.
     *
     * <p>This is how a spell overrides the colour its element would otherwise give it. The spell
     * declares one {@link Color} and the other two shades are derived from it, so a spell author
     * tunes one value instead of three and cannot leave the triple internally inconsistent.
     *
     * <p>The derivation is a blend toward white for {@code accent} and toward black for {@code deep},
     * at the same ratios the built-in element triples already use, which is why a spell that passes
     * its element's {@code core} colour still looks like it did before.
     *
     * <p>Results are cached on {@link Color#asRGB()}: {@code Color} has no {@code equals}, so a
     * {@code Color}-keyed map would never hit and every per-tick call would rebuild three
     * {@link Particle.DustOptions}. Keying the packed int means a spell asking for its colour every
     * tick allocates nothing, which is the same guarantee the static element map provides.
     *
     * @param color the spell's own colour; {@code null} resolves to {@link Element#NONE}'s palette
     * @return a shared, immutable palette for that colour
     */
    public static Palette of(Color color) {
        if (color == null) {
            return PALETTES.get(Element.NONE);
        }
        int rgb = color.asRGB();
        Palette cached = CUSTOM.get(rgb);
        if (cached != null) {
            return cached;
        }
        Palette built = new Palette(Element.NONE, rgb(color.getRed(), color.getGreen(), color.getBlue()),
                blend(color, 255, ACCENT_BLEND), blend(color, 0, DEEP_BLEND));
        Palette raced = CUSTOM.putIfAbsent(rgb, built);
        return (raced == null) ? built : raced;
    }

    /**
     * Blends {@code color} toward {@code target} by {@code ratio}, clamping each channel to 0-255.
     */
    private static int blend(Color color, int target, double ratio) {
        int r = (int) Math.round(color.getRed() + (target - color.getRed()) * ratio);
        int g = (int) Math.round(color.getGreen() + (target - color.getGreen()) * ratio);
        int b = (int) Math.round(color.getBlue() + (target - color.getBlue()) * ratio);
        return Color.fromRGB(clampChannel(r), clampChannel(g), clampChannel(b)).asRGB();
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
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
     * <p>Element palettes are created once during {@code ElementPalette}'s static initialisation.
     * A spell that wants its own colour never constructs one directly either: it declares a
     * {@link Color} constant and passes it to {@link #of(Color)}, which caches the result.
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
                        int coreRgb,
                        int accentRgb,
                        int deepRgb) {
            this.element = element;
            this.core = Color.fromRGB(coreRgb);
            this.accent = Color.fromRGB(accentRgb);
            this.deep = Color.fromRGB(deepRgb);
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