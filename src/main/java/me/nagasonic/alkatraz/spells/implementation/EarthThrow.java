package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.dom.Ground;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

public class EarthThrow extends Spell implements Listener {
    public EarthThrow(String type){
        super(type);
    }
    private double baseDamage;

    private FallingBlock block;
    private double power;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/earth_throw.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earth_throw.yml").get();

        loadCommonConfig(spellConfig);
        baseDamage = spellConfig.getDouble("base_damage");
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            Location loc = p.getEyeLocation();
            Vector direction = loc.getDirection();
            if (p.isOnGround()){
                BlockData data = Bukkit.createBlockData(Ground.getGround(p.getLocation().getBlock().getBiome()));
                FallingBlock b = loc.getWorld().spawnFallingBlock(loc, data);
                b.setHurtEntities(true);
                b.setMaxDamage((int) baseDamage);
                b.setVelocity(direction.multiply(1).setY(0.3));
                this.block = b;
                this.power = NBT.get(wand, nbt2 -> (Double) nbt2.getDouble("power"));
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

        // Call magicCircle with proper center, yaw, pitch and offset
        List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

        // Spawn particles at all calculated points
        for (int i = 0; i < 100; i++){
            for (Location loc : magicCirclePoints) {
                loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.fromRGB(78, 47, 0), 0.4F));
            }
        }
        DataManager.getPlayerData(p).setCasting(false);
    }

    @EventHandler
    private void onLand(EntityChangeBlockEvent e){
        if (e.getEntity() instanceof FallingBlock){
            FallingBlock b = (FallingBlock) e.getEntity();
            if (b.equals(block)){
                e.getBlock().setType(Material.AIR);
                Location loc = e.getBlock().getLocation();
                List<Location> locs = ParticleUtils.circle(loc, 3, 1, 0, 0);
                for (Location l : locs){
                    l.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, l, 5);
                }
                for (LivingEntity le : loc.getNearbyLivingEntities(3)){
                    le.damage(baseDamage * this.power);
                    Vector direction = le.getLocation().toVector().subtract(loc.toVector());
                    direction.normalize().multiply(1);
                    direction.setY(1.25);
                    le.setVelocity(direction);
                }
            }
        }
    }

    @EventHandler
    private void onDrop(EntityDropItemEvent e){
        if (e.getEntity() instanceof FallingBlock){
            FallingBlock b = (FallingBlock) e.getEntity();
            if (b.equals(block)){
                e.setCancelled(true);
                Location loc = b.getLocation();
                b.remove();
                List<Location> locs = ParticleUtils.circle(loc, 3, 1, 0, 0);
                for (Location l : locs){
                    l.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, l, 5);
                }
                for (LivingEntity le : loc.getNearbyLivingEntities(3)){
                    le.damage(baseDamage * this.power);
                    Vector direction = le.getLocation().toVector().subtract(loc.toVector());
                    direction.normalize().multiply(1);
                    direction.setY(1.25);
                    le.setVelocity(direction);
                }
            }
        }
    }
}
