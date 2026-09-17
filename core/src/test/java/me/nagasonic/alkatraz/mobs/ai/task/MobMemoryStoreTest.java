package me.nagasonic.alkatraz.mobs.ai.task;

import de.tr7zw.changeme.nbtapi.NBTCompound;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.MemoryStatus;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobMemoryStoreTest {

    @Test
    void unregisteredKeysAreAbsent() {
        MobMemoryStore store = MobMemoryStore.empty();
        assertEquals(MemoryStatus.ABSENT, store.status(MemoryKey.IS_HURT, 0));
        assertFalse(store.present(MemoryKey.IS_HURT, 0));
    }

    @Test
    void registeredKeysAreRegisteredUntilWritten() {
        MobMemoryStore store = MobMemoryStore.empty();
        store.register(MemoryKey.IS_HURT);
        assertEquals(MemoryStatus.REGISTERED, store.status(MemoryKey.IS_HURT, 0));
        assertFalse(store.present(MemoryKey.IS_HURT, 0));

        store.set(MemoryKey.IS_HURT, true);
        assertEquals(MemoryStatus.PRESENT, store.status(MemoryKey.IS_HURT, 0));
        assertTrue(store.present(MemoryKey.IS_HURT, 0));

        store.erase(MemoryKey.IS_HURT);
        assertEquals(MemoryStatus.REGISTERED, store.status(MemoryKey.IS_HURT, 0));
        assertFalse(store.present(MemoryKey.IS_HURT, 0));
    }

    @Test
    void eraseUnregisteredReturnsToAbsent() {
        MobMemoryStore store = MobMemoryStore.empty();
        store.set(MemoryKey.WALK_TARGET, "x", -1);
        store.erase(MemoryKey.WALK_TARGET);
        assertEquals(MemoryStatus.ABSENT, store.status(MemoryKey.WALK_TARGET, 0));
    }

    @Test
    void expiryIsRespectedOnReadAndSweep() {
        MobMemoryStore store = MobMemoryStore.empty();
        store.set(MemoryKey.IS_HURT, true, 100);
        assertTrue(store.present(MemoryKey.IS_HURT, 99));
        assertTrue(store.get(MemoryKey.IS_HURT, 99).isPresent());
        assertFalse(store.present(MemoryKey.IS_HURT, 100));
        assertEquals(Optional.empty(), store.get(MemoryKey.IS_HURT, 100));
        assertTrue(store.liveValues(100).isEmpty());
    }

    @Test
    void valueEqualityByKeyId() {
        MobMemoryStore store = MobMemoryStore.empty();
        store.set(new MemoryKey<String>("custom"), "hello");
        assertEquals("hello", store.get(new MemoryKey<String>("custom"), 0).orElseThrow());
    }

    @Test
    void nullValueAndNullKeyRejected() {
        MobMemoryStore store = MobMemoryStore.empty();
        assertThrows(NullPointerException.class, () -> store.set(MemoryKey.IS_HURT, null));
        assertThrows(NullPointerException.class, () -> store.set(null, "x"));
        assertThrows(NullPointerException.class, () -> store.get(null, 0));
    }

    @Test
    void writeToWritesAllSupportedTypes_andSkipsExpiredEntries() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        LivingEntity target = mock(LivingEntity.class);
        UUID targetId = UUID.randomUUID();
        when(target.getUniqueId()).thenReturn(targetId);

        NBTCompound nbt = mock(NBTCompound.class);
        NBTCompound isHurtSlot = mock(NBTCompound.class);
        NBTCompound isHurtValue = mock(NBTCompound.class);
        NBTCompound intSlot = mock(NBTCompound.class);
        NBTCompound intValue = mock(NBTCompound.class);
        NBTCompound longSlot = mock(NBTCompound.class);
        NBTCompound longValue = mock(NBTCompound.class);
        NBTCompound doubleSlot = mock(NBTCompound.class);
        NBTCompound doubleValue = mock(NBTCompound.class);
        NBTCompound customSlot = mock(NBTCompound.class);
        NBTCompound customValue = mock(NBTCompound.class);
        NBTCompound walkSlot = mock(NBTCompound.class);
        NBTCompound walkValue = mock(NBTCompound.class);
        NBTCompound attackSlot = mock(NBTCompound.class);
        NBTCompound attackValue = mock(NBTCompound.class);

        when(nbt.getOrCreateCompound("is_hurt")).thenReturn(isHurtSlot);
        when(nbt.getOrCreateCompound("int")).thenReturn(intSlot);
        when(nbt.getOrCreateCompound("long")).thenReturn(longSlot);
        when(nbt.getOrCreateCompound("double")).thenReturn(doubleSlot);
        when(nbt.getOrCreateCompound("custom")).thenReturn(customSlot);
        when(nbt.getOrCreateCompound("walk_target")).thenReturn(walkSlot);
        when(nbt.getOrCreateCompound("attack_target")).thenReturn(attackSlot);
        when(isHurtSlot.getOrCreateCompound("value")).thenReturn(isHurtValue);
        when(intSlot.getOrCreateCompound("value")).thenReturn(intValue);
        when(longSlot.getOrCreateCompound("value")).thenReturn(longValue);
        when(doubleSlot.getOrCreateCompound("value")).thenReturn(doubleValue);
        when(customSlot.getOrCreateCompound("value")).thenReturn(customValue);
        when(walkSlot.getOrCreateCompound("value")).thenReturn(walkValue);
        when(attackSlot.getOrCreateCompound("value")).thenReturn(attackValue);

        MobMemoryStore store = MobMemoryStore.empty();
        store.set(MemoryKey.IS_HURT, true, 100);
        store.set(new MemoryKey<Integer>("int"), 42, -1);
        store.set(new MemoryKey<Long>("long"), Long.MAX_VALUE, -1);
        store.set(new MemoryKey<Double>("double"), 3.5, -1);
        store.set(new MemoryKey<String>("custom"), "hello", -1);
        store.set(MemoryKey.WALK_TARGET, new Location(world, 1, 2, 3, 90f, 45f), -1);
        store.set(MemoryKey.ATTACK_TARGET, target, 500);

        store.writeTo(nbt, 0);

        verify(isHurtSlot).setLong("expiry", 100L);
        verify(isHurtSlot).setString("type", "BOOLEAN");
        verify(isHurtValue).setBoolean("v", true);
        verify(intSlot).setLong("expiry", -1L);
        verify(intSlot).setString("type", "INTEGER");
        verify(intValue).setInteger("v", 42);
        verify(longSlot).setLong("expiry", -1L);
        verify(longSlot).setString("type", "LONG");
        verify(longValue).setLong("v", Long.MAX_VALUE);
        verify(doubleSlot).setLong("expiry", -1L);
        verify(doubleSlot).setString("type", "DOUBLE");
        verify(doubleValue).setDouble("v", 3.5);
        verify(customSlot).setLong("expiry", -1L);
        verify(customSlot).setString("type", "STRING");
        verify(customValue).setString("v", "hello");
        verify(walkSlot).setLong("expiry", -1L);
        verify(walkSlot).setString("type", "LOCATION");
        verify(walkValue).setString("world", "world");
        verify(walkValue).setDouble("x", 1.0);
        verify(walkValue).setDouble("y", 2.0);
        verify(walkValue).setDouble("z", 3.0);
        verify(walkValue).setFloat("yaw", 90f);
        verify(walkValue).setFloat("pitch", 45f);
        verify(attackSlot).setLong("expiry", 500L);
        verify(attackSlot).setString("type", "ENTITY");
        verify(attackValue).setUUID("uuid", targetId);

        store.set(new MemoryKey<Boolean>("stale"), true, 10);
        store.writeTo(nbt, 100);

        verify(nbt, never()).getOrCreateCompound("stale");
    }

    @Test
    void readFromParsesAllTypes_andDropsUnresolvableRefs() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        LivingEntity target = mock(LivingEntity.class);
        UUID targetId = UUID.randomUUID();
        UUID goneId = UUID.randomUUID();

        NBTCompound nbt = mock(NBTCompound.class);
        when(nbt.getKeys()).thenReturn(Set.of(
                "is_hurt", "int", "long", "double", "custom",
                "walk_target", "attack_target", "dead_entity", "unknown_world"));

        NBTCompound isHurtSlot = mock(NBTCompound.class);
        NBTCompound isHurtValue = mock(NBTCompound.class);
        when(nbt.getCompound("is_hurt")).thenReturn(isHurtSlot);
        when(isHurtSlot.getString("type")).thenReturn("BOOLEAN");
        when(isHurtSlot.getLong("expiry")).thenReturn(100L);
        when(isHurtSlot.getCompound("value")).thenReturn(isHurtValue);
        when(isHurtValue.getBoolean("v")).thenReturn(true);

        NBTCompound intSlot = mock(NBTCompound.class);
        NBTCompound intValue = mock(NBTCompound.class);
        when(nbt.getCompound("int")).thenReturn(intSlot);
        when(intSlot.getString("type")).thenReturn("INTEGER");
        when(intSlot.getLong("expiry")).thenReturn(-1L);
        when(intSlot.getCompound("value")).thenReturn(intValue);
        when(intValue.getInteger("v")).thenReturn(42);

        NBTCompound longSlot = mock(NBTCompound.class);
        NBTCompound longValue = mock(NBTCompound.class);
        when(nbt.getCompound("long")).thenReturn(longSlot);
        when(longSlot.getString("type")).thenReturn("LONG");
        when(longSlot.getLong("expiry")).thenReturn(-1L);
        when(longSlot.getCompound("value")).thenReturn(longValue);
        when(longValue.getLong("v")).thenReturn(Long.MAX_VALUE);

        NBTCompound doubleSlot = mock(NBTCompound.class);
        NBTCompound doubleValue = mock(NBTCompound.class);
        when(nbt.getCompound("double")).thenReturn(doubleSlot);
        when(doubleSlot.getString("type")).thenReturn("DOUBLE");
        when(doubleSlot.getLong("expiry")).thenReturn(-1L);
        when(doubleSlot.getCompound("value")).thenReturn(doubleValue);
        when(doubleValue.getDouble("v")).thenReturn(3.5);

        NBTCompound customSlot = mock(NBTCompound.class);
        NBTCompound customValue = mock(NBTCompound.class);
        when(nbt.getCompound("custom")).thenReturn(customSlot);
        when(customSlot.getString("type")).thenReturn("STRING");
        when(customSlot.getLong("expiry")).thenReturn(-1L);
        when(customSlot.getCompound("value")).thenReturn(customValue);
        when(customValue.getString("v")).thenReturn("hello");

        NBTCompound walkSlot = mock(NBTCompound.class);
        NBTCompound walkValue = mock(NBTCompound.class);
        when(nbt.getCompound("walk_target")).thenReturn(walkSlot);
        when(walkSlot.getString("type")).thenReturn("LOCATION");
        when(walkSlot.getLong("expiry")).thenReturn(-1L);
        when(walkSlot.getCompound("value")).thenReturn(walkValue);
        when(walkValue.getString("world")).thenReturn("world");
        when(walkValue.getDouble("x")).thenReturn(1.0);
        when(walkValue.getDouble("y")).thenReturn(2.0);
        when(walkValue.getDouble("z")).thenReturn(3.0);
        when(walkValue.getFloat("yaw")).thenReturn(90f);
        when(walkValue.getFloat("pitch")).thenReturn(45f);

        NBTCompound attackSlot = mock(NBTCompound.class);
        NBTCompound attackValue = mock(NBTCompound.class);
        when(nbt.getCompound("attack_target")).thenReturn(attackSlot);
        when(attackSlot.getString("type")).thenReturn("ENTITY");
        when(attackSlot.getLong("expiry")).thenReturn(500L);
        when(attackSlot.getCompound("value")).thenReturn(attackValue);
        when(attackValue.getUUID("uuid")).thenReturn(targetId);

        NBTCompound deadSlot = mock(NBTCompound.class);
        NBTCompound deadValue = mock(NBTCompound.class);
        when(nbt.getCompound("dead_entity")).thenReturn(deadSlot);
        when(deadSlot.getString("type")).thenReturn("ENTITY");
        when(deadSlot.getCompound("value")).thenReturn(deadValue);
        when(deadValue.getUUID("uuid")).thenReturn(goneId);

        NBTCompound voidWorldSlot = mock(NBTCompound.class);
        NBTCompound voidWorldValue = mock(NBTCompound.class);
        when(nbt.getCompound("unknown_world")).thenReturn(voidWorldSlot);
        when(voidWorldSlot.getString("type")).thenReturn("LOCATION");
        when(voidWorldSlot.getCompound("value")).thenReturn(voidWorldValue);
        when(voidWorldValue.getString("world")).thenReturn("void");

        MobMemoryStore restored = MobMemoryStore.readFrom(nbt,
                uuid -> uuid.equals(targetId) ? target : null,
                name -> name.equals("world") ? world : null);

        assertEquals(Boolean.TRUE, restored.get(MemoryKey.IS_HURT, 0).orElseThrow());
        assertTrue(restored.present(MemoryKey.IS_HURT, 99));
        assertFalse(restored.present(MemoryKey.IS_HURT, 100));
        assertEquals(42, restored.get(new MemoryKey<Integer>("int"), 0).orElseThrow());
        assertEquals(Long.MAX_VALUE, restored.get(new MemoryKey<Long>("long"), 0).orElseThrow());
        assertEquals(3.5, restored.get(new MemoryKey<Double>("double"), 0).orElseThrow());
        assertEquals("hello", restored.get(new MemoryKey<String>("custom"), 0).orElseThrow());

        Location loc = restored.get(MemoryKey.WALK_TARGET, 0).orElseThrow();
        assertEquals(1.0, loc.getX());
        assertEquals(2.0, loc.getY());
        assertEquals(3.0, loc.getZ());
        assertEquals(90f, loc.getYaw());
        assertEquals(45f, loc.getPitch());
        assertEquals("world", loc.getWorld().getName());

        assertSame(target, restored.get(MemoryKey.ATTACK_TARGET, 0).orElseThrow());
        assertEquals(Optional.empty(), restored.get(new MemoryKey<LivingEntity>("dead_entity"), 0));
        assertEquals(Optional.empty(), restored.get(new MemoryKey<Location>("unknown_world"), 0));
    }

    @Test
    void readFromNullCompoundIsEmpty() {
        MobMemoryStore restored = MobMemoryStore.readFrom(null, uuid -> null, name -> null);
        assertTrue(restored.liveValues(0).isEmpty());
    }
}