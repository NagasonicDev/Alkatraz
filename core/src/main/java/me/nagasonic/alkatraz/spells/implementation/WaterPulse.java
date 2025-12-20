package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class WaterPulse extends Spell implements Listener {
    public WaterPulse(String type) {
        super(type);
    }
    private double strength;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/water_pulse.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/water_pulse.yml").get();

        loadCommonConfig(spellConfig);
        strength = spellConfig.getDouble("push_strength");
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        Location centre = p.getLocation();

        BukkitRunnable task = new BukkitRunnable() {
            double r = 0;

            @Override
            public void run() {
                r += 0.5;
                List<Location> circle = ParticleUtils.circle(centre, r, 4/r, 0, 0);
                for (Location loc : circle){
                    loc.getWorld().spawnParticle(Particle.WATER_SPLASH, loc, 5, 0, 0, 0,0);
                    for (Block b : Utils.blocksInRadius(loc, 2)){
                        if (b.getType() == Material.FIRE){
                            b.setType(Material.AIR);
                        }else if (b.getType() == Material.LAVA){
                            Levelled blockdata = (Levelled) b.getBlockData();
                            if (blockdata.getLevel() == 0){
                                b.setType(Material.OBSIDIAN);
                            }else{
                                b.setType(Material.COBBLESTONE);
                            }
                        }
                    }
                    for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
                        if (!(entity instanceof LivingEntity)) continue;
                        if (entity.equals(p)) continue;

                        LivingEntity target = (LivingEntity) entity;
                        Vector direction = target.getLocation().toVector().subtract(loc.toVector());
                        direction.normalize().multiply(strength);
                        direction.setY(0.5);
                        target.setVelocity(direction);
                    }
                }
                if (r == 10){
                    cancel();
                }
            }
        };

        task.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    @Override
    public int circleAction(Player p) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            Location playerLoc = p.getEyeLocation(); // Player eye location
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();

            // Calculate offset vector pointing forward relative to player orientation
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5); // 1.5 blocks in front

            // Call magicCircle with proper center, yaw, pitch and offset
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 3, 0);

            // Spawn particles at all calculated points
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.BLUE, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }
}
