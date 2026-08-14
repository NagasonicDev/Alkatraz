package me.nagasonic.alkatraz.items.magic.effect.implementation;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.items.magic.adapter.MagicDamageListener;
import me.nagasonic.alkatraz.items.magic.attribute.MagicDamage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

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
        World world = loc.getWorld();
        if (world == null) return;

        Player actor = context.playerActor().orElse(null);
        double magicDamage = actor != null ? MagicDamage.of(context.sourceItem()) : 0.0;
        if (magicDamage > 0) {
            MagicDamageListener.registerExplosionBonus(actor.getUniqueId(), magicDamage);
        }
        try {
            world.createExplosion(loc, power, setFire, breakBlocks, actor);
        } finally {
            if (magicDamage > 0) {
                MagicDamageListener.clearExplosionBonus(actor.getUniqueId());
            }
        }
    }

    public static Effect fromConfig(Map<String, Object> config) {
        return new ExplosionEffect(
                Float.parseFloat(String.valueOf(config.getOrDefault("power", 2.0))),
                Boolean.parseBoolean(String.valueOf(config.getOrDefault("set_fire", false))),
                Boolean.parseBoolean(String.valueOf(config.getOrDefault("break_blocks", true)))
        );
    }
}
