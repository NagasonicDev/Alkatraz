package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
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
 * Wave-C migration guard for the one EARTH and three NONE spells moved onto the
 * shared VFX library: {@code earthsplitter} (Fissure), {@code warp}, {@code disguise}
 * and {@code blink}.
 *
 * <h2>Why most of this reads source text</h2>
 * Loading any of these four classes initialises its superclass chain, and
 * {@code Utils}'s static initialiser reaches {@code MinecraftVersion}'s, which calls
 * {@code Alkatraz.getInstance().getServer()} and NPEs without a live server. So the
 * assertions about <i>which</i> palette a class declares and <i>how</i> it builds its
 * cast circle are made against the file on disk rather than through reflection. That
 * is a deliberate trade: a source-text assertion cannot see a computed value, but it
 * can see exactly the defect this wave is about - a hand-rolled
 * {@code scheduleSyncRepeatingTask} block, a {@code new Particle.DustOptions} in a
 * per-tick lambda, a raw {@code Particle.X} that does not resolve on 1.19.
 *
 * <p>The two yml-driven checks and the geometry checks are real: they read the bundled
 * resource and they call the package-private pure-geometry twins, neither of which
 * touches Bukkit statics.
 */
class EarthNoneVfxMigrationTest {

    /** Spell id to the Java class that implements it. */
    private static final String[] EARTH_NONE_SPELLS =
            {"earthsplitter", "warp", "disguise", "blink"};

    private static final String[] EARTH_NONE_CLASSES =
            {"Fissure", "Warp", "Disguise", "Blink"};

    /**
     * Every {@code Particle} constant this plugin must reach through a {@code Utils}
     * alias, because Minecraft 1.20.5 renamed a large batch of them and the plugin
     * supports 1.19 through 1.26. {@code DustOptions} is excluded deliberately: it is
     * a type, not a particle constant, and it did not move.
     */
    private static final String RAW_PARTICLE_PATTERN = "Particle\\.(?!DustOptions)[A-Z]";

    // ================================================================
    // YML - no Bukkit mock needed
    // ================================================================

    @Test
    void everyMigratedSpellSitsInsideItsCirclesCastTimeRange() throws IOException {
        for (String id : EARTH_NONE_SPELLS) {
            YamlConfiguration yml = load(id);
            int circle = yml.getInt("level");
            int castTicks = (int) Math.round(yml.getDouble("cast_time") * 20.0);
            SpellBudgets.CastTimeRange range = SpellBudgets.castTimeRange(circle);
            assertTrue(range.contains(castTicks),
                    id + " declares cast_time " + yml.getDouble("cast_time") + "s (" + castTicks
                            + " ticks) at circle " + circle + ", outside " + range);
        }
    }

    /**
     * The migration must not have quietly touched a spell's identity: element, circle,
     * click code and mana all stay where they were.
     */
    @Test
    void migrationLeftEverySpellsIdentityKeysAlone() throws IOException {
        YamlConfiguration fissure = load("earthsplitter");
        assertEquals("EARTH", fissure.getString("element"), "earthsplitter must stay EARTH");
        assertEquals(5, fissure.getInt("level"), "earthsplitter must stay circle 5");
        assertEquals(380, fissure.getInt("terrain_budget"),
                "earthsplitter's circle-5 terrain budget cap must survive the VFX migration");

        YamlConfiguration warp = load("warp");
        assertEquals("NONE", warp.getString("element"), "warp must stay NONE");
        assertEquals(6, warp.getInt("level"), "warp must stay circle 6");
        assertEquals(4166, warp.getInt("gui_custom_model_data"),
                "warp's wave-3 gui_custom_model_data collision repair must not be reverted");

        assertEquals("NONE", load("disguise").getString("element"), "disguise must stay NONE");
        assertEquals(3, load("disguise").getInt("level"), "disguise must stay circle 3");
        assertEquals("NONE", load("blink").getString("element"), "blink must stay NONE");
        assertEquals(4, load("blink").getInt("level"), "blink must stay circle 4");
    }

