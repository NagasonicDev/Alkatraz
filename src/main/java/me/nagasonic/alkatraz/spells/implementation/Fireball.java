package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.data.type.Fire;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

public class Fireball extends Spell implements Listener {
    public Fireball(String type) {
        super(type);
    }
    private org.bukkit.entity.Fireball fireball;
    private double baseDamage;
    private Player caster;
    private double power;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/fireball.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fireball.yml").get();

        loadCommonConfig(spellConfig);
        baseDamage = spellConfig.getDouble("base_damage");
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand){
        if (!p.isDead()){
            this.caster = p;
            this.fireball = p.launchProjectile(org.bukkit.entity.Fireball.class, p.getLocation().getDirection().multiply(0.5));
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
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

            // Spawn particles at all calculated points
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.ORANGE, 0.4F));
                }
            }
        }, 0L, 10L);
        return d;
    }

    @EventHandler
    private void onDamage(EntityDamageByEntityEvent e){
        if (e != null){
            if (e.getDamager() == this.fireball){
                e.setDamage(calcDamage(baseDamage*power, (LivingEntity) e.getEntity(), caster));
            }
        }
    }
}
