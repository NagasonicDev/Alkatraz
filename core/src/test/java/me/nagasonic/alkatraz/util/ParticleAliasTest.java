package me.nagasonic.alkatraz.util;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the cross-version particle alias table in {@link Utils}.
 *
 * <p><b>Why this test reads {@code Utils.java} as text instead of touching the fields.</b>
 * The alias fields are {@code static final} initialisers that call
 * {@code Utils.oldOrNew(..)} -> {@code Utils.hasNewMappings()} ->
 * {@code MinecraftVersion.getServerVersion()}, whose static initialiser calls
 * {@code Alkatraz.getInstance().getServer()}. In a plain JUnit run
 * {@code Alkatraz.instance} is {@code null}, so loading the {@code Utils} class throws
 * {@code ExceptionInInitializerError} (confirmed empirically, not assumed). Mocking a whole
 * Bukkit server just to read two enum constants would be far heavier than the thing under
 * test, and {@code ElementPaletteTest} already sets the precedent that version-independent
 * code must be reachable from a bare unit test.
 *
 * <p>So the invariant is asserted at the two levels that actually matter and that can be
 * reached without a server:
 * <ol>
 *   <li><b>Declaration drift</b> - the source of {@code Utils} is parsed and every alias is
 *       checked to be declared as {@code public static final Particle X = Particle.valueOf(
 *       oldOrNew("old", "new"))} with the exact pair this test expects. If somebody edits an
 *       alias in {@code Utils} and does not update the table here, this fails.</li>
 *   <li><b>Name resolvability</b> - both name slots are asserted against hardcoded constant
 *       sets transcribed from real jars. The {@code old} slot is additionally resolved live
 *       through {@link Particle#valueOf(String)}, which proves it against whichever
 *       {@code spigot-api} is actually on the test classpath.</li>
 * </ol>
 *
 * <p>The two pinned sets were transcribed by running {@code javap} over
 * {@code org.bukkit.Particle} in {@code spigot-api-1.19-R0.1-SNAPSHOT.jar} (the version
 * {@code core/pom.xml} pins) and {@code spigot-api-1.20.6-R0.1-SNAPSHOT.jar}. If a future
 * task bumps the test dependency, {@link #runtimeApiIsThe119ApiTheHardcodedSetAssumes()}
 * will fail and these sets should be regenerated from the new jar.
 */
class ParticleAliasTest {

    /**
     * Every alias field in {@code Utils}, mapped to its {@code oldOrNew} pair.
     * Key order mirrors declaration order in {@code Utils.java}.
     */
    private static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();

    static {
        // --- pre-existing aliases (renamed in 1.20.5) ---
        alias("DUST", "REDSTONE", "DUST");
        alias("EXPLOSION", "EXPLOSION_LARGE", "EXPLOSION");
        alias("SMOKE", "SMOKE_NORMAL", "SMOKE");
        alias("LARGE_SMOKE", "SMOKE_LARGE", "LARGE_SMOKE");
        alias("WITCH", "SPELL_WITCH", "WITCH");
        alias("ENTITY_EFFECT", "SPELL_MOB_AMBIENT", "ENTITY_EFFECT");
        alias("BLOCK", "BLOCK_CRACK", "BLOCK");
        alias("BLOCK_DUST", "BLOCK_DUST", "BLOCK");
        alias("SPLASH", "WATER_SPLASH", "SPLASH");
        alias("EXPLOSION_EMITTER", "EXPLOSION_HUGE", "EXPLOSION_EMITTER");
        alias("RAIN", "WATER_DROP", "RAIN");
        alias("TOTEM", "TOTEM", "TOTEM_OF_UNDYING");
        alias("ENCHANT", "ENCHANTMENT_TABLE", "ENCHANT");

        // --- aliases added for the 1.20.5+ spell code. Every one of these names survived
        // the rename untouched, so oldOrNew receives the same string twice. Do not
        // "simplify" these into a bare Particle.X reference: that breaks the uniformity of
        // the table and hides the fact that the name was checked against both jars.
        alias("FLAME", "FLAME", "FLAME");
        alias("LAVA", "LAVA", "LAVA");
        alias("PORTAL", "PORTAL", "PORTAL");
        alias("SOUL", "SOUL", "SOUL");
        alias("SQUID_INK", "SQUID_INK", "SQUID_INK");
        alias("CLOUD", "CLOUD", "CLOUD");
        alias("SWEEP_ATTACK", "SWEEP_ATTACK", "SWEEP_ATTACK");
        alias("FALLING_DUST", "FALLING_DUST", "FALLING_DUST");
        alias("BUBBLE_POP", "BUBBLE_POP", "BUBBLE_POP");
        alias("BUBBLE_COLUMN_UP", "BUBBLE_COLUMN_UP", "BUBBLE_COLUMN_UP");
        alias("GLOW", "GLOW", "GLOW");
        alias("END_ROD", "END_ROD", "END_ROD");
        alias("HEART", "HEART", "HEART");
        alias("DRAGON_BREATH", "DRAGON_BREATH", "DRAGON_BREATH");
        alias("ASH", "ASH", "ASH");
        alias("CRIT", "CRIT", "CRIT");
    }

    private static void alias(String field, String oldName, String newName) {
        ALIASES.put(field, Arrays.asList(oldName, newName));
    }

    /** Every {@code org.bukkit.Particle} constant declared by spigot-api 1.19-R0.1-SNAPSHOT. */
    private static final Set<String> MINECRAFT_1_19_CONSTANTS = names(
            "EXPLOSION_NORMAL", "EXPLOSION_LARGE", "EXPLOSION_HUGE", "FIREWORKS_SPARK",
            "WATER_BUBBLE", "WATER_SPLASH", "WATER_WAKE", "SUSPENDED", "SUSPENDED_DEPTH",
            "CRIT", "CRIT_MAGIC", "SMOKE_NORMAL", "SMOKE_LARGE", "SPELL", "SPELL_INSTANT",
            "SPELL_MOB", "SPELL_MOB_AMBIENT", "SPELL_WITCH", "DRIP_WATER", "DRIP_LAVA",
            "VILLAGER_ANGRY", "VILLAGER_HAPPY", "TOWN_AURA", "NOTE", "PORTAL",
            "ENCHANTMENT_TABLE", "FLAME", "LAVA", "CLOUD", "REDSTONE", "SNOWBALL",
            "SNOW_SHOVEL", "SLIME", "HEART", "ITEM_CRACK", "BLOCK_CRACK", "BLOCK_DUST",
            "WATER_DROP", "MOB_APPEARANCE", "DRAGON_BREATH", "END_ROD", "DAMAGE_INDICATOR",
            "SWEEP_ATTACK", "FALLING_DUST", "TOTEM", "SPIT", "SQUID_INK", "BUBBLE_POP",
            "CURRENT_DOWN", "BUBBLE_COLUMN_UP", "NAUTILUS", "DOLPHIN", "SNEEZE",
            "CAMPFIRE_COSY_SMOKE", "CAMPFIRE_SIGNAL_SMOKE", "COMPOSTER", "FLASH",
            "FALLING_LAVA", "LANDING_LAVA", "FALLING_WATER", "DRIPPING_HONEY",
            "FALLING_HONEY", "LANDING_HONEY", "FALLING_NECTAR", "SOUL_FIRE_FLAME", "ASH",
            "CRIMSON_SPORE", "WARPED_SPORE", "SOUL", "DRIPPING_OBSIDIAN_TEAR",
            "FALLING_OBSIDIAN_TEAR", "LANDING_OBSIDIAN_TEAR", "REVERSE_PORTAL", "WHITE_ASH",
            "DUST_COLOR_TRANSITION", "VIBRATION", "FALLING_SPORE_BLOSSOM", "SPORE_BLOSSOM_AIR",
            "SMALL_FLAME", "SNOWFLAKE", "DRIPPING_DRIPSTONE_LAVA", "FALLING_DRIPSTONE_LAVA",
            "DRIPPING_DRIPSTONE_WATER", "FALLING_DRIPSTONE_WATER", "GLOW_SQUID_INK", "GLOW",
            "WAX_ON", "WAX_OFF", "ELECTRIC_SPARK", "SCRAPE", "SONIC_BOOM", "SCULK_SOUL",
            "SCULK_CHARGE", "SCULK_CHARGE_POP", "SHRIEK", "BLOCK_MARKER",
            "LEGACY_BLOCK_CRACK", "LEGACY_BLOCK_DUST", "LEGACY_FALLING_DUST");

    /**
     * Every {@code org.bukkit.Particle} constant declared by spigot-api 1.20.6-R0.1-SNAPSHOT.
     * 1.20.6 is used as the proxy for "at or above the 1.20.5 rename": it is comfortably
     * past the boundary and every {@code new} slot resolves in it. It is intentionally NOT
     * resolved at runtime, because only the 1.19 API is on the test classpath.
     */
    private static final Set<String> MINECRAFT_1_20_5_PLUS_CONSTANTS = names(
            "POOF", "EXPLOSION", "EXPLOSION_EMITTER", "FIREWORK", "BUBBLE", "SPLASH",
            "FISHING", "UNDERWATER", "CRIT", "ENCHANTED_HIT", "SMOKE", "LARGE_SMOKE", "EFFECT",
            "INSTANT_EFFECT", "ENTITY_EFFECT", "WITCH", "DRIPPING_WATER", "DRIPPING_LAVA",
            "ANGRY_VILLAGER", "HAPPY_VILLAGER", "MYCELIUM", "NOTE", "PORTAL", "ENCHANT",
            "FLAME", "LAVA", "CLOUD", "DUST", "ITEM_SNOWBALL", "ITEM_SLIME", "HEART", "ITEM",
            "BLOCK", "RAIN", "ELDER_GUARDIAN", "DRAGON_BREATH", "END_ROD", "DAMAGE_INDICATOR",
            "SWEEP_ATTACK", "FALLING_DUST", "TOTEM_OF_UNDYING", "SPIT", "SQUID_INK",
            "BUBBLE_POP", "CURRENT_DOWN", "BUBBLE_COLUMN_UP", "NAUTILUS", "DOLPHIN", "SNEEZE",
            "CAMPFIRE_COSY_SMOKE", "CAMPFIRE_SIGNAL_SMOKE", "COMPOSTER", "FLASH",
            "FALLING_LAVA", "LANDING_LAVA", "FALLING_WATER", "DRIPPING_HONEY",
            "FALLING_HONEY", "LANDING_HONEY", "FALLING_NECTAR", "SOUL_FIRE_FLAME", "ASH",
            "CRIMSON_SPORE", "WARPED_SPORE", "SOUL", "DRIPPING_OBSIDIAN_TEAR",
            "FALLING_OBSIDIAN_TEAR", "LANDING_OBSIDIAN_TEAR", "REVERSE_PORTAL", "WHITE_ASH",
            "DUST_COLOR_TRANSITION", "VIBRATION", "FALLING_SPORE_BLOSSOM", "SPORE_BLOSSOM_AIR",
            "SMALL_FLAME", "SNOWFLAKE", "DRIPPING_DRIPSTONE_LAVA", "FALLING_DRIPSTONE_LAVA",
            "DRIPPING_DRIPSTONE_WATER", "FALLING_DRIPSTONE_WATER", "GLOW_SQUID_INK", "GLOW",
            "WAX_ON", "WAX_OFF", "ELECTRIC_SPARK", "SCRAPE", "SONIC_BOOM", "SCULK_SOUL",
            "SCULK_CHARGE", "SCULK_CHARGE_POP", "SHRIEK", "CHERRY_LEAVES", "EGG_CRACK",
            "DUST_PLUME", "WHITE_SMOKE", "GUST", "SMALL_GUST", "GUST_EMITTER_LARGE",
            "GUST_EMITTER_SMALL", "TRIAL_SPAWNER_DETECTION",
            "TRIAL_SPAWNER_DETECTION_OMINOUS", "VAULT_CONNECTION", "INFESTED", "ITEM_COBWEB",
            "DUST_PILLAR", "OMINOUS_SPAWNING", "RAID_OMEN", "TRIAL_OMEN", "BLOCK_MARKER");

    private static Set<String> names(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    // ------------------------------------------------------------------
    // declaration drift
    // ------------------------------------------------------------------

    @Test
    void runtimeApiIsThe119ApiTheHardcodedSetAssumes() {
        Set<String> runtime = runtimeConstantNames();
        assertTrue(runtime.contains("REDSTONE"),
                "The runtime spigot-api should be 1.19 (pre-rename) and expose REDSTONE; got "
                        + runtime.size() + " constants. If the pinned test dependency moved, "
                        + "regenerate MINECRAFT_1_19_CONSTANTS and MINECRAFT_1_20_5_PLUS_CONSTANTS "
                        + "with javap against the new jar.");
        assertFalse(runtime.contains("DUST"),
                "The runtime spigot-api should be 1.19 (pre-rename) and must NOT expose DUST. "
                        + "Regenerate the hardcoded constant sets if this fails.");
        assertEquals(MINECRAFT_1_19_CONSTANTS, runtime,
                "The hardcoded 1.19 constant set drifted from the spigot-api actually on the test "
                        + "classpath. Regenerate it with javap.");
    }

    @Test
    void everyAliasIsDeclaredInUtilsWithTheExpectedNamePair() {
        String source = readUtilsSource();
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            String field = entry.getKey();
            List<String> pair = entry.getValue();
            String expected = "public static final Particle " + field
                    + " = Particle.valueOf(oldOrNew(\"" + pair.get(0)
                    + "\", \"" + pair.get(1) + "\"));";
            assertTrue(source.contains(expected),
                    "Utils.java must declare the alias exactly as: " + expected
                            + " -- either the declaration drifted or this test's table needs updating.");
        }
    }

    @Test
    void everyAliasDeclaredInUtilsIsCoveredByThisTest() {
        String source = readUtilsSource();
        Matcher matcher = Pattern.compile(
                        "public static final Particle\\s+(\\w+)\\s*=\\s*Particle\\.valueOf\\(oldOrNew\\(")
                .matcher(source);
        int found = 0;
        while (matcher.find()) {
            found++;
            String field = matcher.group(1);
            assertTrue(ALIASES.containsKey(field),
                    "Utils declares particle alias '" + field + "' but ParticleAliasTest does not "
                            + "know about it. Add it to ALIASES and to both hardcoded constant sets.");
        }
        assertEquals(ALIASES.size(), found,
                "Expected to find " + ALIASES.size() + " oldOrNew-based particle aliases in Utils.java "
                        + "but found " + found + ".");
    }

    // ------------------------------------------------------------------
    // name resolvability
    // ------------------------------------------------------------------

    @Test
    void everyPreRenameAliasNameIsDeclaredByThe119Api() {
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            String oldName = entry.getValue().get(0);
            assertTrue(MINECRAFT_1_19_CONSTANTS.contains(oldName),
                    "Alias " + entry.getKey() + " declares old name '" + oldName
                            + "', which does not exist in the 1.19 Particle enum. It would throw "
                            + "IllegalArgumentException at class-initialisation on a 1.19 server. "
                            + "Pick a substitute that really exists in 1.19.");
        }
    }

    @Test
    void everyPreRenameAliasNameResolvesOnTheRuntimeApi() {
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            String oldName = entry.getValue().get(0);
            Particle resolved;
            try {
                resolved = Particle.valueOf(oldName);
            } catch (IllegalArgumentException ex) {
                fail("Alias " + entry.getKey() + " old name '" + oldName
                        + "' does not resolve on the runtime spigot-api: " + ex.getMessage());
                return;
            }
            assertNotNull(resolved,
                    "Alias " + entry.getKey() + " resolved to null for old name '" + oldName + "'.");
        }
    }

    @Test
    void everyPostRenameAliasNameIsDeclaredByThe1205PlusApi() {
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            String newName = entry.getValue().get(1);
            assertTrue(MINECRAFT_1_20_5_PLUS_CONSTANTS.contains(newName),
                    "Alias " + entry.getKey() + " declares new name '" + newName
                            + "', which does not exist in the 1.20.5+ Particle enum. It would throw "
                            + "IllegalArgumentException at class-initialisation on a modern server.");
        }
    }

    @Test
    void everyResolvedAliasLandsOnAConstantThe119ApiDeclares() {
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            Particle resolved = Particle.valueOf(entry.getValue().get(0));
            assertNotNull(resolved,
                    "Alias " + entry.getKey() + " must resolve to a real Particle constant.");
            assertTrue(runtimeConstantNames().contains(resolved.name()),
                    "Alias " + entry.getKey() + " resolved to " + resolved.name()
                            + ", which the 1.19 API does not declare.");
        }
    }

    // ------------------------------------------------------------------
    // distinctness
    // ------------------------------------------------------------------

    @Test
    void aliasesThatMustLookDifferentResolveToDifferentConstants() {
        assertDistinct("SMOKE", "LARGE_SMOKE");
        assertDistinct("EXPLOSION", "EXPLOSION_EMITTER");
        assertDistinct("BLOCK_DUST", "FALLING_DUST");
        assertDistinct("DUST", "SWEEP_ATTACK");
        assertDistinct("DUST", "SPLASH");
        assertDistinct("FLAME", "LAVA");
        assertDistinct("CLOUD", "SMOKE");
        assertDistinct("CLOUD", "END_ROD");
        assertDistinct("ASH", "SMOKE");
        assertDistinct("ASH", "CLOUD");
        assertDistinct("PORTAL", "SOUL");
        assertDistinct("GLOW", "SQUID_INK");
        assertDistinct("GLOW", "HEART");
        assertDistinct("CRIT", "WITCH");
        assertDistinct("CRIT", "DUST");
        assertDistinct("DRAGON_BREATH", "LARGE_SMOKE");
        assertDistinct("BUBBLE_POP", "BUBBLE_COLUMN_UP");
        assertDistinct("BUBBLE_POP", "SPLASH");
        assertDistinct("BUBBLE_COLUMN_UP", "SPLASH");
        assertDistinct("TOTEM", "BLOCK");
        assertDistinct("ENTITY_EFFECT", "WITCH");
    }

    /**
     * Documents the one intentional collapse in the table instead of pretending it does not
     * exist. {@code BLOCK} and {@code BLOCK_DUST} both resolve to {@code BLOCK} on 1.20.5+
     * because {@code BLOCK_DUST} was itself folded into {@code BLOCK} by the rename. Both
     * names are real constants on both sides, so this is a visual-fidelity compromise and
     * not a crash risk. If that fidelity ever matters, the fix is a version branch in
     * {@code BLOCK_DUST} itself, which is out of scope for this test.
     */
    @Test
    void blockAndBlockDustAreKnownToCollapseOn1205AndAbove() {
        assertEquals("BLOCK", ALIASES.get("BLOCK").get(1),
                "BLOCK's new name changed; update this test's documented rationale.");
        assertEquals("BLOCK", ALIASES.get("BLOCK_DUST").get(1),
                "BLOCK_DUST's new name changed; update this test's documented rationale.");
        assertNotEqualsOn119("BLOCK", "BLOCK_DUST");
    }

    private void assertDistinct(String first, String second) {
        assertNotEqualsOn119(first, second);
        assertNotEqualsOn1205(first, second);
    }

    /** On 1.19 the two aliases must not land on the same pre-rename constant. */
    private void assertNotEqualsOn119(String first, String second) {
        String a = ALIASES.get(first).get(0);
        String b = ALIASES.get(second).get(0);
        assertFalse(a.equals(b),
                first + " and " + second + " both map to '" + a
                        + "' on 1.19 but are meant to look different.");
    }

    /** On 1.20.5+ the two aliases must not land on the same post-rename constant. */
    private void assertNotEqualsOn1205(String first, String second) {
        String a = ALIASES.get(first).get(1);
        String b = ALIASES.get(second).get(1);
        assertFalse(a.equals(b),
                first + " and " + second + " both map to '" + a
                        + "' on 1.20.5+ but are meant to look different.");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Set<String> runtimeConstantNames() {
        return Arrays.stream(Particle.values()).map(Enum::name).collect(Collectors.toSet());
    }

    private static String readUtilsSource() {
        Path relative = Paths.get("src", "main", "java", "me", "nagasonic", "alkatraz", "util", "Utils.java");
        Path source = locate(relative);
        try {
            return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + source, ex);
        }
    }

    private static Path locate(Path relative) {
        List<String> tried = new ArrayList<>();
        Path dir = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 5 && dir != null; depth++) {
            Path candidate = dir.resolve(relative);
            tried.add(candidate.toString());
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate Utils.java relative to the working "
                + "directory. Tried: " + tried);
    }
}