    /**
     * Warp's 6-second channel is a <b>declared paid-over</b>, not an oversight: at circle
     * 6 the standard wind-up contract is 20 ticks, and {@code waystone_recall} exists
     * precisely so nobody has to sit through it. A migration that quietly shortened
     * the channel to the circle-6 standard would remove the reason warp exists.
     */
    @Test
    void warpsSixSecondChannelRemainsADeclaredPaidOver() throws IOException {
        YamlConfiguration warp = load("warp");
        int seconds = warp.getInt("wind_up_duration");
        assertEquals(6, seconds, "warp must keep its declared six-second channel");
        int ticks = seconds * 20;
        assertTrue(SpellBudgets.isPaidOverWindUp(6, ticks),
                "warp's " + ticks + "-tick channel is no longer a paid-over at circle 6");

        assertTrue(WindUp.requiresWindUp("warp", 6),
                "warp is not on the wind-up exemption list, so it must still owe a wind-up");
        assertFalse(WindUp.requiresWindUp("warp", 5),
                "wind-up policy is circle-gated; below 6 there is no contract to owe");
    }

    /**
     * The other three sit below circle 6 and must not have acquired a wind-up as part of
     * a VFX migration.
     */
    @Test
    void subCircleSixMigratedSpellsOweNoWindUp() {
        assertFalse(WindUp.requiresWindUp("earthsplitter", 5),
                "earthsplitter is circle 5 and must never owe a wind-up");
        assertFalse(WindUp.requiresWindUp("disguise", 3),
                "disguise is circle 3 and must never owe a wind-up");
        assertFalse(WindUp.requiresWindUp("blink", 4),
                "blink is circle 4 and must never owe a wind-up");
    }

    // ================================================================
    // Source text - the actual migration contract
    // ================================================================

    @Test
    void everyMigratedClassDeclaresItsElementPalette() throws IOException {
        for (String simple : EARTH_NONE_CLASSES) {
            String source = sourceOf(simple);
            assertTrue(source.contains("private static final ElementPalette.Palette PALETTE"),
                    simple + " must declare a static final PALETTE off ElementPalette");
            assertTrue(source.contains("ElementPalette.of(Element."),
                    simple + " must build its PALETTE from its own Element, not from a literal");
        }
    }

    @Test
    void noMigratedClassConstructsAColourPerTick() throws IOException {
        for (String simple : EARTH_NONE_CLASSES) {
            String source = sourceOf(simple);
            assertFalse(source.contains("new Particle.DustOptions"),
                    simple + " must take its dust from a pre-built ElementPalette shade, never "
                            + "constructing Particle.DustOptions itself");
            int literals = countRgbLiterals(source);
            assertTrue(literals <= 1,
                    simple + " must name at most one colour, its CAST_COLOR; every other colour "
                            + "comes from a pre-built ElementPalette shade, and a second literal "
                            + "is a colour the single declaration cannot reach");
        }
    }

    /**
     * Fissure used to draw its trench lip and its cast circle in the <i>same</i> colour
     * {@code (139, 90, 43)}, at two different dust sizes, so the circle and the spell
     * effect read as the same shade. They must now be different palette shades.
     */
    @Test
    void fissuresEdgeAndCircleDustAreNoLongerTheSameShade() throws IOException {
        String source = sourceOf("Fissure");
        assertTrue(source.contains("EDGE_DUST = PALETTE.coreDust()"),
                "the trench's leading lip is the information and wants the core shade");
        assertTrue(source.contains("CIRCLE_DUST = PALETTE.accentDust()"),
                "the interior dust must be a different shade from the lip, not the same brown");
        assertFalse(source.contains("EDGE_DUST = PALETTE.accentDust()"),
                "if edge and circle share a shade the fissure is one flat colour again");
    }

    @Test
    void noMigratedClassKeepsARawParticleConstant() throws IOException {
        for (String simple : EARTH_NONE_CLASSES) {
            String source = sourceOf(simple);
            assertFalse(java.util.regex.Pattern.compile(RAW_PARTICLE_PATTERN).matcher(source).find(),
                    simple + " still references a raw Particle.* constant; every particle must go "
                            + "through a Utils alias so 1.19 does not fail at class-initialisation");
        }
    }

