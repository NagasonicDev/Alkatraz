package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.hooks.Protection;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Per-affected-player consent: opt-in is explicit and Protection is still checked. */
class ConsentSafetyTeleportTest {

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        Map<NamespacedKey, Byte> values = new HashMap<>();
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
                .thenAnswer(inv -> values.get(inv.<NamespacedKey>getArgument(0)));
        doAnswer(inv -> {
            values.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(data).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
        doAnswer(inv -> {
            values.remove(inv.getArgument(0));
            return null;
        }).when(data).remove(any(NamespacedKey.class));
        when(player.getPersistentDataContainer()).thenReturn(data);
        return player;
    }

    @Test
    void consentIsOffUntilExplicitlyEnabled() {
        Player ally = player(UUID.randomUUID());

        assertFalse(TeleportSafety.hasTeleportConsent(ally));
        assertTrue(TeleportSafety.setTeleportConsent(ally, true));
        assertTrue(TeleportSafety.hasTeleportConsent(ally));
        assertFalse(TeleportSafety.toggleTeleportConsent(ally));
        assertFalse(TeleportSafety.hasTeleportConsent(ally));
    }

    @Test
    void movingAnotherPlayerRequiresConsentAndProtection() {
        World world = mock(World.class);
        Location destination = new Location(world, 4, 64, 4);
        Player caster = player(UUID.randomUUID());
        Player ally = player(UUID.randomUUID());

        try (MockedStatic<Protection> protection = mockStatic(Protection.class)) {
            protection.when(() -> Protection.teleport(any(), any())).thenReturn(true);

            assertFalse(TeleportSafety.canTeleportAlly(caster, ally, destination));
            TeleportSafety.setTeleportConsent(ally, true);
            assertTrue(TeleportSafety.canTeleportAlly(caster, ally, destination));

            protection.when(() -> Protection.teleport(any(), any())).thenReturn(false);
            assertFalse(TeleportSafety.canTeleportAlly(caster, ally, destination));
        }
    }

    @Test
    void casterDoesNotNeedConsentButStillNeedsProtection() {
        World world = mock(World.class);
        Location destination = new Location(world, 1, 64, 1);
        Player caster = player(UUID.randomUUID());

        try (MockedStatic<Protection> protection = mockStatic(Protection.class)) {
            protection.when(() -> Protection.teleport(any(), any())).thenReturn(true);
            assertTrue(TeleportSafety.canTeleportAlly(caster, caster, destination));
            protection.when(() -> Protection.teleport(any(), any())).thenReturn(false);
            assertFalse(TeleportSafety.canTeleportAlly(caster, caster, destination));
        }
    }
}
