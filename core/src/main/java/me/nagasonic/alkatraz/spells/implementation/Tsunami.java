package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class Tsunami extends AttackSpell {

    private static final BlockData WATER_DATA = Material.WATER.createBlockData();
    public Tsunami(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/tsunami_options.yml");
        Alkatraz.getInstance().save("spells/tsunami.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/tsunami.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double totalPower = getPower(caster, getBasePower()) * getWandPower(wand);
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                totalPower,
                AttackType.MAGIC
        );

        double range = getModifiedStat(caster, "range", 18);
        Vector direction = caster.getEyeLocation().getDirection().setY(0).normalize();
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        int waveWidth = 5;
        List<WaveColumn> activeColumns = new ArrayList<>();
        Location startLoc = caster.getLocation().clone();

        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_DROWNED_SWIM, 1.5f, 0.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.3f);

        for (double d = 0; d <= range + 2; d++) {
            startLoc.clone().add(direction.clone().multiply(d)).getChunk().load(true);
        }

        new BukkitRunnable() {
            double distance = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || distance > range) {
                    for (WaveColumn wc : activeColumns) {
                        wc.removeWater();
                    }
                    activeColumns.clear();
                    cancel();
                    return;
                }

                distance += 1.0;
                Location waveCenter = startLoc.clone().add(direction.clone().multiply(distance))
                        .add(0, 0.5, 0);

                double frontDist = distance + 1.0;
                for (double w = -waveWidth; w <= waveWidth; w++) {
                    Location ahead = waveCenter.clone().add(perpendicular.clone().multiply(w));
                    for (int h = 0; h < 5; h++) {
                        Location check = ahead.clone().add(direction.clone().multiply(1.5)).add(0, h, 0);
                        Block b = check.getBlock();
                        if (!b.isPassable() && !b.isLiquid()) {
                            for (WaveColumn wc : activeColumns) wc.removeWater();
                            activeColumns.clear();
                            cancel();
                            return;
                        }
                    }
                }

                for (int w = -waveWidth; w <= waveWidth; w++) {
                    double shrink = Math.abs(w) / (double) waveWidth;
                    int height = (int) (5.0 * (1.0 - shrink * 0.4));
                    Location colLoc = waveCenter.clone().add(perpendicular.clone().multiply(w));

                    List<FakeBlock> placedBlocks = new ArrayList<>();

                    for (int row = 0; row < 3; row++) {
                        Location forwardLoc = colLoc.clone().add(direction.clone().multiply(row * 0.8));
                        for (int h = 0; h < height; h++) {
                            Location blockLoc = forwardLoc.clone().add(0, h, 0);
                            Block b = blockLoc.getBlock();
                            if (b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.VOID_AIR) {
                                sendFakeBlock(blockLoc, WATER_DATA);
                                placedBlocks.add(new FakeBlock(blockLoc.clone(), b.getBlockData()));
                            }
                        }
                    }

                    activeColumns.add(new WaveColumn(placedBlocks));

                    for (int h = 0; h < height; h++) {
                        Location sprayLoc = colLoc.clone().add(0, h, 0);
                        sprayLoc.getWorld().spawnParticle(Particle.WATER_SPLASH, sprayLoc, 3, 0.5, 0.2, 0.5, 0);
                        sprayLoc.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, sprayLoc, 2, 0.3, 0.2, 0.3, 0);
                    }

                    if (Math.abs(w) <= 1) {
                        colLoc.getWorld().spawnParticle(Utils.DUST, colLoc, 0, new Particle.DustOptions(Color.fromRGB(0, 120, 255), 1.2F));
                    }
                }

                for (int i = -1; i <= 1; i += 2) {
                    Location edgeLoc = waveCenter.clone().add(perpendicular.clone().multiply((waveWidth + 1) * i));
                    for (int h = 1; h <= 4; h++) {
                        edgeLoc.getWorld().spawnParticle(Particle.WATER_SPLASH, edgeLoc.clone().add(0, h, 0), 5, 0.5, 0.1, 0.5, 0.1);
                    }
                }

                for (int trail = 1; trail <= 3; trail++) {
                    double trailDist = distance - (trail * 2.5);
                    if (trailDist > 0 && trailDist < distance) {
                        Location trailLoc = startLoc.clone().add(direction.clone().multiply(trailDist))
                                .add(0, 0.5, 0);
                        for (int w = -waveWidth; w <= waveWidth; w++) {
                            Location foamLoc = trailLoc.clone().add(perpendicular.clone().multiply(w));
                            foamLoc.getWorld().spawnParticle(Particle.BUBBLE_POP, foamLoc, 1, 0.3, 0.1, 0.3, 0);
                        }
                    }
                }

                if (activeColumns.size() > 6) {
                    WaveColumn oldest = activeColumns.remove(0);
                    oldest.removeWater();
                }

                for (Entity entity : waveCenter.getWorld().getNearbyEntities(
                        waveCenter, waveWidth + 2, 7, waveWidth + 2)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;
                    if (props.hasHit(le)) continue;
                    props.hit(le);

                    Vector push = direction.clone().multiply(2.0).setY(0.8);
                    le.setVelocity(le.getVelocity().add(push));

                    SpellDamageUtil.damageWithSpell(
                            le,
                            getPower(caster, le, totalPower),
                            caster,
                            wand,
                            Tsunami.this
                    );
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 2L);
    }

    private record FakeBlock(Location location, BlockData originalData) {}

    private static class WaveColumn {
        private final List<FakeBlock> blocks;

        WaveColumn(List<FakeBlock> blocks) {
            this.blocks = blocks;
        }

        void removeWater() {
            for (FakeBlock fb : blocks) {
                sendFakeBlock(fb.location(), fb.originalData());
            }
        }
    }

    private static void sendFakeBlock(Location loc, BlockData data) {
        loc.getChunk().load(true);
        for (Player p : loc.getWorld().getPlayers()) {
            p.sendBlockChange(loc, data);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;

        double wandp = getWandPowerOrDefault(wand);
        double power = getPower(caster, getBasePower()) * wandp;
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
                AttackType.MAGIC
        );

        Vector direction = caster.getLocation().getDirection().setY(0).normalize();
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        List<WaveColumn> activeColumns = new ArrayList<>();
        Location startLoc = caster.getLocation().clone();

        new BukkitRunnable() {
            double distance = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || distance > 12) {
                    for (WaveColumn wc : activeColumns) {
                        wc.removeWater();
                    }
                    activeColumns.clear();
                    cancel();
                    return;
                }

                distance += 1.0;
                Location waveCenter = startLoc.clone().add(direction.clone().multiply(distance))
                        .add(0, 0.5, 0);

                for (double w = -3; w <= 3; w++) {
                    Location ahead = waveCenter.clone().add(perpendicular.clone().multiply(w));
                    for (int h = 0; h < 5; h++) {
                        Location check = ahead.clone().add(direction.clone().multiply(1.5)).add(0, h, 0);
                        Block b = check.getBlock();
                        if (!b.isPassable() && !b.isLiquid()) {
                            for (WaveColumn wc : activeColumns) wc.removeWater();
                            activeColumns.clear();
                            cancel();
                            return;
                        }
                    }
                }

                for (int w = -3; w <= 3; w++) {
                    List<FakeBlock> placedBlocks = new ArrayList<>();
                    for (int h = 0; h < 2; h++) {
                        Location blockLoc = waveCenter.clone().add(perpendicular.clone().multiply(w)).add(0, h, 0);
                        Block b = blockLoc.getBlock();
                        if (b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.VOID_AIR) {
                            sendFakeBlock(blockLoc, WATER_DATA);
                            placedBlocks.add(new FakeBlock(blockLoc.clone(), b.getBlockData()));
                        }
                    }
                    activeColumns.add(new WaveColumn(placedBlocks));

                    waveCenter.getWorld().spawnParticle(Particle.WATER_SPLASH, waveCenter, 5, 3, 1, 0.5, 0);
                }

                if (activeColumns.size() > 6) {
                    WaveColumn oldest = activeColumns.remove(0);
                    oldest.removeWater();
                }

                for (Entity entity : waveCenter.getWorld().getNearbyEntities(
                        waveCenter, 4, 3, 4)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;
                    if (props.hasHit(le)) continue;
                    props.hit(le);

                    Vector push = direction.clone().multiply(1.2).setY(0.5);
                    le.setVelocity(le.getVelocity().add(push));

                    SpellDamageUtil.damageWithSpell(
                            le,
                            getPower(caster, le, power),
                            caster,
                            wand,
                            Tsunami.this
                    );
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 2L);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Particle.WATER_SPLASH, location, 40, 0.5, 0.5, 0.5, 0.2);
        location.getWorld().spawnParticle(Particle.BUBBLE_POP, location, 20, 0.5, 0.5, 0.5, 0);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.WATER_SPLASH, location, 60, 1, 1, 1, 0.5);
        location.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, location, 30, 1, 1, 1, 0);
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
                            new Particle.DustOptions(Color.fromRGB(0, 100, 255), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&9Tidal Scroll of the Deep")
                .addCustomLoreLine("&8The ocean bends to the caster's command.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
