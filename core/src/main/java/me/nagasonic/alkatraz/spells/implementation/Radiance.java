package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.UndeadTypes;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Radiance - a sustained dome that heals everyone in it and burns the undead.
 *
 * <h2>Visuals</h2>
 * Two phases, two builders. The bespoke two-stage wind-up keeps its own shape (it is
 * circle 5 and predates the shared {@link me.nagasonic.alkatraz.spells.vfx.WindUp} policy,
 * which only binds at circle 6+) but now draws {@link SpellVfx#orbit} bodies that spiral
 * inward onto the caster, preserving the tell it always had. The persistent dome is a
 * single {@link SpellVfx#field} redrawn each tick: a {@code core} shell for the boundary
 * a player has to be able to see, and a {@code deep} interior so being inside reads as
 * enclosed rather than as a second surface.
 *
 * <h2>Colour</h2>
 * Every particle is routed through {@link ParticleCaster} and every constant through a
 * {@link Utils} alias. Shades come from the spell's own element palette, so the three
 * former hardcoded yellows and the azure-in-a-LIGHT-spell class of bug cannot recur.
 */
public class Radiance extends AttackSpell {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    // ---- Wind-up: orbit bodies spiralling in ------------------------------------

    /** Orbit radius, in blocks, at the first frame of the wind-up. */
    private static final double WIND_UP_START_RADIUS = 6.0D;

    /** Orbit radius, in blocks, at the halfway frame - the point the charge turns inward. */
    private static final double WIND_UP_MID_RADIUS = 2.0D;

    /** Orbit radius, in blocks, at the final frame. */
    private static final double WIND_UP_END_RADIUS = 0.7D;

    /** Orbiting bodies per frame during the first half of the wind-up. */
    private static final int WIND_UP_BODIES = 8;

    /** Orbiting bodies per frame during the second half, where the density climbs. */
    private static final int WIND_UP_BODIES_LATE = 4;

    /** Extra bodies the second half gains per frame as the charge commits. */
    private static final int WIND_UP_LATE_BODY_GAIN = 14;

    /** Radians the orbit turns per tick. */
    private static final double WIND_UP_SPIN = 0.08D;

    /** Height of the orbit's centre above the caster's feet. */
    private static final double WIND_UP_ORBIT_HEIGHT = 1.0D;

    /** Ticks between wind-up sound cues, first half and second half. */
    private static final int WIND_UP_SOUND_INTERVAL_EARLY = 20;
    private static final int WIND_UP_SOUND_INTERVAL_LATE = 15;

    // ---- The dome --------------------------------------------------------------

    /** Dome height as a multiple of the active radius. */
    private static final double DOME_HEIGHT_SCALE = 0.55D;

    /** Dome shell particle count per tick. */
    private static final int DOME_SHELL_POINTS = 44;

    /** Dome interior particle count per tick. */
    private static final int DOME_FILL_POINTS = 14;

    /** Radians the dome turns per tick. */
    private static final double DOME_SPIN = 0.05D;

    /** Height above the entity at which a heal is shown to the caster. */
    private static final double HEAL_MARKER_HEIGHT = 0.5D;

    /** Height above an undead victim at which its burn is shown. */
    private static final double BURN_MARKER_HEIGHT = 1.0D;

    private double healAmount;
    private double radius;
    private double duration;
    private int windUpDuration;

    public Radiance(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/radiance_options.yml");
        Alkatraz.getInstance().save("spells/radiance.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/radiance.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.healAmount = spellConfig.getDouble("heal_amount");
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration", 5.0);
        this.windUpDuration = spellConfig.getInt("wind_up_duration", 3) * 20;
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchRadiance(caster, wand);
                    return;
                }

                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    return;
                }

                double progress = (double) ticks / windUpDuration;
                double orbitRadius = orbitRadiusAt(progress);
                Location centre = caster.getLocation().add(0.0D, WIND_UP_ORBIT_HEIGHT, 0.0D);
                World world = centre.getWorld();
                if (world != null) {
                    int bodies = progress < 0.5
                            ? WIND_UP_BODIES
                            : WIND_UP_BODIES_LATE + (int) ((progress - 0.5) * 2.0 * WIND_UP_LATE_BODY_GAIN);
                    // Bodies spread from a third of the orbit radius out to the full radius,
                    // so the formation has depth rather than reading as one flat ring.
                    SpellVfx.orbit(Radiance.this, caster, centre,
                            orbitRadius * 0.34D, orbitRadius, bodies, ticks * WIND_UP_SPIN);
                }

                if (progress < 0.5) {
                    if (ticks % WIND_UP_SOUND_INTERVAL_EARLY == 0) {
                        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT,
                                0.5f, 0.8f + (float) (progress * 1.4f));
                    }
                } else {
                    double late = (progress - 0.5) * 2.0;
                    if (ticks % WIND_UP_SOUND_INTERVAL_LATE == 0) {
                        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT,
                                0.7f, 1.2f + (float) (late * 0.5f));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The wind-up orbit's radius at a given progress: a long fall from
     * {@link #WIND_UP_START_RADIUS} to {@link #WIND_UP_MID_RADIUS} across the first half,
     * then a slower convergence onto {@link #WIND_UP_END_RADIUS}. The two different slopes
     * are the wind-up's phase break, and are why it is not a plain linear shrink.
     *
     * @param progress wind-up progress in {@code [0, 1]}
     * @return the orbit radius in blocks, never negative
     */
    private static double orbitRadiusAt(double progress) {
        double t = Math.min(1.0D, Math.max(0.0D, progress));
        if (t < 0.5D) {
            double eased = (t / 0.5D) * (t / 0.5D);
            return WIND_UP_START_RADIUS + (WIND_UP_MID_RADIUS - WIND_UP_START_RADIUS) * eased;
        }
        double eased = (t - 0.5D) / 0.5D;
        return WIND_UP_MID_RADIUS + (WIND_UP_END_RADIUS - WIND_UP_MID_RADIUS) * eased;
    }

    private boolean isUndead(LivingEntity entity) {
        return UndeadTypes.contains(entity.getType());
    }

    private void launchRadiance(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double activeRadius = getModifiedStat(caster, "radius", radius);
        double wandPower = getWandPower(wand, caster);
        double healPerTick = getModifiedStat(caster, "heal", healAmount * wandPower) / duration;
        int totalTicks = (int) (duration * 20);

        // radiance has NO power: key - the design lists '-' for it, and it is a
        // SUSTAIN field whose output is heal_amount/duration. Seed the shared
        // properties from that base and wrap them, so getPower (option modifiers,
        // affinity/resistance, and the section 5.3 anti-dark amplification) still
        // applies exactly once per hit. Passing getBasePower() here would be 0.0.
        AttackProperties props = new AttackProperties(
                caster,
                caster.getLocation(),
                healPerTick * wandPower,
                AttackType.MAGIC
        );
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);

        World world = caster.getWorld();
        Location center = caster.getLocation().clone();

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        new BukkitRunnable() {
            int ticksElapsed = 0;
            long lastDamageTick = -SpellAttackContext.DAMAGE_INTERVAL_TICKS;
            // Per-TARGET strike ledger. `lastDamageTick` above stays because it
            // also drives the caster's and friendly players' HEAL, which is a
            // per-field pulse by design; it must NOT drive the damage.
            final Map<UUID, Long> nextStrikeTick = new HashMap<>();

            @Override
            public void run() {
                if (context.isSpent() || caster.isDead() || !caster.isOnline()) {
                    cancel();
                    return;
                }

                if (ticksElapsed >= totalTicks) {
                    center.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
                    cancel();
                    return;
                }

                // The dome: one shell plus one interior, re-spun every tick. Drawn before
                // the entity sweep so the burn and heal markers land on top of it.
                SpellVfx.field(Radiance.this, caster, center, activeRadius,
                        activeRadius * DOME_HEIGHT_SCALE, DOME_SHELL_POINTS, DOME_FILL_POINTS,
                        ticksElapsed * DOME_SPIN);

                boolean damageTick = ticksElapsed - lastDamageTick >= SpellAttackContext.DAMAGE_INTERVAL_TICKS;
                if (damageTick) {
                    lastDamageTick = ticksElapsed;
                    context.tick();
                }

                for (Entity entity : world.getNearbyEntities(center, activeRadius, activeRadius, activeRadius)) {
                    if (entity.equals(caster)) {
                        if (damageTick) {
                            double maxHealth = caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            caster.setHealth(Math.min(maxHealth, caster.getHealth() + healPerTick));

                            ParticleCaster.spawn(caster, caster.getWorld(), Utils.HEART,
                                    caster.getEyeLocation().add(0.0D, HEAL_MARKER_HEIGHT, 0.0D),
                                    2, 0.3, 0.3, 0.3, 0);
                        }
                        continue;
                    }

                    if (!(entity instanceof LivingEntity le)) continue;

                    if (isUndead(le)) {
                        if (damageTick) {
                            // SUSTAIN CADENCE LEDGER - per TARGET. `damageTick`
                            // alone is a per-FIELD gate, and props.hasHit is a
                            // per-CAST one, so neither can express "once a second
                            // per victim". See "SUSTAIN cadence: hasHit must not gate
                            // the repeats" at the top of wave 3.
                            Long nextStrike = nextStrikeTick.get(le.getUniqueId());
                            if (nextStrike == null || nextStrike <= ticksElapsed) {
                                nextStrikeTick.put(le.getUniqueId(), ticksElapsed + SpellAttackContext.DAMAGE_INTERVAL_TICKS);
                                props.hit(le);
                                // WAS calcPower(healPerTick, le, caster) - dead code with
                                // zero legitimate callers that skips getModifiedStat and,
                                // being on Spell rather than AttackSpell, never carried the
                                // anti-dark amplification either.
                                SpellDamageUtil.damageWithSpell(le, context.power(le), caster, wand, Radiance.this);
                                ParticleCaster.spawn(caster, le.getWorld(), Utils.FLAME,
                                        le.getLocation().add(0.0D, BURN_MARKER_HEIGHT, 0.0D),
                                        8, 0.3, 0.5, 0.3, 0.05);
                            }
                        }
                    } else if (entity instanceof Player targetPlayer) {
                        if (damageTick) {
                            double maxHealth = targetPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            targetPlayer.setHealth(Math.min(maxHealth, targetPlayer.getHealth() + healPerTick));
                            ParticleCaster.spawn(caster, targetPlayer.getWorld(), Utils.HEART,
                                    targetPlayer.getLocation().add(0.0D, BURN_MARKER_HEIGHT, 0.0D),
                                    3, 0.3, 0.3, 0.3, 0);
                        }
                    }
                }

                ticksElapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.GLOW, location, 20);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.GLOW, location, 40, 0.3, 0.3, 0.3, 0.1);
    }

    @Override
    public boolean canMobCast(Mob mob) {
        // The mob field heals the caster and sears whatever undead is nearby, so it
        // needs no target. AttackSpell's default requires one, which would be a
        // regression from Spell's unconditional true.
        return true;
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        double totalHeal = healAmount * getWandPowerOrDefault(wand);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchMobRadiance(caster, totalHeal);
                    return;
                }

                if (caster.isDead()) {
                    cancel();
                    return;
                }

                double progress = (double) ticks / windUpDuration;
                double orbitRadius = WIND_UP_START_RADIUS + (WIND_UP_END_RADIUS - WIND_UP_START_RADIUS) * progress;
                Location centre = caster.getLocation().add(0.0D, WIND_UP_ORBIT_HEIGHT, 0.0D);
                if (centre.getWorld() != null) {
                    // A mob's wind-up is the same tell at a fraction of the density: a mob
                    // must be readable as charging without competing with a player's cast.
                    SpellVfx.orbit(Radiance.this, caster, centre, orbitRadius * 0.34D, orbitRadius,
                            WIND_UP_BODIES / 2, ticks * WIND_UP_SPIN);
                }

                if (ticks % WIND_UP_SOUND_INTERVAL_EARLY == 0) {
                    caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.8f);
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchMobRadiance(Mob caster, double totalHeal) {
        if (caster.isDead()) return;

        new BukkitRunnable() {
            int healTicks = 0;

            @Override
            public void run() {
                if (healTicks >= (int)(duration * 20)) {
                    cancel();
                    return;
                }

                // The mob field deals no damage at all, so there is no per-target
                // ledger to keep here - but its heal pulse moves onto the same
                // 20-tick contract interval as the player branch, which also
                // halved its healing (10 pulses x 1.6 became 5 x 1.6).
                if (healTicks % SpellAttackContext.DAMAGE_INTERVAL_TICKS == 0) {
                    double healPerTick = totalHeal / duration;
                    double maxHealth = caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    caster.setHealth(Math.min(maxHealth, caster.getHealth() + healPerTick));
                    ParticleCaster.spawn(caster, caster.getWorld(), Utils.GLOW, caster.getLocation(), 10, 2, 1, 2, 0);
                }

                healTicks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.radiance.book_name"))
                .addCustomLoreLine(lang().get("spells.radiance.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
