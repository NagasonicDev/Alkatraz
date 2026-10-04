package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.DamageableBarrier;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import me.nagasonic.alkatraz.spells.types.properties.implementation.BarrierProperties;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Set;

/**
 * A {@link BarrierProperties} that the collision system can actually damage.
 *
 * <p>{@code CrownOfDawn} is an {@code AttackSpell}, not a {@code BarrierSpell}, because it
 * deals damage and must reach {@code getPower} - damage-contract rule 3 makes a
 * {@code power:} key that never reaches a damage call dead config.
 * {@code SpellComponentHandler.resolveDefense} therefore falls through to its second
 * branch, which requires the properties object itself to be a
 * {@link DamageableBarrier}. {@code BarrierProperties} does not, so this subclass supplies
 * the interface, and every accessor it needs already exists on the parent with identical
 * behaviour; this class only adds the interface.
 *
 * <p>{@code damage(double)}, {@code isBroken()}, {@code setCastLocation(Location)} and
 * {@code getCaster()} need no override - the parent already declares exactly those
 * signatures.
 */
public class CrownOfDawnBarrierProperties extends BarrierProperties implements DamageableBarrier {

    public CrownOfDawnBarrierProperties(LivingEntity caster, Location castLocation,
                                        double hitpoints, BarrierType type, double radius) {
        super(caster, castLocation, hitpoints, type, radius);
    }

    @Override
    public double hitpoints() {
        return getHitpoints();
    }

    @Override
    public double initialHitpoints() {
        return getInitialHitpoints();
    }

    @Override
    public BarrierType type() {
        return getType();
    }

    @Override
    public Set<SpellProperties> collided() {
        return getCollided();
    }

    @Override
    public double barrierRadius() {
        return getRadius();
    }
}
