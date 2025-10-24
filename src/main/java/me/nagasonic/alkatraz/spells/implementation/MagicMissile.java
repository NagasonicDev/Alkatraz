package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;


public class MagicMissile extends Spell {
    public MagicMissile(String type) {
        super(type);
    }
    private double baseDamage;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/magic_missile.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/magic_missile.yml").get();

        loadCommonConfig(spellConfig);
        baseDamage = spellConfig.getDouble("base_damage");
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            Location loc1 = p.getEyeLocation();
            Vector direction = p.getEyeLocation().getDirection();
            Location loc2 = p.getEyeLocation().add(direction.multiply(20));
            List<Location> locs = ParticleUtils.line(0.5, loc1, loc2);
            locs.remove(0); //Function puts loc2 as the first index, so if it is a solid block, the missile will not fire.
            locs.add(loc2);
            for (Location loc : locs){
                Block b = loc.getBlock();
                if (!b.isPassable() && !b.isLiquid() && b.isCollidable() && b.isSolid()) {
                    break;
                }
                p.spawnParticle(Particle.REDSTONE, loc, 50, new Particle.DustOptions(Color.AQUA, 0.5F));
                for (Entity entity : loc.getNearbyEntities(1, 1, 1)){
                    if (!entity.isDead() && entity != p){
                        LivingEntity le = (LivingEntity) entity;
                        double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("power"));
                        le.damage(wandPower * baseDamage);
                        Vector unitVector = entity.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
                        entity.setVelocity(unitVector.multiply(1));
                    }
                }
            }
        }
    }

    @Override
    public void circleAction(Player p) {
        Location playerLoc = p.getEyeLocation(); // Player eye location
        float yaw = playerLoc.getYaw();
        float pitch = playerLoc.getPitch();

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

    public double getBaseDamage() {
        return baseDamage;
    }
}
