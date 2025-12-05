package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LesserHeal extends Spell {

    public LesserHeal(String type){
        super(type);
    }
    private double baseHeal;
    private double maxHeal;
    private int taskID;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/lesser_heal.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/lesser_heal.yml").get();

        loadCommonConfig(spellConfig);
        baseHeal = spellConfig.getDouble("base_heal");
        maxHeal = spellConfig.getDouble("max_heal");
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            if (p.isSneaking() || p.getTargetEntity(20) == null || !(p.getTargetEntity(20) instanceof Player)){
                double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
                double heal = (baseHeal * wandPower) * (1 + DataManager.getPlayerData(p).getAffinity(Element.LIGHT) / 100);
                if (heal > maxHeal){
                    heal = maxHeal;
                }
                p.setHealth(p.getHealth() + heal);
                AtomicInteger l = new AtomicInteger(0);
                List<Location> locs = ParticleUtils.createHelix(p.getLocation(), 2, 0.5, 2, 10);
                taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                    if (l.get() < locs.size()){
                        Location a = null;
                        try {
                            a = locs.get(l.get());
                        } catch (IndexOutOfBoundsException e) {
                        }
                        if (a != null){
                            a.getWorld().spawnParticle(Particle.TOTEM, a, 1, 0, 0, 0, 0);
                            l.addAndGet(1);
                        }
                    }else{ stopCast();}
                }, 0L, 1L);
            }else{
                Player target = (Player) p.getTargetEntity(20);
                double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
                double heal = (baseHeal * wandPower) * (1 + DataManager.getPlayerData(p).getAffinity(Element.LIGHT) / 100);
                if (heal > maxHeal){
                    heal = maxHeal;
                }
                target.setHealth(target.getHealth() + heal);
                AtomicInteger l = new AtomicInteger(0);
                List<Location> locs = ParticleUtils.createHelix(target.getLocation(), 2, 0.5, 2, 10);
                taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                    if (l.get() < locs.size()){
                        Location a = null;
                        try {
                            a = locs.get(l.get());
                        } catch (IndexOutOfBoundsException e) {
                        }
                        if (a != null){
                            a.getWorld().spawnParticle(Particle.TOTEM, a, 1, 0, 0, 0, 0);
                            l.addAndGet(1);
                        }
                    }else{ stopCast();}
                }, 0L, 1L);
            }
        }
    }

    private void stopCast(){
        Bukkit.getServer().getScheduler().cancelTask(taskID);
    }

    @Override
    public int circleAction(Player p) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (p.isSneaking() || p.getTargetEntity(20) == null || !(p.getTargetEntity(20) instanceof Player)){
                Location playerLoc = p.getLocation();
                float yaw = playerLoc.getYaw();
                float pitch = 0;

                // Call magicCircle with proper center, yaw, pitch and offset
                List<Location> magicCirclePoints = ParticleUtils.circle(playerLoc, 1, 20, yaw, pitch);
                magicCirclePoints.add(playerLoc);

                // Spawn particles at all calculated points
                for (int i = 0; i < magicCirclePoints.size(); i++){
                    for (Location loc1 : magicCirclePoints) {
                        loc1.getWorld().spawnParticle(Utils.DUST, loc1, 1, new Particle.DustOptions(Color.YELLOW, 0.4F));
                    }
                }
            }else{
                Location playerLoc = p.getTargetEntity(20).getLocation();
                float yaw = playerLoc.getYaw();
                float pitch = 0;

                // Calculate offset vector pointing forward relative to player orientation
                Vector forward = playerLoc.getDirection().normalize().multiply(1.5); // 1.5 blocks in front
                Location loc = playerLoc.clone().add(forward);

                // Call magicCircle with proper center, yaw, pitch and offset
                List<Location> magicCirclePoints = ParticleUtils.circle(loc, 1, 20, yaw, -pitch + 90);
                magicCirclePoints.add(loc);

                // Spawn particles at all calculated points
                for (int i = 0; i < magicCirclePoints.size(); i++){
                    for (Location loc1 : magicCirclePoints) {
                        loc1.getWorld().spawnParticle(Utils.DUST, loc1, 1, new Particle.DustOptions(Color.YELLOW, 0.4F));
                    }
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }
}
