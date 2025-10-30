package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AirBurst extends Spell {
    public AirBurst(String type){
        super(type);
    }
    private double power;
    private int taskID;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/air_burst.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/air_burst.yml").get();

        loadCommonConfig(spellConfig);
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            this.power = NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
            AtomicInteger l = new AtomicInteger(0);
            List<Location> lineLocs = ParticleUtils.line(2, p.getEyeLocation(), p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(40)));
            Vector v = p.getEyeLocation().getDirection();
            float yaw = p.getEyeLocation().getYaw();
            float pitch = p.getEyeLocation().getPitch();
            taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                if (l.get() < lineLocs.size()){
                    Location a = null;
                    try {
                        a = lineLocs.get(l.get());
                    } catch (IndexOutOfBoundsException e) {
                    }
                    if (a != null){
                        List<Location> locs = ParticleUtils.circle(a, 1.5, 16, yaw, -pitch + 90);
                        for (Location loc : locs){
                            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 2, 0, 0, 0, 0.2);
                        }
                        for (Entity entity : a.getNearbyEntities(1, 1, 1)){
                            if (!entity.isDead() && entity != p && entity instanceof LivingEntity){
                                entity.setVelocity(v.multiply(calcDamage(1.5, (LivingEntity) entity, p)));
                            }
                        }
                        l.addAndGet(1);
                    }
                }else{ stopCast();}
            }, 0L, 1L);
        }
    }

    private void stopCast() {
        Bukkit.getServer().getScheduler().cancelTask(taskID);
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
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

            // Spawn particles at all calculated points
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.WHITE, 0.4F));
                }
            }
        }, 0L, 10L);
        return d;
    }
}