    @Test
    void subCircleSixSpellsDelegateTheirCastCircleToTheSharedBuilder() throws IOException {
        for (String simple : new String[]{"Fissure", "Disguise", "Blink"}) {
            String source = sourceOf(simple);
            assertTrue(source.contains("CastCircle.start(this, caster, e"),
                    simple + " must build its cast circle with CastCircle.start");
            assertFalse(source.contains("ParticleUtils.magicCircle"),
                    simple + " still hand-rolls ParticleUtils.magicCircle; the shared layout is "
                            + "CastCircle's and the two are asserted offset-for-offset equivalent");
            assertFalse(source.contains("scheduleSyncRepeatingTask"),
                    simple + " still schedules its own repeating task; CastCircle owns the lifecycle");
        }
    }

    /**
     * Warp is the cross-element half of the clone pair: its channel ritual now goes
     * through the same builder {@code skeletal_knights} uses, in its own element colour.
     */
    @Test
    void warpDrawsItsChannelRitualWithTheSharedGlyphBuilder() throws IOException {
        String source = sourceOf("Warp");
        assertTrue(source.contains("SpellVfx.channelGlyphs("),
                "warp must draw its ritual with SpellVfx.channelGlyphs, the builder that exists "
                        + "for the warp/skeletal_knights pair");
        assertEquals(1, countRgbLiterals(source),
                "warp's eleven hand-picked aqua constants, byte-identical to skeletal_knights', "
                        + "must be gone; the one colour it keeps is its CAST_COLOR declaration");
        for (String cloned : new String[]{"PHASE_AQUA", "PHASE_LIGHT", "PHASE_DEEP", "FLOOR_LIGHT",
                "FLOOR_AQUA", "CIRCLE_LIGHT", "CIRCLE_AQUA", "BURST_AQUA"}) {
            assertFalse(source.contains(cloned),
                    "warp's cloned constant " + cloned + " must be replaced by a palette shade");
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
    void warpHasNoPrivateChannelMapAndRegistersWithTheSharedRegistry() throws IOException {
        String source = sourceOf("Warp");
        assertFalse(source.contains("private static final Map<UUID, WarpData> channels"),
                "warp's private channel map must be replaced by ChannelRegistry");
        assertFalse(source.contains("WarpData"),
                "warp's per-channel record must be replaced by ChannelRegistry + WindUp");
        assertFalse(source.contains("abortChannel("),
                "the interruption path is ChannelRegistry's; warp must not keep a private copy");
        assertFalse(source.contains("@EventHandler"),
                "warp must no longer implement Listener; ChannelRegistry owns move/damage/quit");
        assertTrue(source.contains("ChannelRegistry.INSTANCE.register("),
                "warp must register its in-flight wind-up with the shared ChannelRegistry");
        assertTrue(source.contains("ChannelRegistry.isChanneling("),
                "warp's one-channel-per-caster guard must read the shared registry");
        assertTrue(source.contains("WindUp.begin(this, caster,"),
                "warp's bespoke channel animation must be the shared WindUp");
    }

    /**
     * The deferred cast is load-bearing: {@code castAction} cancels the cast and opens
     * the destination menu, and the wind-up must only begin once a destination has
     * actually been picked. A migration that started the wind-up in {@code castAction}
     * would charge six seconds of channel before the player had chosen anything.
     */
    @Test
    void warpsCastIsStillDeferredUntilADestinationIsPicked() throws IOException {
        String source = sourceOf("Warp");
        String castAction = between(source, "public void castAction(", "public void mobCastAction(");
        assertTrue(castAction.contains("cancelCast(caster)"),
                "warp must still cancel the framework cast before opening the picker");
        assertTrue(castAction.contains("new WarpDestinationMenu(caster, this).open()"),
                "warp's castAction must still only open the destination picker");
        assertFalse(castAction.contains("WindUp.begin"),
                "the wind-up must not start until beginChannel, i.e. until a destination is picked");

        String beginChannel = between(source, "public void beginChannel(", "private void completeTeleport(");
        assertTrue(beginChannel.contains("WindUp.begin(this, caster,"),
                "the wind-up belongs in beginChannel, the destination-picked path");
        assertTrue(beginChannel.contains("StatUtils.subMana("),
                "mana must still be spent when the destination is committed to");
        assertTrue(beginChannel.contains("config.windUpTicks("),
                "warp must keep its own declared 6-second duration rather than the circle standard");
    }

    /**
     * The wind-up's marker is the destination, not the caster. Without the marker the
     * charge is a glow around the player's head and the six-second pause has no visible
     * consequence - the player cannot tell where they are about to end up.
     */
    @Test
    void warpsWindUpIsAnchoredToTheDestination() throws IOException {
        String source = sourceOf("Warp");
        String beginChannel = between(source, "public void beginChannel(", "private void completeTeleport(");
        assertTrue(beginChannel.contains("windUpTicks, destination"),
                "the wind-up marker must be the destination so the landing spot is telegraphed");
    }

    // ================================================================
    // Geometry - the pure twins, no Bukkit statics
    // ================================================================

    /**
     * {@code SpellVfx.groundGeometry} is what Fissure's wind-up crack is drawn from.
     * A worldless centre is legitimate here (a unit test cannot build a world), and the
     * points must lie inside the declared length and half-width, in draw order, so a
     * prefix is the revealed portion.
     */
    @Test
    void groundGeometryPlacesEveryPointInsideItsDeclaredBounds() throws Exception {
        Location centre = new Location(null, 0.0, 64.0, 0.0);
        List<Location> points = groundGeometry(centre, 6.0, 2.0, 5, 3, 0.0);

        assertEquals(15, points.size(), "groundGeometry must return samples * lanes points");
        for (Location point : points) {
            assertEquals(64.0, point.getY(), 1e-9,
                    "a ground shape is flat; it must not rise or sink");
            assertTrue(Math.abs(point.getX()) <= 6.0 + 1e-9,
                    "a ground point drifted past the run direction's length");
            assertTrue(Math.abs(point.getZ()) <= 1.0 + 1e-9,
                    "a ground point drifted past half the declared width");
        }
        assertEquals(0.0, points.get(0).getX(), 1e-9,
                "the first station must sit at the centre line's origin");
        assertEquals(6.0, points.get(12).getX(), 1e-9,
                "the last station must sit at the full run length, so a rising advance "
                        + "reads as a crack opening");
    }

    /**
     * A lane count of zero must clamp to one rather than divide by zero, and a sample
     * count of zero must produce nothing at all.
     */
    @Test
    void groundGeometryClampsDegenerateCounts() throws Exception {
        Location centre = new Location(null, 0.0, 0.0, 0.0);
        assertTrue(groundGeometry(centre, 6.0, 2.0, 0, 3, 0.0).isEmpty(),
                "a ground shape with zero samples must draw nothing");
        assertEquals(5, groundGeometry(centre, 6.0, 2.0, 5, 0, 0.0).size(),
                "a ground shape with zero lanes must clamp to one lane per station");
        assertTrue(groundGeometry(null, 6.0, 2.0, 5, 3, 0.0).isEmpty(),
                "a ground shape with no centre must draw nothing");
    }

    /**
     * {@code SpellVfx.beamGeometry} is what Blink's origin-to-destination thread is drawn
     * from. The spine is {@code steps + 1} samples, each flanked by two ribbon points, so
     * the taper can be asserted rather than eyeballed.
     */
    @Test
    void beamGeometryTapersFromStartWidthToEndWidth() throws Exception {
        Location origin = new Location(null, 0.0, 0.0, 0.0);
        Location target = new Location(null, 10.0, 0.0, 0.0);
        List<Location> points = beamGeometry(origin, target, 4, 1.0, 3.0);

        assertEquals(10, points.size(),
                "a beam must return two ribbon points per spine sample, steps + 1 samples");

        // A horizontal beam's ribbon is offset along Z, so the origin's pair straddles
        // the spine at half the start width and the target's at half the wider end width.
        assertEquals(0.5, points.get(0).getZ(), 1e-9,
                "the origin's first ribbon must sit half the start width off the spine");
        assertEquals(-0.5, points.get(1).getZ(), 1e-9,
                "the origin's second ribbon must sit half the start width off the spine, "
                        + "on the other side");
        assertEquals(0.0, points.get(0).getX(), 1e-9,
                "the origin's ribbon must sit at the origin's distance along the beam");

        assertEquals(1.5, points.get(8).getZ(), 1e-9,
                "the target's first ribbon must sit half the end width off the spine");
        assertEquals(-1.5, points.get(9).getZ(), 1e-9,
                "the target's second ribbon must sit half the end width off the spine");
        assertEquals(10.0, points.get(8).getX(), 1e-9,
                "the target's ribbon must land on the target, not short of it");
    }

    @Test
    void beamGeometryRefusesToDrawWithNoEndpoints() throws Exception {
        Location origin = new Location(null, 0.0, 0.0, 0.0);
        assertTrue(beamGeometry(origin, null, 4, 1.0, 1.0).isEmpty(),
                "a beam with no target must draw nothing");
        assertTrue(beamGeometry(null, origin, 4, 1.0, 1.0).isEmpty(),
                "a beam with no origin must draw nothing");
        assertEquals(4, beamGeometry(origin, new Location(null, 0.0, 0.0, 0.0), 0, 1.0, 1.0).size(),
                "a beam with zero steps must clamp to a single spine sample - two ribbon points - "
                        + "not spin forever, and must still terminate on a zero-length beam");
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = EarthNoneVfxMigrationTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /**
     * Reads one spell class from disk. The candidates cover being run from the module
     * directory (surefire's working directory) and from the repository root.
     */
    private static String sourceOf(String simpleName) throws IOException {
        String relative = "src/main/java/me/nagasonic/alkatraz/spells/implementation/" + simpleName + ".java";
        Path[] candidates = {
                Paths.get(relative),
                Paths.get("core").resolve(relative),
                Paths.get("..").resolve("core").resolve(relative),
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("could not locate " + simpleName + ".java from "
                + Path.of("").toAbsolutePath());
    }

    /** The slice of a source file between two markers, for scoping one method's body. */
    private static String between(String source, String from, String to) {
        int start = source.indexOf(from);
        assertTrue(start >= 0, "source no longer contains the marker " + from);
        int end = source.indexOf(to, start);
        assertTrue(end > start, "source no longer contains the marker " + to + " after " + from);
        return source.substring(start, end);
    }

    /**
     * Calls a package-private pure-geometry twin on {@link SpellVfx}.
     *
     * <p>Reflection rather than a direct call because those twins are package-private
     * to {@code me.nagasonic.alkatraz.spells.vfx} and this test lives in the
     * {@code implementation} package. They are the ones worth asserting: they touch no
     * Bukkit static, so they run in a plain unit test.
     */
    @SuppressWarnings("unchecked")
    private static List<Location> groundGeometry(Location centre, double length, double width,
                                                 int samples, int lanes, double yawDegrees) throws Exception {
        Method method = SpellVfx.class.getDeclaredMethod("groundGeometry", Location.class,
                double.class, double.class, int.class, int.class, double.class);
        method.setAccessible(true);
        return (List<Location>) method.invoke(null, centre, length, width, samples, lanes, yawDegrees);
    }

    @SuppressWarnings("unchecked")
    private static List<Location> beamGeometry(Location origin, Location target, int steps,
                                               double startWidth, double endWidth) throws Exception {
        Method method = SpellVfx.class.getDeclaredMethod("beamGeometry", Location.class,
                Location.class, int.class, double.class, double.class);
        method.setAccessible(true);
        return (List<Location>) method.invoke(null, origin, target, steps, startWidth, endWidth);
    }
}