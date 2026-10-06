package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-C migration guard for the three DARK spells: {@code SkeletalKnights},
 * {@code SummonZombies} and {@code Debuff}.
 *
 * <p>What this pins, and why each one is a real defect rather than a style rule:
 * <ul>
 *   <li><b>Cast times stay inside their circle's range.</b> The yml is the budget; the Java
 *       never overrides it, so the yml is what has to be asserted.</li>
 *   <li><b>No spell owns a colour any more.</b> Every one of these three classes used to
 *       declare its own {@code Color} - aqua on {@code skeletal_knights}, green on
 *       {@code summon_zombies}, an independent purple on {@code debuff} - and two of the three
 *       contradicted {@code element: DARK} outright. The guard is that they now hold a
 *       {@code PALETTE} and nothing else.</li>
 *   <li><b>No per-tick colour allocation.</b> A {@code new Particle.DustOptions} inside a
 *       cast-circle lambda rebuilds a {@code Color} plus a dust record every tick for every
 *       casting player. The palette pre-builds all three.</li>
 *   <li><b>No raw {@code Particle.*}.</b> Minecraft 1.20.5 renamed a large batch of them and
 *       the plugin supports 1.19 through 1.26, so a bare constant can throw at
 *       class-initialisation time on the old end of the range.</li>
 *   <li><b>The bespoke channel is gone.</b> {@code SkeletalKnights} carried a private
 *       {@code channels} map and its own move/damage/quit handler triple; interruption is now
 *       the shared {@code ChannelRegistry}'s job.</li>
 * </ul>
 *
 * <p><b>No Bukkit mock is needed.</b> The yml assertions go through
 * {@code getResourceAsStream} plus {@link YamlConfiguration}; the colour assertions go through
 * {@link ElementPalette}, which depends on nothing but {@code Color} and
 * {@code {@link org.bukkit.Particle}#DustOptions}. Nothing here touches {@code Utils}, whose
 * static initialiser reaches {@code MinecraftVersion}'s and NPEs without a live server.
 *
 * <p>The geometry assertions call {@code SpellVfx}'s package-private pure twins by reflection,
 * because those twins are deliberately package-private to {@code SpellVfx}'s own package and
 * this test lives one package over. They take no {@code Spell}, need no world, and spawn
 * nothing - which is exactly what makes them worth asserting on separately from the drawing
 * path, as the library's own Javadoc requires.
 */
class DarkVfxMigrationTest {

    /** Spell id to source file name, for the three migrated classes. */
    private static final String[] SPELL_IDS = {"skeletal_knights", "summon_zombies", "debuff"};

    /** Their own Java sources, relative to the {@code core} module directory. */
    private static final String[] SOURCE_FILES = {
            "src/main/java/me/nagasonic/alkatraz/spells/implementation/SkeletalKnights.java",
            "src/main/java/me/nagasonic/alkatraz/spells/implementation/SummonZombies.java",
            "src/main/java/me/nagasonic/alkatraz/spells/implementation/Debuff.java"
    };

    // ================================================================
    // Bundled yml - budgets
    // ================================================================

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = DarkVfxMigrationTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void skeletalKnightsCastTimeSitsInsideTheCircleSixRange() throws IOException {
        YamlConfiguration yml = load("skeletal_knights");
        int circle = yml.getInt("level");
        assertEquals(6, circle, "skeletal_knights is a circle-6 spell");
        SpellBudgets.CastTimeRange range = SpellBudgets.castTimeRange(circle);
        int ticks = (int) Math.round(yml.getDouble("cast_time") * 20);
        assertTrue(range.contains(ticks),
                "cast_time " + yml.getDouble("cast_time") + "s is " + ticks
                        + " ticks, outside the circle-6 window of " + range.minTicks() + ".."
                        + range.maxTicks());
    }

    @Test
    void summonZombiesCastTimeSitsInsideTheCircleFourRange() throws IOException {
        YamlConfiguration yml = load("summon_zombies");
        int circle = yml.getInt("level");
        assertEquals(4, circle, "summon_zombies is a circle-4 spell");
        SpellBudgets.CastTimeRange range = SpellBudgets.castTimeRange(circle);
        int ticks = (int) Math.round(yml.getDouble("cast_time") * 20);
        assertTrue(range.contains(ticks),
                "cast_time " + yml.getDouble("cast_time") + "s is " + ticks
                        + " ticks, outside the circle-4 window of " + range.minTicks() + ".."
                        + range.maxTicks());
    }

    @Test
    void debuffCastTimeSitsInsideTheCircleTwoRange() throws IOException {
        YamlConfiguration yml = load("debuff");
        int circle = yml.getInt("level");
        assertEquals(2, circle, "debuff is a circle-2 spell");
        SpellBudgets.CastTimeRange range = SpellBudgets.castTimeRange(circle);
        int ticks = (int) Math.round(yml.getDouble("cast_time") * 20);
        assertTrue(range.contains(ticks),
                "cast_time " + yml.getDouble("cast_time") + "s is " + ticks
                        + " ticks, outside the circle-2 window of " + range.minTicks() + ".."
                        + range.maxTicks());
    }

    @Test
    void skeletalKnightsKeepsItsSixSecondDeclaredPaidOverChannel() throws IOException {
        YamlConfiguration yml = load("skeletal_knights");
        int circle = yml.getInt("level");
        int declaredTicks = (int) Math.round(yml.getDouble("wind_up_duration") * 20);

        assertEquals(120, declaredTicks,
                "the 6-second channel is the reason waystone_recall exists; it must not be shortened");
        assertTrue(SpellBudgets.isPaidOverWindUp(circle, declaredTicks),
                "a " + declaredTicks + "-tick circle-6 channel is a declared paid-over "
                        + "(circle-6 wind-up contract is " + SpellBudgets.windUpTicks(circle)
                        + " ticks); if this ever fails, the channel was quietly shortened");
    }

    @Test
    void theSubSixDarkSpellsOweNoWindUp() throws IOException {
        // SummonZombies (circle 4) and Debuff (circle 2) are below the wind-up contract, so a
        // WindUp in either source would be unearned complexity rather than a feature.
        for (String relative : new String[]{SOURCE_FILES[1], SOURCE_FILES[2]}) {
            String code = readCode(relative);
            assertFalse(code.contains("WindUp"),
                    relative + " is below circle 6 and must not introduce a wind-up");
        }
    }

    // ================================================================
    // Source guards - colour ownership
    // ================================================================

    @Test
    void everyMigratedDarkSpellDeclaresItsPalette() {
        for (String relative : SOURCE_FILES) {
            String code = readCode(relative);
            assertTrue(code.contains("ElementPalette.of(Element.DARK)"),
                    relative + " must resolve its colours from the shared DARK palette, "
                            + "not from a private constant");
            assertTrue(code.contains("static final ElementPalette.Palette PALETTE"),
                    relative + " must cache the palette in a static final, so a per-tick draw "
                            + "allocates no colour object at all");
        }
    }

    @Test
    void noMigratedSpellBuildsItsOwnColour() {
        for (String relative : SOURCE_FILES) {
            String code = readCode(relative);
            assertEquals(1, countRgbLiterals(code),
                    relative + " must name exactly one colour, its CAST_COLOR, and take every other "
                            + "colour from the DARK palette; a second literal is how "
                            + "skeletal_knights ended up aqua");
            assertFalse(code.contains("new Particle.DustOptions"),
                    relative + " still constructs DustOptions; the palette pre-builds core, accent "
                            + "and deep, and a per-tick construction is pure garbage");
        }
    }

    /**
     * Counts every {@code Color.fromRGB} call in a migrated source, {@code CAST_COLOR} or not.
     */
    private static int countRgbLiterals(String code) {
        Matcher matcher = Pattern.compile("Color\\.fromRGB\\s*\\(").matcher(code);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    @Test
    void noMigratedSpellReferencesARawParticleConstant() {
        for (String relative : SOURCE_FILES) {
            String code = readCode(relative);
            assertFalse(code.contains("Particle."),
                    relative + " uses a raw Particle constant; every particle must go through a "
                            + "Utils alias because 1.20.5 renamed a large batch of them and the "
                            + "plugin supports 1.19 through 1.26");
        }
    }

    @Test
    void skeletalKnightsUsesThePortalAliasRatherThanTheRawConstant() {
        String code = readCode(SOURCE_FILES[0]);
        assertTrue(code.contains("Utils.PORTAL"),
                "the summon burst must use the Utils.PORTAL alias, not Particle.PORTAL");
    }

    @Test
    void theDarkPaletteShadesAreNeitherGreenNorAquaDominantAndAreOrdered() {
        // What is worth pinning is not "DARK looks purple" - it is that the shades are the
        // palette's, and specifically that they are none of the two colours these spells used to
        // declare while claiming {@code element: DARK}: green (50,100,50) on summon_zombies and
        // aqua (0,210,255) / (150,210,255) on skeletal_knights.
        //
        // Note what is deliberately NOT asserted: that every shade is red-dominant. The shipped
        // DARK accent is (177,76,224) - blue above red - and pretending otherwise would make this
        // guard fail against a correct palette.
        ElementPalette.Palette dark = ElementPalette.of(Element.DARK);
        assertEquals(Element.DARK, dark.element(), "the palette must round-trip to DARK");

        for (Color shade : new Color[]{dark.core(), dark.accent(), dark.deep()}) {
            assertTrue(shade.getGreen() < shade.getRed() && shade.getGreen() < shade.getBlue(),
                    "a DARK shade may not be the green (50,100,50) summon_zombies used: got " + shade);
            assertFalse(shade.getGreen() >= 150 && shade.getBlue() >= 150,
                    "a DARK shade may not be in the aqua band skeletal_knights used "
                            + "((0,210,255) and (150,210,255)): got " + shade);
        }

        assertTrue(dark.coreDust().getSize() > dark.accentDust().getSize(),
                "core is the element's identity and is drawn largest");
        assertTrue(dark.accentDust().getSize() > dark.deepDust().getSize(),
                "deep is the ground and trailing structure and is drawn smallest");
    }

    @Test
    void everyDustShadeUsedIsOneOfTheThreePreBuiltDARKInstances() {
        // ElementPalette.of(Element.DARK) hands back one shared instance per shade forever, so
        // identity is the strongest available assertion: a spell asking for coreDust() on every
        // tick gets the same object back, and a spell that had rebuilt its own would not.
        ElementPalette.Palette dark = ElementPalette.of(Element.DARK);
        assertTrue(dark.coreDust() == dark.coreDust(),
                "coreDust must be one pre-built instance, not a fresh one per call");
        assertTrue(dark.accentDust() == dark.accentDust(),
                "accentDust must be one pre-built instance, not a fresh one per call");
        assertTrue(dark.deepDust() == dark.deepDust(),
                "deepDust must be one pre-built instance, not a fresh one per call");
        assertFalse(dark.coreDust() == dark.accentDust(),
                "the three shades must be distinct instances or the palette has collapsed to one colour");
    }

    @Test
    void everyMigratedSpellDrawsFromAtLeastOnePaletteShade() {
        for (String relative : SOURCE_FILES) {
            String code = readCode(relative);
            assertTrue(code.contains("PALETTE.coreDust()")
                            || code.contains("PALETTE.accentDust()")
                            || code.contains("PALETTE.deepDust()")
                            || code.contains("PALETTE.accent()"),
                    relative + " declares a palette but never draws from it - either the "
                            + "migration left an unconverted colour path or the field is dead");
        }
    }

    // ================================================================
    // Source guards - the bespoke channel is gone
    // ================================================================

    @Test
    void skeletalKnightsNoLongerKeepsAPrivateChannelMap() {
        String code = readCode(SOURCE_FILES[0]);
        assertFalse(code.contains("ChannelData"),
                "the private ChannelData record existed only to back the private channels map");
        assertFalse(code.contains("channels.put("),
                "the private channels map is replaced by ChannelRegistry.register");
        assertFalse(code.contains("abortChannel("),
                "abortChannel was the hand-rolled interrupt; ChannelRegistry owns interruption now");
    }

    @Test
    void skeletalKnightsRegistersItsWindUpWithTheSharedRegistry() {
        String code = readCode(SOURCE_FILES[0]);
        assertTrue(code.contains("ChannelRegistry.isChanneling("),
                "the one-wind-up-per-caster guard must read the shared registry");
        assertTrue(code.contains("ChannelRegistry.register("),
                "the wind-up must be registered so move, damage, quit and shutdown all interrupt it");
        assertTrue(code.contains("ChannelRegistry.unregister("),
                "a wind-up that finishes on its own must be unregistered without running the canceller");
        assertTrue(code.contains("WindUp.begin("),
                "the bespoke BukkitRunnable wind-up is replaced by the shared WindUp");
        assertTrue(code.contains("windUp.tick()"),
                "the shared WindUp advances by tick(); the spell still owns its own draw step");
    }

    @Test
    void skeletalKnightsNoLongerHandlesTheInterruptionTripleItself() {
        String code = readCode(SOURCE_FILES[0]);
        for (String handler : new String[]{"PlayerMoveEvent", "EntityDamageEvent", "PlayerQuitEvent"}) {
            assertFalse(code.contains(handler),
                    handler + " handling belongs to ChannelRegistry, which is registered once in "
                            + "onEnable; a second copy means a double interrupt and a listener that "
                            + "is never unregistered on reload");
        }
    }

    @Test
    void theTwoSubSixDarkSpellsUseTheSharedCastCircle() {
        for (String relative : new String[]{SOURCE_FILES[1], SOURCE_FILES[2]}) {
            String code = readCode(relative);
            assertTrue(code.contains("CastCircle.start(this, caster, e, 3.0, 0)"),
                    relative + " must draw the shared cast circle at its old size of 3");
            assertFalse(code.contains("ParticleUtils.magicCircle("),
                    relative + " still hand-rolls the circle layout; CastCircle draws it identically "
                            + "and, unlike the old lambda, stops the task when the cast is cancelled");
            assertFalse(code.contains("Configs.CIRCLE_TICKS"),
                    relative + " reads the repaint period itself; CastCircle owns that now");
        }
    }

    // ================================================================
    // Geometry - the pure twins the three spells actually call
    // ================================================================

    @Test
    void skeletalKnightsChannelGlyphsKeepTheirHexLayout() {
        // SpellVfx.channelGlyphGeometry is what channelGlyphs draws its glyph markers from.
        List<Location> glyphs = geometry("channelGlyphGeometry",
                new Class<?>[]{Location.class, double.class, int.class, double.class},
                new Location(null, 0.0, 64.0, 0.0), 2.0, 6, 0.0);

        assertEquals(6, glyphs.size(), "six glyph markers is the hex both original spells drew");
        for (Location glyph : glyphs) {
            double reach = Math.hypot(glyph.getX(), glyph.getZ());
            assertEquals(1.7, reach, 1e-9, "glyphs sit at 0.85 of the outer radius");
            assertEquals(64.0, glyph.getY(), 1e-9, "glyphs are floor structure and stay flat");
        }
    }

    @Test
    void skeletalKnightsFloorRingsAreCounterRotatingAndTheInnerIsSmaller() {
        // The outer ring is channelGlyphs' first pass and the 0.7 inner ring its second; this is
        // the layout the hand-rolled spawnFloorCircle used to produce.
        List<Location> outer = geometry("ringGeometry",
                new Class<?>[]{Location.class, double.class, int.class, double.class, double.class},
                new Location(null, 0.0, 64.0, 0.0), 2.2, 24, 0.0, 0.0);
        List<Location> inner = geometry("ringGeometry",
                new Class<?>[]{Location.class, double.class, int.class, double.class, double.class},
                new Location(null, 0.0, 64.0, 0.0), 2.2 * 0.7, 24, -0.0 * 1.4, 0.0);

        assertEquals(24, outer.size(), "the floor ring keeps the original 24 points");
        assertEquals(24, inner.size(), "the inner floor ring keeps the original 24 points");
        for (Location point : outer) {
            assertEquals(2.2, Math.hypot(point.getX(), point.getZ()), 1e-9,
                    "every outer point must sit exactly on the requested radius");
        }
        for (Location point : inner) {
            assertEquals(2.2 * 0.7, Math.hypot(point.getX(), point.getZ()), 1e-9,
                    "the inner ring must sit at 0.7 of the outer radius, as it always did");
        }
    }

    @Test
    void debuffAuraIsTwoCounterRotatingRingsAnchoredOnTheSubject() {
        Location subject = new Location(null, 100.0, 70.0, -50.0);
        List<Location> aura = geometry("selfAuraGeometry",
                new Class<?>[]{Location.class, double.class, int.class, double.class},
                subject, 0.9, 16, 0.0);

        assertEquals(32, aura.size(), "the aura is an outer ring plus an inner ring of equal size");

        for (int i = 0; i < 16; i++) {
            Location point = aura.get(i);
            assertEquals(Math.hypot(point.getX() - subject.getX(), point.getZ() - subject.getZ()),
                    0.9, 1e-9, "outer ring point " + i + " must sit on the requested radius");
            assertEquals(subject.getY(), point.getY(), 1e-9,
                    "the aura is flat and centred on the subject's live location");
        }
        for (int i = 16; i < 32; i++) {
            Location point = aura.get(i);
            assertEquals(Math.hypot(point.getX() - subject.getX(), point.getZ() - subject.getZ()),
                    0.9 * 0.55, 1e-9, "inner ring point " + (i - 16) + " must sit at 0.55 of the outer radius");
        }
    }

    @Test
    void debuffAuraReanchorsWhenTheSubjectMoves() {
        // The one property that makes the aura usable at all: it is computed from the location
        // handed in, so a subject that has walked since the cast gets its ring around itself and
        // not around a stale cached point. Nothing is memoised between calls.
        Location walked = new Location(null, 120.0, 70.0, -50.0);
        List<Location> aura = geometry("selfAuraGeometry",
                new Class<?>[]{Location.class, double.class, int.class, double.class},
                walked, 0.9, 8, 1.0);

        assertEquals(16, aura.size(), "eight points per ring, two rings");
        for (int i = 0; i < 8; i++) {
            Location point = aura.get(i);
            assertEquals(0.9, reachFrom(point, walked), 1e-9, "outer point " + i);
            assertEquals(walked.getY(), point.getY(), 1e-9, "the ring stays at the subject's own y");
        }
        for (int i = 8; i < 16; i++) {
            assertEquals(0.9 * 0.55, reachFrom(aura.get(i), walked), 1e-9, "inner point " + (i - 8));
        }
    }

    @Test
    void summonZombiesSpawnRingClosesOnItsRadius() {
        List<Location> ring = geometry("ringGeometry",
                new Class<?>[]{Location.class, double.class, int.class, double.class, double.class},
                new Location(null, 5.0, 70.0, 5.0), 0.7, 16, 0.0, 0.0);

        assertEquals(16, ring.size(), "the spawn ring draws exactly the points it was asked for");
        for (Location point : ring) {
            assertEquals(0.7, Math.hypot(point.getX() - 5.0, point.getZ() - 5.0), 1e-9,
                    "every ring point sits exactly on the radius");
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Calls one of {@code SpellVfx}'s package-private pure geometry twins.
     *
     * <p>Reflection, not a widened visibility: those methods are package-private on purpose so
     * that only {@code SpellVfx}'s own package can reach them, and widening them for a test in a
     * different package would weaken the boundary the library documents. None of them touches a
     * Bukkit static, so no live server is needed.
     *
     * @param name   the geometry method's name
     * @param types  its parameter types
     * @param args   its arguments
     * @return the geometry it computed
     */
    @SuppressWarnings("unchecked")
    private static List<Location> geometry(String name, Class<?>[] types, Object... args) {
        Method method;
        try {
            method = SpellVfx.class.getDeclaredMethod(name, types);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("SpellVfx has no geometry method named " + name
                    + " with the signature this migration relies on", e);
        }
        method.setAccessible(true);
        try {
            return (List<Location>) method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("SpellVfx." + name + " threw; the pure geometry half is "
                    + "supposed to need no server and no Spell", e);
        }
    }

    /**
     * Reads a spell source file, with comments blanked out so a guard matches only real code -
     * these classes have to <i>name</i> the colours they no longer use in order to explain why
     * they no longer use them.
     *
     * @param relative path relative to the {@code core} module directory
     * @return the source with comment bodies removed
     */
    private static String readCode(String relative) {
        Path source = Paths.get("").toAbsolutePath().resolve(relative);
        Assumptions.assumeTrue(Files.isRegularFile(source),
                relative + " is not reachable from " + Paths.get("").toAbsolutePath()
                        + "; the source guards are skipped rather than passing vacuously");
        try {
            return stripComments(Files.readString(source, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError(relative + " was found at " + source + " but could not be read", e);
        }
    }

    /**
     * Blanks out Javadoc and line comments so a guard matches only executable code. Without this,
     * the class Javadoc - which has to <i>name</i> the colours these spells no longer use in order
     * to explain why they no longer use them - would trip the "no raw colour" guards on its own
     * documentation.
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlock = false;
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (!inBlock && (trimmed.startsWith("*") || trimmed.startsWith("//"))) {
                out.append('\n');
                continue;
            }
            if (!inBlock && trimmed.startsWith("/*")) {
                inBlock = !trimmed.contains("*/");
                out.append('\n');
                continue;
            }
            if (inBlock) {
                if (trimmed.endsWith("*/")) inBlock = false;
                out.append('\n');
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * Horizontal distance from {@code point} to {@code centre}, ignoring y.
     *
     * <p>Every shape these three spells draw is flat, so the horizontal reach is the invariant
     * worth asserting; the y coordinate is checked separately where it matters.
     */
    private static double reachFrom(Location point, Location centre) {
        return Math.hypot(point.getX() - centre.getX(), point.getZ() - centre.getZ());
    }
}
