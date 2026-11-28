package me.nagasonic.alkatraz.spells.domains;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;

import java.util.Random;

/** Element-specific palette and effects for a domain. Bukkit API only. */
public interface DomainStyle {

    Material transform(Domain domain, int ring, Random random);

    default void onClaim(Domain domain, DomainTile tile, DomainManager manager) {
    }

    default void tick(Domain domain, DomainManager manager, int tick) {
    }

    /** Called for each hostile target on the domain's damage interval. */
    default void affect(Domain domain, LivingEntity target, double factor) {
    }

    /** Called for each ally on the domain's damage interval. */
    default void support(Domain domain, LivingEntity ally, double factor) {
    }
}
