package me.nagasonic.alkatraz.spells.implementation;

import com.google.common.util.concurrent.AtomicDouble;
import fr.skytasul.glowingentities.GlowingEntities;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Detect extends Spell {
    public Detect(String type){
        super(type);
    }
    private Map<Integer, Double> ranges = new HashMap<>();
    private List<LivingEntity> entities = new ArrayList<>();
    private long duration;
    private int taskID;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/detect.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/detect.yml").get();
        for (int i = 2; i <= 9; i++){
            ranges.put(i, spellConfig.getDouble("range.circle_" + i));
        }
        duration = spellConfig.getLong("detect_duration");
        loadCommonConfig(spellConfig);
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AtomicDouble l = new AtomicDouble(1);
            Location a = p.getLocation();
            PlayerData data = DataManager.getPlayerData(p);
            GlowingEntities ge = Alkatraz.getGlowingEntities();
            double range = ranges.get(data.getCircle());
            int r = 20;
            taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                if (l.get() < r){
                    List<Location> locs = ParticleUtils.circle(a, l.get(), 4/l.get(), 0, 0);
                    for (Location loc : locs){
                        loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.WHITE, 0.4F));
                    }
                    l.addAndGet(0.5);
                    if (l.get() == (double) r /2){
                        for (Entity entity : a.getNearbyEntities(range, range, range)){
                            if (!entity.isDead() && entity != p && entity instanceof LivingEntity le){
                                try {
                                    ChatColor color = ChatColor.WHITE;
                                    if (le instanceof Monster){
                                        color = ChatColor.RED;
                                    } else if (le instanceof Player) {
                                        color = ChatColor.YELLOW;
                                    }
                                    ge.setGlowing(le, p, color);
                                    if (!entities.contains(le)){
                                        entities.add(le);
                                    }
                                } catch (ReflectiveOperationException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }else { stopCast();}
            }, 0L, 1L);
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                for (LivingEntity le : entities){
                    try {
                        ge.unsetGlowing(le, p);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, duration * 20);
        }
    }

    private void stopCast(){
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
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }
}
