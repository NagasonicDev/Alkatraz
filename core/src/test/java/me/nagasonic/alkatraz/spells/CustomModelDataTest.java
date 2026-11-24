package me.nagasonic.alkatraz.spells;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec section 4: no two bundled spells share a {@code gui_custom_model_data}.
 *
 * <p>The load-bearing value is the key in each spell's own yml, read at
 * {@code Spell.java:285} and applied at {@code SpellsMenu.java:119}.
 *
 * <p>{@code texturepack.yml}'s matching {@code spell_*} keys are unused by the
 * plugin: {@code TexturePackManager.loadItemCMDs()} skips any {@code spell_*}
 * GUI key. This test asserts only the per-spell yml keys.
 */
class CustomModelDataTest {

    /**
     * Spec section 4: the 35 new circle 5-9 spells take the contiguous
     * 4200-4234 band.
     */
    private static final int NEW_SPELL_CMD_LOW = 4200;
    private static final int NEW_SPELL_CMD_HIGH = 4234;
    private static final int NEW_SPELL_COUNT = 35;

    private static List<String> roster;

    @BeforeAll
    static void discoverRoster() {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "the spells resource directory is not enumerable on this classpath");
        roster = BundledSpellRoster.spellIds();
    }

    @Test
    void everySpellDeclaresAPositiveCustomModelData() throws IOException {
        for (String id : roster) {
            int cmd = BundledSpellRoster.load(id).getInt("gui_custom_model_data");
            assertTrue(cmd > 0,
                    id + " declares gui_custom_model_data: " + cmd + "; a zero means the GUI item "
                            + "renders as a plain stack with no model, and getInt's default is 0");
        }
    }

    @Test
    void noTwoSpellsShareACustomModelData() throws IOException {
        Map<Integer, String> owner = new LinkedHashMap<>();
        List<String> collisions = new ArrayList<>();
        for (String id : roster) {
            int cmd = BundledSpellRoster.load(id).getInt("gui_custom_model_data");
            String previous = owner.putIfAbsent(cmd, id);
            if (previous != null) {
                collisions.add(previous + " and " + id + " both use " + cmd);
            }
        }
        assertTrue(collisions.isEmpty(),
                "a shared gui_custom_model_data renders the wrong texture in the spells menu: "
                        + String.join("; ", collisions));
    }

    @Test
    void theUnchangedNeighboursKeepTheirValues() throws IOException {
        assertEquals(4118, BundledSpellRoster.load("dark_tendrils").getInt("gui_custom_model_data"),
                "dark_tendrils keeps its pinned 4118");
        assertEquals(4130, BundledSpellRoster.load("water_sphere").getInt("gui_custom_model_data"),
                "water_sphere keeps its pinned 4130");
    }

    @Test
    void theThirtyFiveNewSpellsFillTheAllocatedBand() throws IOException {
        TreeSet<Integer> allocated = new TreeSet<>();
        for (String id : roster) {
            int cmd = BundledSpellRoster.load(id).getInt("gui_custom_model_data");
            if (cmd >= NEW_SPELL_CMD_LOW && cmd <= NEW_SPELL_CMD_HIGH) allocated.add(cmd);
        }
        assertEquals(NEW_SPELL_COUNT, NEW_SPELL_CMD_HIGH - NEW_SPELL_CMD_LOW + 1,
                "4200 through 4234 inclusive is 35 slots, one per new spell");
        assertEquals(NEW_SPELL_COUNT, allocated.size(),
                "every one of the 35 band slots must be claimed by a bundled spell; a partial or empty "
                        + "allocation means a new spell is missing its gui_custom_model_data");
        assertEquals(NEW_SPELL_CMD_LOW, allocated.first(), "the band starts at 4200");
        assertEquals(NEW_SPELL_CMD_HIGH, allocated.last(), "and ends at 4234");
    }
}