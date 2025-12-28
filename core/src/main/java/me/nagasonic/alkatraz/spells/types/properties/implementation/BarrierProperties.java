package me.nagasonic.alkatraz.spells.types.properties.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class BarrierProperties extends SpellProperties {

    private double hitpoints;
    private final double initialHitpoints;
    private boolean broken;
    private BarrierType type;
    private BossBar healthBar;

    public BarrierProperties(Player caster, Location castLocation, double hitpoints, BarrierType type) {
        super(caster, castLocation);
        this.hitpoints = hitpoints;
        this.initialHitpoints = hitpoints;
        this.type = type;
        this.healthBar = Bukkit.createBossBar(format("&dBarrier: %health%/%initHealth%")
                .replaceAll("%health%", String.valueOf(hitpoints))
                .replaceAll("%initHealth%", String.valueOf(initialHitpoints)),
                BarColor.PINK,
                BarStyle.SOLID
        );
        healthBar.setProgress(1);
    }

    public double getHitpoints() {
        return hitpoints;
    }

    public void damage(double amount) {
        hitpoints -= amount;
        healthBar.setTitle(format("&dBarrier: %health%/%initHealth%")
                        .replaceAll("%health%", String.valueOf(hitpoints))
                        .replaceAll("%initHealth%", String.valueOf(initialHitpoints)));
        healthBar.setProgress(hitpoints <= 0 ? 0 : hitpoints / initialHitpoints);
        if (hitpoints <= 0) {
            hitpoints = 0;
            broken = true;
            new BukkitRunnable() {
                @Override
                public void run() {
                    healthBar.removeAll();
                }
            }.runTaskLater(Alkatraz.getInstance(), 20L); //Wait one second before clearing health bar
        }
    }

    public boolean isBroken() {
        return broken;
    }

    public BarrierType getType() {
        return type;
    }

    public double getInitialHitpoints() {
        return initialHitpoints;
    }

    public BossBar getHealthBar() {
        return healthBar;
    }
}
