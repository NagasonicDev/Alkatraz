package me.nagasonic.alkatraz.spells.types.properties.implementation;

import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AttackProperties extends SpellProperties {

    private double remainingPower;
    private final double initialPower;
    private boolean countered;
    private AttackType type;
    private final Set<UUID> hit = new HashSet<>();

    public AttackProperties(LivingEntity caster, Location castLocation, double initialPower, AttackType type) {
        super(caster, castLocation);
        this.remainingPower = initialPower;
        this.initialPower = initialPower;
        this.type = type;
        countered = false;
    }

    public double getRemainingPower() {
        return remainingPower;
    }

    public void reducePower(double factor) {
        remainingPower *= factor;
        if (remainingPower <= 0) {
            cancel();
        }
    }

    public boolean isCountered() {
        return countered;
    }

    public void counter() {
        this.countered = true;
        cancel();
    }

    public AttackType getType() {
        return type;
    }

    public double getInitialPower() {
        return initialPower;
    }

    public boolean hasHit(Entity entity){
        return entity != null && hit.contains(entity.getUniqueId());
    }

    public void hit(Entity entity){
        if (entity == null) return;
        hit.add(entity.getUniqueId());
    }
}
