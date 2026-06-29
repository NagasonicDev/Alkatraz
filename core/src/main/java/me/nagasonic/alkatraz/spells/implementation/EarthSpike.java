package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.dom.Ground;
import me.nagasonic.alkatraz.events.CastEvent;
import me.nagasonic.alkatraz.events.PlayerCastEvent;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.components.SpellBlockComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
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
import org.bukkit.entity.Mob;
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
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Particle.BLOCK_DUST, location, 15, Material.DIRT.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.BLOCK_DUST, location, 30, Material.DIRT.createBlockData());
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/earth_spike.yml");
        Alkatraz.getInstance().saveConfig("spells/earth_spike_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earth_spike.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player player, ItemStack wand) {
        Block target = player.getTargetBlockExact((int) Math.round((Double) getOption("spike_reach").getSelectedValue(player).getValue()));
        if (target == null || !Ground.isGround(target.getType())) {
            Utils.sendActionBar(player, "&cMust be casted on earth.");
            return;
        }
        if (!target.getType().isSolid()) return;
        AttackProperties props = new AttackProperties(player, Utils.castLocation(player), getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.PHYSICAL);
        PlayerCastEvent castEvent = new PlayerCastEvent(player, this, props, wand);
        Bukkit.getPluginManager().callEvent(castEvent);
        double heightMultiplier = (Double) getOption("spike_height").getSelectedValue(player).getValue();

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
            int height = Math.max(1, (int) Math.round(entry.getValue() * heightMultiplier));
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
                        if (Ground.isGround(from.getType())) to.setType(from.getType(), false); //Only move "Earth" blocks
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
                    for (Entity entity : topBlock.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
                        if (props.isCountered() || props.isCancelled()) {
                            return;
                        }
                        if (entity instanceof LivingEntity le && !le.equals(player)) {
                            le.damage(getPower(player, le, props.getRemainingPower()), player);
                            le.setVelocity(new Vector(0, 1, 0));
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
    public void mobCastAction(Mob caster, ItemStack wand) {
        Block target = caster.getTarget().getLocation().add(0, -0.5, 0).getBlock();
        if (!Ground.isGround(target.getType())) {
            return;
        }
        if (target == null || !target.getType().isSolid()) return;
        double wandp = wand == null ? 1 : NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        double power = getPower(caster, getBasePower())
                * wandp;
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster), power, AttackType.PHYSICAL);
        CastEvent castEvent = new CastEvent(caster, this, props, wand);
        Bukkit.getPluginManager().callEvent(castEvent);
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
                        if (Ground.isGround(from.getType())) to.setType(from.getType(), false); //Only move "Earth" blocks
                    }

                    SpellBlockComponent comp = new SpellBlockComponent(
                            EarthSpike.this,
                            props,
                            caster,
                            wand,
                            SpellComponentType.OFFENSE,
                            world.getBlockAt(x, y + step, z),
                            1,
                            2
                    );
                    SpellComponentHandler.register(comp);
                    Block topBlock = world.getBlockAt(x, y + step, z);
                    for (Entity entity : topBlock.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
                        if (props.isCountered() || props.isCancelled()) {
                            return;
                        }
                        if (entity instanceof LivingEntity le && !le.equals(caster)) {
                            le.damage(getPower(caster, le, props.getRemainingPower()), caster);
                            le.setVelocity(new Vector(0, 1, 0));
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
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation(); // Player eye location
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

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(Element.EARTH.getColor() + "Tome of the Blind Earthseer &oSection II")
                .addCustomLoreLine("&8The 2nd lesson of the seeker of the")
                .addCustomLoreLine("&8pinnacle of Earth magic")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }
}
