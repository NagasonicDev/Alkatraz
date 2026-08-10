package me.nagasonic.alkatraz.api.magic.trigger.event;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

/**
 * Trigger event fired when a projectile (such as an arrow) fired by a magic bow,
 * crossbow, or trident hits a living entity.
 * <p>
 * Provides access to both the shooter (actor) and the hit entity (target) from the
 * underlying {@link TriggerContext}. The source item is the weapon that fired the
 * projectile, linked to it at launch time.
 */
public final class ProjectileHitTriggerEvent extends InternalTriggerEvent {

    /**
     * Constructs a new projectile-hit trigger event.
     *
     * @param context the {@link TriggerContext} containing the shooter, hit entity, and linked weapon
     */
    public ProjectileHitTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_projectile_hit"), context);
    }

    /**
     * Returns the entity that fired the projectile.
     *
     * @return the shooter entity
     */
    public LivingEntity shooter() {
        return context().actor();
    }

    /**
     * Returns the entity that was hit by the projectile.
     *
     * @return the hit entity
     */
    public LivingEntity hitTarget() {
        return context().target();
    }
}
