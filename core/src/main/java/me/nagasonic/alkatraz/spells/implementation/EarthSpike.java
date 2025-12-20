package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EarthSpike extends Spell implements Listener {
    public EarthSpike(String type) {
        super(type);
    }
    private double damage;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/earth_spike.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earth_spike.yml").get();

        loadCommonConfig(spellConfig);
        damage = spellConfig.getDouble("base_damage");
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player player, ItemStack wand) {
        Block target = player.getTargetBlockExact(20);
        if (target == null || !target.getType().isSolid()) return;

        Map<BlockFace, Integer> columns = new HashMap<>();
        columns.put(BlockFace.SELF, 7);
        columns.put(BlockFace.NORTH, 5);
        columns.put(BlockFace.SOUTH, 5);
        columns.put(BlockFace.EAST, 5);
        columns.put(BlockFace.WEST, 5);
        columns.put(BlockFace.NORTH_EAST, 2);
        columns.put(BlockFace.NORTH_WEST, 2);
        columns.put(BlockFace.SOUTH_EAST, 2);
        columns.put(BlockFace.SOUTH_WEST, 2);

        for (Map.Entry<BlockFace, Integer> entry : columns.entrySet()) {
            Block b = target.getRelative(entry.getKey());
            int height = entry.getValue();
            Location loc = b.getLocation();
            for (Entity entity : b.getWorld().getNearbyEntities(loc, 0.7, 0.7, 0.7)) {
                if (entity instanceof LivingEntity le && !le.equals(player)) {
                    le.damage(damage, player);
                    le.setVelocity(new Vector(0, 0.6, 0));
                }
            }
            Utils.raiseColumn(b, height, height);
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
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 10, new Particle.DustOptions(Color.fromRGB(78, 47, 0), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }
}
