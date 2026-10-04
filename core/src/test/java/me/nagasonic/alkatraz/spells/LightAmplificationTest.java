package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.LightAmplification;
import me.nagasonic.alkatraz.spells.util.UndeadTypes;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The amplification arithmetic and its cap are pinned here, together with the
 * roster that Radiance and every light spell share. The target is a mocked
 * {@link Player} so the affinity lookup goes through a mocked
 * {@link MagicProfile} - a real mob target would hit NBT persistent data, which
 * needs a live server.
 */
class LightAmplificationTest {

    private MockedStatic<ProfileManager> profileMock;
    private MagicProfile profile;
    private Player caster;
    private Player target;

    @BeforeEach
    void setUp() {
        profileMock = mockStatic(ProfileManager.class);
        profile = mock(MagicProfile.class);
        caster = mock(Player.class);
        target = mock(Player.class);
        profileMock.when(() -> ProfileManager.getProfile(target, MagicProfile.class)).thenReturn(profile);
        profileMock.when(() -> ProfileManager.getProfile(caster, MagicProfile.class)).thenReturn(profile);
    }

    @AfterEach
    void tearDown() {
        profileMock.close();
    }

    private void affinity(double dark, double light) {
        when(profile.getAffinity(Element.DARK)).thenReturn(dark);
        when(profile.getAffinity(Element.LIGHT)).thenReturn(light);
    }

    @Test
    void undeadRosterMatchesRadianceExactly() {
        assertEquals(13, UndeadTypes.all().size(), "the promoted roster must keep all 13 Radiance entries");
        // The three that are easy to lose when the set is retyped by hand.
        assertTrue(UndeadTypes.contains(EntityType.ZOGLIN), "zoglin is on the Radiance roster");
        assertTrue(UndeadTypes.contains(EntityType.SKELETON_HORSE), "skeleton horse is on the Radiance roster");
        assertTrue(UndeadTypes.contains(EntityType.ZOMBIE_HORSE), "zombie horse is on the Radiance roster");
        // The three that are NOT undead anywhere in the shipped source.
        assertFalse(UndeadTypes.contains(EntityType.ENDERMAN), "enderman is not on the Radiance roster");
        assertFalse(UndeadTypes.contains(EntityType.SILVERFISH), "silverfish is not on the Radiance roster");
        assertFalse(UndeadTypes.contains(EntityType.GHAST), "ghast is not on the Radiance roster");
        assertTrue(UndeadTypes.contains(EntityType.ZOMBIE), "zombie is undead");
        assertTrue(UndeadTypes.contains(EntityType.WITHER), "wither is on the roster");
        assertFalse(UndeadTypes.contains(EntityType.PLAYER), "players are not undead");
        assertFalse(UndeadTypes.contains(null), "a null type must not throw");
    }

