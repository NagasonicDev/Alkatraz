package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class WaterSphere extends Spell {

    public WaterSphere(String type){
        super(type);
    }
    private double baseDamage;
    private int taskID;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/water_sphere.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/water_sphere.yml").get();

        loadCommonConfig(spellConfig);
        baseDamage = spellConfig.getDouble("base_damage");
    }
    
    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AtomicInteger l = new AtomicInteger(0);
            List<Location> lineLocs = ParticleUtils.line(2, p.getEyeLocation(), p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(40)));
            taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                if (l.get() < lineLocs.size()){
                    Location a = null;
                    try {
                        a = lineLocs.get(l.get());
                    } catch (IndexOutOfBoundsException e) {

                    }
                    if (a != null){
                        List<Location> locs = ParticleUtils.fibonacciSphere(a, 0.75, 48);
                        for (Location loc : locs){
                            loc.getWorld().spawnParticle(Particle.WATER_DROP, loc, 2);
                            Block b = loc.getBlock();
                            if (b.getType() != Material.AIR){
                                if (b.getType() == Material.FARMLAND){
                                    Farmland farm = (Farmland) b.getBlockData();
                                    farm.setMoisture(farm.getMaximumMoisture());
                                }
                            }

                        }
                        for (Entity entity : a.getNearbyEntities(1, 1, 1)){
                            if (!entity.isDead() && entity != p && entity instanceof LivingEntity){
                                LivingEntity le = (LivingEntity) entity;
                                double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("power"));
                                le.damage(wandPower * baseDamage);
                                Vector unitVector = entity.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
                                entity.setVelocity(unitVector.multiply(0.1));
                            }
                        }
                        l.addAndGet(1);
                    }
                }else { stopCast();}
            }, 0L, 2L);
        }
    }

    private void stopCast() {
        Bukkit.getServer().getScheduler().cancelTask(taskID);
    }

    @Override
    public void circleAction(Player p) {
        Location playerLoc = p.getEyeLocation(); // Player eye location
        float yaw = playerLoc.getYaw();
        float pitch = playerLoc.getPitch();

        // Calculate offset vector pointing forward relative to player orientation
        Vector forward = playerLoc.getDirection().normalize().multiply(1.5); // 1.5 blocks in front

        // Call magicCircle with proper center, yaw, pitch and offset
        List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

        // Spawn particles at all calculated points
        for (int i = 0; i < 100; i++){
            for (Location loc : magicCirclePoints) {
                loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 0, new Particle.DustOptions(Color.BLUE, 0.4F));
            }
        }
    }
}
