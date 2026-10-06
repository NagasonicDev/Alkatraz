package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.effect.EffectApplicator;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShadowRealm extends AttackSpell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `DARK` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(106, 13, 173);

    /**
     * The single DARK palette. Every colour in this spell comes from here: nothing in
     * this file may name an RGB value, which is how this spell ended up with five
     * hand-picked near-blacks that all read as slightly different greys.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.DARK);

    /** Dome shell particles per frame. The shell is the boundary, so it is the loudest part. */
    private static final int SHELL_POINTS = 48;
    /** Interior volume particles per frame, drawn in {@code deep} so the dome reads as filled. */
    private static final int FILL_POINTS = 18;
    /** Vertical wall strands per frame, on opposite sides of the dome. */
    private static final int WALL_COLUMNS = 2;
    private static final int WALL_LAYERS = 6;
    private static final int WALL_POINTS_PER_LAYER = 10;
    /** Wall phase step, in radians, between consecutive vertical slices. */
    private static final double WALL_PHASE_STEP = 0.5D;
    /** Dome height as a multiple of its radius; the field is taller than it is wide. */
    private static final double HEIGHT_PER_RADIUS = 1.5D;
    /** Wall stand-off from the dome's axis, as a fraction of its radius. */
    private static final double WALL_RADIUS_FRACTION = 0.8D;
    /** Full-height interior pulses, drawn every {@link #PULSE_INTERVAL_TICKS} scheduler run. */
    private static final int PULSE_INTERVAL_TICKS = 2;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double radius;
    private double duration;
    private double damagePerTick;
    private int windUpDuration;

    public ShadowRealm(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/shadow_realm_options.yml");
        Alkatraz.getInstance().save("spells/shadow_realm.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/shadow_realm.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration");
        this.damagePerTick = spellConfig.getDouble("damage_per_tick");
        this.windUpDuration = spellConfig.getInt("wind_up_duration", 2) * 20;
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.PORTAL, location, 25, 0.3, 0.3, 0.3, 0.1);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.PORTAL, location, 45, 0.4, 0.4, 0.4, 0.15);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_SPLASH, 0.6f, 1.5f);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        Location targetLoc = Utils.resolveTarget(caster, 30);
        double activeRadius = getModifiedStat(caster, "radius", radius);
        double activeDamage = getModifiedStat(caster, "damage_per_tick", damagePerTick);
        World world = targetLoc.getWorld();

        // shadow_realm has NO power: key, so getBasePower() is 0.0. Seed the shared
        // properties from damage_per_tick and wrap them with the context's
        // properties-carrying overload, so getPower (option modifiers, then
        // affinity/resistance) applies exactly once per hit.
        AttackProperties props = new AttackProperties(
                caster,
                targetLoc,
                activeDamage * getWandPower(wand, caster),
                AttackType.MAGIC
        );
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchShadowRealm(caster, wand, targetLoc, activeRadius, world, props, context);
                    return;
                }

                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    return;
                }

                Location casterLoc = caster.getLocation();
                double progress = (double) ticks / windUpDuration;
                boolean phase1 = progress < 0.5;

                if (phase1) {
                    double phaseProgress = progress / 0.5;
                    double sphereRadius = phaseProgress * activeRadius * 0.6;

                    for (int i = 0; i < 20; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double b = Math.acos(2 * Math.random() - 1);
                        double r = Math.random() * sphereRadius;
                        double x = r * Math.sin(b) * Math.cos(a);
                        double y = r * Math.sin(b) * Math.sin(a);
                        double z = r * Math.cos(b);
                        Location sphereLoc = targetLoc.clone().add(x, y, z);
                        ParticleCaster.spawn(caster, sphereLoc.getWorld(), Utils.DUST, sphereLoc, 0,
                                PALETTE.deepDust());
                        if (Math.random() < 0.2) {
                            ParticleCaster.spawn(caster, sphereLoc.getWorld(), Utils.SMOKE, sphereLoc, 1, 0.1, 0.1, 0.1, 0);
                        }
                    }

                    for (int i = 0; i < 5; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 1.5;
                        Location tendrilLoc = casterLoc.clone().add(Math.cos(a) * r, 0.5 + Math.random(), Math.sin(a) * r);
                        ParticleCaster.spawn(caster, tendrilLoc.getWorld(), Utils.WITCH, tendrilLoc, 1, 0.1, 0.1, 0.1, 0);
                    }

                    if (ticks % 15 == 0) {
                        world.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.3f + (float)(progress * 0.7f));
                    }
                } else {
                    double phaseProgress = (progress - 0.5) / 0.5;
                    double currentRadius = activeRadius * (0.3 + phaseProgress * 0.7);

                    for (int i = 0; i < 30; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double b = Math.acos(2 * Math.random() - 1);
                        double r = currentRadius * (0.7 + Math.random() * 0.3);
                        double x = r * Math.sin(b) * Math.cos(a);
                        double y = r * Math.sin(b) * Math.sin(a);
                        double z = r * Math.cos(b);
                        Location sphereLoc = targetLoc.clone().add(x, y, z);
                        ParticleCaster.spawn(caster, sphereLoc.getWorld(), Utils.DUST, sphereLoc, 0,
                                PALETTE.accentDust());
                        if (Math.random() < 0.15) {
                            ParticleCaster.spawn(caster, sphereLoc.getWorld(), Utils.DRAGON_BREATH, sphereLoc, 1, 0.1, 0.1, 0.1, 0, 0.0f);
                        }
                    }

                    for (int i = 0; i < 8; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = currentRadius * (0.5 + Math.random() * 0.5);
                        Location tendrilLoc = targetLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        ParticleCaster.spawn(caster, tendrilLoc.getWorld(), Utils.WITCH, tendrilLoc, 1, 0.1, 0.1, 0.1, 0);
                        if (Math.random() < 0.3) {
                            ParticleCaster.spawn(caster, tendrilLoc.getWorld(), Utils.PORTAL, tendrilLoc, 1, 0.1, 0.1, 0.1, 0.1);
                        }
                    }

                    if (ticks % 10 == 0) {
                        world.playSound(targetLoc, Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.4f + (float)(phaseProgress * 0.4f));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchShadowRealm(Player caster, ItemStack wand, Location targetLoc, double activeRadius,
                                   World world, AttackProperties props, SpellAttackContext context) {
        world.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.3f);
        world.playSound(targetLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.2f);

        double fieldHeight = activeRadius * HEIGHT_PER_RADIUS;
        Set<UUID> afflicted = new HashSet<>();
        int effectTicks = (int) Math.ceil(duration * 20) + 60;

        new BukkitRunnable() {
            int ticks = 0;
            // SUSTAIN CADENCE LEDGER - per TARGET. AttackProperties' hit Set is
            // never cleared for the life of the cast, so hasHit would allow one
            // application per CAST rather than one per 20 ticks. The once-per-
            // interval clock is runnable-local; props.hit() is still called so a
            // single counter still kills the whole field.
            final Map<UUID, Long> nextStrikeTick = new HashMap<>();

            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    world.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
                    world.playSound(targetLoc, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
                    cancel();
                    return;
                }

                drawField(caster, targetLoc, activeRadius, fieldHeight, ticks);

                for (Entity entity : world.getNearbyEntities(
                        targetLoc, activeRadius, activeRadius, activeRadius)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    if (afflicted.add(le.getUniqueId())) {
                        if (le instanceof Player targetPlayer) {
                            EffectApplicator.applyPotion(caster, targetPlayer, new PotionEffect(PotionEffectType.BLINDNESS, effectTicks, 1, false, true), ShadowRealm.this);
                            EffectApplicator.applyPotion(caster, targetPlayer, new PotionEffect(PotionEffectType.SLOW, effectTicks, 2, false, true), ShadowRealm.this);
                            EffectApplicator.applyPotion(caster, targetPlayer, new PotionEffect(PotionEffectType.WEAKNESS, effectTicks, 1, false, true), ShadowRealm.this);
                        } else {
                            EffectApplicator.applyPotion(caster, le, new PotionEffect(PotionEffectType.SLOW, effectTicks, 2, false, true), ShadowRealm.this);
                            EffectApplicator.applyPotion(caster, le, new PotionEffect(PotionEffectType.WEAKNESS, effectTicks, 1, false, true), ShadowRealm.this);
                        }
                    }

                    ParticleCaster.spawn(caster, le.getWorld(), Utils.LARGE_SMOKE, le.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0);

                    // SUSTAIN CADENCE. The old code damaged on every one of the 20
                    // scheduler runs, so a target took 20 x 3.0 = 60 against a budget
                    // of 36. One hit every 20 ticks is 5 hits x 3.0 = 15.
                    Long nextStrike = nextStrikeTick.get(le.getUniqueId());
                    if (nextStrike != null && nextStrike > ticks) continue;
                    nextStrikeTick.put(le.getUniqueId(), ticks + SpellAttackContext.DAMAGE_INTERVAL_TICKS);
                    // context.tick() sits inside the branch, not at the top of run(),
                    // because nothing scales on the ticks where the ledger says no.
                    context.tick();
                    props.hit(le);
                    SpellDamageUtil.damageWithSpell(le, context.power(le),
                            caster, wand, ShadowRealm.this);
                }

                if (ticks % PULSE_INTERVAL_TICKS == 0) {
                    double a = Math.random() * 2 * Math.PI;
                    double r = activeRadius * 0.3;
                    Location pulseLoc = targetLoc.clone().add(Math.cos(a) * r, Math.random() * activeRadius, Math.sin(a) * r);
                    ParticleCaster.spawn(caster, pulseLoc.getWorld(), Utils.DRAGON_BREATH, pulseLoc, 10, 0.5, 0.3, 0.5, 0, 0.0f);
                    ParticleCaster.spawn(caster, pulseLoc.getWorld(), Utils.DUST, pulseLoc, 0, PALETTE.coreDust());
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 5L);
    }

    /**
     * One frame of the persistent realm. Signature shape is {@link SpellVfx#field}: a
     * ground dome shell in {@code core} - the boundary a player inside has to be able
     * to see - over an interior volume in {@code deep}, so standing inside reads as
     * enclosed rather than as touching a second surface. Two counter-phased vertical
     * wall strands on opposite sides give the dome its vertical walls, which the dome
     * shell alone does not express.
     */
    private void drawField(LivingEntity caster, Location targetLoc, double activeRadius,
                           double fieldHeight, int ticks) {
        double spin = ticks * 0.15D;
        SpellVfx.field(CAST_COLOR, caster, targetLoc, activeRadius, fieldHeight,
                SHELL_POINTS, FILL_POINTS, spin);

        double wallRadius = activeRadius * WALL_RADIUS_FRACTION;
        for (int side = 0; side < WALL_COLUMNS; side++) {
            double angle = spin + Math.PI * side;
            Location wallBase = targetLoc.clone().add(Math.cos(angle) * wallRadius, 0.0, Math.sin(angle) * wallRadius);
            SpellVfx.column(CAST_COLOR, caster, wallBase, fieldHeight, 0.35D,
                    WALL_LAYERS, WALL_POINTS_PER_LAYER,
                    (side == 0 ? WALL_PHASE_STEP : -WALL_PHASE_STEP) + spin);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;

        Location targetLoc = caster.getTarget().getLocation();
        double wandp = getWandPowerOrDefault(wand);

        // Same shape as the player path: shadow_realm has no power: key, so the
        // base is damage_per_tick, seeded into the shared properties and scaled
        // exactly once per hit.
        AttackProperties props = new AttackProperties(
                caster,
                targetLoc,
                damagePerTick * wandp,
                AttackType.MAGIC
        );
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchMobShadowRealm(caster, wand, targetLoc, props, context);
                    return;
                }

                if (caster.isDead()) {
                    cancel();
                    return;
                }

                double progress = (double) ticks / windUpDuration;
                double currentRadius = radius * (0.3 + progress * 0.7);

                for (int i = 0; i < 15; i++) {
                    double a = Math.random() * 2 * Math.PI;
                    double b = Math.acos(2 * Math.random() - 1);
                    double r = currentRadius * (0.7 + Math.random() * 0.3);
                    double x = r * Math.sin(b) * Math.cos(a);
                    double y = r * Math.sin(b) * Math.sin(a);
                    double z = r * Math.cos(b);
                    Location sphereLoc = targetLoc.clone().add(x, y, z);
                    ParticleCaster.spawn(caster, sphereLoc.getWorld(), Utils.DUST, sphereLoc, 0,
                            PALETTE.deepDust());
                    if (Math.random() < 0.1) {
                        ParticleCaster.spawn(caster, sphereLoc.getWorld(), Utils.SMOKE, sphereLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }

                if (ticks % 15 == 0) {
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.3f);
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchMobShadowRealm(Mob caster, ItemStack wand, Location targetLoc,
                                      AttackProperties props, SpellAttackContext context) {
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.3f);
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.2f);

        new BukkitRunnable() {
            int ticks = 0;
            // SUSTAIN CADENCE LEDGER - per TARGET, same reason as the player branch.
            final Map<UUID, Long> nextStrikeTick = new HashMap<>();

            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }

                SpellVfx.field(CAST_COLOR, caster, targetLoc, radius, radius * HEIGHT_PER_RADIUS,
                        SHELL_POINTS, FILL_POINTS, ticks * 0.2D);
                ParticleCaster.spawn(caster, targetLoc.getWorld(), Utils.SMOKE, targetLoc,
                        (int) (SHELL_POINTS / 4), radius * 0.4, radius * 0.4, radius * 0.4, 0);

                for (Entity entity : targetLoc.getWorld().getNearbyEntities(
                        targetLoc, radius, radius, radius)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1, false, true));

                    // SUSTAIN CADENCE. This runnable already ran on a 20-tick period,
                    // so the ledger is a no-op in practice - it is here so the mob
                    // branch cannot drift back to a faster cadence, and so the shape
                    // matches the player branch.
                    Long nextStrike = nextStrikeTick.get(le.getUniqueId());
                    if (nextStrike != null && nextStrike > ticks) continue;
                    nextStrikeTick.put(le.getUniqueId(), ticks + SpellAttackContext.DAMAGE_INTERVAL_TICKS);
                    context.tick();
                    props.hit(le);
                    SpellDamageUtil.damageWithSpell(le, context.power(le),
                            caster, wand, ShadowRealm.this);
                }

                ParticleCaster.spawn(caster, targetLoc.getWorld(), Utils.LARGE_SMOKE, targetLoc, 15, radius, 1, radius, 0);
                ParticleCaster.spawn(caster, targetLoc.getWorld(), Utils.WITCH, targetLoc, 5, radius, 1, radius, 0);

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 20L);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.shadowrealm.book_name"))
                .addCustomLoreLine(lang().get("spells.shadowrealm.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
