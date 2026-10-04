package me.nagasonic.alkatraz.spells;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loot table and the Java that reads it, asserted against each other.
 *
 * <p>Before this wave nothing tested loot, SpellbookFactory, RandomSpellbook or
 * SpellbookConfig at all, so every probability change was unverified by CI - which
 * is how three loot entries producing a dead {@code spell_count=0} item shipped.
 *
 * <p>Config assertions use {@code getResourceAsStream} + {@code YamlConfiguration}
 * and the roster comes from {@link BundledSpellRoster}, so no Bukkit mock is
 * needed. The source-scanning tests read {@code SpellbookLoot.java} and
 * {@code SpellbookConfig.java} as text, which is more honest than reflection: the
 * thing being tested is that two hand-maintained lists agree.
 *
 * <p><b>Reading dotted keys.</b> YamlConfiguration turns {@code mob.enderman.dark: 4}
 * into NESTED sections, so {@code getKeys(false)} on {@code loot.element_book_caps}
 * returns the five group names, not the 25 caps, and {@code getInt("mob.enderman.dark")}
 * is the only way to read one. {@link #elementCaps()} does that flattening once and the
 * cap tests all read through it.
 */
class LootConfigTest {

    /** {@code randomBook(1, 2, 3)} and friends: 36 bands in SpellbookLoot. */
    private static final Pattern RANDOM_BOOK_CALL =
            Pattern.compile("randomBook\\(([0-9][0-9,\\s]*)\\)");

    /**
     * {@code registerSimpleMob("zombie", EntityType.ZOMBIE, m, "spellbook_weight", 1, 2)}:
     * the two one-band mobs, whose circles are ints at the call site rather than a csv
     * string inside the helper.
     */
    private static final Pattern SIMPLE_MOB_CALL =
            Pattern.compile("registerSimpleMob\\(\"[a-z_]+\",\\s*[A-Za-z.]+,\\s*m,\\s*\"[a-z_]+\",\\s*([0-9][0-9,\\s]*)\\);");

    /** {@code elementBook(Element.FIRE, "chest.nether", 6)}: 25 capped element books. */
    private static final Pattern ELEMENT_BOOK_CALL =
            Pattern.compile("elementBook\\(Element\\.(\\w+), \"([a-z_.]+)\", (\\d)\\)");

    /** {@code SpellbookLoot}'s loot.yml path and the default its Java passes, for every key it reads. */
    private static final String[][] REQUIRED_KEYS = {
            {"loot.loot_multiplier", "1.0"},
            {"loot.chest_loot.enabled", "true"},
            {"loot.chest_loot.common.enabled", "true"},
            {"loot.chest_loot.common.spellbook_weight", "100"},
            {"loot.chest_loot.common.spellbook_1_2_weight", "30"},
            {"loot.chest_loot.common.filler_weight", "100"},
            {"loot.chest_loot.common.max_items", "5"},
            {"loot.chest_loot.dungeon.enabled", "true"},
            {"loot.chest_loot.dungeon.spellbook_1_2_weight", "80"},
            {"loot.chest_loot.dungeon.spellbook_1_3_weight", "60"},
            {"loot.chest_loot.dungeon.spellbook_2_4_weight", "20"},
            {"loot.chest_loot.dungeon.max_items", "1"},
            {"loot.chest_loot.nether.enabled", "true"},
            {"loot.chest_loot.nether.fire_element_weight", "100"},
            {"loot.chest_loot.nether.spellbook_2_4_weight", "50"},
            {"loot.chest_loot.nether.spellbook_4_6_weight", "20"},
            {"loot.chest_loot.nether.max_items", "1"},
            {"loot.chest_loot.end.enabled", "true"},
            {"loot.chest_loot.end.spellbook_2_4_weight", "100"},
            {"loot.chest_loot.end.spellbook_4_6_weight", "80"},
            {"loot.chest_loot.end.spellbook_6_9_weight", "40"},
            {"loot.chest_loot.end.air_element_weight", "60"},
            {"loot.chest_loot.end.max_items", "2"},
            {"loot.structure_loot.enabled", "true"},
            {"loot.structure_loot.desert_pyramid.enabled", "true"},
            {"loot.structure_loot.desert_pyramid.fire_element_weight", "100"},
            {"loot.structure_loot.desert_pyramid.spellbook_2_4_weight", "40"},
            {"loot.structure_loot.desert_pyramid.max_items", "1"},
            {"loot.structure_loot.ocean_ruins.enabled", "true"},
            {"loot.structure_loot.ocean_ruins.water_element_weight", "100"},
            {"loot.structure_loot.ocean_ruins.spellbook_1_3_weight", "50"},
            {"loot.structure_loot.ocean_ruins.max_items", "1"},
            {"loot.structure_loot.stronghold.enabled", "true"},
            {"loot.structure_loot.stronghold.spellbook_2_4_weight", "100"},
            {"loot.structure_loot.stronghold.spellbook_4_6_weight", "60"},
            {"loot.structure_loot.stronghold.dark_element_weight", "40"},
            {"loot.structure_loot.stronghold.max_items", "1"},
            {"loot.structure_loot.woodland_mansion.enabled", "true"},
            {"loot.structure_loot.woodland_mansion.dark_element_weight", "100"},
            {"loot.structure_loot.woodland_mansion.spellbook_2_4_weight", "80"},
            {"loot.structure_loot.woodland_mansion.max_items", "1"},
            {"loot.mob_drops.enabled", "true"},
            {"loot.mob_drops.zombie.enabled", "true"},
            {"loot.mob_drops.zombie.spellbook_weight", "100"},
            {"loot.mob_drops.zombie.filler_weight", "1900"},
            {"loot.mob_drops.stray.enabled", "true"},
            {"loot.mob_drops.stray.spellbook_weight", "100"},
            {"loot.mob_drops.stray.filler_weight", "1900"},
            {"loot.mob_drops.skeleton.enabled", "true"},
            {"loot.mob_drops.skeleton.spellbook_1_2_weight", "60"},
            {"loot.mob_drops.skeleton.spellbook_1_3_weight", "40"},
            {"loot.mob_drops.skeleton.filler_weight", "1900"},
            {"loot.mob_drops.witch.enabled", "true"},
            {"loot.mob_drops.witch.spellbook_1_3_weight", "80"},
            {"loot.mob_drops.witch.spellbook_2_4_weight", "60"},
            {"loot.mob_drops.witch.dark_element_weight", "40"},
            {"loot.mob_drops.witch.filler_weight", "520"},
            {"loot.mob_drops.piglin.enabled", "true"},
            {"loot.mob_drops.piglin.fire_element_weight", "80"},
            {"loot.mob_drops.piglin.spellbook_2_3_weight", "60"},
            {"loot.mob_drops.piglin.filler_weight", "1360"},
            {"loot.mob_drops.hoglin.enabled", "true"},
            {"loot.mob_drops.hoglin.spellbook_2_3_weight", "100"},
            {"loot.mob_drops.hoglin.filler_weight", "900"},
            {"loot.mob_drops.phantom.enabled", "true"},
            {"loot.mob_drops.phantom.air_element_weight", "80"},
            {"loot.mob_drops.phantom.spellbook_1_2_weight", "50"},
            {"loot.mob_drops.phantom.filler_weight", "1370"},
            {"loot.mob_drops.guardian.enabled", "true"},
            {"loot.mob_drops.guardian.water_element_weight", "100"},
            {"loot.mob_drops.guardian.spellbook_2_3_weight", "60"},
            {"loot.mob_drops.guardian.filler_weight", "1040"},
            {"loot.mob_drops.evoker.enabled", "true"},
            {"loot.mob_drops.evoker.spellbook_2_4_weight", "70"},
            {"loot.mob_drops.evoker.spellbook_4_6_weight", "50"},
            {"loot.mob_drops.evoker.dark_element_weight", "30"},
            {"loot.mob_drops.evoker.specific_spellbook_weight", "25"},
            {"loot.mob_drops.evoker.specific_spell", "skeletal_knights"},
            {"loot.mob_drops.evoker.filler_weight", "150"},
            {"loot.mob_drops.vindicator.enabled", "true"},
            {"loot.mob_drops.vindicator.dark_element_weight", "80"},
            {"loot.mob_drops.vindicator.spellbook_2_3_weight", "60"},
            {"loot.mob_drops.vindicator.filler_weight", "340"},
            {"loot.mob_drops.ravager.enabled", "true"},
            {"loot.mob_drops.ravager.earth_element_weight", "100"},
            {"loot.mob_drops.ravager.spellbook_3_4_weight", "70"},
            {"loot.mob_drops.ravager.spellbook_4_6_weight", "30"},
            {"loot.mob_drops.ravager.specific_spellbook_weight", "30"},
            {"loot.mob_drops.ravager.specific_spell", "tectonic_strike"},
            {"loot.mob_drops.ravager.filler_weight", "203"},
            {"loot.mob_drops.blaze.enabled", "true"},
            {"loot.mob_drops.blaze.fire_element_weight", "100"},
            {"loot.mob_drops.blaze.specific_spellbook_weight", "50"},
            {"loot.mob_drops.blaze.specific_spell", "fire_blast"},
            {"loot.mob_drops.blaze.filler_weight", "350"},
            {"loot.mob_drops.drowned.enabled", "true"},
            {"loot.mob_drops.drowned.water_element_weight", "100"},
            {"loot.mob_drops.drowned.specific_spellbook_weight", "40"},
            {"loot.mob_drops.drowned.specific_spell", "water_pulse"},
            {"loot.mob_drops.drowned.filler_weight", "860"},
            {"loot.mob_drops.enderman.enabled", "true"},
            {"loot.mob_drops.enderman.dark_element_weight", "70"},
            {"loot.mob_drops.enderman.air_element_weight", "50"},
            {"loot.mob_drops.enderman.specific_spellbook_weight", "20"},
            {"loot.mob_drops.enderman.specific_spell", "blink"},
            {"loot.mob_drops.enderman.filler_weight", "1150"},
            {"loot.mob_drops.wither_skeleton.enabled", "true"},
            {"loot.mob_drops.wither_skeleton.spellbook_2_4_weight", "70"},
            {"loot.mob_drops.wither_skeleton.dark_element_weight", "80"},
            {"loot.mob_drops.wither_skeleton.filler_weight", "683"},
            {"loot.mob_drops.elder_guardian.enabled", "true"},
            {"loot.mob_drops.elder_guardian.water_element_weight", "100"},
            {"loot.mob_drops.elder_guardian.spellbook_5_7_weight", "60"},
            {"loot.mob_drops.elder_guardian.specific_spellbook_weight", "40"},
            {"loot.mob_drops.elder_guardian.specific_spell", "undertow"},
            {"loot.mob_drops.elder_guardian.filler_weight", "340"},
            {"loot.mob_drops.elder_guardian.max_items", "2"},
            {"loot.mob_drops.ender_dragon.enabled", "true"},
            {"loot.mob_drops.ender_dragon.air_element_weight", "100"},
            {"loot.mob_drops.ender_dragon.spellbook_4_6_weight", "80"},
            {"loot.mob_drops.ender_dragon.spellbook_7_9_weight", "20"},
            {"loot.mob_drops.ender_dragon.specific_spellbook_weight", "25"},
            {"loot.mob_drops.ender_dragon.specific_spell", "skywrath"},
            {"loot.mob_drops.ender_dragon.max_items", "2"},
            {"loot.mob_drops.wither.enabled", "true"},
            {"loot.mob_drops.wither.dark_element_weight", "100"},
            {"loot.mob_drops.wither.spellbook_4_6_weight", "80"},
            {"loot.mob_drops.wither.spellbook_7_9_weight", "20"},
            {"loot.mob_drops.wither.specific_spellbook_weight", "25"},
            {"loot.mob_drops.wither.specific_spell", "oblivion"},
            {"loot.mob_drops.wither.filler_weight", "275"},
            {"loot.mob_drops.wither.max_items", "2"},
            {"loot.fishing.enabled", "true"},
            {"loot.fishing.random_spellbook_weight", "40"},
            {"loot.fishing.water_element_weight", "50"},
            {"loot.fishing.air_element_weight", "30"},
            {"loot.fishing.light_element_weight", "20"},
            {"loot.fishing.max_items", "2"},
            {"loot.piglin_bartering.enabled", "true"},
            {"loot.piglin_bartering.fire_element_weight", "60"},
            {"loot.piglin_bartering.random_spellbook_weight", "40"},
            {"loot.piglin_bartering.nothing_weight", "900"},
            {"loot.piglin_bartering.max_items", "1"},
    };

    /** The three sources allowed to reach a circle-9 element book. */
    private static final Set<String> ENDGAME = Set.of("chest.end", "mob.ender_dragon", "mob.wither");

    private static YamlConfiguration resource(String path) throws IOException {
        try (InputStream in = LootConfigTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing bundled resource " + path);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration loot() throws IOException {
        return resource("/loot.yml");
    }

    private static YamlConfiguration pluginConfig() throws IOException {
        return resource("/config.yml");
    }

    private static String spellbookLootSource() throws IOException {
        return BundledSpellRoster.readSource(
                "src/main/java/me/nagasonic/alkatraz/loot/implementation/SpellbookLoot.java");
    }

    /**
     * {@code loot.element_book_caps} flattened to {@code dotted.key -> circle}.
     *
     * <p>Group entries resolve to a ConfigurationSection rather than a Number and are
     * dropped, so the map holds one entry per real cap.
     */
    private static Map<String, Integer> elementCaps() throws IOException {
        ConfigurationSection section = loot().getConfigurationSection("loot.element_book_caps");
        assertNotNull(section, "loot.element_book_caps must exist");
        Map<String, Integer> caps = new TreeMap<>();
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value instanceof Number number) {
                caps.put(key, number.intValue());
            }
        }
        return caps;
    }

    /** {@code capKey -> cap} as written at the {@code elementBook(...)} call sites in SpellbookLoot. */
    private static Map<String, Integer> declaredCaps() throws IOException {
        Matcher matcher = ELEMENT_BOOK_CALL.matcher(spellbookLootSource());
        Map<String, Integer> declared = new LinkedHashMap<>();
        while (matcher.find()) {
            assertFalse(declared.containsKey(matcher.group(2)),
                    "two element books share the cap key " + matcher.group(2)
                            + ", so loot.yml could not tell them apart");
            declared.put(matcher.group(2), Integer.parseInt(matcher.group(3)));
        }
        return declared;
    }

    /** circle -> how many bundled spells sit there. Memoised; the callers hit it once per band. */
    private static Map<Integer, Integer> rosterCounts;

    private static Map<Integer, Integer> rosterCounts() throws IOException {
        if (rosterCounts != null) return rosterCounts;
        Map<Integer, Integer> counts = new HashMap<>();
        for (String id : BundledSpellRoster.spellIds()) {
            int level = BundledSpellRoster.load(id).getInt("level");
            counts.merge(level, 1, Integer::sum);
        }
        rosterCounts = counts;
        return counts;
    }

    @Test
    void everyKeyTheJavaReadsExistsInLootYml() throws IOException {
        YamlConfiguration yaml = loot();
        for (String[] entry : REQUIRED_KEYS) {
            String path = entry[0];
            assertTrue(yaml.isSet(path), "loot.yml is missing " + path);
            assertEquals(entry[1], String.valueOf(yaml.get(path)),
                    path + " is " + yaml.get(path) + " in loot.yml but SpellbookLoot reads a default of "
                            + entry[1] + ". A drifted yml is the same failure as a missing one, because "
                            + "ConfigUpdater propagates NEW keys but never changed default values.");
        }
    }

    @Test
    void everyElementCapIsBetweenOneAndNine() throws IOException {
        Map<String, Integer> caps = elementCaps();
        Map<String, Integer> declared = declaredCaps();

        assertEquals(25, declared.size(),
                "SpellbookLoot must have exactly 25 elementBook call sites, one per capped source. "
                        + "A new call site with no cap is an uncapped book.");
        assertEquals(25, caps.size(),
                "loot.element_book_caps must have exactly 25 entries, one per elementBook call site. "
                        + "Found " + caps.keySet() + ".");
        for (Map.Entry<String, Integer> entry : caps.entrySet()) {
            String key = entry.getKey();
            assertTrue(entry.getValue() >= 1 && entry.getValue() <= 9,
                    key + " caps at " + entry.getValue() + ", which is outside 1-9. A cap of 0 "
                            + "produces an empty book.");
            assertEquals(declared.get(key), entry.getValue(),
                    key + " is " + entry.getValue() + " in loot.yml but SpellbookLoot passes a default of "
                            + declared.get(key) + " to getElementMaxCircle. The two have to agree or the "
                            + "yml is decoration.");
        }
    }

    @Test
    void onlyEndgameSourcesMayCapAtNine() throws IOException {
        Set<String> atNine = new HashSet<>();
        for (Map.Entry<String, Integer> entry : elementCaps().entrySet()) {
            if (entry.getValue() != 9) {
                assertTrue(entry.getValue() >= 1 && entry.getValue() <= 8,
                        entry.getKey() + " caps at " + entry.getValue() + ", which is neither a normal cap "
                                + "nor one of the three endgame sources " + ENDGAME);
                continue;
            }
            assertTrue(ENDGAME.contains(entry.getKey()),
                    entry.getKey() + " caps at 9, so it can hand out a 600-mana circle-9 capstone. Only "
                            + ENDGAME + " are allowed to.");
            atNine.add(entry.getKey());
        }
        assertEquals(ENDGAME, atNine,
                "the set of circle-9 caps must be exactly " + ENDGAME + " - a fourth one is a new endgame "
                        + "source nobody budgeted for, and a missing one is a dead key");
    }

    @Test
    void everyElementCapMatchesItsSourcesOwnSiblingBand() throws IOException {
        // The rule is "the cap equals the top circle of that source's own non-element
        // sibling band". These are the three sources that changed band in this wave.
        Map<String, Integer> caps = elementCaps();
        assertEquals(7, caps.get("mob.elder_guardian"),
                "elder_guardian's band moved from (4,5,6) to (5,6,7), so its cap moved with it");
        assertEquals(7, caps.get("fishing.water"),
                "fishing's band moved from (1..6) to (1..7), so every fishing element cap moved to 7");
        assertEquals(7, caps.get("fishing.air"),
                "fishing's band moved from (1..6) to (1..7), so every fishing element cap moved to 7");
        assertEquals(7, caps.get("fishing.light"),
                "fishing's band moved from (1..6) to (1..7), so every fishing element cap moved to 7");
        assertEquals(7, caps.get("piglin_bartering.fire"),
                "piglin bartering's band moved from (3..6) to (3..7), so its cap moved to 7");
        assertEquals(6, caps.get("mob.ravager"),
                "ravager gained a (4,5,6) band, so its EARTH cap is 6");
    }

    @Test
    void noRandomSpellBookRangeIsDeadGivenTheRoster() throws IOException {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "neither src/main/resources/spells nor the /spells classpath directory resolved");
        Map<Integer, Integer> counts = rosterCounts();
        String source = spellbookLootSource();

        List<String> bands = new ArrayList<>();
        Matcher direct = RANDOM_BOOK_CALL.matcher(source);
        while (direct.find()) {
            bands.add(direct.group(1).replaceAll("\\s", ""));
        }
        assertEquals(36, bands.size(),
                "SpellbookLoot declares 36 randomBook(<circles>) bands. Found " + bands.size()
                        + ", so a band was added or removed without updating this test.");

        Matcher simple = SIMPLE_MOB_CALL.matcher(source);
        List<String> simpleBands = new ArrayList<>();
        while (simple.find()) {
            simpleBands.add(simple.group(1).replaceAll("\\s", ""));
        }
        assertEquals(2, simpleBands.size(),
                "zombie and stray go through registerSimpleMob and pass their circles as ints. Found "
                        + simpleBands.size() + " such call sites, so one is no longer being scanned.");
        bands.addAll(simpleBands);

        Set<Integer> covered = new TreeSet<>();
        for (String band : bands) {
            for (String part : band.split(",")) {
                int circle = Integer.parseInt(part.trim());
                assertTrue(counts.getOrDefault(circle, 0) > 0,
                        "a random band names circle " + circle + ", which no bundled spell occupies. That "
                                + "book is tagged spellbook_type=random with spell_count=0, prints "
                                + "spellbook.empty on right-click and is NOT consumed - three such entries "
                                + "shipped. Band was (" + band + ").");
                covered.add(circle);
            }
        }
        assertEquals(38, bands.size(),
                "every random book in SpellbookLoot must be scanned: 36 randomBook bands plus 2 "
                        + "registerSimpleMob bands.");
        for (int circle = 1; circle <= 9; circle++) {
            assertTrue(covered.contains(circle),
                    "no loot band reaches circle " + circle + ". Circles 1-6 are reachable today; 7-9 are "
                            + "the new tier and only the end city, dragon, wither, fishing and bartering "
                            + "should carry them.");
        }
    }

    @Test
    void everySpecificDropNamesARegisteredSpell() throws IOException {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "neither src/main/resources/spells nor the /spells classpath directory resolved");
        ConfigurationSection mobs = loot().getConfigurationSection("loot.mob_drops");
        assertNotNull(mobs, "loot.mob_drops must exist");
        List<String> ids = BundledSpellRoster.spellIds();

        int checked = 0;
        for (String mob : mobs.getKeys(false)) {
            String id = loot().getString("loot.mob_drops." + mob + ".specific_spell");
            if (id == null || id.isBlank()) continue;
            assertTrue(ids.contains(id),
                    "loot.yml's " + mob + ".specific_spell is '" + id + "', which no bundled spell yml "
                            + "provides. SpellbookLoot warns and skips it, so the entry silently "
                            + "vanishes from the pool instead of failing the build.");
            int circle = BundledSpellRoster.load(id).getInt("level");
            assertTrue(circle >= 1,
                    mob + ".specific_spell is " + id + " at circle " + circle + "; a circle-0 specific "
                            + "would carry weight 0 and never roll");
            checked++;
        }
        assertEquals(8, checked,
                "eight mobs carry a signature drop: the three that already had one plus the five this "
                        + "wave adds");
    }

    @Test
    void noSpecificDropOutranksItsOwnElementBookCap() throws IOException {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "neither src/main/resources/spells nor the /spells classpath directory resolved");
        ConfigurationSection mobs = loot().getConfigurationSection("loot.mob_drops");
        assertNotNull(mobs, "loot.mob_drops must exist");
        Map<String, Integer> caps = elementCaps();

        int checked = 0;
        for (String mob : mobs.getKeys(false)) {
            String id = loot().getString("loot.mob_drops." + mob + ".specific_spell");
            if (id == null || id.isBlank()) continue;
            int circle = BundledSpellRoster.load(id).getInt("level");

            // A mob can own one cap (mob.blaze) or a pair (mob.enderman.dark and
            // mob.enderman.air), and "mob.wither" must not pick up wither_skeleton's cap,
            // so the prefix has to end at a path separator.
            String exact = "mob." + mob;
            List<String> owned = new ArrayList<>();
            for (String key : caps.keySet()) {
                if (key.equals(exact) || key.startsWith(exact + ".")) {
                    owned.add(key);
                }
            }
            assertFalse(owned.isEmpty(),
                    mob + ".specific_spell is " + id + " but loot.element_book_caps has no cap for it. "
                            + "A specific with no cap beside it outranks an uncapped element book.");
            for (String key : owned) {
                int cap = caps.get(key);
                assertTrue(circle <= cap,
                        mob + ".specific_spell is " + id + " at circle " + circle + ", above its own "
                                + "element book cap of " + cap + " on " + key + ". The specific would be "
                                + "strictly better than the themed book sitting beside it.");
            }
            checked++;
        }
        assertEquals(8, checked,
                "every one of the eight signature drops has to be checked against a cap, including the "
                        + "enderman pair which has two");
    }

    @Test
    void villagerTradeKeyNamesMatchBetweenConfigAndSpellbookConfig() throws IOException {
        YamlConfiguration config = pluginConfig();
        assertTrue(config.isSet("villager_trading.chance_one_trade"),
                "config.yml defines chance_one_trade");
        assertFalse(config.isSet("villager_trading.chance_one_trades"),
                "the trailing-s key was the bug: SpellbookConfig read chance_one_trades while "
                        + "config.yml defines chance_one_trade, so the knob was dead and "
                        + "getVillagerChanceOneTrade() always returned its hardcoded 40");

        String source = BundledSpellRoster.readSource(
                "src/main/java/me/nagasonic/alkatraz/config/SpellbookConfig.java");
        assertFalse(source.contains("chance_one_trades"),
                "SpellbookConfig must not read the plural key again");
        assertTrue(source.contains("\"villager_trading.chance_one_trade\""),
                "SpellbookConfig must read villager_trading.chance_one_trade");
    }

    @Test
    void circleWeightsAreUnchangedByThisWave() throws IOException {
        YamlConfiguration config = pluginConfig();
        int[] expected = {10000, 5000, 2500, 1250, 750, 375, 150, 75, 30};
        for (int circle = 1; circle <= 9; circle++) {
            assertEquals(expected[circle - 1],
                    config.getInt("spellbook.circle_weights.circle_" + circle),
                    "spellbook.circle_weights.circle_" + circle + " is unchanged. The wave-6 fix makes "
                            + "the ladder mean what its comment already claims - a circle's share is "
                            + "w(N) / sum(w) - so the halving ladder needs no re-tuning.");
        }
        assertFalse(config.isSet("spellbook.circle_weights.circle_0"),
                "circle 0 has no weight key at all, which is why getCircleWeight returns 0 for it and "
                        + "why magic_missile can never be skipped rather than thrown on");
    }

    @Test
    void unregisterAllClearsBothInjectorRegistries() throws IOException {
        String source = spellbookLootSource();
        int method = source.indexOf("public static void unregisterAll()");
        assertTrue(method >= 0, "SpellbookLoot.unregisterAll() must exist");
        String body = source.substring(method, Math.min(source.length(), method + 400));

        assertTrue(body.contains("LootInjector.unregisterAll()"),
                "unregisterAll must clear the LootInjector registry");
        assertTrue(body.contains("MobLootInjector.unregisterAll()"),
                "unregisterAll must ALSO clear the MobLootInjector registry. It used to clear only "
                        + "LootInjector, so /alkatraz reload re-registering would leave every mob "
                        + "injector in place and add a second copy of each mob drop rule, doubling mob "
                        + "spellbook drops on every reload.");
    }
}