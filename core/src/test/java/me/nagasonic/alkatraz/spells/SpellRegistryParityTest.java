package me.nagasonic.alkatraz.spells;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec section 12.2: three hand-maintained lists must agree.
 *
 * <ol>
 *   <li>{@code SpellRegistry.registerSpells()} - the {@code registerIfEnabled} ids.</li>
 *   <li>{@code Alkatraz.saveSpellConfigs()} - the {@code spells/*.yml} literals.</li>
 *   <li>the bundled {@code spells/*.yml} resource tree.</li>
 * </ol>
 *
 * <p>All three are read WITHOUT executing any of them. Calling {@code registerSpells()}
 * constructs all 68 spell objects, each of which reads ConfigManager and most of which
 * register events on the plugin's PluginManager - that needs a live Server mock and
 * makes the test order-dependent. Scanning the two source files as text is also simply
 * the more honest test: the subject is that two handwritten lists agree.
 *
 * <p>Direction matters. Registry -&gt; resource and saveConfig -&gt; resource hold from the
 * moment a spell yml lands. Resource -&gt; registry only holds once T27 has registered
 * the 30 new spells, so that one direction self-activates off the sentinel id
 * {@code arcane_resonance} - the last of the 29 banded additions in the master's alphabetical custom
 * model data allocation, so it cannot be registered before the rest.
 */
class SpellRegistryParityTest {

    /** Added by wave 7's T27; see the class javadoc for why this is the sentinel. */
    private static final String REGISTRATION_COMPLETE_SENTINEL = "arcane_resonance";

    private static List<String> bundled;
    private static List<String> registered;
    private static List<String> saved;

    @BeforeAll
    static void readAllThreeLists() throws IOException {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "the spells resource directory is not enumerable on this classpath");
        bundled = BundledSpellRoster.spellIds();
        registered = BundledSpellRoster.registryIds();
        saved = BundledSpellRoster.savedSpellConfigIds();
    }

    @Test
    void everyRegisteredSpellHasABundledResource() {
        for (String id : registered) {
            assertTrue(bundled.contains(id),
                    "registerSpells() registers '" + id + "' but no spells/" + id
                            + ".yml is bundled, so ConfigManager.getConfig would load nothing");
        }
    }

    @Test
    void everyRegisteredSpellHasASaveConfigEntry() {
        for (String id : registered) {
            assertTrue(saved.contains(id),
                    "'" + id + "' is registered but Alkatraz has no spells/" + id
                            + ".yml literal, so it is never copied out of the jar on a fresh install");
        }
    }

    @Test
    void everySaveConfigEntryHasABundledResource() {
        for (String id : saved) {
            assertTrue(bundled.contains(id),
                    "Alkatraz saves spells/" + id + ".yml but no such resource is bundled, so "
                            + "ConfigManager.saveConfig would log a missing-resource warning");
        }
    }

    @Test
    void everyBundledSpellIsRegisteredOnceWaveSevenLands() {
        Assumptions.assumeTrue(registered.contains(REGISTRATION_COMPLETE_SENTINEL),
                REGISTRATION_COMPLETE_SENTINEL + " is not registered yet - T27 owns SpellRegistry.java, "
                        + "so the resource -> registry direction is asserted from wave 7 onward");
        for (String id : bundled) {
            assertTrue(registered.contains(id),
                    "spells/" + id + ".yml is bundled but registerSpells() never registers it, so it "
                            + "is invisible to every menu, grimoire and tab-completion path");
        }
    }

    @Test
    void everyBundledSpellIsSavedOnceWaveSevenLands() {
        Assumptions.assumeTrue(registered.contains(REGISTRATION_COMPLETE_SENTINEL),
                REGISTRATION_COMPLETE_SENTINEL + " is not registered yet - T27 owns Alkatraz.java");
        for (String id : bundled) {
            assertTrue(saved.contains(id),
                    "spells/" + id + ".yml is bundled but Alkatraz has no saveConfig literal for it");
        }
    }

    @Test
    void theThreeListsHaveTheSameSize() {
        Assumptions.assumeTrue(registered.contains(REGISTRATION_COMPLETE_SENTINEL),
                REGISTRATION_COMPLETE_SENTINEL + " is not registered yet - sizes only coincide from wave 7");
        assertEquals(bundled.size(), registered.size(),
                "the bundled resource count and the registry count must match once registration is complete");
        assertEquals(bundled.size(), saved.size(),
                "the bundled resource count and the saveConfig literal count must match");
        assertEquals(30, bundled.size() - 38,
                "the roster grew by exactly the 30 new spells in spec section 9");
    }
}