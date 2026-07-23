package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.dom.Ground;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class Fissure extends AttackSpell {
    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private int windUpDuration;

    public Fissure(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/earthsplitter_options.yml");
        Alkatraz.getInstance().save("spells/earthsplitter.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earthsplitter.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.windUpDuration = spellConfig.getInt("wind_up_duration", 6) * 20;
    }

    private void launchFissureAttack(Player caster, ItemStack wand, AttackProperties props, double range, Vector direction, Vector perpendicular, Location startLoc, double maxWidth) {
        double totalPower = getPower(caster, getBasePower()) * getWandPower(wand);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.2f, 0.4f);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.6f);

        new BukkitRunnable() {
            double distance = 0;
            double prevDistance = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || distance > range) {
                    cancel();
                    return;
                }

                prevDistance = distance;
                distance += 1.0;

                double progress = distance / range;
                double fissureWidth = 0.3 + progress * maxWidth;

                for (double w = -fissureWidth; w <= fissureWidth; w += 0.5) {
                    Location groundCheck = startLoc.clone().add(direction.clone().multiply(distance))
                            .add(perpendicular.clone().multiply(w));
                    groundCheck.setY(startLoc.getY());
                    Block foundation = groundCheck.getWorld().getBlockAt(groundCheck);
                    int blocksDown = 0;
                    while (!foundation.getType().isSolid() && blocksDown < 3) {
                        foundation = foundation.getRelative(0, -1, 0);
                        blocksDown++;
                    }
                    if (!foundation.getType().isSolid()) continue;
                    for (int depth = 0; depth < 2; depth++) {
                        Block target = foundation.getRelative(0, -depth, 0);
                        if (Ground.isGround(target.getType())) {
                            target.breakNaturally();
                            target.getWorld().spawnParticle(Particle.BLOCK_CRACK, target.getLocation().add(0.5, 0.5, 0.5),
                                    5, 0.2, 0.2, 0.2, 0.3, target.getBlockData());
                        }
                    }
                }

                for (double w = -fissureWidth; w <= fissureWidth; w += 0.5) {
                    Location fissureLoc = startLoc.clone().add(direction.clone().multiply(distance))
                            .add(0, -0.5, 0)
                            .add(perpendicular.clone().multiply(w));

                    fissureLoc.getWorld().spawnParticle(Particle.BLOCK_CRACK, fissureLoc, 3, 0.2, 0.2, 0.2, 0.3,
                            Material.STONE.createBlockData());

                    if (Math.random() < 0.3) {
                        fissureLoc.getWorld().spawnParticle(Particle.FALLING_DUST, fissureLoc, 1, 0.1, 0.1, 0.1, 0,
                                Material.DIRT.createBlockData());
                    }

                    if (Math.abs(w) > fissureWidth - 0.8) {
                        fissureLoc.getWorld().spawnParticle(Utils.DUST, fissureLoc.clone().add(0, 0.2, 0), 0,
                                new Particle.DustOptions(Color.fromRGB(139, 90, 43), 0.8F));
                    }
                }

                for (double w = -fissureWidth; w <= fissureWidth; w += 0.3) {
                    Location deepLoc = startLoc.clone().add(direction.clone().multiply(distance))
                            .add(0, -1, 0)
                            .add(perpendicular.clone().multiply(w));
                    deepLoc.getWorld().spawnParticle(Utils.DUST, deepLoc, 0,
                            new Particle.DustOptions(Color.BLACK, 0.5F));
                }

                for (int edge = -1; edge <= 1; edge += 2) {
                    double edgeWidth = fissureWidth * edge;
                    Location edgeLoc = startLoc.clone().add(direction.clone().multiply(distance))
                            .add(0, 0.5, 0)
                            .add(perpendicular.clone().multiply(edgeWidth));
                    edgeLoc.getWorld().spawnParticle(Particle.BLOCK_CRACK, edgeLoc, 6, 0.4, 0.3, 0.4, 0.2,
                            Material.STONE.createBlockData());

                    for (double s = -0.5; s <= 0.5; s += 0.5) {
                        Location sideRaise = edgeLoc.clone().add(0, 0.5 + Math.abs(s), 0)
                                .add(perpendicular.clone().multiply(s * 0.3));
                        sideRaise.getWorld().spawnParticle(Particle.FALLING_DUST, sideRaise, 1, 0.1, 0.1, 0.1, 0,
                                Material.STONE.createBlockData());
                    }
                }

                for (double trailDist = prevDistance; trailDist < distance; trailDist += 0.5) {
                    double trailProgress = trailDist / range;
                    double trailWidth = 0.3 + trailProgress * maxWidth;
                    for (double w = -trailWidth; w <= trailWidth; w += 0.3) {
                        Location trailLoc = startLoc.clone().add(direction.clone().multiply(trailDist))
                                .add(0, -0.3, 0)
                                .add(perpendicular.clone().multiply(w));
                        if (Math.random() < 0.1) {
                            trailLoc.getWorld().spawnParticle(Particle.BLOCK_CRACK, trailLoc, 1, 0.1, 0.05, 0.1, 0.2,
                                    Material.STONE.createBlockData());
                        }
                    }
                }

                if (Math.random() < 0.15) {
                    startLoc.getWorld().playSound(startLoc.clone().add(direction.clone().multiply(distance)),
                            Sound.BLOCK_STONE_BREAK, 0.5f, 0.4f + (float) Math.random() * 0.3f);
                }

                double hitWidth = fissureWidth + 1.0;
                for (Entity entity : startLoc.getWorld().getNearbyEntities(
                        startLoc.clone().add(direction.clone().multiply(distance)),
                        hitWidth, 3, hitWidth)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;
                    if (props.hasHit(le)) continue;
                    props.hit(le);

                    le.setVelocity(new Vector(0, 1.4, 0).add(direction.clone().multiply(0.5)));

                    SpellDamageUtil.damageWithSpell(
                            le,
                            getPower(caster, le, totalPower),
                            caster,
                            wand,
                            Fissure.this
                    );
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchMobFissureAttack(Mob caster, ItemStack wand, AttackProperties props) {
        double power = getPower(caster, getBasePower()) * getWandPowerOrDefault(wand);
        Vector direction = caster.getLocation().getDirection().setY(0).normalize();
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        Location startLoc = caster.getLocation().clone();

        new BukkitRunnable() {
            double distance = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || distance > 10) {
                    cancel();
                    return;
                }

                distance += 1.0;
                double progress = distance / 10.0;
                double fissureWidth = 0.5 + progress * 3.0;

                Location fissureLoc = startLoc.clone().add(direction.clone().multiply(distance))
                        .add(0, 0, 0);

                for (double w = -fissureWidth; w <= fissureWidth; w += 0.5) {
                    Location loc = fissureLoc.clone().add(perpendicular.clone().multiply(w));
                    loc.getWorld().spawnParticle(Particle.BLOCK_CRACK, loc, 4, 0.3, 0.1, 0.3, 0.3,
                            Material.STONE.createBlockData());
                }

                for (Entity entity : fissureLoc.getWorld().getNearbyEntities(
                        fissureLoc, fissureWidth + 1.5, 3, fissureWidth + 1.5)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;
                    if (props.hasHit(le)) continue;
                    props.hit(le);

                    le.setVelocity(new Vector(0, 1.2, 0).add(direction.clone().multiply(0.4)));

                    SpellDamageUtil.damageWithSpell(
                            le,
                            getPower(caster, le, power),
                            caster,
                            wand,
                            Fissure.this
                    );
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
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

        double range = getModifiedStat(caster, "range", 15);
        Vector direction = caster.getEyeLocation().getDirection().setY(0).normalize();
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        Location startLoc = caster.getLocation().clone();
        double maxWidth = 4.0;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchFissureAttack(caster, wand, props, range, direction, perpendicular, startLoc, maxWidth);
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
                    int crackCount = (int)(phaseProgress * 12);
                    for (int i = 0; i < crackCount; i++) {
                        double angle = (2 * Math.PI * i / crackCount) + (ticks * 0.03);
                        double crackLen = 1.0 + phaseProgress * 2.0;
                        for (double d = 0; d < crackLen; d += 0.4) {
                            Location crackLoc = casterLoc.clone().add(
                                    Math.cos(angle) * d,
                                    0.05,
                                    Math.sin(angle) * d
                            );
                            crackLoc.getWorld().spawnParticle(Utils.DUST, crackLoc, 0,
                                    new Particle.DustOptions(Color.fromRGB(50, 30, 10), 0.8F));
                        }
                    }

                    for (int i = 0; i < 6; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 2.0;
                        Location tremorLoc = casterLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        tremorLoc.getWorld().spawnParticle(Particle.FALLING_DUST, tremorLoc, 2, 0.2, 0.1, 0.2, 0,
                                Material.DIRT.createBlockData());
                    }

                    for (int i = 0; i < 4; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 1.5;
                        Location stoneLoc = casterLoc.clone().add(Math.cos(a) * r, 0.5 + Math.random() * 1.5, Math.sin(a) * r);
                        stoneLoc.getWorld().spawnParticle(Particle.BLOCK_CRACK, stoneLoc, 2, 0.1, 0.1, 0.1, 0.1,
                                Material.STONE.createBlockData());
                    }

                    if (ticks % 20 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_STONE_BREAK, 0.4f, 0.5f + (float)(progress * 0.6f));
                    }
                } else {
                    double phaseProgress = (progress - 0.67) / 0.33;
                    double crackExtend = phaseProgress * 8.0;

                    for (int i = 0; i < 8; i++) {
                        double angle = Math.random() * 2 * Math.PI;
                        double r = Math.random() * (1.0 * (1.0 - phaseProgress));
                        Location crackLoc = casterLoc.clone().add(
                                Math.cos(angle) * r,
                                0.05,
                                Math.sin(angle) * r
                        );
                        crackLoc.getWorld().spawnParticle(Utils.DUST, crackLoc, 0,
                                new Particle.DustOptions(Color.fromRGB(30, 15, 0), 1.0F));
                    }

                    for (double d = 0; d < crackExtend; d += 0.6) {
                        for (int side = -1; side <= 1; side += 2) {
                            Location crackLoc = casterLoc.clone().add(
                                    direction.clone().multiply(d)
                            ).add(perpendicular.clone().multiply(side * 0.3 * (d / crackExtend)));
                            crackLoc.getWorld().spawnParticle(Utils.DUST, crackLoc, 0,
                                    new Particle.DustOptions(Color.fromRGB(80, 50, 20), 0.6F));
                        }
                    }

                    if (ticks % 15 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.3f, 0.3f);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
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
        World world = caster.getWorld();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchMobFissureAttack(caster, wand, props);
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
                    int crackCount = (int)(phaseProgress * 12);
                    for (int i = 0; i < crackCount; i++) {
                        double angle = (2 * Math.PI * i / crackCount) + (ticks * 0.03);
                        double crackLen = 1.0 + phaseProgress * 2.0;
                        for (double d = 0; d < crackLen; d += 0.4) {
                            Location crackLoc = casterLoc.clone().add(
                                    Math.cos(angle) * d,
                                    0.05,
                                    Math.sin(angle) * d
                            );
                            crackLoc.getWorld().spawnParticle(Utils.DUST, crackLoc, 0,
                                    new Particle.DustOptions(Color.fromRGB(50, 30, 10), 0.8F));
                        }
                    }

                    for (int i = 0; i < 6; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 2.0;
                        Location tremorLoc = casterLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        tremorLoc.getWorld().spawnParticle(Particle.FALLING_DUST, tremorLoc, 2, 0.2, 0.1, 0.2, 0,
                                Material.DIRT.createBlockData());
                    }

                    if (ticks % 20 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_STONE_BREAK, 0.4f, 0.5f + (float)(progress * 0.6f));
                    }
                } else {
                    double phaseProgress = (progress - 0.67) / 0.33;
                    double crackExtend = phaseProgress * 8.0;

                    for (double d = 0; d < crackExtend; d += 0.6) {
                        for (int side = -1; side <= 1; side += 2) {
                            Location crackLoc = casterLoc.clone().add(
                                    direction.clone().multiply(d)
                            ).add(perpendicular.clone().multiply(side * 0.3 * (d / crackExtend)));
                            crackLoc.getWorld().spawnParticle(Utils.DUST, crackLoc, 0,
                                    new Particle.DustOptions(Color.fromRGB(80, 50, 20), 0.6F));
                        }
                    }

                    if (ticks % 15 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.3f, 0.3f);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Particle.BLOCK_CRACK, location, 30, 0.5, 0.5, 0.5, 0.5,
                Material.STONE.createBlockData());
        location.getWorld().spawnParticle(Particle.FALLING_DUST, location, 15, 0.5, 0.5, 0.5, 0,
                Material.STONE.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.BLOCK_CRACK, location, 50, 1, 1, 1, 0.5,
                Material.STONE.createBlockData());
        location.getWorld().spawnParticle(Particle.TOTEM, location, 20, 1, 1, 1, 0);
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
                            new Particle.DustOptions(Color.fromRGB(139, 90, 43), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.fissure.book_name"))
                .addCustomLoreLine(lang().get("spells.fissure.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
