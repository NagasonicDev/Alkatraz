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
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 7 DARK burst - dark's <b>Curse</b> pillar, between the bound servants at
 * circles 4, 6 and 8.
 *
 * <p>Three darts of condensed shadow leave the caster in a tight fan, curve onto
 * whatever living thing they can reach, and on impact split into two shards of ink
 * that scatter and die. Every dart lays the same curse on what it touched - a
 * {@code WEAKNESS}/{@code BLINDNESS} pair that is still ticking long after the
 * volley has finished and the caster has walked away.
 *
 * <h2>The curse outlives the cast</h2>
 * There is <b>no teardown path in this class</b>: the only {@code cancel()} calls stop
 * the darts' and shards' runnables, and nothing anywhere calls
 * {@code removePotionEffect(...)}. Vanilla expires both effects
 * {@code debuff_duration} seconds after they were applied, whether or not the caster is
 * still online, still holding the wand, or anywhere near the target. Contrast
 * {@link ShadowRealm}, which applies its potion effects from inside the field's own
 * runnable and therefore ends with the field.
 *
 * <h2>Damage contract</h2>
 * All five rules are engaged. One {@link AttackProperties} per cast, built in
 * {@link #castAction} and shared by every dart, so countering any one dart kills the
 * whole volley. Exactly one {@code getPower} per impact, immediately followed by the
 * single {@link SpellDamageUtil#damageWithSpell}. {@code props.hasHit} is deliberately
 * <em>not</em> the per-target guard: it is per cast, so using it would limit a target to
 * a single dart and make the declared {@code hit_count: 3} a lie. The per-dart guard is
 * a local {@code Set<UUID>}, the exact mechanism {@code FlamingVolley} used before it
 * moved to one shared properties object, and each dart additionally consumes itself on
 * its first impact - so no target can be hit twice by the same dart.
 *
 * <p>Worst case per cast, per single target: {@code 3 x 16 = 48.0}, which is exactly
 * {@code SpellBudgets.burstBudget(7)}.
 *
 * <h2>What this spell never does</h2>
 * Dark takes power, it never gives it. No healing, no shielding, no
 * {@code ABSORPTION}, no {@code RESISTANCE}, no boss bar, and no effect of any kind on
 * the caster. The only two potion effect types referenced are {@code WEAKNESS} and
 * {@code BLINDNESS}, and both are applied to the <em>target</em>.
 */
public class UmbralVolley extends AttackSpell {

    /**
     * This class's identity on screen: violet {@code RGB(96, 0, 168)}, distinct from
     * {@code dark_tendrils}' {@code RGB(75,0,130)} circle dust, {@code shadow_realm}'s
     * {@code RGB(20,0,40)}, {@code void_anchor}'s near-black and {@code oblivion}'s
     * crimson. Hoisted so a per-tick draw allocates no colour object at all.
     */
    private static final Particle.DustOptions DART_DUST =
            new Particle.DustOptions(Color.fromRGB(96, 0, 168), 0.7F);
    /** The split shards, one shade back so the tell reads as debris rather than darts. */
    private static final Particle.DustOptions SHARD_DUST =
            new Particle.DustOptions(Color.fromRGB(58, 0, 96), 0.5F);

    /** Scheduler ticks between homing re-acquisitions. Every tick would be a world scan. */
    private static final int TRACK_EVERY_TICKS = 2;
    /** Half-extent of a dart's impact box query, in blocks. Matches FlamingVolley's arrow. */
    private static final double IMPACT_RADIUS = 0.6;
    /** Collision radius registered with the spell component handler. */
    private static final double COMPONENT_RADIUS = 0.5;
    /** Total yaw the split shards fan across, in degrees. */
    private static final double SHARD_FAN_DEGREES = 20.0;
    /** The dart's trailing spread is its direction scaled by this. */
    private static final double DART_SPREAD_SCALE = 0.6;
    /** Radius of the dust puff a shard leaves on its last tick, in blocks. */
    private static final double SHARD_PUFF_RADIUS = 0.3;

    private UmbralVolleyConfig config;

    public UmbralVolley(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/umbral_volley.yml");
        Alkatraz.getInstance().saveConfig("spells/umbral_volley_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/umbral_volley.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = UmbralVolleyConfig.fromConfig(spellConfig.getValues(false));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        UmbralVolleyConfig cfg = config;

        int count = (int) getModifiedStat(caster, "dart_count", optionInt(caster, "dart_count", cfg.dartCount()));
        if (count <= 0) {
            cancelCast(caster);
            return;
        }
        int curseSeconds = (int) getModifiedStat(caster, "debuff_duration",
                optionInt(caster, "curse_duration", cfg.debuffSeconds()));

        Vector aim = Utils.safeNormalize(caster.getEyeLocation().getDirection());
        if (aim == null) {
            cancelCast(caster);
            return;
        }

        // ONE AttackProperties for the whole volley (damage-contract rule 5): countering
        // any dart kills every dart still in the air, because every runnable's first act
        // is the same isCountered() check.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster),
                AttackType.MAGIC);

        for (double yawOffset : cfg.yawOffsets(count)) {
            launchDart(caster, wand, props, aim, Math.toRadians(yawOffset), curseSeconds);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        UmbralVolleyConfig cfg = config;
        LivingEntity mobTarget = caster.getTarget();

        Vector aim = Utils.safeNormalize(mobTarget.getEyeLocation().toVector()
                .subtract(caster.getEyeLocation().toVector()));
        if (aim == null) return;

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand),
                AttackType.MAGIC);

        // A mob gets no getModifiedStat and no options, and a tighter fan so two darts
        // still read as a volley rather than a shotgun blast.
        for (double yawOffset : UmbralVolleyConfig.yawOffsets(cfg.dartSpread() / 2.0, cfg.mobDartCount())) {
            launchDart(caster, wand, props, aim, Math.toRadians(yawOffset), cfg.debuffSeconds());
        }
    }

    /**
     * One dart. State is {@code position}, {@code direction}, {@code distanceTravelled},
     * {@code ticks}, the currently locked {@code locked} target and its own
     * {@code hit} set - the per-dart guard, which is deliberately NOT
     * {@code props.hasHit}.
     */
    private void launchDart(LivingEntity caster, ItemStack wand, AttackProperties props,
                            Vector aim, double yawOffsetRadians, int curseSeconds) {
        UmbralVolleyConfig cfg = config;
        new BukkitRunnable() {
            final Location position = caster.getEyeLocation().clone();
            final Set<UUID> hit = new HashSet<>();
            Vector direction = rotateAroundY(aim.clone(), yawOffsetRadians);
            double distanceTravelled = 0.0;
            int ticks = 0;
            LivingEntity locked = null;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                position.add(direction.clone().multiply(cfg.dartSpeed()));
                distanceTravelled += cfg.dartSpeed();
                ticks++;

                if (distanceTravelled > cfg.dartRange() || ticks >= cfg.dartLifetimeTicks()) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                Block current = position.getBlock();
                if (current.isLiquid() || (!current.isPassable() && current.getType().isOccluding())) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                if (ticks % TRACK_EVERY_TICKS == 0) {
                    if (locked == null || locked.isDead() || !locked.isValid()) {
                        locked = findTarget(caster, position, cfg.dartTrackRange());
                    }
                    if (locked != null) {
                        Vector bearing = Utils.safeNormalize(locked.getEyeLocation().toVector()
                                .subtract(position.toVector()));
                        if (bearing != null) {
                            double homing = Math.max(0.0, Math.min(1.0, cfg.dartHoming()));
                            Vector steered = Utils.safeNormalize(direction.clone()
                                    .multiply(1.0 - homing)
                                    .add(bearing.multiply(homing)));
                            direction = steered == null ? bearing : steered;
                        }
                    }
                }

                spawnDartBody(caster, position, direction);

                // One OFFENSE component per tick so barriers and counters can collide
                // with the dart in flight rather than only at the moment of impact.
                SpellComponentHandler.register(new SpellParticleComponent(
                        UmbralVolley.this, props, caster, wand, SpellComponentType.OFFENSE,
                        position, COMPONENT_RADIUS, 1));

                for (Entity entity : position.getWorld().getNearbyEntities(
                        position, IMPACT_RADIUS, IMPACT_RADIUS, IMPACT_RADIUS)) {
                    if (!(entity instanceof LivingEntity target)) continue;
                    if (target.equals(caster) || target.isDead()) continue;
                    if (!hit.add(target.getUniqueId())) continue;

                    // The ONE getPower for this damage instance, immediately followed by
                    // the one damage call. The 2-arg overload is never used on this hit.
                    double damage = getPower(caster, target, props.getRemainingPower());
                    SpellDamageUtil.damageWithSpell(target, damage, caster, wand, UmbralVolley.this);

                    applyCurse(target, curseSeconds);
                    split(caster, position, direction);
                    spawnDisperse(position);
                    position.getWorld().playSound(position, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The nearest valid victim. Guards copied from {@code DarkTendrils.TendrilAI} so
     * the two DARK hunting spells agree on what is not a target: never the caster,
     * never an armour stand, never a spectator or creative player, and never a player
     * in stealth at a higher circle than the caster's.
     */
    private LivingEntity findTarget(LivingEntity caster, Location at, double range) {
        MagicProfile casterProfile = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);
        LivingEntity best = null;
        double closest = range;

        for (LivingEntity entity : Utils.getNearbyLivingEntities(at, range)) {
            if (entity.equals(caster)) continue;
            if (entity instanceof ArmorStand) continue;
            if (entity instanceof Player player) {
                GameMode mode = player.getGameMode();
                if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) continue;
                MagicProfile other = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
                if (other != null && other.isStealth()
                        && (casterProfile == null || other.getCircleLevel() > casterProfile.getCircleLevel())) {
                    continue;
                }
            }
            double distance = entity.getLocation().distance(at);
            if (distance < closest) {
                best = entity;
                closest = distance;
            }
        }
        return best;
    }

    /**
     * The split: {@code split_count} shards that scatter and die. <b>Shards deal no
     * damage and carry no spell component</b> - the split is the tell that the dart
     * landed, and keeping it damage-free is what makes {@code hit_count: 3} (the dart
     * count) the honest number for {@code SpellBudgetTest}.
     */
    private void split(LivingEntity caster, Location at, Vector aim) {
        UmbralVolleyConfig cfg = config;
        int shards = Math.max(0, cfg.splitCount());
        int shardTicks = Math.max(1, cfg.splitShardTicks());

        for (int i = 0; i < shards; i++) {
            // A fixed direction, taken once: index 0 goes left of the parent aim,
            // index 1 goes right, and neither curves afterwards.
            double yaw = (i - 0.5) * 2.0 * SHARD_FAN_DEGREES * Math.PI / 180.0;
            Vector step = rotateAroundY(aim.clone(), yaw).multiply(cfg.splitSpeed());

            new BukkitRunnable() {
                final Location shardPosition = at.clone();
                int shardTick = 0;

                @Override
                public void run() {
                    if (shardTick >= shardTicks) {
                        for (Location point : ParticleUtils.sphere(shardPosition, SHARD_PUFF_RADIUS, 6)) {
                            ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, SHARD_DUST);
                        }
                        cancel();
                        return;
                    }
                    // Tick 0 picks the direction up; every later tick advances the same
                    // vector, so the shards leave as straight streaks.
                    if (shardTick > 0) shardPosition.add(step);
                    ParticleCaster.spawn(caster, shardPosition.getWorld(), Utils.WITCH, shardPosition, 1, 0.1, 0.1, 0.1, 0.01);
                    ParticleCaster.spawn(caster, shardPosition.getWorld(), Utils.SQUID_INK, shardPosition, 1, 0.1, 0.1, 0.1, 0.01);
                    shardTick++;
                }
            }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
        }
    }

    /**
     * The whole of dark's identity for this spell.
     *
     * <p>Nothing here is ever removed by this class, so both effects outlive the cast
     * by construction. {@code WEAKNESS} and {@code BLINDNESS} only - neither deals
     * damage, so the {@code hit_count: 3} total stays the whole of the spell's
     * per-cast damage. There is deliberately no {@code WITHER}: damage over time would
     * be damage the budget cannot see.
     */
    private void applyCurse(LivingEntity target, int curseSeconds) {
        int ticks = Math.max(1, curseSeconds) * 20;
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS, ticks, config.weaknessAmplifier(), false, false));
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS, ticks, config.blindnessAmplifier(), false, false));
    }

    private void spawnDartBody(LivingEntity caster, Location at, Vector direction) {
        Vector spread = direction.clone().multiply(DART_SPREAD_SCALE);
        ParticleCaster.spawn(caster, at.getWorld(), Utils.DUST, at, 0,
                spread.getX(), spread.getY(), spread.getZ(), 0, DART_DUST);
        ParticleCaster.spawn(caster, at.getWorld(), Utils.SMOKE, at, 2, 0.1, 0.1, 0.1, 0.01);
    }

    private void spawnDisperse(Location at) {
        ParticleCaster.spawn(null, at.getWorld(), Utils.SMOKE, at, 8, 0.3, 0.3, 0.3, 0.05);
    }

    private int optionInt(Player caster, String optionId, int fallback) {
        SpellOption option = getOption(optionId);
        if (option == null) return fallback;
        OptionValue<?> selected = option.getSelectedValue(caster);
        if (selected == null) return fallback;
        Object value = selected.getValue();
        return value instanceof Number n ? n.intValue() : fallback;
    }

    /** Rotates a direction about the Y axis by {@code angle} radians, leaving the input untouched. */
    private static Vector rotateAroundY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SQUID_INK, location, 20);
        location.getWorld().playSound(location, Sound.ENTITY_PHANTOM_HURT, 1.0f, 0.5f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.SQUID_INK, location, 50);
        ParticleCaster.spawn(null, location.getWorld(), Utils.SOUL, location, 30);
        location.getWorld().playSound(location, Sound.ENTITY_PHANTOM_DEATH, 1.0f, 0.8f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.umbralvolley.book_name"))
                .addCustomLoreLine(lang().get("spells.umbralvolley.lore1"))
                .addCustomLoreLine(lang().get("spells.umbralvolley.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}