package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SummonCombatGuardTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final Map<Entity, ReadableNBT> compounds = new HashMap<>();
    private MockedStatic<NBT> nbt;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        nbt = mockStatic(NBT.class);
        nbt.when(() -> NBT.getPersistentData(any(Entity.class), any(Function.class))).thenAnswer(inv -> {
            Entity entity = inv.getArgument(0);
            Function<ReadableNBT, ?> fn = inv.getArgument(1);
            return fn.apply(compoundOf(entity));
        });
    }

    @AfterEach
    void tearDown() {
        nbt.close();
    }

    private ReadableNBT compoundOf(Entity entity) {
        return compounds.computeIfAbsent(entity, k -> mock(ReadableNBT.class));
    }

    private void stubSummon(Entity entity, String flag, UUID summonerUuid) {
        ReadableNBT compound = compoundOf(entity);
        when(compound.getBoolean("summoned_zombie")).thenReturn("summoned_zombie".equals(flag));
        when(compound.getBoolean("summoned_knight")).thenReturn("summoned_knight".equals(flag));
        when(compound.getBoolean("summoned_mount")).thenReturn("summoned_mount".equals(flag));
        when(compound.getString("summoner_uuid")).thenReturn(summonerUuid == null ? null : summonerUuid.toString());
    }

    private static boolean fire(Entity damager, Entity victim) throws Exception {
        SummonCombatGuard guard = new SummonCombatGuard();
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(victim);
        final boolean[] cancelled = {false};
        doAnswer(inv -> {
            cancelled[0] = true;
            return null;
        }).when(event).setCancelled(anyBoolean());
        guard.onDamage(event);
        return cancelled[0];
    }

    @Test
    void ownerDamagingOwnSummonIsCancelled() throws Exception {
        Entity victim = mock(Entity.class);
        Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(OWNER);
        stubSummon(victim, "summoned_zombie", OWNER);
        assertTrue(fire(owner, victim));
    }

    @Test
    void otherPlayerDamagingSummonIsNotCancelled() throws Exception {
        Entity victim = mock(Entity.class);
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(OTHER);
        stubSummon(victim, "summoned_zombie", OWNER);
        assertFalse(fire(other, victim));
    }

    @Test
    void ownerShootingOwnSummonWithProjectileIsCancelled() throws Exception {
        Entity victim = mock(Entity.class);
        Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(OWNER);
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(owner);
        stubSummon(victim, "summoned_zombie", OWNER);
        assertTrue(fire(arrow, victim));
    }

    @Test
    void summonDamagingAnotherSummonIsCancelled() throws Exception {
        Entity damager = mock(Entity.class);
        Entity victim = mock(Entity.class);
        stubSummon(damager, "summoned_knight", OWNER);
        stubSummon(victim, "summoned_mount", OWNER);
        assertTrue(fire(damager, victim));
    }

    @Test
    void summonDamagingVanillaMobIsNotCancelled() throws Exception {
        Entity damager = mock(Entity.class);
        Entity victim = mock(Entity.class);
        stubSummon(damager, "summoned_zombie", OWNER);
        assertFalse(fire(damager, victim));
    }

    @Test
    void isSummonDetectsAllThreeSummonFlagsOnly() {
        Entity zombie = mock(Entity.class);
        Entity knight = mock(Entity.class);
        Entity mount = mock(Entity.class);
        Entity vanilla = mock(Entity.class);
        stubSummon(zombie, "summoned_zombie", OWNER);
        stubSummon(knight, "summoned_knight", OWNER);
        stubSummon(mount, "summoned_mount", OWNER);
        assertTrue(SummonCombatGuard.isSummon(zombie));
        assertTrue(SummonCombatGuard.isSummon(knight));
        assertTrue(SummonCombatGuard.isSummon(mount));
        assertFalse(SummonCombatGuard.isSummon(vanilla));
    }
}