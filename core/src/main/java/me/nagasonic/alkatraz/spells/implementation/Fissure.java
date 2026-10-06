package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.dom.Ground;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
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

import java.util.HashMap;
import java.util.Map;

public class Fissure extends AttackSpell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `EARTH` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(160, 82, 45);
    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * The EARTH palette, resolved once. Every dust below is a pre-built instance off
     * it, so a per-tick emission allocates no {@link Color} and no
     * {@link Particle.DustOptions}.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.EARTH);

    /** The trench's leading lip - the brightest shade, because the front is the information. */
    private static final Particle.DustOptions EDGE_DUST = PALETTE.coreDust();
    /** Interior dust and the wind-up's forward-running crack; the mid shade. */
    private static final Particle.DustOptions CIRCLE_DUST = PALETTE.accentDust();
    /** Ground shadow and the phase-one tremor cracks; the darkest shade. */
    private static final Particle.DustOptions DEEP_DUST = PALETTE.deepDust();

    private int windUpDuration;
    private boolean blockDamage;
    private long recoverTime;
    private int terrainBudget;

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
        this.blockDamage = spellConfig.getBoolean("block_damage", true);
        this.recoverTime = spellConfig.getLong("recover_time", 0);
        this.terrainBudget = spellConfig.getInt("terrain_budget", SpellBudgets.terrainBudget(5));
    }

    private void launchFissureAttack(Player caster, ItemStack wand, AttackProperties props, double range, Vector direction, Vector perpendicular, Location startLoc, double maxWidth) {
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.2f, 0.4f);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.6f);
        Map<Location, Material> brokenBlocks = new HashMap<>();

        new BukkitRunnable() {
            double distance = 0;
            double prevDistance = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || distance > range) {
                    cancel();
                    scheduleRecover(caster, brokenBlocks);
                    return;
                }

                prevDistance = distance;
                distance += 1.0;

                double progress = distance / range;
                double fissureWidth = 0.3 + progress * maxWidth;

                Location head = startLoc.clone().add(direction.clone().multiply(distance));

                for (double w = -fissureWidth; w <= fissureWidth; w += 0.5) {
                    Location groundCheck = head.clone().add(perpendicular.clone().multiply(w));
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
                        if (blockDamage && Ground.isGround(target.getType())) {
                            // The circle-5 TERRAIN budget is 380 blocks and there was no cap
                            // at all before - the spell destroyed 346. Stop once the cast's
                            // allowance is spent.
                            if (brokenBlocks.size() >= terrainBudget) continue;
                            if (Protection.blockEdit(caster, target.getLocation())) {
                                brokenBlocks.putIfAbsent(target.getLocation(), target.getType());
                                target.breakNaturally();
                                ParticleCaster.spawn(caster, target.getWorld(), Utils.BLOCK, target.getLocation().add(0.5, 0.5, 0.5),
                                        5, 0.2, 0.2, 0.2, 0.3, target.getBlockData());
                            }
                        }
                    }
                }

                for (double w = -fissureWidth; w <= fissureWidth; w += 0.5) {
                    Location fissureLoc = head.clone().add(0, -0.5, 0).add(perpendicular.clone().multiply(w));

                    ParticleCaster.spawn(caster, fissureLoc.getWorld(), Utils.BLOCK, fissureLoc, 3, 0.2, 0.2, 0.2, 0.3,
                            Material.STONE.createBlockData());

                    if (Math.random() < 0.3) {
                        ParticleCaster.spawn(caster, fissureLoc.getWorld(), Utils.FALLING_DUST, fissureLoc, 1, 0.1, 0.1, 0.1, 0,
                                Material.DIRT.createBlockData());
                    }

                    if (Math.abs(w) > fissureWidth - 0.8) {
                        ParticleCaster.spawn(caster, fissureLoc.getWorld(), Utils.DUST, fissureLoc.clone().add(0, 0.2, 0), 0,
                                EDGE_DUST);
                    }
                }

                for (double w = -fissureWidth; w <= fissureWidth; w += 0.3) {
                    Location deepLoc = head.clone().add(0, -1, 0).add(perpendicular.clone().multiply(w));
                    ParticleCaster.spawn(caster, deepLoc.getWorld(), Utils.DUST, deepLoc, 0, DEEP_DUST);
                }

                // A mid-tone spine down the middle of the cut. Without it the trench
                // reads as two bright lips and a black void between them; with it the
                // three shades run bright edge -> mid interior -> dark depth.
                Location spineLoc = head.clone().add(0, -0.9, 0);
                ParticleCaster.spawn(caster, spineLoc.getWorld(), Utils.DUST, spineLoc, 2, 0.15, 0.15, 0.15, 0,
                        CIRCLE_DUST);

                for (int edge = -1; edge <= 1; edge += 2) {
                    double edgeWidth = fissureWidth * edge;
                    Location edgeLoc = head.clone().add(0, 0.5, 0).add(perpendicular.clone().multiply(edgeWidth));
                    ParticleCaster.spawn(caster, edgeLoc.getWorld(), Utils.BLOCK, edgeLoc, 6, 0.4, 0.3, 0.4, 0.2,
                            Material.STONE.createBlockData());

                    for (double s = -0.5; s <= 0.5; s += 0.5) {
                        Location sideRaise = edgeLoc.clone().add(0, 0.5 + Math.abs(s), 0)
                                .add(perpendicular.clone().multiply(s * 0.3));
                        ParticleCaster.spawn(caster, sideRaise.getWorld(), Utils.FALLING_DUST, sideRaise, 1, 0.1, 0.1, 0.1, 0,
                                Material.STONE.createBlockData());
                    }
                }

                for (double trailDist = prevDistance; trailDist < distance; trailDist += 0.5) {
                    double trailProgress = trailDist / range;
                    double trailWidth = 0.3 + trailProgress * maxWidth;
                    Location trailHead = startLoc.clone().add(direction.clone().multiply(trailDist)).add(0, -0.3, 0);
                    for (double w = -trailWidth; w <= trailWidth; w += 0.3) {
                        Location trailLoc = trailHead.clone().add(perpendicular.clone().multiply(w));
                        if (Math.random() < 0.1) {
                            ParticleCaster.spawn(caster, trailLoc.getWorld(), Utils.BLOCK, trailLoc, 1, 0.1, 0.05, 0.1, 0.2,
                                    Material.STONE.createBlockData());
                        }
                    }
                }

                if (Math.random() < 0.15) {
                    startLoc.getWorld().playSound(startLoc.clone().add(direction.clone().multiply(distance)),
                            Sound.BLOCK_STONE_BREAK, 0.5f, 0.4f + (float) Math.random() * 0.3f);
                }

                double hitWidth = fissureWidth + 1.0;
                for (Entity entity : startLoc.getWorld().getNearbyEntities(head, hitWidth, 3, hitWidth)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;
                    if (props.hasHit(le)) continue;
                    props.hit(le);

                    le.setVelocity(new Vector(0, 1.4, 0).add(direction.clone().multiply(0.5)));

                    SpellDamageUtil.damageWithSpell(
                            le,
                            getPower(caster, le, props.getRemainingPower()),
                            caster,
                            wand,
                            Fissure.this
                    );
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void scheduleRecover(LivingEntity caster, Map<Location, Material> brokenBlocks) {
        if (recoverTime <= 0 || brokenBlocks.isEmpty()) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                // WAS an unconditional setType(original, false) loop with no
                // Protection re-check - it could rewrite a region the caster lost
                // build permission in after casting.
                TerrainEditor.restoreAll(caster, new TerrainEditor.RestoreTicket(brokenBlocks));
            }
        }.runTaskLater(Alkatraz.getInstance(), recoverTime * 20);
    }

    private void launchMobFissureAttack(Mob caster, ItemStack wand, AttackProperties props) {
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
                    ParticleCaster.spawn(caster, loc.getWorld(), Utils.BLOCK, loc, 4, 0.3, 0.1, 0.3, 0.3,
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
                            getPower(caster, le, props.getRemainingPower()),
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

        // `power` is retired from earthsplitter.yml per spec section 7 - it is a
        // TERRAIN spell judged on blocks, and a `power` key that never reaches
        // damage is dead config. Incidental damage is seeded from the circle-5
        // BURST budget instead, so the per-target total is bounded by 22.1.
        // getPower is applied once per impact, in launchFissureAttack; the old code
        // applied it at cast AND again on each entity, squaring both the option
        // MULTIPLYs and the affinity term.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                SpellBudgets.burstBudget(5) * getWandPower(wand, caster),
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
                            ParticleCaster.spawn(caster, crackLoc.getWorld(), Utils.DUST, crackLoc, 0,
                                    DEEP_DUST);
                        }
                    }

                    for (int i = 0; i < 6; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 2.0;
                        Location tremorLoc = casterLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        ParticleCaster.spawn(caster, tremorLoc.getWorld(), Utils.FALLING_DUST, tremorLoc, 2, 0.2, 0.1, 0.2, 0,
                                Material.DIRT.createBlockData());
                    }

                    for (int i = 0; i < 4; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 1.5;
                        Location stoneLoc = casterLoc.clone().add(Math.cos(a) * r, 0.5 + Math.random() * 1.5, Math.sin(a) * r);
                        ParticleCaster.spawn(caster, stoneLoc.getWorld(), Utils.BLOCK, stoneLoc, 2, 0.1, 0.1, 0.1, 0.1,
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
                        ParticleCaster.spawn(caster, crackLoc.getWorld(), Utils.DUST, crackLoc, 0,
                                DEEP_DUST);
                    }

                    drawWindUpCrack(caster, casterLoc, direction, crackExtend);

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
        // Same retirement as the player path - see castAction.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                SpellBudgets.burstBudget(5) * wandp,
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
                            ParticleCaster.spawn(caster, crackLoc.getWorld(), Utils.DUST, crackLoc, 0,
                                    DEEP_DUST);
                        }
                    }

                    for (int i = 0; i < 6; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 2.0;
                        Location tremorLoc = casterLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        ParticleCaster.spawn(caster, tremorLoc.getWorld(), Utils.FALLING_DUST, tremorLoc, 2, 0.2, 0.1, 0.2, 0,
                                Material.DIRT.createBlockData());
                    }

                    if (ticks % 20 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_STONE_BREAK, 0.4f, 0.5f + (float)(progress * 0.6f));
                    }
                } else {
                    double phaseProgress = (progress - 0.67) / 0.33;
                    double crackExtend = phaseProgress * 8.0;

                    drawWindUpCrack(caster, casterLoc, direction, crackExtend);

                    if (ticks % 15 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.3f, 0.3f);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The wind-up's second phase: a crack running forward out of the caster along
     * their facing, growing a little further every tick.
     *
     * <p>Shared geometry, shared colours - {@link SpellVfx#ground} supplies both, so
     * the two lanes and the shade split are no longer this class's problem. The
     * leading edge is {@code core} and the run behind it steps toward {@code deep},
     * which is why a long crack reads as opening rather than as a static stripe.
     *
     * @param caster    the charging entity, for density falloff
     * @param origin    the caster's feet, where the crack starts
     * @param direction the flat direction the crack runs; assumed normalised
     * @param length    how far the crack currently reaches, in blocks
     */
    private void drawWindUpCrack(LivingEntity caster, Location origin, Vector direction, double length) {
        if (length <= 0) return;
        int samples = Math.max(2, (int) Math.ceil(length / 0.6));
        double yawDegrees = Math.toDegrees(Math.atan2(direction.getZ(), direction.getX()));
        SpellVfx.ground(CAST_COLOR, caster, origin, length, 0.6, samples, 2, yawDegrees, 1.0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.BLOCK, location, 30, 0.5, 0.5, 0.5, 0.5,
                Material.STONE.createBlockData());
        ParticleCaster.spawn(caster, location.getWorld(), Utils.FALLING_DUST, location, 15, 0.5, 0.5, 0.5, 0,
                Material.STONE.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.BLOCK, location, 50, 1, 1, 1, 0.5,
                Material.STONE.createBlockData());
        ParticleCaster.spawn(null, location.getWorld(), Utils.TOTEM, location, 20, 1, 1, 1, 0);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.earthsplitter.book_name"))
                .addCustomLoreLine(lang().get("spells.earthsplitter.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
