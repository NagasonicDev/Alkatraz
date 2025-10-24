package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

public class LesserHeal extends Spell {

    public LesserHeal(String type){
        super(type);
    }
    private double baseHeal;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/lesser_heal.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/lesser_heal.yml").get();

        loadCommonConfig(spellConfig);
        baseHeal = spellConfig.getDouble("base_heal");
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            if (p.isSneaking() || p.getTargetEntity(20) == null || !(p.getTargetEntity(20) instanceof Player)){
                double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("power"));
                double heal = baseHeal * wandPower;
                p.setHealth(p.getHealth() + heal);
                List<Location> locs = ParticleUtils.createHelix(p.getLocation().add(0, -1, 0), 5, 0.5, 2, 10);
                for (Location loc : locs){
                    loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 1);
                }
            }else{
                Player target = (Player) p.getTargetEntity(20);
                double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("power"));
                double heal = baseHeal * wandPower;
                target.setHealth(p.getHealth() + heal);
                List<Location> locs = ParticleUtils.createHelix(p.getLocation().add(0, -1, 0), 5, 0.5, 2, 10);
                for (Location loc : locs){
                    loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 1);
                }
            }
        }
    }

    @Override
    public void circleAction(Player p) {
        if (p.isSneaking() || p.getTargetEntity(20) == null || !(p.getTargetEntity(20) instanceof Player)){
            Location playerLoc = p.getLocation().add(0, -0.5, 0); // Player eye location
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
                    loc1.getWorld().spawnParticle(Utils.DUST, loc1, 0, new Particle.DustOptions(Color.AQUA, 0.4F));
                }
            }
        }else{
            Location playerLoc = p.getTargetEntity(20).getLocation().add(0, -0.5, 0); // Player eye location
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
                    loc1.getWorld().spawnParticle(Utils.DUST, loc1, 0, new Particle.DustOptions(Color.AQUA, 0.4F));
                }
            }
        }
    }
}
