package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.components.SpellBlockComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EarthSpike extends AttackSpell implements Listener {
    public EarthSpike(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {
        location.getWorld().spawnParticle(Particle.BLOCK_DUST, location, 15, Material.DIRT.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.BLOCK_DUST, location, 30, Material.DIRT.createBlockData());
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/earth_spike.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earth_spike.yml").get();

        loadCommonConfig(spellConfig);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player player, ItemStack wand) {
        Block target = player.getTargetBlockExact(20);
        if (target == null || !target.getType().isSolid()) return;
        AttackProperties props = new AttackProperties(player, Utils.castLocation(player), getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.PHYSICAL);

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
            Block block = target.getRelative(entry.getKey());
            if (props.isCountered() || props.isCancelled()) return;
            int height = entry.getValue();
            Location loc = block.getLocation();
            World world = block.getWorld();
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();

            BukkitRunnable task = new BukkitRunnable() {
                int step = 0;
                @Override
                public void run() {
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    // Move blocks top → bottom
                    for (int i = 0; i <= height; i++) {
                        if (props.isCountered() || props.isCancelled()) {
                            cancel();
                            return;
                        }
                        Block from = world.getBlockAt(x, y - i + step, z);
                        Block to   = world.getBlockAt(x, y - i + step + 1, z);
                        to.setType(from.getType(), false);
                    }

                    SpellBlockComponent comp = new SpellBlockComponent(
                            EarthSpike.this,
                            props,
                            player,
                            wand,
                            SpellComponentType.OFFENSE,
                            world.getBlockAt(x, y + step, z),
                            1,
                            2
                    );
                    SpellComponentHandler.register(comp);
                    Block topBlock = world.getBlockAt(x, y + step, z);
                    for (Entity entity : topBlock.getWorld().getNearbyEntities(loc, 0.7, 0.7, 0.7)) {
                        if (props.isCountered() || props.isCancelled()) {
                            return;
                        }
                        if (entity instanceof LivingEntity le && !le.equals(player)) {
                            le.damage(getPower(player, le, props.getRemainingPower()), player);
                            le.setVelocity(new Vector(0, 0.6, 0));
                        }
                    }
                    world.getBlockAt(x, y - height + step, z).setType(Material.AIR, false);
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    step++;
                    if (step >= height) cancel();
                }
            };
            task.runTaskTimer(Alkatraz.getInstance(), 0, 1);
        }
    }

    @Override
    public int circleAction(Player p, PlayerSpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
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
