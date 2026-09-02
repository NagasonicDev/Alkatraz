package me.nagasonic.alkatraz.spells.types;

import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Set;

public interface DamageableBarrier {
    double hitpoints();
    double initialHitpoints();
    boolean isBroken();
    BarrierType type();
    void damage(double amount);
    default void onHit(double damage, AttackSpell source) {}
    default void onBreak(Location center) {}
    Set<SpellProperties> collided();
    double barrierRadius();
    void setCastLocation(Location location);
    LivingEntity getCaster();
}
