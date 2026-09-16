package me.nagasonic.alkatraz.api.ai.task;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Objects;

/**
 * Blackboard key for a single memory slot. The key carries the value type so a
 * {@link TaskBrainContext} can read and write memories type-safely. Keys are an
 * open registry: framework constants below, plugin-defined keys via the public
 * constructor. Equality is defined by {@link #id()}.
 *
 * @param <T> the value type stored under this key
 */
public final class MemoryKey<T> {

    /** The entity this mob is currently targeting. */
    public static final MemoryKey<LivingEntity> ATTACK_TARGET = new MemoryKey<>("attack_target");

    /** A {@link Location} the mob should path toward. */
    public static final MemoryKey<Location> WALK_TARGET = new MemoryKey<>("walk_target");

    /** The closest hostile living entities within the sensing radius. */
    public static final MemoryKey<List<LivingEntity>> NEAREST_HOSTILES = new MemoryKey<>("nearest_hostiles");

    /** Whether this mob has recently taken damage. */
    public static final MemoryKey<Boolean> IS_HURT = new MemoryKey<>("is_hurt");

    private final String id;

    /** Creates a custom key. The id must be unique within a brain. */
    public MemoryKey(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("MemoryKey id must be non-blank");
        }
        this.id = id;
    }

    /** The key's unique id. */
    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryKey<?> that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MemoryKey[" + id + "]";
    }
}
