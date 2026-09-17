package me.nagasonic.alkatraz.mobs.ai.task;

import de.tr7zw.changeme.nbtapi.NBTCompound;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.MemoryStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The TaskBrain blackboard: keyed memory values with lazy expiry plus a record
 * of registered (declared-but-never-written) keys. Values persist lossily —
 * only BOOLEAN/NUMBER/STRING/LOCATION/entity (as UUID) survive NBT; unsupported
 * value types are dropped at write time and unresolvable refs are dropped on
 * read.
 */
public final class MobMemoryStore {

    private final Map<MemoryKey<?>, TimedValue> values = new HashMap<>();
    private final Set<MemoryKey<?>> registered = new HashSet<>();

    private MobMemoryStore() {}

    public static MobMemoryStore empty() {
        return new MobMemoryStore();
    }

    public void register(MemoryKey<?> key) {
        registered.add(Objects.requireNonNull(key, "key"));
    }

    public Set<MemoryKey<?>> registeredKeys() {
        return Set.copyOf(registered);
    }

    public <T> Optional<T> get(MemoryKey<T> key, long nowTick) {
        Objects.requireNonNull(key, "key");
        TimedValue entry = values.get(key);
        if (entry == null || entry.isExpired(nowTick)) return Optional.empty();
        @SuppressWarnings("unchecked")
        T value = (T) entry.value();
        return Optional.ofNullable(value);
    }

    public boolean present(MemoryKey<?> key, long nowTick) {
        Objects.requireNonNull(key, "key");
        TimedValue entry = values.get(key);
        return entry != null && !entry.isExpired(nowTick);
    }

    public MemoryStatus status(MemoryKey<?> key, long nowTick) {
        if (present(key, nowTick)) return MemoryStatus.PRESENT;
        return registered.contains(key) ? MemoryStatus.REGISTERED : MemoryStatus.ABSENT;
    }

    public void set(MemoryKey<?> key, Object value) {
        set(key, value, -1);
    }

    /** Stores {@code value} under {@code key} until absolute {@code expireTick} (-1 = never). */
    public void set(MemoryKey<?> key, Object value, long expireTick) {
        if (value == null) throw new NullPointerException("value");
        values.put(Objects.requireNonNull(key, "key"), new TimedValue(value, expireTick));
    }

    public void erase(MemoryKey<?> key) {
        values.remove(Objects.requireNonNull(key, "key"));
    }

    /** Unmodifiable snapshot of all unexpired values at {@code nowTick}. */
    public Map<MemoryKey<?>, Object> liveValues(long nowTick) {
        Map<MemoryKey<?>, Object> snapshot = new HashMap<>();
        for (Map.Entry<MemoryKey<?>, TimedValue> entry : values.entrySet()) {
            if (!entry.getValue().isExpired(nowTick)) snapshot.put(entry.getKey(), entry.getValue().value());
        }
        return Map.copyOf(snapshot);
    }

    public void writeTo(NBTCompound nbt, long nowTick) {
        for (Map.Entry<MemoryKey<?>, TimedValue> entry : values.entrySet()) {
            TimedValue timed = entry.getValue();
            if (timed.isExpired(nowTick)) continue;
            NBTCompound slot = nbt.getOrCreateCompound(entry.getKey().id());
            slot.setLong("expiry", timed.expireTick());
            writeValue(slot, timed.value());
        }
    }

    private static void writeValue(NBTCompound slot, Object value) {
        NBTCompound v = slot.getOrCreateCompound("value");
        if (value instanceof Boolean b) {
            slot.setString("type", "BOOLEAN");
            v.setBoolean("v", b);
        } else if (value instanceof Integer i) {
            slot.setString("type", "INTEGER");
            v.setInteger("v", i);
        } else if (value instanceof Long l) {
            slot.setString("type", "LONG");
            v.setLong("v", l);
        } else if (value instanceof Double d) {
            slot.setString("type", "DOUBLE");
            v.setDouble("v", d);
        } else if (value instanceof String s) {
            slot.setString("type", "STRING");
            v.setString("v", s);
        } else if (value instanceof Location loc) {
            World world = loc.getWorld();
            if (world == null) return;
            slot.setString("type", "LOCATION");
            v.setString("world", world.getName());
            v.setDouble("x", loc.getX());
            v.setDouble("y", loc.getY());
            v.setDouble("z", loc.getZ());
            v.setFloat("yaw", loc.getYaw());
            v.setFloat("pitch", loc.getPitch());
        } else if (value instanceof LivingEntity entity) {
            slot.setString("type", "ENTITY");
            v.setUUID("uuid", entity.getUniqueId());
        }
    }

    public static MobMemoryStore readFrom(NBTCompound nbt,
                                          Function<UUID, LivingEntity> entityResolver,
                                          Function<String, World> worldResolver) {
        MobMemoryStore store = new MobMemoryStore();
        if (nbt == null) return store;
        for (String key : nbt.getKeys()) {
            NBTCompound slot = nbt.getCompound(key);
            if (slot == null) continue;
            Object value = readValue(slot, entityResolver, worldResolver);
            if (value == null) continue;
            MemoryKey<Object> memoryKey = new MemoryKey<>(key);
            store.values.put(memoryKey, new TimedValue(value, slot.getLong("expiry")));
            store.registered.add(memoryKey);
        }
        return store;
    }

    public static MobMemoryStore readFrom(NBTCompound nbt) {
        return readFrom(nbt,
                uuid -> {
                    Entity entity = Bukkit.getEntity(uuid);
                    return entity instanceof LivingEntity living ? living : null;
                },
                Bukkit::getWorld);
    }

    private static Object readValue(NBTCompound slot,
                                    Function<UUID, LivingEntity> entityResolver,
                                    Function<String, World> worldResolver) {
        String type = slot.getString("type");
        NBTCompound v = slot.getCompound("value");
        if (v == null) return null;
        return switch (type) {
            case "BOOLEAN" -> v.getBoolean("v");
            case "INTEGER" -> v.getInteger("v");
            case "LONG" -> v.getLong("v");
            case "DOUBLE" -> v.getDouble("v");
            case "STRING" -> v.getString("v");
            case "LOCATION" -> {
                World world = worldResolver.apply(v.getString("world"));
                if (world == null) yield null;
                yield new Location(world, v.getDouble("x"), v.getDouble("y"), v.getDouble("z"),
                        v.getFloat("yaw"), v.getFloat("pitch"));
            }
            case "ENTITY" -> entityResolver.apply(v.getUUID("uuid"));
            default -> null;
        };
    }
}