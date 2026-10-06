package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Circle 7 LIGHT, and the hardest-hitting spell in the element - which is exactly why it is
 * the one that rewards a dark-invested target.
 *
 * <p>Shards orbit the caster, lock onto <em>separate</em> targets and then seek. Hard to
 * dodge, trivially answerable: every shard rides ONE {@link SpellAttackContext}, so a single
 * counter kills the whole volley at once. That sharing is the spell's identity and must not be
 * refactored into per-shard contexts.
 *
 * <h2>Why the targets list is never padded with duplicates</h2>
 * {@code AttackProperties.hasHit} is per-cast and {@code context.tick()} never clears it, so
 * a second shard arriving at a target the first shard already hit is refused and deals
 * nothing. A volley aimed twice at one target would therefore land {@code power} once, not
 * {@code power x 2}, and {@code hit_count: 2} would be a lie. One target in range means one
 * shard fires and the cast deals 18.9: under budget, never over.
 *
 * <p>Worst case per cast: {@code 18.9 x 2 = 37.8}, against {@code burstBudget(7) = 48.0}.
 * The {@code shards} option's 3- and 6-shard values exceed that and carry {@code mana_cost}
 * impacts, which spec section 4.3 permits as the only way a max option may exceed budget.
 */
public class ChoirOfLight extends AttackSpell {

    /** Cold celestial blue - the only cool colour among the five light spells. */
    private static final Color CHOIR_COLOR = Color.fromRGB(130, 200, 255);
    private static final Particle.DustOptions SHARD_DUST = new Particle.DustOptions(CHOIR_COLOR, 0.9F);
    private static final Particle.DustOptions ORBIT_DUST = new Particle.DustOptions(CHOIR_COLOR, 0.5F);

    /** Radians the orbit ring turns per tick during the wind-up. */
    private static final double ORBIT_SPIN = 0.35;
    /** Radians each orbiting shard bobs, offset per shard index so they do not stack. */
    private static final double ORBIT_BOB_RATE = 0.2;
    private static final double ORBIT_BOB_HEIGHT = 0.3;
    /** Points of trail drawn behind a seeking shard, and the spacing between them, in blocks. */
    private static final int TRAIL_POINTS = 3;
    private static final double TRAIL_SPACING = 0.35;
    /** The damage scan radius around a shard, which is also the radius it registers with. */
    private static final double SHARD_RADIUS = 0.7;
    private static final int COMPONENT_LIFETIME = 1;
    /**
     * A shard stays locked on while its target is within this multiple of {@code lock_range}
     * of it. Beyond that it re-acquires, so a target that sprints away does not drag a shard
     * across the map.
     */
    private static final double RETARGET_RANGE_FACTOR = 1.5;

    private ChoirOfLightConfig config = ChoirOfLightConfig.fromConfig(null);

