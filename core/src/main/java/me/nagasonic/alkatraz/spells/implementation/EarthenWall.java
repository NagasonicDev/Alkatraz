package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.dom.Ground;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EarthenWall extends AttackSpell implements Listener {

    private double duration;
    private double height;
    private double length;
    private long selfDestructTicks;

    public EarthenWall(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Particle.BLOCK_CRACK, location, 15, Material.DIRT.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.BLOCK_CRACK, location, 30, 1, 1, 1, 0.1, Material.STONE.createBlockData());
        location.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, location, 1);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/earthen_wall_options.yml");
        Alkatraz.getInstance().save("spells/earthen_wall.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earthen_wall.yml").get();
        loadCommonConfig(spellConfig);
        this.duration = spellConfig.getDouble("duration");
        this.height = spellConfig.getDouble("height");
        this.length = spellConfig.getDouble("length");
        this.selfDestructTicks = spellConfig.getLong("self_destruct_ticks", 100);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
        loadOptions();
    }

    @Override
    public void castAction(Player player, ItemStack wand) {
        AttackProperties props = new AttackProperties(player, Utils.castLocation(player),
                getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.MAGIC);
        Location start = player.getLocation();

        double spacing = 0.5;
        boolean selfDestruct = (boolean) getOption("self_destruct").getSelectedValue(player).getValue();

        List<WallSegment> wallPoints = new ArrayList<>();
        Map<Location, Material> originals = new HashMap<>();

        Location currentPos = start.clone();
        Vector currentDir = player.getEyeLocation().getDirection().normalize();
        currentDir.setY(0);
        currentDir.normalize();

        float[] lastYaw = {-player.getEyeLocation().getYaw()};
        double turnStrength = 0.015;
        double finalLength = getModifiedStat(player, "wall_length",
                (Double) getOption("wall_length").getSelectedValue(player).getValue());
        double finalHeight = getModifiedStat(player, "wall_height",
                (Double) getOption("wall_height").getSelectedValue(player).getValue());
        int heightBlocks = (int) Math.ceil(finalHeight);
        int totalSegments = (int) Math.ceil(finalLength / spacing);

        new BukkitRunnable() {
            int ticks = 0;
            boolean buildDone = false;
            boolean sinking = false;

            @Override
            public void run() {
                ticks++;

                // === BUILD PHASE — steer and add new segments ===
                if (!buildDone && !props.isCountered() && !props.isCancelled()) {
                    float currentYaw = -player.getEyeLocation().getYaw();
                    float yawDelta = currentYaw - lastYaw[0];
                    lastYaw[0] = currentYaw;
                    yawDelta = Math.max(-10, Math.min(10, yawDelta));
                    currentDir.rotateAroundY(yawDelta * turnStrength);
                    currentPos.add(currentDir.clone().multiply(spacing));
                    Location ground = Utils.findTopSolid(currentPos.clone().add(0, 3, 0), 10);
                    if (ground != null) {
                        wallPoints.add(new WallSegment(ground));
                        if (selfDestruct) {
                            for (int dy = -heightBlocks; dy <= heightBlocks; dy++) {
                                Location l = ground.clone().add(0, dy, 0);
                                originals.putIfAbsent(l.toBlockLocation(), l.getBlock().getType());
                            }
                        }
                    }
                    if (wallPoints.size() >= totalSegments) {
                        buildDone = true;
                    }
                }

                // === RENDER PHASE — rise or sink blocks ===
                for (WallSegment segment : wallPoints) {
                    if (!sinking) {
                        if (segment.step < heightBlocks) {
                            World world = segment.base.getWorld();
                            int x = segment.base.getBlockX();
                            int y = segment.base.getBlockY();
                            int z = segment.base.getBlockZ();

                            for (int i = 0; i <= heightBlocks; i++) {
                                Block from = world.getBlockAt(x, y - i + segment.step, z);
                                Block to = world.getBlockAt(x, y - i + segment.step + 1, z);
                                if (Ground.isGround(from.getType())) to.setType(from.getType(), false);
                            }

                            world.getBlockAt(x, y - heightBlocks + segment.step, z).setType(Material.AIR, false);

                            Block topBlock = world.getBlockAt(x, y + segment.step, z);
                            SpellParticleComponent comp = new SpellParticleComponent(
                                    EarthenWall.this, props, player, wand,
                                    SpellComponentType.OFFENSE, topBlock.getLocation(), 0.3, 2);
                            SpellComponentHandler.register(comp);

                            for (Entity entity : topBlock.getWorld().getNearbyEntities(topBlock.getLocation(), 0.5, 0.5, 0.5)) {
                                if (!(entity instanceof LivingEntity)) continue;
                                if (entity.equals(player)) continue;
                                ((LivingEntity) entity).damage(props.getRemainingPower());
                                ((LivingEntity) entity).setVelocity(new Vector(0, 0.5, 0));
                            }

                            segment.step++;
                        } else {
                            segment.risen = true;
                        }
                    } else {
                        segment.sinkAge++;
                        double sinkProgress = Math.min(1.0, segment.sinkAge / 20.0);
                        int visibleHeight = Math.max(0, heightBlocks - (int) Math.floor(heightBlocks * sinkProgress));
                        for (int h = 0; h < heightBlocks; h++) {
                            Location loc = segment.base.clone().add(0, h, 0);
                            if (h >= visibleHeight) {
                                Block block = loc.getBlock();
                                if (block.getType() == segment.wallMat) {
                                    Location key = loc.toBlockLocation();
                                    if (originals.containsKey(key)) {
                                        block.setType(originals.get(key), false);
                                    } else {
                                        block.setType(Material.AIR, false);
                                    }
                                }
                            }
                        }
                    }
                }

                // === TRANSITION TO SINK (self-destruct only) ===
                if (selfDestruct && buildDone && !sinking) {
                    boolean allRisen = true;
                    for (WallSegment segment : wallPoints) {
                        if (!segment.risen) {
                            allRisen = false;
                            break;
                        }
                    }
                    if (allRisen) {
                        long standTicks = totalSegments + selfDestructTicks;
                        if (ticks >= standTicks) {
                            sinking = true;
                        }
                    }
                }

                // === CLEANUP ===
                if (selfDestruct && sinking) {
                    boolean allSunk = true;
                    for (WallSegment segment : wallPoints) {
                        if (segment.sinkAge < 25) {
                            allSunk = false;
                            break;
                        }
                    }
                    if (allSunk) {
                        for (Map.Entry<Location, Material> e : originals.entrySet()) {
                            e.getKey().getBlock().setType(e.getValue(), false);
                        }
                        cancel();
                    }
                }

                if (!selfDestruct && buildDone) {
                    boolean allRisen = true;
                    for (WallSegment segment : wallPoints) {
                        if (!segment.risen) {
                            allRisen = false;
                            break;
                        }
                    }
                    if (allRisen && ticks > totalSegments + 60) {
                        cancel();
                    }
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        double wandp = wand == null ? 1 : NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        double power = getPower(caster, getBasePower()) * wandp;
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster), power, AttackType.MAGIC);
        Location start = caster.getLocation();

        double spacing = 0.5;
        int heightBlocks = (int) Math.ceil(height);
        int totalSegments = (int) Math.ceil(length / spacing);
        List<WallSegment> wallPoints = new ArrayList<>();
        Map<Location, Material> originals = new HashMap<>();

        Location currentPos = start.clone();
        Vector currentDir = caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector()).normalize();
        currentDir.setY(0);
        currentDir.normalize();

        new BukkitRunnable() {
            int ticks = 0;
            boolean buildDone = false;
            boolean sinking = false;

            @Override
            public void run() {
                ticks++;

                // === BUILD PHASE ===
                if (!buildDone && !props.isCountered() && !props.isCancelled()) {
                    currentPos.add(currentDir.clone().multiply(spacing));
                    Location ground = Utils.findTopSolid(currentPos.clone().add(0, 3, 0), 10);
                    if (ground != null) {
                        wallPoints.add(new WallSegment(ground));
                        for (int dy = -heightBlocks; dy <= heightBlocks; dy++) {
                            Location l = ground.clone().add(0, dy, 0);
                            originals.putIfAbsent(l.toBlockLocation(), l.getBlock().getType());
                        }
                    }
                    if (wallPoints.size() >= totalSegments) {
                        buildDone = true;
                    }
                }

                // === RENDER PHASE — rise or sink blocks ===
                for (WallSegment segment : wallPoints) {
                    if (!sinking) {
                        if (segment.step < heightBlocks) {
                            World world = segment.base.getWorld();
                            int x = segment.base.getBlockX();
                            int y = segment.base.getBlockY();
                            int z = segment.base.getBlockZ();

                            for (int i = 0; i <= heightBlocks; i++) {
                                Block from = world.getBlockAt(x, y - i + segment.step, z);
                                Block to = world.getBlockAt(x, y - i + segment.step + 1, z);
                                if (Ground.isGround(from.getType())) to.setType(from.getType(), false);
                            }

                            world.getBlockAt(x, y - heightBlocks + segment.step, z).setType(Material.AIR, false);

                            Block topBlock = world.getBlockAt(x, y + segment.step, z);
                            for (Entity entity : topBlock.getWorld().getNearbyEntities(topBlock.getLocation(), 0.5, 0.5, 0.5)) {
                                if (!(entity instanceof LivingEntity)) continue;
                                if (entity.equals(caster)) continue;
                                ((LivingEntity) entity).damage(props.getRemainingPower());
                                ((LivingEntity) entity).setVelocity(new Vector(0, 0.5, 0));
                            }

                            segment.step++;
                        } else {
                            segment.risen = true;
                        }
                    } else {
                        segment.sinkAge++;
                        double sinkProgress = Math.min(1.0, segment.sinkAge / 20.0);
                        int visibleHeight = Math.max(0, heightBlocks - (int) Math.floor(heightBlocks * sinkProgress));
                        for (int h = 0; h < heightBlocks; h++) {
                            Location loc = segment.base.clone().add(0, h, 0);
                            if (h >= visibleHeight) {
                                Block block = loc.getBlock();
                                if (block.getType() == segment.wallMat) {
                                    Location key = loc.toBlockLocation();
                                    if (originals.containsKey(key)) {
                                        block.setType(originals.get(key), false);
                                    } else {
                                        block.setType(Material.AIR, false);
                                    }
                                }
                            }
                        }
                    }
                }

                // === TRANSITION TO SINK ===
                if (buildDone && !sinking) {
                    boolean allRisen = true;
                    for (WallSegment segment : wallPoints) {
                        if (!segment.risen) {
                            allRisen = false;
                            break;
                        }
                    }
                    if (allRisen) {
                        int destroyTick = totalSegments + (int) Math.ceil(duration * 20);
                        if (ticks >= destroyTick) {
                            sinking = true;
                        }
                    }
                }

                // === CLEANUP ===
                if (sinking) {
                    boolean allSunk = true;
                    for (WallSegment segment : wallPoints) {
                        if (segment.sinkAge < 25) {
                            allSunk = false;
                            break;
                        }
                    }
                    if (allSunk) {
                        for (Map.Entry<Location, Material> e : originals.entrySet()) {
                            e.getKey().getBlock().setType(e.getValue(), false);
                        }
                        cancel();
                    }
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation();
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5);
            List<Location> points = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 3, 0);
            for (int i = 0; i < 100; i++) {
                for (Location loc : points) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                            new Particle.DustOptions(Color.fromRGB(139, 69, 19), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&6Gaia's Bulwark")
                .addCustomLoreLine("&8&oThe earth rises to meet your call.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }

    class WallSegment {
        Location base;
        int step;
        int sinkAge;
        Material wallMat;
        boolean risen;

        WallSegment(Location base) {
            this.base = base;
            this.step = 0;
            this.sinkAge = 0;
            this.risen = false;
            Material groundMat = base.getBlock().getType();
            this.wallMat = Ground.isGround(groundMat) ? groundMat : Material.STONE;
        }
    }
}
