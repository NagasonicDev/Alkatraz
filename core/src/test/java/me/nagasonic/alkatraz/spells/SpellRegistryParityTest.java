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
 * moment a spell yml lands. Resource -&gt; registry only holds once the final batch has
 * registered the last of the 35 new spells, so that one direction self-activates off
 * the sentinel id {@code leviathans_testament} - the last of the 35 banded additions in
 * the custom model data allocation, so it cannot be registered before the rest.
 */
class SpellRegistryParityTest {

    /** Added by the final batch; see the class javadoc for why this is the sentinel. */
    private static final String REGISTRATION_COMPLETE_SENTINEL = "leviathans_testament";

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
    void everyBundledSpellIsRegisteredOnceTheFinalBatchLands() {
        Assumptions.assumeTrue(registered.contains(REGISTRATION_COMPLETE_SENTINEL),
                REGISTRATION_COMPLETE_SENTINEL + " is not registered yet - the final batch owns "
                        + "SpellRegistry.java, so the resource -> registry direction is asserted once it lands");
        for (String id : bundled) {
            assertTrue(registered.contains(id),
                    "spells/" + id + ".yml is bundled but registerSpells() never registers it, so it "
                            + "is invisible to every menu, grimoire and tab-completion path");
        }
    }

    @Test
    void everyBundledSpellIsSavedOnceTheFinalBatchLands() {
        Assumptions.assumeTrue(registered.contains(REGISTRATION_COMPLETE_SENTINEL),
                REGISTRATION_COMPLETE_SENTINEL + " is not registered yet - the final batch owns Alkatraz.java");
        for (String id : bundled) {
            assertTrue(saved.contains(id),
                    "spells/" + id + ".yml is bundled but Alkatraz has no saveConfig literal for it");
        }
    }

    @Test
    void theThreeListsHaveTheSameSize() {
        Assumptions.assumeTrue(registered.contains(REGISTRATION_COMPLETE_SENTINEL),
                REGISTRATION_COMPLETE_SENTINEL + " is not registered yet - sizes only coincide once registration is complete");
        assertEquals(bundled.size(), registered.size(),
                "the bundled resource count and the registry count must match once registration is complete");
        assertEquals(bundled.size(), saved.size(),
                "the bundled resource count and the saveConfig literal count must match");
        // The rework leaves 33 survivors and adds 35 new spells, so the shipped roster is
        // 68 - which is 38 minus the 35 deletions plus the 35 additions, and therefore
        // also 38 + 30. The expectation is expressed as the delta from the 38 pre-rework
        // spells because that is the number the roster arithmetic actually produces.
        int expectedNewSpells = 30;
        assertEquals(expectedNewSpells, bundled.size() - 38,
                "the roster must settle at 68 spells (33 survivors + 35 new)");
    }

    /**
     * The one failure mode in this file that no other test can see.
     *
     * <p>Wave 7 rewrote {@code registerSpells()} as a loop over {@code SPELL_IDS},
     * which is derived from {@code SPELL_FACTORY.keySet()}. Java runs field
     * initialisers and static blocks in SOURCE ORDER, so if the {@code static { ... }}
     * block that populates the factory map ever moves below the {@code SPELL_IDS}
     * declaration, {@code SPELL_IDS} freezes an empty list at class-load time.
     * {@code registerSpells()} then loops zero times and the plugin logs
     * "Registered 0 spells." with every spell silently dead - no exception, no
     * warning, no menu entry, no error in the console beyond the count.
     *
     * <p>Every other test in this class reads the source as TEXT and never executes
     * it, so they all stay green through that regression. This one pins the order.
     */
    @Test
    void theFactoryStaticBlockIsDeclaredBeforeSpellIds() throws IOException {
        String source = BundledSpellRoster.readSource("src/main/java/me/nagasonic/alkatraz/spells/SpellRegistry.java");
        int staticBlock = source.indexOf("static {");
        int spellIds = source.indexOf("SPELL_IDS =");
        assertTrue(staticBlock >= 0,
                "SpellRegistry.java has no 'static {' block, so SPELL_FACTORY is never populated");
        assertTrue(spellIds >= 0,
                "SpellRegistry.java no longer declares SPELL_IDS; registerSpells() cannot loop over it");
        assertTrue(staticBlock < spellIds,
                "the static block that populates SPELL_FACTORY is declared BELOW SPELL_IDS, so "
                        + "SPELL_IDS freezes an empty list and registerSpells() registers nothing. "
                        + "Move the static block above the SPELL_IDS declaration.");
    }
}