    @Test
    void rosterIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () -> UndeadTypes.all().add(EntityType.PLAYER),
                "all() hands out the shared set, so callers must not be able to mutate the roster");
    }

    @Test
    void constantsMatchTheSpec() {
        assertEquals(2.0, LightAmplification.UNDEAD_MULTIPLIER, 1e-9,
                "the undead term is a flat x2.0");
        assertEquals(150.0, LightAmplification.GAP_CAP, 1e-9,
                "the dark-invested term caps at a 150 affinity gap");
    }

    @Test
    void nullTargetTakesNoBonus() {
        assertEquals(1.0, LightAmplification.forTarget(caster, null), 1e-9,
                "there is no target to amplify against, so light damage is unchanged");
    }

    @Test
    void neutralTargetTakesNoBonus() {
        affinity(0.0, 0.0);
        assertEquals(1.0, LightAmplification.forTarget(caster, target), 1e-9,
                "a target invested in neither element takes no bonus");
    }

    @Test
    void darkInvestedTargetScalesWithTheGap() {
        affinity(105.0, 0.0);
        assertEquals(2.05, LightAmplification.forTarget(caster, target), 1e-9,
                "a 105-point darkness gap is a x2.05 multiplier");
    }

    @Test
    void capHoldsAtOneHundredFiftyGap() {
        affinity(500.0, 0.0);
        assertEquals(2.5, LightAmplification.forTarget(caster, target), 1e-9,
                "the dark-invested term is capped at x2.5");
    }

    @Test
    void lightInvestedTargetTakesNoBonus() {
        affinity(20.0, 80.0);
        assertEquals(1.0, LightAmplification.forTarget(caster, target), 1e-9,
                "a light-invested target is never amplified against light");
    }

    @Test
    void missingProfileIsTreatedAsNoAffinity() {
        profileMock.when(() -> ProfileManager.getProfile(target, MagicProfile.class)).thenReturn(null);
        assertEquals(1.0, LightAmplification.forTarget(caster, target), 1e-9,
                "an unloaded profile must not throw and must fall back to no bonus");
    }

    @Test
    void undeadTargetTakesTheFlatBonus() {
        when(target.getType()).thenReturn(EntityType.ZOMBIE);
        affinity(0.0, 0.0);
        assertEquals(2.0, LightAmplification.forTarget(caster, target), 1e-9,
                "an undead target takes the flat x2.0 regardless of its affinities");
    }

    @Test
    void undeadAndDarkInvestedTakeTheMaxNotTheProduct() {
        when(target.getType()).thenReturn(EntityType.WITHER_SKELETON);
        affinity(500.0, 0.0);
        assertEquals(2.0, LightAmplification.forTarget(caster, target), 1e-9,
                "the two terms take the MAX, so an undead that is also dark-invested caps at x2.0, not x5.0");
    }

    @Test
    void nonUndeadTypeIsScoredOnAffinityOnly() {
        when(target.getType()).thenReturn(EntityType.PLAYER);
        affinity(0.0, 0.0);
        assertEquals(1.0, LightAmplification.forTarget(caster, target), 1e-9,
                "a living player target falls through to the affinity branch, not the undead branch");
    }

    @Test
    void theTwoTermsTakeTheMaxAndNeverTheProduct() {
        // An undead that is also dark-invested must cap at x2.5, not reach x5.0.
        double undeadTerm = LightAmplification.UNDEAD_MULTIPLIER;                 // 2.0
        double darkTerm = 1.0 + Math.min(215.0 - 0.0, LightAmplification.GAP_CAP) / 100.0;  // 2.5
        assertEquals(2.5, Math.max(undeadTerm, darkTerm), 1e-9,
                "the stronger term wins outright");
        assertEquals(5.0, undeadTerm * darkTerm, 1e-9,
                "and the product of 5.0 is explicitly the behaviour being prevented - spec "
                        + "section 5.3 says the two terms take the stronger, not the product");
    }

    @Test
    void theUndeadTermWinsAtEqualStrength() {
        double undeadTerm = LightAmplification.UNDEAD_MULTIPLIER;                  // 2.0
        double weakDarkTerm = 1.0 + Math.min(60.0, LightAmplification.GAP_CAP) / 100.0;  // 1.6
        assertEquals(2.0, Math.max(undeadTerm, weakDarkTerm), 1e-9,
                "a mildly dark-invested zombie takes the flat undead x2.0, not x1.6");
    }

    @Test
    void theAmplificationIsABoundedCoefficient() {
        assertTrue(LightAmplification.UNDEAD_MULTIPLIER >= 2.0,
                "the undead term is at least x2.0 - that is the whole point of the counter-element");
        assertTrue(1.0 + LightAmplification.GAP_CAP / 100.0 <= 2.5 + 1e-9,
                "the dark-invested term caps at x2.5");
        assertTrue(LightAmplification.forTarget(null, null) == 1.0,
                "a null target is neutral rather than a crash - a payload with no living target "
                        + "must not be amplified or divided by zero");
    }

    @Test
    void amplificationOnlyReachesLightSpells() {
        // getPower gates the whole term on the spell's element, so no non-light spell
        // can inherit it. Asserting the gate rather than the multiplication is the part
        // that matters - AttackSpellPowerTest covers the arithmetic with mocks.
        assertTrue(me.nagasonic.alkatraz.spells.util.SpellBudgets.isAllowed(
                        me.nagasonic.alkatraz.api.Element.LIGHT, SpellRole.DENIAL),
                "light owns BURST, SUSTAIN, UTILITY and DENIAL, so every one of its damage roles "
                        + "is amplified - there is no light role that escapes the counter-element");
    }
}