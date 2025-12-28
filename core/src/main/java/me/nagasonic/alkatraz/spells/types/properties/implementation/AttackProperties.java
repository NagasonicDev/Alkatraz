package me.nagasonic.alkatraz.spells.types.properties.implementation;

import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class AttackProperties extends SpellProperties {

    private double remainingPower;
    private final double initialPower;
    private boolean countered;
    private AttackType type;

    public AttackProperties(Player caster, Location castLocation, double initialPower, AttackType type) {
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
}
