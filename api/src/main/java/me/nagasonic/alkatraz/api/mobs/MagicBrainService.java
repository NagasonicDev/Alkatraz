package me.nagasonic.alkatraz.api.mobs;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime brain editing for any living mob (magic or vanilla).
 *
 * <p>A brain id refers to a {@code brains/<id>.yml} config. Assigning a brain
 * persists the id in entity NBT and immediately applies the goals; magic mobs
 * resolve to their {@link MagicEntityType} when no explicit brain is set.
 *
 * <p>Callers obtain the instance via {@link #getInstance()}. Operations that
 * write entity data are safe to call from any thread: when invoked off the
 * Bukkit main thread they are scheduled onto it. Boolean return values are
 * meaningful on the main thread; off-thread calls perform the operation
 * asynchronously and return {@code true} once the request is accepted.
 */
public interface MagicBrainService {

    /**
     * Assigns a brain to an entity: persists the id and applies it immediately.
     *
     * @param entity  the mob to modify (any {@link org.bukkit.entity.Mob})
     * @param brainId the brain id to load from {@code brains/<id>.yml}
     * @return true if the request was accepted (valid mob + resolvable brain)
     */
    boolean setBrain(LivingEntity entity, String brainId);

    /**
     * Re-applies the entity's current brain from disk (single entity).
     *
     * @param entity the mob to refresh
     * @return true if a brain id resolved and was applied
     */
    boolean reloadBrain(LivingEntity entity);

    /**
     * Re-applies the brain of every living mob with a resolvable brain id
     * across all loaded worlds. Returns the number of mobs updated.
     *
     * @return number of mobs whose brain was re-applied (0 when off-thread)
     */
    int reloadBrains();

    /**
     * Removes the entity's brain: deletes the brain tag and applies an empty
     * brain so the mob becomes passive. Vanilla AI is not restored until it
     * respawns.
     *
     * @param entity the mob to pacify
     * @return true if the request was accepted
     */
    boolean clearBrain(LivingEntity entity);

    /**
     * Returns the resolvable brain id for the entity, or {@code null}.
     * Prefers the explicit {@code alkatraz_brain} tag, then the
     * {@code magic_entity_type} tag of a magic mob.
     *
     * @param entity the mob to query
     * @return the active brain id, or {@code null}
     */
    @Nullable
    String getBrain(LivingEntity entity);

    /** Returns the registered service instance. */
    static MagicBrainService getInstance() {
        return Holder.INSTANCE;
    }

    /** Registers the service instance (called by core at startup). */
    static void setInstance(MagicBrainService service) {
        Holder.INSTANCE = service;
    }

    /** Holds the singleton; mirrors {@code AttributeService} holder semantics. */
    final class Holder {
        private static MagicBrainService INSTANCE;
        private Holder() {}
    }
}