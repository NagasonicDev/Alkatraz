package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.nms.NMS;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.util.Skin;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two concealment reversals a dispel depends on (spec 8.4).
 *
 * <p>Both are the same shape of assertion: a dispel that only cleared the
 * profile flag would leave the victim permanently invisible (or permanently
 * wearing someone else's face), because neither spell's own quit/join handlers
 * touch anyone but the victim. So the load-bearing claim here is
 * <em>once per online viewer</em>.
 *
 * <p>Every {@code MockedStatic} is closed in {@link #tearDown()}. A leaked one
 * breaks every later test in the JVM.
 */
class ConcealmentReversalTest {

    private static final UUID VICTIM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    private static final UUID CASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID IMPOSTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d0");

    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<Alkatraz> alkatraz;
    private MockedStatic<ProfileManager> profiles;
    private MockedStatic<Skin> skins;

    private NMS nms;
    private Server server;
    private BukkitScheduler scheduler;
    private Player victim;
    private PlayerInventory inventory;
    private MagicProfile victimProfile;

    private Player viewerA;
    private Player viewerB;
    private Player viewerC;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        alkatraz = mockStatic(Alkatraz.class);
        profiles = mockStatic(ProfileManager.class);
        skins = mockStatic(Skin.class);

        nms = mock(NMS.class);
        alkatraz.when(Alkatraz::getNms).thenReturn(nms);

        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        bukkit.when(Bukkit::getServer).thenReturn(server);

        inventory = mock(PlayerInventory.class);
        victim = mock(Player.class);
        when(victim.getUniqueId()).thenReturn(VICTIM_ID);
        when(victim.getName()).thenReturn("Victim");
        when(victim.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(null);
        when(inventory.getChestplate()).thenReturn(null);
        when(inventory.getLeggings()).thenReturn(null);
        when(inventory.getBoots()).thenReturn(null);

        victimProfile = mock(MagicProfile.class);
        profiles.when(() -> ProfileManager.getProfile(VICTIM_ID, MagicProfile.class)).thenReturn(victimProfile);

        viewerA = mock(Player.class);
        viewerB = mock(Player.class);
        viewerC = mock(Player.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(viewerA, viewerB, viewerC, victim));
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
        alkatraz.close();
        profiles.close();
        skins.close();
    }

    @Test
    void stealthDispelReversesInvisibilityForEveryOnlineViewer() {
        when(victimProfile.isStealth()).thenReturn(true);
        Stealth stealth = new Stealth("STEALTH");

        stealth.dispel(victim);

        // Four online players: three other viewers plus the victim themselves, who
        // is in Bukkit.getOnlinePlayers() too. All four must be reset — the onQuit
        // and onJoin handlers only ever pass `player, player`, which is precisely
        // the bug that made them unusable here.
        //
        // setInvisible takes no viewer, so it is once per loop iteration and lands
        // four times. setTransparent and fakeArmor each name one viewer, so each of
        // the four viewers must be named exactly once: "times(4)" on a single viewer
        // would be unreachable, and an aggregate-only assertion could not tell four
        // viewers from the same viewer four times.
        verify(nms, times(4)).setInvisible(victim, false);
        verify(nms, times(1)).setTransparent(victim, viewerA, false);
        verify(nms, times(1)).setTransparent(victim, viewerB, false);
        verify(nms, times(1)).setTransparent(victim, viewerC, false);
        verify(nms, times(1)).setTransparent(victim, victim, false);
        verify(nms, times(1)).fakeArmor(victim, viewerA, null, null, null, null);
        verify(nms, times(1)).fakeArmor(victim, viewerB, null, null, null, null);
        verify(nms, times(1)).fakeArmor(victim, viewerC, null, null, null, null);
        verify(nms, times(1)).fakeArmor(victim, victim, null, null, null, null);
        // ...and no fifth viewer was invented.
        verify(nms, times(4)).setTransparent(same(victim), any(Player.class), eq(false));
        verify(nms, times(4)).fakeArmor(same(victim), any(Player.class), any(), any(), any(), any());
    }

    @Test
    void stealthDispelClearsTheProfileFlagAndCancelsTheUpkeepTask() {
        when(victimProfile.isStealth()).thenReturn(true);
        Stealth stealth = new Stealth("STEALTH");
        stealth.seedStealthTaskForTest(VICTIM_ID, 4242);

        stealth.dispel(victim);

        verify(victimProfile).setStealth(false);
        verify(scheduler).cancelTask(4242);
    }

    @Test
    void stealthDispelOnAPlayerWhoWasNotStealthedDoesNothing() {
        when(victimProfile.isStealth()).thenReturn(false);
        Stealth stealth = new Stealth("STEALTH");

        stealth.dispel(victim);

        verify(nms, never()).setInvisible(any(Entity.class), anyBoolean());
        verify(victimProfile, never()).setStealth(anyBoolean());
    }

    @Test
    void disguiseDispelResetsTheSkinBeforeItTouchesTheProfile() {
        when(victimProfile.getDisguise()).thenReturn(IMPOSTER_ID.toString());
        Skin ownSkin = mock(Skin.class);
        skins.when(() -> Skin.fromURL(anyString())).thenReturn(ownSkin);

        Disguise disguise = new Disguise("DISGUISE");
        assertTrue(disguise.dispel(victim), "a successful skin reset must report cleared");

        // `victim` has to be in the InOrder too: setDisplayName is called on the
        // player, not on the NMS facade, so ordering it against a pair that
        // excludes it is not a thing InOrder can express.
        InOrder order = inOrder(nms, victimProfile, victim);
        order.verify(nms).changeSkin(victim, List.of(viewerA, viewerB, viewerC, victim), ownSkin);
        order.verify(victimProfile).setDisguise(isNull());
        order.verify(victim).setDisplayName("Victim");
    }

    @Test
    void disguiseDispelReportsResistedWhenTheSkinFetchThrows() {
        when(victimProfile.getDisguise()).thenReturn(IMPOSTER_ID.toString());
        skins.when(() -> Skin.fromURL(anyString()))
                .thenThrow(new RuntimeException("sessionserver unreachable"));

        Disguise disguise = new Disguise("DISGUISE");

        assertFalse(disguise.dispel(victim),
                "a Mojang outage must report resisted rather than half-clearing the player");
        verify(victimProfile, never()).setDisguise(anyString());
        verify(nms, never()).changeSkin(any(Player.class), anyList(), any(Skin.class));
    }

    @Test
    void disguiseDispelReportsResistedForABlankOrUnparseableStoredId() {
        Disguise disguise = new Disguise("DISGUISE");

        when(victimProfile.getDisguise()).thenReturn("");
        assertFalse(disguise.dispel(victim),
                "'' is what Profile.stringStat registers as its default, and a dispel writes null "
                        + "in memory — both must read as 'not disguised', not as a parse failure");

        when(victimProfile.getDisguise()).thenReturn("not-a-uuid");
        assertFalse(disguise.dispel(victim),
                "an unparseable stored id must resist rather than throw out of a cast");

        skins.verify(() -> Skin.fromURL(anyString()), never());
    }

    @Test
    void disguiseCancelCastDropsThePendingCastSoTheCloseCannotRefund() {
        Disguise disguise = new Disguise("DISGUISE");

        // cancelCast must not throw on a caster with no pending cast.
        disguise.cancelCast(CASTER_ID);
        disguise.cancelCast(VICTIM_ID);

        verify(victim, never()).closeInventory();
    }
}