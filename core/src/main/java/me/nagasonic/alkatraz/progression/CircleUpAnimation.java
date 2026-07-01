package me.nagasonic.alkatraz.progression;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Plays a small ascending-particle animation centred around the player
 * when they circle up. Runs asynchronously from the main thread via Bukkit scheduler.
 */
public final class CircleUpAnimation {

    private CircleUpAnimation() {}

    private static final int DURATION_TICKS = 40;

    public static void play(Player player, Runnable onComplete) {
        Location origin = player.getLocation().add(0, 0.5, 0);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= DURATION_TICKS) {
                    if (player.isOnline() && ticks >= DURATION_TICKS) {
                        Location center = player.getLocation().add(0, 1, 0);
                        center.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                                center, 80, 1, 1, 1, 0.5);
                        center.getWorld().spawnParticle(Particle.END_ROD,
                                center, 40, 0.5, 0.5, 0.5, 0.1);
                        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        onComplete.run();
                    }
                    cancel();
                    return;
                }

                Location playerLoc = player.getLocation().add(0, 0.5, 0);
                double progress = ticks / (double) DURATION_TICKS;
                double radius = 1.8 - progress * 0.8;
                double height = progress * 2.5;

                for (int i = 0; i < 3; i++) {
                    double angle = (ticks * 0.3) + (i * Math.PI * 2 / 3);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = playerLoc.clone().add(x, height, z);
                    particleLoc.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                            particleLoc, 2, 0, 0, 0, 0.1);
                }

                if (ticks % 4 == 0) {
                    int count = 12;
                    for (int i = 0; i < count; i++) {
                        double angle = (Math.PI * 2 * i) / count;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        Location ringLoc = origin.clone().add(x, 0.1, z);
                        ringLoc.getWorld().spawnParticle(Particle.REDSTONE, ringLoc, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(200, 180, 255), 0.8f));
                    }
                }

                if (ticks % 8 == 0) {
                    origin.getWorld().playSound(origin, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, (float) (0.8f + progress * 0.6f));
                }

                if (ticks == DURATION_TICKS / 2) {
                    origin.getWorld().playSound(origin, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }
}
