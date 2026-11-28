package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import org.bukkit.Color;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Small shared effect helpers; all calls are main-thread only. */
final class DomainEffects {

    private DomainEffects() {
    }

    static Color color(Element element) {
        return ElementPalette.of(element).core();
    }

    static void add(LivingEntity entity, PotionEffectType type, int ticks, int amplifier) {
        if (entity == null || entity.isDead() || type == null) return;
        entity.addPotionEffect(new PotionEffect(type, ticks, amplifier));
    }

    static void push(LivingEntity target, org.bukkit.Location center, double strength) {
        org.bukkit.util.Vector direction = target.getLocation().clone().subtract(center).toVector();
        direction.setY(0);
        if (direction.lengthSquared() > 0.0001) {
            target.setVelocity(direction.normalize().multiply(strength));
        }
    }

    static void pull(LivingEntity target, org.bukkit.Location center, double strength) {
        org.bukkit.util.Vector direction = center.clone().subtract(target.getLocation()).toVector();
        direction.setY(0);
        if (direction.lengthSquared() > 0.0001) {
            target.setVelocity(direction.normalize().multiply(strength));
        }
    }
}
