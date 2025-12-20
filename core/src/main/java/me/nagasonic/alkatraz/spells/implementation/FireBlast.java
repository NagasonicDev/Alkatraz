package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

public class FireBlast extends Spell implements Listener {
    public FireBlast(String type){
        super(type);
    }
    private LargeFireball fireblast;
    private double baseDamage;
    private Player caster;
    private double power;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/fire_blast.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fire_blast.yml").get();

        loadCommonConfig(spellConfig);
        baseDamage = spellConfig.getDouble("base_damage");
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            this.caster = p;
            this.fireblast = p.launchProjectile(LargeFireball.class, p.getLocation().getDirection().multiply(0.5));
            this.power = NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        }
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
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.RED, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    @EventHandler
    private void onDamage(EntityDamageByEntityEvent e){
        if (e != null){
            if (e.getDamager() == this.fireblast && e.getEntity() instanceof LivingEntity){
                e.setDamage(calcDamage(baseDamage*power, (LivingEntity) e.getEntity(), caster));
            }
        }
    }

    @EventHandler
    private void onHit(ProjectileHitEvent e){
        if (e != null){
            if (e.getEntity() == this.fireblast){
                Location loc = e.getHitBlock() != null ? e.getHitBlock().getLocation() : e.getHitEntity().getLocation();
                Alkatraz.logFine(loc.toString());
                List<Block> blocks = Utils.blocksInRadius(loc, 2);
                for (Block block : blocks){
                    if (block.getType() == Material.AIR){
                        block.setType(Material.FIRE);
                    }
                }
            }
        }
    }
}
