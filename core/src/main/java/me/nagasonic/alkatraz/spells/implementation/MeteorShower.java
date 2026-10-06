package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MeteorShower extends AttackSpell implements Listener {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `FIRE` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(255, 140, 0);
    private static final Map<UUID, MeteorData> activeMeteorData = new ConcurrentHashMap<>();

    /**
     * Every colour this spell draws comes from the shared FIRE palette. The two dusts below
     * were byte-identical {@code (255, 100, 0)} literals, so the landing marker and the impact
     * burst were the same colour and the marker was invisible against its own detonation.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);

    /**
     * The ground marker: {@code accent}. It has to read as a <i>telegraph</i> - a warning drawn
     * before the hit - so it sits a shade below the detonation it is warning about.
     */
    private static final Particle.DustOptions MARKER_DUST = PALETTE.accentDust();

    /**
     * The impact burst: {@code core}, the brightest shade, because the detonation is the
     * leading edge a player reacts to and nothing may out-shout it.
     */
    private static final Particle.DustOptions BURST_DUST = PALETTE.coreDust();

    /** Bodies per rising flame ring during the wind-up; both rings use the same count. */
    private static final int WIND_UP_RING_BODIES = 6;
    /** Innermost radius of the rising flame rings, in blocks. */
    private static final double WIND_UP_RING_INNER_RADIUS = 1.5;
    /** How far past the inner radius the outermost ring sits, in blocks. */
    private static final double WIND_UP_RING_SPREAD = 2.0;
    /** Stations along the ground scorch mark drawn at the resolved target. */
    private static final int MARKER_SAMPLES = 12;
    /** Parallel furrows across the scorch mark, so it reads as scorched ground not a solid slab. */
    private static final int MARKER_LANES = 3;
    /** Particles in the release frame's warning ring at the target. */
    private static final int LANDING_RING_POINTS = 24;
    /** Flame motes rising off the hub each wind-up tick. */
    private static final int WIND_UP_COLUMN_MOTES = 20;
    /** Ground smoke at the caster's feet each wind-up tick. */
    private static final int WIND_UP_FOOT_SMOKE = 8;
    /** Ticks between the rising-pitch blaze shots, and between the dragon growls. */
    private static final int WIND_UP_SHOOT_PERIOD = 20;
    private static final int WIND_UP_GROWL_PERIOD = 15;
    /** The dragon growl only joins the charge once it has visibly committed. */
    private static final float WIND_UP_GROWL_THRESHOLD = 0.5f;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private record MeteorData(Player caster, AttackProperties props, int[] blocksLeft, ItemStack wand) {}

    private boolean blockDamage;
    private double craterRadius;
    private double damageRadius;
    private int fireRadius;
    private int terrainBudget;

    public MeteorShower(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/meteor_shower_options.yml");
        Alkatraz.getInstance().save("spells/meteor_shower.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/meteor_shower.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        // wind_up_duration is gone: the shared WindUp owns the duration now, and at circle 6
        // that is SpellBudgets.windUpTicks(6) - which is the spell's cast time, not extra to it.
        this.blockDamage = spellConfig.getBoolean("block_damage", true);
        this.craterRadius = spellConfig.getDouble("crater_radius", 3.0);
        this.damageRadius = spellConfig.getDouble("damage_radius", 5.0);
        this.fireRadius = spellConfig.getInt("fire_radius", 5);
        this.terrainBudget = spellConfig.getInt("terrain_budget", 620);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @EventHandler
    private void onEntityExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof LargeFireball fb)) return;
        MeteorData data = activeMeteorData.remove(fb.getUniqueId());
        if (data == null) return;
        e.setCancelled(true);
        triggerMeteorExplosion(e.getLocation(), data.caster(), data.props(), data.blocksLeft(), data.wand());
    }

    @EventHandler
    private void onEntityDeath(EntityDeathEvent e) {
        activeMeteorData.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    private void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Alkatraz.getInstance()) return;
        activeMeteorData.clear();
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        // RAW base power - getPower is applied once per impact, in
        // triggerMeteorExplosion. It used to be applied here AND in
        // launchMeteorAttack AND again per impact.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster),
                AttackType.MAGIC
        );

        Location targetLoc = Utils.resolveTarget(caster, 30);
        // The crater radius is now driven by the option instead of being hardcoded
        // at 5 while the option controlled only meteor scatter.
        double radius = getModifiedStat(caster, "radius", craterRadius);

        // Register the cast so the spell is counterable and its projectiles can be
        // deflected. register was never called before, so meteor_shower was
        // uncounterable and immune to deflection.
        SpellComponentHandler.register(new SpellParticleComponent(
                this, props, caster, wand,
                SpellComponentType.OFFENSE, Utils.castLocation(caster), 0.5, 2));

        // The wind-up IS the cast time at circle 6 (see the cast_time comment in the yml), so
        // the shared WindUp owns the duration, the escalation and the landing telegraph. The
        // resolved target is handed straight to it, which is the one thing the bespoke charge
        // could not do: it used to hide the impact point until the final third of the charge.
        runWindUp(caster, targetLoc, radius,
                () -> launchMeteorAttack(caster, wand, props, targetLoc, radius, new int[]{terrainBudget}));
    }

    /**
     * Drives the circle-6 charge and runs {@code payload} exactly once, only after the shared
     * {@link WindUp} reports complete.
     *
     * <p>The wind-up only ever draws and counts - it never fires anything itself - so the
     * payload stays here. Three exits stop it without firing: the caster dying or logging off,
     * an interruption cancelling the wind-up, and normal completion. A cancelled wind-up must
     * never release the meteors, because mana is spent at the start of the cast and the player
     * was told the cast was interrupted.
     *
     * @param caster        the charging entity
     * @param marker        where the meteors will land, drawn as the dodge cue; may be null
     * @param scatterRadius the effective meteor scatter radius, used to size the scorch mark
     * @param payload       what to run once the charge completes
     */
    private void runWindUp(LivingEntity caster, Location marker, double scatterRadius, Runnable payload) {
        final WindUp windUp = WindUp.begin(this, caster, WindUp.standardTicks(this, caster), marker);
        final int[] tick = {0};

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isCharging(caster)) {
                    cancel();
                    return;
                }
                if (windUp.isCancelled()) {
                    cancel();
                    return;
                }

                windUp.tick();
                float progress = windUp.progress();
                drawCharge(caster, progress, tick[0]);
                drawScorchMark(caster, marker, scatterRadius, progress);
                playChargeSounds(caster, tick[0], progress);
                tick[0]++;

                if (windUp.isComplete()) {
                    cancel();
                    payload.run();
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private static boolean isCharging(LivingEntity caster) {
        if (caster.isDead()) return false;
        return !(caster instanceof Player player) || player.isOnline();
    }

    /**
     * The tell that survives the migration: two counter-rotating flame rings rising off the
     * caster, a collapsing column of motes above them and smoke at the feet.
     *
     * <p>{@link SpellVfx#orbit} is what makes the pair counter-rotate without a second hand-rolled
     * trigonometry loop. The hub rises with progress so the charge reads as gathering upward
     * rather than spinning in place.
     */
    private void drawCharge(LivingEntity caster, float progress, int tick) {
        Location feet = caster.getLocation();
        World world = feet.getWorld();
        if (world == null) return;

        double lift = progress * 3.0;
        Location hub = feet.clone().add(0.0, lift, 0.0);
        double outer = WIND_UP_RING_INNER_RADIUS + WIND_UP_RING_SPREAD;

        // Outer ring spins with the charge, inner ring against it, so the pair never reads as
        // a static circle.
        SpellVfx.orbit(CAST_COLOR, caster, hub, WIND_UP_RING_INNER_RADIUS, outer,
                WIND_UP_RING_BODIES, tick * 0.06);
        SpellVfx.orbit(CAST_COLOR, caster, hub.clone().add(0.0, 0.5, 0.0),
                WIND_UP_RING_INNER_RADIUS * 0.55, WIND_UP_RING_INNER_RADIUS,
                WIND_UP_RING_BODIES, -tick * 0.09);

        // The column narrows as it charges, mirroring the old two-phase "compress then release".
        double columnHeight = 2.0 + progress * 6.0;
        double columnReach = Math.max(0.2, 1.0 - progress * 0.6);
        for (int i = 0; i < WIND_UP_COLUMN_MOTES; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double reach = Math.random() * columnReach;
            Location mote = feet.clone().add(
                    Math.cos(angle) * reach,
                    lift + Math.random() * columnHeight,
                    Math.sin(angle) * reach);
            ParticleCaster.spawn(caster, world, Utils.FLAME, mote, 2, 0.05, 0.05, 0.05, 0.03);
        }

        for (int i = 0; i < WIND_UP_FOOT_SMOKE; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double reach = Math.random() * 2.5;
            Location smoke = feet.clone().add(Math.cos(angle) * reach, 0.1, Math.sin(angle) * reach);
            ParticleCaster.spawn(caster, world, Utils.SMOKE, smoke, 1, 0.2, 0.1, 0.2, 0.01);
        }
    }

    /**
     * The scorched GROUND the craters will carve, layered under the concentric landing rings
     * {@link WindUp} draws at the same point.
     *
     * <p>Two shapes at one place on purpose: the rings say <i>here</i>, this says <i>this much
     * of it</i>. The long axis is the damage footprint and the short axis is the crater, so the
     * mark is sized from the spell's own config rather than from a magic number.
     *
     * <p>{@code advance} is the <b>fraction</b> of the shape revealed, not a direction - it
     * fills in as the charge commits.
     */
    private void drawScorchMark(LivingEntity caster, Location target, double scatterRadius, float progress) {
        if (target == null) return;
        World world = target.getWorld();
        if (world == null) return;

        double length = damageRadius * 2.0;
        double width = Math.max(craterRadius, scatterRadius) * 2.0;
        // Point the long axis at the caster so the mark reads as coming from them.
        Location feet = caster.getLocation();
        double yaw = Math.toDegrees(Math.atan2(
                target.getZ() - feet.getZ(), target.getX() - feet.getX()));

        SpellVfx.ground(CAST_COLOR, caster, target, length, width,
                MARKER_SAMPLES, MARKER_LANES, yaw, progress);
    }

    /**
     * The audio half of the two-phase tell, which {@link WindUp} deliberately does not own: a
     * blaze shot per second that rises in pitch, plus a dragon growl that only joins in once
     * the charge is visibly committed.
     */
    private void playChargeSounds(LivingEntity caster, int tick, float progress) {
        World world = caster.getWorld();
        if (world == null) return;

        Location at = caster.getLocation();
        if (tick % WIND_UP_SHOOT_PERIOD == 0) {
            world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 0.5f + progress * 1.5f);
        }
        if (progress >= WIND_UP_GROWL_THRESHOLD && tick % WIND_UP_GROWL_PERIOD == 0) {
            world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 0.4f);
        }
    }

    private void launchMeteorAttack(Player caster, ItemStack wand, AttackProperties props,
                                    Location targetLoc, double radius, int[] blocksLeft) {
        World world = caster.getWorld();
        final int totalMeteors = 6;
        final int durationTicks = 80;
        // The old cadence fired 2 per tick for ticksElapsed < 10, so all six meteors
        // launched within 3 ticks and the <50 / <70 branches plus durationTicks were
        // unreachable. One meteor every durationTicks/totalMeteors ticks spreads all
        // six across the whole 80-tick window, making the window real.
        final int ticksPerMeteor = Math.max(1, durationTicks / totalMeteors);

        world.playSound(targetLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.6f);
        world.playSound(targetLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.3f);

        for (int i = 0; i < 30; i++) {
            double a = Math.random() * 2 * Math.PI;
            double r = Math.random() * radius * 1.5;
            Location glowLoc = targetLoc.clone().add(Math.cos(a) * r, Math.random() * 12 + 4, Math.sin(a) * r);
            ParticleCaster.spawn(caster, glowLoc.getWorld(), Utils.FLAME, glowLoc, 1, 0, 0, 0, 0);
        }

        // The release tell, and the seam between the wind-up and the first impact: the charge
        // has stopped drawing, so this is the last frame that still says "incoming". A MARKER_DUST
        // warning ring around a BURST_DUST ignition point - two shades, two jobs. It used to be
        // one bare BURST_DUST puff, which read as a stray particle rather than as a target.
        drawLandingRing(caster, targetLoc, radius);
        ParticleCaster.spawn(caster, targetLoc.getWorld(), Utils.DUST, targetLoc, 0, BURST_DUST);

        MeteorData meteorData = new MeteorData(caster, props, blocksLeft, wand);

        new BukkitRunnable() {
            int ticksElapsed = 0;
            int meteorsLaunched = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()) {
                    cancel();
                    return;
                }
                if (meteorsLaunched >= totalMeteors || ticksElapsed >= durationTicks) {
                    cancel();
                    return;
                }

                int meteorsThisTick = (ticksElapsed % ticksPerMeteor == 0) ? 1 : 0;

                for (int m = 0; m < meteorsThisTick && meteorsLaunched < totalMeteors; m++) {
                    double angle = Math.random() * 2 * Math.PI;
                    double r = Math.random() * radius;
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    Location impactLoc = targetLoc.clone().add(x, 0, z);
                    impactLoc.setY(targetLoc.getY());
                    Block below = impactLoc.getBlock();
                    while (below.getY() > targetLoc.getY() - 10 && below.getY() > -64 && below.isPassable()) {
                        impactLoc.subtract(0, 1, 0);
                        below = impactLoc.getBlock();
                    }
                    impactLoc.add(0, 1, 0);

                    Location spawnLoc = impactLoc.clone().add(0, 30 + Math.random() * 20, 0);

                    LargeFireball fireball = world.spawn(spawnLoc, LargeFireball.class);
                    Vector direction = Utils.safeNormalize(impactLoc.toVector().subtract(spawnLoc.toVector())).multiply(2);
                    fireball.setDirection(direction);
                    fireball.setYield(0);
                    fireball.setIsIncendiary(false);
                    fireball.setShooter(caster);

                    activeMeteorData.put(fireball.getUniqueId(), meteorData);

                    if (Math.random() < 0.3) {
                        world.playSound(spawnLoc, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.5f);
                    }

                    meteorsLaunched++;
                }

                ticksElapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void triggerMeteorExplosion(Location explodeLoc, Player caster,
                                        AttackProperties props, int[] blocksLeft, ItemStack wand) {
        World world = explodeLoc.getWorld();

        ParticleCaster.spawn(caster, world, Utils.EXPLOSION_EMITTER, explodeLoc, 0, 0, 0, 0, 0);
        ParticleCaster.spawn(caster, world, Utils.LAVA, explodeLoc, 30, 2, 1, 2, 0);
        ParticleCaster.spawn(caster, world, Utils.FLAME, explodeLoc, 60, 3, 1.5, 3, 0.05);
        ParticleCaster.spawn(caster, world, Utils.LARGE_SMOKE, explodeLoc, 20, 2, 1, 2, 0);
        ParticleCaster.spawn(caster, world, Utils.DUST, explodeLoc, 0, BURST_DUST);
        world.playSound(explodeLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        world.playSound(explodeLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.3f);

        // ONE getPower per impact, deduped per target for the whole cast so a
        // target standing in the field takes one meteor's worth, not six.
        for (LivingEntity le : Utils.getNearbyLivingEntities(explodeLoc, damageRadius)) {
            if (le.equals(caster)) continue;
            if (props.hasHit(le)) continue;
            props.hit(le);
            SpellDamageUtil.damageWithSpell(le, getPower(caster, le, props.getRemainingPower()),
                    caster, wand, this);
        }

        if (!blockDamage) return;

        // Real drops, per-block Protection, and a shared cast budget. The old triple
        // loop did setType(Material.AIR) 70% of the time, deleting blocks with zero
        // drops, matched "BEDROCK" by substring, and hardcoded radius 5 regardless of
        // the radius option.
        int allowance = Math.max(0, blocksLeft[0]);
        if (allowance > 0) {
            int destroyed = TerrainEditor.destroySphere(caster, explodeLoc, craterRadius, true, allowance);
            blocksLeft[0] = allowance - destroyed;
        }

        // igniteCandidates already gates through Protection.ignite, so the returned
        // list is safe to write directly.
        for (Location igniteLoc : TerrainEditor.igniteCandidates(
                caster, explodeLoc, fireRadius, fireRadius * fireRadius * 4)) {
            igniteLoc.getBlock().setType(Material.FIRE, false);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;

        // RAW base power. getPower is applied once per impact, below.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand),
                AttackType.MAGIC
        );
        int[] blocksLeft = {terrainBudget};

        Location targetLoc = caster.getTarget().getLocation();

        // Same shared wind-up as the player path, so a mob's Meteor Shower is telegraphed
        // exactly as a player's is. standardTicks grants no mastery discount to a non-player,
        // so a mob always pays the full circle-6 wind-up.
        runWindUp(caster, targetLoc, craterRadius,
                () -> launchMobMeteorAttack(caster, wand, props, targetLoc, blocksLeft));
    }

    private void launchMobMeteorAttack(Mob caster, ItemStack wand, AttackProperties props,
                                       Location targetLoc, int[] blocksLeft) {
        final int totalMeteors = 6;
        final int durationTicks = 80;
        final int ticksPerMeteor = Math.max(1, durationTicks / totalMeteors);

        new BukkitRunnable() {
            int meteorsLaunched = 0;
            int ticksElapsed = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()
                        || meteorsLaunched >= totalMeteors || ticksElapsed >= durationTicks) {
                    cancel();
                    return;
                }
                if (ticksElapsed % ticksPerMeteor != 0) {
                    ticksElapsed++;
                    return;
                }

                double angle = Math.random() * 2 * Math.PI;
                double r = Math.random() * craterRadius;
                Location impactLoc = targetLoc.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                ParticleCaster.spawn(caster, impactLoc.getWorld(), Utils.FLAME, impactLoc, 10, 0.5, 0.5, 0.5, 0.05);
                ParticleCaster.spawn(caster, impactLoc.getWorld(), Utils.LAVA, impactLoc, 5, 0.5, 0.5, 0.5, 0);
                ParticleCaster.spawn(caster, impactLoc.getWorld(), Utils.DUST, impactLoc, 0, BURST_DUST);

                // One getPower per impact, deduped for the whole cast.
                for (LivingEntity le : Utils.getNearbyLivingEntities(impactLoc, damageRadius)) {
                    if (le.equals(caster)) continue;
                    if (props.hasHit(le)) continue;
                    props.hit(le);
                    SpellDamageUtil.damageWithSpell(le, getPower(caster, le, props.getRemainingPower()),
                            caster, wand, MeteorShower.this);
                }

                // The mob path broke no terrain at all, so a mob's Meteor Shower was
                // strictly weaker than a player's. It now carves craters and lights
                // fires, from the same shared block budget.
                if (blockDamage) {
                    int allowance = Math.max(0, blocksLeft[0]);
                    if (allowance > 0) {
                        blocksLeft[0] = allowance
                                - TerrainEditor.destroySphere(caster, impactLoc, craterRadius, true, allowance);
                    }
                    for (Location igniteLoc : TerrainEditor.igniteCandidates(
                            caster, impactLoc, fireRadius, fireRadius * fireRadius * 4)) {
                        igniteLoc.getBlock().setType(Material.FIRE, false);
                    }
                }
                meteorsLaunched++;
                ticksElapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.LAVA, location, 20, 0.5, 0.5, 0.5, 0);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.LARGE_SMOKE, location, 30, 1, 1, 1, 0);
    }

    /**
     * The release frame's warning ring at the resolved target, in {@link #MARKER_DUST}.
     *
     * <p>Deliberately hand-placed rather than delegated to {@link SpellVfx#ring}: that builder
     * owns its own shade split, and this one specifically has to sit a shade <i>below</i> the
     * {@link #BURST_DUST} ignition point it surrounds.
     *
     * @param caster the charging entity, for per-player density falloff
     * @param target where the meteors will land
     * @param radius the meteor scatter radius the ring is sized from
     */
    private void drawLandingRing(LivingEntity caster, Location target, double radius) {
        World world = target.getWorld();
        if (world == null) return;

        double ringRadius = Math.max(1.0, radius);
        int points = LANDING_RING_POINTS;
        Location ringPoint = target.clone();
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            ringPoint.setX(target.getX() + Math.cos(angle) * ringRadius);
            ringPoint.setY(target.getY() + 0.1);
            ringPoint.setZ(target.getZ() + Math.sin(angle) * ringRadius);
            ParticleCaster.spawn(caster, world, Utils.DUST, ringPoint, 0, MARKER_DUST);
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.meteorshower.book_name"))
                .addCustomLoreLine(lang().get("spells.meteorshower.lore1"))
                .addCustomLoreLine("")
                // Follows the yml's promoted level: 6, matching Warp and Skeletal Knights.
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
