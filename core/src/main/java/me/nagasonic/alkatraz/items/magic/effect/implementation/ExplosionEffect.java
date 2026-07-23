package me.nagasonic.alkatraz.items.magic.effect.implementation;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Map;

public final class ExplosionEffect implements Effect {
    private final float power;
    private final boolean setFire;
    private final boolean breakBlocks;

    public ExplosionEffect(float power, boolean setFire, boolean breakBlocks) {
        this.power = power;
        this.setFire = setFire;
        this.breakBlocks = breakBlocks;
    }

    @Override
    public void execute(TriggerContext context) {
        LivingEntity origin = context.target() != null ? context.target() : context.actor();
        if (origin == null) return;
        Location loc = origin.getLocation();
        loc.getWorld().createExplosion(loc, power, setFire, breakBlocks);
    }

    public static Effect fromConfig(Map<String, Object> config) {
        return new ExplosionEffect(
                Float.parseFloat(String.valueOf(config.getOrDefault("power", 2.0))),
                Boolean.parseBoolean(String.valueOf(config.getOrDefault("set_fire", false))),
                Boolean.parseBoolean(String.valueOf(config.getOrDefault("break_blocks", true)))
        );
    }
}