    public ChoirOfLight(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/choir_of_light.yml");
        Alkatraz.getInstance().saveConfig("spells/choir_of_light_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/choir_of_light.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = ChoirOfLightConfig.fromConfig(spellConfig.getValues(false));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        int shardCount = Math.max(1, (int) getModifiedStat(caster, "shard_count", config.safeShardCount()));
        double lockRange = getModifiedStat(caster, "lock_range", config.lockRange());

        List<LivingEntity> targets = new ArrayList<>(shardCount);
        Entity aimed = Utils.getTargetEntity(caster, (int) lockRange);
        if (aimed instanceof LivingEntity first && first.isValid() && !first.isDead()
                && !first.equals(caster)) {
            targets.add(first);
        }
        for (LivingEntity candidate : Utils.getNearbyLivingEntities(caster.getLocation(), lockRange)) {
            if (targets.size() >= shardCount) break;
            if (candidate.equals(caster) || candidate.isDead() || !candidate.isValid()) continue;
            if (candidate instanceof Player p
                    && (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE)) continue;
            if (targets.contains(candidate)) continue;
            targets.add(candidate);
        }
        if (targets.isEmpty()) {
            Utils.sendActionBar(caster, lang().get("spells.choiroflight.no_target"));
            cancelCast(caster);
            return;
        }

        // ONE context for the entire volley. Every SpellParticleComponent below carries
        // context.properties(), so SpellComponentHandler.collide counters one object and every
        // shard's run loop reads context.isSpent(). Damage-contract rule 5.
        SpellAttackContext context = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);

        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        int mastery = profile == null ? 0 : profile.getSpellMastery(this);
        int orbitTicks = config.orbitTicks(mastery, getMaxMastery());

        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        launchVolley(caster, wand, context, targets, shardCount, orbitTicks);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        AttackProperties props = new AttackProperties(
                caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand),
                AttackType.MAGIC);
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);
        // A mob has one target, and rule 2's per-cast dedup means a second shard on that same
        // entity would deal nothing at all. One shard is the honest mob volley: 18.9, well
        // under burstBudget(7).
        List<LivingEntity> targets = new ArrayList<>(1);
        targets.add(caster.getTarget());
        launchVolley(caster, wand, context, targets, 1, config.orbitTicks(0, getMaxMastery()));
    }

    /**
     * One shard in flight. Immutable, and replaced in the list rather than mutated, so
     * {@code spent} is a value and not a flag on a live object.
     *
     * @param position  the shard's current position
     * @param target    the entity it is locked on
     * @param heading   the fanned launch direction, followed only on the shard's first step
     * @param travelled blocks travelled so far, against {@code shard_range}
     * @param age       ticks alive, against {@code lifetimeTicks}
     * @param spent     true once it has hit something or run out of road
     */
    private record Shard(Location position, LivingEntity target, Vector heading,
                         double travelled, int age, boolean spent) {
    }

    /**
     * The whole volley on one {@link BukkitRunnable}: orbit first, then seek.
     *
     * @param caster     the casting entity, for per-player particle density falloff
     * @param wand       the casting wand, or null on a mob cast
     * @param context    the single per-cast handle shared by every shard
     * @param targets    distinct targets, one per shard; never padded with duplicates
     * @param orbitCount how many shards share the ring, which is the volley width
     * @param orbitTicks how long the wind-up runs before the shards launch
     */
    private void launchVolley(LivingEntity caster, ItemStack wand, SpellAttackContext context,
                              List<LivingEntity> targets, int orbitCount, int orbitTicks) {
        Location origin = Utils.castLocation(caster);
        Vector base = Utils.safeNormalize(caster.getEyeLocation().getDirection());
        if (base == null) return;

        double spread = config.spreadDegrees();
        int count = Math.max(1, orbitCount);
        List<Shard> shards = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            // Yaw offsets spread evenly across spread_degrees; a single shard gets 0.0.
            double yaw = count <= 1 ? 0.0 : -spread / 2.0 + spread * i / (count - 1);
            shards.add(new Shard(origin.clone(), targets.get(i),
                    base.clone().rotateAroundY(Math.toRadians(yaw)), 0.0, 0, false));
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // ONCE for the whole volley, before any damage. tick() clears the per-tick
                // backstop; the durable dedup is props.hasHit, which is per-cast and is never
                // cleared by anything.
                context.tick();

                if (context.isSpent()) {
                    for (Shard shard : shards) dissipate(shard.position());
                    cancel();
                    return;
                }
                if (caster.isDead() || (caster instanceof Player offline && !offline.isOnline())) {
                    cancel();
                    return;
                }

                Location casterEye = caster.getEyeLocation();
                World eyeWorld = casterEye.getWorld();
                if (eyeWorld == null) {
                    cancel();
                    return;
                }

                // PHASE 1 - the orbit. No movement, no damage.
                if (ticks < orbitTicks) {
                    double orbitAngle = ticks * ORBIT_SPIN;
                    double orbitRadius = config.orbitRadius();
                    for (int i = 0; i < count; i++) {
                        double a = 2 * Math.PI * i / count + orbitAngle;
                        Location point = casterEye.clone().add(
                                Math.cos(a) * orbitRadius,
                                ORBIT_BOB_HEIGHT * Math.sin(ticks * ORBIT_BOB_RATE + i),
                                Math.sin(a) * orbitRadius);
                        ParticleCaster.spawn(caster, eyeWorld, Utils.DUST, point, 1, ORBIT_DUST);
                    }
                    ParticleCaster.spawn(caster, eyeWorld, Utils.ENTITY_EFFECT, casterEye, 1, 0.05, 0.05, 0.05, 0.0, CHOIR_COLOR);
                    ticks++;
                    return;
                }

                // PHASE 2 - the seek.
                for (int i = 0; i < shards.size(); i++) {
                    Shard shard = shards.get(i);
                    if (shard.spent()) continue;

                    LivingEntity locked = retarget(shard);
                    if (locked == null) {
                        dissipate(shard.position());
                        shards.set(i, spent(shard));
                        continue;
                    }

                    Vector to = Utils.safeNormalize(
                            locked.getEyeLocation().toVector().subtract(shard.position().toVector()));
                    if (to == null) {
                        dissipate(shard.position());
                        shards.set(i, spent(shard));
                        continue;
                    }
                    // The fanned heading carries the shard's launch step; every step after that
                    // homes, which is what makes the volley seek rather than volley.
                    Vector step = shard.age() == 0 ? shard.heading() : to;

                    Location next = shard.position().add(step.clone().multiply(config.shardSpeed()));
                    double travelled = shard.travelled() + config.shardSpeed();
                    int age = shard.age() + 1;

                    // At the defaults shard_range binds first: 28 / 0.9 is about 32 ticks
                    // against a 50-tick lifetime.
                    if (travelled >= config.shardRange() || age >= config.lifetimeTicks()
                            || blocked(next)) {
                        dissipate(next);
                        shards.set(i, spent(shard));
                        continue;
                    }

                    // The SHARED properties again, so one counter ends every shard.
                    SpellParticleComponent component = new SpellParticleComponent(
                            ChoirOfLight.this, context.properties(), caster, wand,
                            SpellComponentType.OFFENSE, next.clone(), SHARD_RADIUS, COMPONENT_LIFETIME);
                    SpellComponentHandler.register(component);

                    World world = next.getWorld();
                    if (world == null) {
                        dissipate(next);
                        shards.set(i, spent(shard));
                        continue;
                    }
                    ParticleCaster.spawn(caster, world, Utils.DUST, next, 1, SHARD_DUST);
                    for (int j = 1; j <= TRAIL_POINTS; j++) {
                        Location trail = next.clone().add(to.clone().multiply(-TRAIL_SPACING * j));
                        ParticleCaster.spawn(caster, world, Utils.DUST, trail, 1, SHARD_DUST);
                    }

                    boolean landed = false;
                    for (Entity entity : world.getNearbyEntities(next, SHARD_RADIUS, SHARD_RADIUS, SHARD_RADIUS)) {
                        if (!(entity instanceof LivingEntity victim)) continue;
                        if (victim.equals(caster)) continue;
                        if (victim.isDead() || !victim.isValid()) continue;
                        // Rule 2: the SECOND shard to arrive at an already-hit target is refused.
                        if (!context.hit(victim)) continue;
                        SpellDamageUtil.damageWithSpell(victim, context.power(victim), caster, wand, ChoirOfLight.this);
                        dissipate(next);
                        landed = true;
                        break;
                    }

                    shards.set(i, landed ? spent(shard) : new Shard(next, locked, shard.heading(), travelled, age, false));
                }

                boolean anyLive = false;
                for (Shard shard : shards) {
                    if (!shard.spent()) {
                        anyLive = true;
                        break;
                    }
                }
                if (!anyLive) {
                    cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * Keeps a shard locked on while its target is alive and within reach; otherwise re-acquires
     * the nearest valid living entity around the shard itself.
     *
     * @param shard the shard being re-evaluated
     * @return the entity to keep seeking, or null when there is nothing left to sing to
     */
    private LivingEntity retarget(Shard shard) {
        LivingEntity locked = shard.target();
        if (locked != null && locked.isValid() && !locked.isDead()
                && !(locked instanceof Player offline && !offline.isOnline())
                && shard.position().getWorld() != null
                && shard.position().getWorld().equals(locked.getLocation().getWorld())
                && shard.position().distance(locked.getLocation()) <= config.lockRange() * RETARGET_RANGE_FACTOR) {
            return locked;
        }
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity candidate : Utils.getNearbyLivingEntities(shard.position(), config.lockRange())) {
            if (candidate.isDead() || !candidate.isValid()) continue;
            if (candidate.equals(shard.target())) continue;
            double distanceSq = candidate.getLocation().distanceSquared(shard.position());
            if (distanceSq < bestSq) {
                bestSq = distanceSq;
                best = candidate;
            }
        }
        return best;
    }

    private static Shard spent(Shard shard) {
        return new Shard(shard.position(), shard.target(), shard.heading(), shard.travelled(), shard.age(), true);
    }

    private static boolean blocked(Location location) {
        Block block = location.getBlock();
        return block.isLiquid() || (!block.isPassable() && block.getType().isOccluding());
    }

    private void dissipate(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        ParticleCaster.spawn(null, world, Utils.EXPLOSION, loc, 1);
        ParticleCaster.spawn(null, world, Utils.SMOKE, loc, 4, 0.2, 0.2, 0.2, 0.0);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.ENTITY_EFFECT, location, 24, CHOIR_COLOR);
        location.getWorld().playSound(location, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.ENTITY_EFFECT, location, 48, CHOIR_COLOR);
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 2);
        location.getWorld().playSound(location, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.9f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.choiroflight.book_name"))
                .addCustomLoreLine(lang().get("spells.choiroflight.lore1"))
                .addCustomLoreLine(lang().get("spells.choiroflight.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}
