package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
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
import org.bukkit.block.data.Levelled;
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

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private static final BlockData WATER_DATA = Material.WATER.createBlockData();
    private int windUpDuration;
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
        this.windUpDuration = spellConfig.getInt("wind_up_duration", 6) * 20;
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
        Location startLoc = caster.getLocation().clone();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchTsunamiAttack(caster, wand, props, range, direction, perpendicular, waveWidth, startLoc);
                    return;
                }

                if (caster.isDead() || (caster instanceof Player && !((Player) caster).isOnline())) {
                    cancel();
                    return;
                }

                Location casterLoc = caster.getLocation();
                double progress = (double) ticks / windUpDuration;
                boolean phase1 = progress < 0.67;

                if (phase1) {
                    double phaseProgress = progress / 0.67;
                    for (int i = 0; i < 3; i++) {
                        double spiralRadius = 1.0 + phaseProgress * 2.0;
                        double spiralHeight = phaseProgress * 4.0;
                        double angle = (2 * Math.PI * i / 3) + (ticks * 0.08);
                        for (int j = 0; j < 8; j++) {
                            double h = (j / 8.0) * spiralHeight;
                            double r = spiralRadius * (1.0 - h / (spiralHeight + 0.1));
                            Location spiralLoc = casterLoc.clone().add(
                                    Math.cos(angle + j * 0.4) * r,
                                    h,
                                    Math.sin(angle + j * 0.4) * r
                            );
                            spiralLoc.getWorld().spawnParticle(Utils.SPLASH, spiralLoc, 2, 0.1, 0.1, 0.1, 0);
                            spiralLoc.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, spiralLoc, 1, 0.05, 0.05, 0.05, 0);
                        }
                    }

                    for (int i = 0; i < 6; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 1.5;
                        Location bubbleLoc = casterLoc.clone().add(Math.cos(a) * r, Math.random() * 2, Math.sin(a) * r);
                        bubbleLoc.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, bubbleLoc, 1, 0.1, 0.1, 0.1, 0);
                    }

                    if (ticks % 20 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_WATER_AMBIENT, 0.5f, 0.8f + (float)(progress * 0.8f));
                    }
                } else {
                    double phaseProgress = (progress - 0.67) / 0.33;

                    for (int i = 0; i < 15; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * (2.0 * (1.0 - phaseProgress));
                        Location spiralLoc = casterLoc.clone().add(
                                Math.cos(a) * r,
                                1.0 + Math.random() * 2.0,
                                Math.sin(a) * r
                        );
                        spiralLoc.getWorld().spawnParticle(Utils.SPLASH, spiralLoc, 3, 0.1, 0.1, 0.1, 0);
                        spiralLoc.getWorld().spawnParticle(Particle.BUBBLE_POP, spiralLoc, 1, 0.05, 0.05, 0.05, 0);
                    }

                    for (int i = 0; i < 6; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = 2.0 * (1.0 - phaseProgress);
                        Location forwardLoc = casterLoc.clone().add(direction.clone().multiply(1.5)).add(Math.cos(a) * r, 1.0 + Math.random(), Math.sin(a) * r);
                        forwardLoc.getWorld().spawnParticle(Utils.SPLASH, forwardLoc, 2, 0.1, 0.1, 0.1, 0);
                        forwardLoc.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, forwardLoc, 1, 0.05, 0.05, 0.05, 0);
                    }

                    if (ticks % 12 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_WATER_AMBIENT, 0.6f, 0.7f + (float)(phaseProgress * 0.8f));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchTsunamiAttack(Player caster, ItemStack wand, AttackProperties props, double range, Vector direction, Vector perpendicular, int waveWidth, Location startLoc) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_DROWNED_SWIM, 1.5f, 0.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.3f);

        for (double d = 0; d <= range + 2; d++) {
            startLoc.clone().add(direction.clone().multiply(d)).getChunk().load(true);
        }

        List<WaveColumn> activeColumns = new ArrayList<>();

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

                // No collision cancellation — wave flows up and over terrain

                for (int w = -waveWidth; w <= waveWidth; w++) {
                    double shrink = Math.abs(w) / (double) waveWidth;
                    int height = (int) (5.0 * (1.0 - shrink * 0.4));
                    Location colLoc = waveCenter.clone().add(perpendicular.clone().multiply(w));

                    // Find ground level at this column so the wave flows over terrain
                    double colBaseY = colLoc.getY();
                    for (int checkY = colLoc.getBlockY() + height; checkY >= colLoc.getBlockY() - 3; checkY--) {
                        Block below = colLoc.getWorld().getBlockAt(colLoc.getBlockX(), checkY, colLoc.getBlockZ());
                        if (!below.isPassable() && !below.isLiquid()) {
                            colBaseY = checkY + 1.0;
                            break;
                        }
                    }

                    List<FakeBlock> placedBlocks = new ArrayList<>();

                    for (int row = 0; row < 3; row++) {
                        Location forwardLoc = colLoc.clone().add(direction.clone().multiply(row * 0.8));
                        forwardLoc.setY(colBaseY);
                        for (int h = 0; h < height; h++) {
                            Location blockLoc = forwardLoc.clone().add(0, h, 0);
                            Block b = blockLoc.getBlock();
                            if (b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.VOID_AIR) {
                                sendFakeBlock(blockLoc, WATER_DATA);
                                placedBlocks.add(new FakeBlock(blockLoc.clone(), b.getBlockData()));
                            }
                        }
                    }

                    for (Block b : Utils.blocksInRadius(colLoc, 2)) {
                        if (b.getType() == Material.FIRE) {
                            b.setType(Material.AIR);
                        } else if (b.getType() == Material.LAVA) {
                            Levelled data = (Levelled) b.getBlockData();
                            b.setType(data.getLevel() == 0 ? Material.OBSIDIAN : Material.COBBLESTONE);
                        }
                    }

                    activeColumns.add(new WaveColumn(placedBlocks));

                    for (int h = 0; h < height; h++) {
                        Location sprayLoc = colLoc.clone().add(0, h, 0);
                        sprayLoc.setY(colBaseY + h);
                        sprayLoc.getWorld().spawnParticle(Utils.SPLASH, sprayLoc, 3, 0.5, 0.2, 0.5, 0);
                        sprayLoc.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, sprayLoc, 2, 0.3, 0.2, 0.3, 0);
                    }

                    if (Math.abs(w) <= 1) {
                        colLoc.getWorld().spawnParticle(Utils.DUST, colLoc, 0, new Particle.DustOptions(Color.fromRGB(0, 120, 255), 1.2F));
                    }
                }

                for (int i = -1; i <= 1; i += 2) {
                    Location edgeLoc = waveCenter.clone().add(perpendicular.clone().multiply((waveWidth + 1) * i));
                    for (int h = 1; h <= 4; h++) {
                        edgeLoc.getWorld().spawnParticle(Utils.SPLASH, edgeLoc.clone().add(0, h, 0), 5, 0.5, 0.1, 0.5, 0.1);
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
                            getPower(caster, le, props.getInitialPower()),
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

    private void launchMobTsunamiAttack(Mob caster, ItemStack wand, AttackProperties props) {
        double power = getPower(caster, getBasePower()) * getWandPowerOrDefault(wand);
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

                // No collision cancellation — wave flows up and over terrain

                for (int w = -3; w <= 3; w++) {
                    Location colLoc = waveCenter.clone().add(perpendicular.clone().multiply(w));

                    double colBaseY = colLoc.getY();
                    for (int checkY = colLoc.getBlockY() + 2; checkY >= colLoc.getBlockY() - 3; checkY--) {
                        Block below = colLoc.getWorld().getBlockAt(colLoc.getBlockX(), checkY, colLoc.getBlockZ());
                        if (!below.isPassable() && !below.isLiquid()) {
                            colBaseY = checkY + 1.0;
                            break;
                        }
                    }

                    List<FakeBlock> placedBlocks = new ArrayList<>();
                    for (int h = 0; h < 2; h++) {
                        Location blockLoc = colLoc.clone();
                        blockLoc.setY(colBaseY + h);
                        Block b = blockLoc.getBlock();
                        if (b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.VOID_AIR) {
                            sendFakeBlock(blockLoc, WATER_DATA);
                            placedBlocks.add(new FakeBlock(blockLoc.clone(), b.getBlockData()));
                        }
                    }
                    activeColumns.add(new WaveColumn(placedBlocks));

                    for (Block b : Utils.blocksInRadius(colLoc, 2)) {
                        if (b.getType() == Material.FIRE) {
                            b.setType(Material.AIR);
                        } else if (b.getType() == Material.LAVA) {
                            Levelled data = (Levelled) b.getBlockData();
                            b.setType(data.getLevel() == 0 ? Material.OBSIDIAN : Material.COBBLESTONE);
                        }
                    }

                    waveCenter.getWorld().spawnParticle(Utils.SPLASH, waveCenter, 5, 3, 1, 0.5, 0);
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

        Location targetLoc = caster.getTarget().getLocation();
        World world = caster.getWorld();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchMobTsunamiAttack(caster, wand, props);
                    return;
                }

                if (caster.isDead()) {
                    cancel();
                    return;
                }

                Location casterLoc = caster.getLocation();
                double progress = (double) ticks / windUpDuration;
                boolean phase1 = progress < 0.67;

                if (phase1) {
                    double phaseProgress = progress / 0.67;
                    for (int i = 0; i < 3; i++) {
                        double spiralRadius = 1.0 + phaseProgress * 2.0;
                        double spiralHeight = phaseProgress * 4.0;
                        double angle = (2 * Math.PI * i / 3) + (ticks * 0.08);
                        for (int j = 0; j < 8; j++) {
                            double h = (j / 8.0) * spiralHeight;
                            double r = spiralRadius * (1.0 - h / (spiralHeight + 0.1));
                            Location spiralLoc = casterLoc.clone().add(
                                    Math.cos(angle + j * 0.4) * r,
                                    h,
                                    Math.sin(angle + j * 0.4) * r
                            );
                            spiralLoc.getWorld().spawnParticle(Utils.SPLASH, spiralLoc, 2, 0.1, 0.1, 0.1, 0);
                        }
                    }

                    if (ticks % 20 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_WATER_AMBIENT, 0.5f, 0.8f + (float)(progress * 0.8f));
                    }
                } else {
                    double phaseProgress = (progress - 0.67) / 0.33;

                    for (int i = 0; i < 15; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * (2.0 * (1.0 - phaseProgress));
                        Location spiralLoc = casterLoc.clone().add(
                                Math.cos(a) * r,
                                1.0 + Math.random() * 2.0,
                                Math.sin(a) * r
                        );
                        spiralLoc.getWorld().spawnParticle(Utils.SPLASH, spiralLoc, 3, 0.1, 0.1, 0.1, 0);
                    }

                    if (ticks % 12 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_WATER_AMBIENT, 0.6f, 0.7f + (float)(phaseProgress * 0.8f));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Utils.SPLASH, location, 40, 0.5, 0.5, 0.5, 0.2);
        location.getWorld().spawnParticle(Particle.BUBBLE_POP, location, 20, 0.5, 0.5, 0.5, 0);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Utils.SPLASH, location, 60, 1, 1, 1, 0.5);
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
                .setDisplayName(lang().get("spells.tsunami.book_name"))
                .addCustomLoreLine(lang().get("spells.tsunami.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
