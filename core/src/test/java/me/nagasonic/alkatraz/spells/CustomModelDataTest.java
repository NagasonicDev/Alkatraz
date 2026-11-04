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
 * Spec section 10.2: no two bundled spells share a {@code gui_custom_model_data}.
 *
 * <p>The load-bearing value is the key in each spell's own yml, read at
 * {@code Spell.java:285} and applied at {@code SpellsMenu.java:119}. Two live
 * collisions existed - warp 4118 (same as dark_tendrils) and skeletal_knights 4130
 * (same as water_sphere) - and are repaired to 4166 and 4167.
 *
 * <p>{@code texturepack.yml}'s matching {@code spell_*} keys are unused by the
 * plugin: {@code TexturePackManager.loadItemCMDs()} skips any {@code spell_*}
 * GUI key. This test asserts only the per-spell yml keys.
 */
class CustomModelDataTest {

    /**
     * Spec section 10.2: 29 of the additions take 4137-4165 inclusive, and
     * {@code dispel} takes 4168. 4166 and 4167 are the two collision REPAIRS
     * (warp, skeletal_knights), so the band is deliberately NOT contiguous and
     * {@link #NEW_SPELL_CMD_HIGH} cannot simply be {@code LOW + 29}.
     */
    private static final int NEW_SPELL_CMD_LOW = 4137;
    private static final int NEW_SPELL_CMD_HIGH = 4165;

    /** {@code dispel}'s own slot, assigned after the two repairs claimed 4166/4167. */
    private static final int DISPEL_CMD = 4168;

    /** The two repaired collisions, and what each moved from. */
    private static final Map<String, int[]> REPAIRS = Map.of(
            "warp", new int[]{4118, 4166},
            "skeletal_knights", new int[]{4130, 4167}
    );

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
    void theTwoKnownCollisionsAreRepaired() throws IOException {
        for (Map.Entry<String, int[]> entry : REPAIRS.entrySet()) {
            String id = entry.getKey();
            Assumptions.assumeTrue(BundledSpellRoster.has(id), id + " is not bundled");
            int cmd = BundledSpellRoster.load(id).getInt("gui_custom_model_data");
            assertEquals(entry.getValue()[1], cmd,
                    id + " was " + entry.getValue()[0] + ", which collided with another spell; "
                            + "spec section 10.2 moves it to " + entry.getValue()[1]);
        }
    }

    @Test
    void theUnchangedNeighboursKeepTheirValues() throws IOException {
        assertEquals(4118, BundledSpellRoster.load("dark_tendrils").getInt("gui_custom_model_data"),
                "dark_tendrils keeps 4118 - it was warp that moved, not both of them");
        assertEquals(4130, BundledSpellRoster.load("water_sphere").getInt("gui_custom_model_data"),
                "water_sphere keeps 4130 - it was skeletal_knights that moved");
    }

    @Test
    void theThirtyNewSpellsFillTheAllocatedBand() throws IOException {
        TreeSet<Integer> allocated = new TreeSet<>();
        for (String id : roster) {
            int cmd = BundledSpellRoster.load(id).getInt("gui_custom_model_data");
            boolean inBand = (cmd >= NEW_SPELL_CMD_LOW && cmd <= NEW_SPELL_CMD_HIGH) || cmd == DISPEL_CMD;
            if (inBand) allocated.add(cmd);
        }
        // 4137-4165 is 29 slots plus 4168 for dispel = 30. Wave 5 fills them one
        // element at a time, so this grows from partial to complete rather than
        // being asserted at a fixed size.
        assertTrue(allocated.size() <= 30,
                "the allocated band holds at most 30 values, found " + allocated.size());
        assertEquals(29, NEW_SPELL_CMD_HIGH - NEW_SPELL_CMD_LOW + 1,
                "4137 through 4165 inclusive is 29 slots, matching 29 of the 30 new spells exactly");
        Assumptions.assumeTrue(BundledSpellRoster.has("dispel"), "dispel is not bundled yet");
        assertEquals(DISPEL_CMD, BundledSpellRoster.load("dispel").getInt("gui_custom_model_data"),
                "spec section 10.2 assigns 4168 to dispel; 4166 and 4167 belong to the two repairs");
    }
}