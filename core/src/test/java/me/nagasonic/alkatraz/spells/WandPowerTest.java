package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * getWandPower counted the wand twice in two different unit systems (percent on
 * the item definition, raw fraction on the profile stat), yielding 52.8x
 * instead of ~1.43x for a fully kitted profile. The two-arg overload must be
 * percent-only and must source from the profile, which already includes
 * MAIN_HAND.
 * <p>
 * Note on the wand argument: {@code ItemStack.hasItemMeta()} calls
 * {@code Bukkit.getItemFactory()}, so any code path that actually inspects a
 * wand needs a live server. The profile-present cases below pass a real
 * {@link ItemStack} precisely because the fixed implementation never touches it;
 * the fallback cases pass {@code null}, which the one-arg overload short-circuits.
 */
class WandPowerTest {

    private MockedStatic<Bukkit> bukkitMock;
    private MockedStatic<ProfileManager> profileMock;
    private Player player;
    private MagicProfile profile;

    @BeforeEach
    void setUp() {
        bukkitMock = mockStatic(Bukkit.class);
        profileMock = mockStatic(ProfileManager.class);
        player = mock(Player.class);
        profile = mock(MagicProfile.class);
        profileMock.when(() -> ProfileManager.getProfile(player, MagicProfile.class)).thenReturn(profile);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
        profileMock.close();
    }

    @Test
    void kittedProfileYieldsPercentNotMultiplier() {
        // astral wand 20 + runic grimoire 15 + 4 jewellery + 4pc set = 43
        when(profile.getSpellPower()).thenReturn(43.0);
        assertEquals(1.43, Spell.getWandPower(new ItemStack(Material.STICK), player), 1e-9,
                "a fully kitted profile must be 1 + 43/100 = 1.43, not (1 + 20/100) * 44 = 52.8");
    }

    @Test
    void emptyProfileIsNeutral() {
        when(profile.getSpellPower()).thenReturn(0.0);
        assertEquals(1.0, Spell.getWandPower(new ItemStack(Material.STICK), player), 1e-9,
                "no spell power means no multiplier");
    }

    @Test
    void nullPlayerFallsBackToItemDefinition() {
        assertEquals(1.0, Spell.getWandPower((ItemStack) null, (Player) null), 1e-9,
                "a null wand with no player is neutral");
    }

    @Test
    void missingProfileFallsBackToItemDefinition() {
        profileMock.when(() -> ProfileManager.getProfile(player, MagicProfile.class)).thenReturn(null);
        assertEquals(1.0, Spell.getWandPower((ItemStack) null, player), 1e-9,
                "a missing profile must not throw and must fall back to the item definition");
    }

    @Test
    void orDefaultOverloadInheritsThePercentReading() {
        when(profile.getSpellPower()).thenReturn(43.0);
        assertEquals(1.43, Spell.getWandPowerOrDefault(new ItemStack(Material.STICK), player), 1e-9,
                "getWandPowerOrDefault delegates to the two-arg overload and must not re-inflate it");
    }

    @Test
    void tenPercentWandIsReadAsATenthNotTenTimes() {
        when(profile.getSpellPower()).thenReturn(10.0);
        assertEquals(1.10, Spell.getWandPower((ItemStack) null, player), 1e-9,
                "a stored spell_power of 10 is a 10% bonus, never a 10x bonus");
    }

    @Test
    void percentSemanticsAreMonotonicInSpellPower() {
        when(profile.getSpellPower()).thenReturn(10.0);
        assertEquals(1.10, Spell.getWandPower(new ItemStack(Material.STICK), player), 1e-9,
                "10 spell power is a 10% bonus");
        when(profile.getSpellPower()).thenReturn(100.0);
        assertEquals(2.00, Spell.getWandPower(new ItemStack(Material.STICK), player), 1e-9,
                "100 spell power doubles the spell");
        when(profile.getSpellPower()).thenReturn(1000.0);
        assertEquals(11.00, Spell.getWandPower(new ItemStack(Material.STICK), player), 1e-9,
                "1000 spell power is 11x, linear in percent - no compounding");
    }

    @Test
    void theOldUnitMixWouldHaveInflatedAKittedProfileByThirtySevenTimes() {
        when(profile.getSpellPower()).thenReturn(43.0);
        double fixed = Spell.getWandPower(new ItemStack(Material.STICK), player);
        // The old expression counted the wand twice in two unit systems: the item
        // definition's percent (1 + 20/100) times the profile stat read as a raw
        // fraction (1 + 43).
        double broken = (1 + 20 / 100.0) * (1 + 43);
        assertEquals(1.43, fixed, 1e-9, "the fixed form is 1 + 43/100");
        assertEquals(52.8, broken, 1e-9, "the old form is 52.8 - this is the number being removed");
        assertTrue(broken / fixed > 36.0,
                "the bug was worth roughly x37 on a fully kitted circle-9 profile, which is why "
                        + "every budget number in the design depends on the fix landing first");
    }

    @Test
    void theNoPlayerOverloadIsNeutralForAPlainWand() {
        // A wand with no alkatraz:spell_power attribute, so the one-arg overload -
        // which mob casting uses - is exactly 1.0.
        //
        // Reading a real ItemStack needs Bukkit.getItemFactory(), hence the stubbed
        // factory/meta/PDC below. The non-magic branch of the overload falls through to
        // legacy NBT via item-nbt-api, which cannot initialise its reflection layer
        // without a live CraftBukkit server, so the fixture presents a magic item - one
        // whose PDC carries an item_definition key - and that definition resolves to no
        // spell_power attribute. That is the NBT-free reading of "no attribute, no
        // multiplier" and it exercises the real percent-only lookup in Spell.
        ItemFactory factory = mock(ItemFactory.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(factory.getItemMeta(Material.STICK)).thenReturn(meta);
        // readStack asks for item_definition and then item_instance; only the first
        // resolves, so the instance falls back to the definition's default.
        when(pdc.get(any(), ArgumentMatchers.<PersistentDataType<String, String>>any()))
                .thenReturn("alkatraz:plain_wand")
                .thenReturn(null);
        bukkitMock.when(Bukkit::getItemFactory).thenReturn(factory);

        assertEquals(1.0, Spell.getWandPower(new ItemStack(Material.STICK)), 1e-9,
                "a wand with no spell_power attribute must not invent a multiplier");
    }
}