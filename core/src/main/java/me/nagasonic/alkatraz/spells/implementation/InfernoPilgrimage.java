package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellBlockComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Circle 7 FIRE burst: a five-block curtain of flame that walks 18 blocks down the exact
 * line the caster was looking along, dragging them forward behind it.
 *
 * <h2>Three pulses, not a DoT</h2>
 * This is the roster's most aura-looking fire spell and it is not one. Damage lives
 * entirely inside {@link #pulse}, which is guarded by {@code ticks % pulseInterval == 0}
 * <em>and</em> {@code pulses < cfg.pulseCount()}, so it fires at ticks 0, 8 and 16 and then
 * never again - 16 ticks, 0.8 seconds, inside the one-second ceiling spec section 5.1 puts
 * on every fire damage event. The 1-tick scheduler moves the wall; it never damages.
 * Dissipation is particles only.
 *
 * <p>{@code props.hasHit} is deliberately NOT the gate: it would retire a target after
 * pulse 0 and the spell could never reach three hits. The per-cast {@code hits} map is the
 * cap, and it is hard-capped at {@code cfg.pulseCount()} - which is the yml's
 * {@code hit_count}, so the total {@code SpellBudgetTest} asserts (3 x 16 = 48.0) is exactly
 * the total one stationary target can take. {@code props.hit} is still recorded so counters
 * and {@link SpellComponentHandler} see the target as struck.
 */
public class InfernoPilgrimage extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);

    /** The wall's centre column - the leading edge of the curtain. */
    private static final Particle.DustOptions WALL_CORE = PALETTE.coreDust();
    /** The flanking columns, one shade back so the curtain reads as a curtain. */
    private static final Particle.DustOptions WALL_EDGE = PALETTE.accentDust();

    /** Smoke is drawn on every third cell rather than all fifteen, or the wall reads as fog. */
    private static final int SMOKE_EVERY = 3;

    private InfernoPilgrimageConfig config;

    public InfernoPilgrimage(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/inferno_pilgrimage.yml");
        Alkatraz.getInstance().saveConfig("spells/inferno_pilgrimage_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/inferno_pilgrimage.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = InfernoPilgrimageConfig.fromConfig(spellConfig.getValues(false));
        if (!config.pulsesFitTravel()) {
            // A hit_count the wall cannot physically deliver means the yml and the code
            // disagree, and firing two pulses while the budget test asserts three is exactly
            // the class of defect this wave exists to kill.
            throw new IllegalStateException("inferno_pilgrimage declares pulse_count="
                    + config.pulseCount() + " but travel_ticks=" + config.travelTicks()
                    + " only fits " + config.pulseCount() + " pulses");
        }
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        Vector dir = Utils.safeNormalize(caster.getEyeLocation().getDirection());
        if (dir == null) {
            cancelCast(caster);
            return;
        }
        InfernoPilgrimageConfig cfg = config;
        double speed = getModifiedStat(caster, "wall_speed", cfg.wallSpeed());
        if (speed <= 0.0) {
            cancelCast(caster);
            return;
        }
        double reach = getModifiedStat(caster, "pulse_reach", cfg.pulseReach());
        launchWall(caster, wand, dir, speed, reach, getWandPower(wand, caster), true);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        Vector dir = Utils.safeNormalize(caster.getTarget().getEyeLocation().toVector()
                .subtract(caster.getEyeLocation().toVector()));
        if (dir == null) return;
        // Defaults only - a mob is never dragged, so it never gets an option.
        launchWall(caster, wand, dir, config.wallSpeed(), config.pulseReach(),
                getWandPowerOrDefault(wand), false);
    }

    /**
     * The whole wall, shared by both cast paths. One {@link AttackProperties} for all three
     * pulses, so countering kills every pulse still to come.
     */
    private void launchWall(LivingEntity caster, ItemStack wand, Vector dir, double speed,
                            double reach, double wandPower, boolean escortCaster) {
        InfernoPilgrimageConfig cfg = config;
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * wandPower, AttackType.MAGIC);

        Location feet = caster.getLocation().clone();
        Vector right = dir.clone().rotateAroundY(Math.PI / 2);
        int travelTicks = Math.max(1, (int) Math.ceil(cfg.travelBlocks() / speed));

        SpellBlockComponent component = new SpellBlockComponent(
                this, props, caster, wand, SpellComponentType.OFFENSE,
                feet.getBlock(), Math.max(1.0, cfg.pulseWidth()),
                travelTicks + cfg.dissipateTicks() + 2);
        SpellComponentHandler.register(component);

        // The per-cast cap. Scoped to this one cast, so it dedups across all three pulses.
        final Map<UUID, Integer> hits = new HashMap<>();
        // A zero interval would be a modulo by zero on the first tick; clamp to one.
        final int pulseEvery = Math.max(1, cfg.pulseIntervalTicks());

        new BukkitRunnable() {
            int ticks = 0;
            int pulses = 0;

            @Override
            public void run() {
                if (ticks >= travelTicks || props.isCountered() || props.isCancelled()) {
                    cancel();
                    dissipate(caster, feet, dir, right, travelTicks, speed, cfg);
                    return;
                }
                if (caster.isDead()) {
                    cancel();     // nothing left to anchor the dissipation to
                    return;
                }

                double travelled = Math.min(cfg.travelBlocks(), speed * ticks);
                drawWall(caster, feet, dir, right, travelled, ticks, cfg);

                if (ticks % pulseEvery == 0 && pulses < cfg.pulseCount()) {
                    pulse(caster, wand, props, hits, feet, dir, right, speed, reach, pulses, cfg);
                    pulses++;
                }

                if (escortCaster && ticks < cfg.escortTicks()) {
                    Vector v = caster.getVelocity().clone();
                    caster.setVelocity(dir.clone().multiply(cfg.escortSpeed()).setY(v.getY()));
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The visible curtain: {@code wall_width} cells across by {@code wall_height} cells tall,
     * centred chest-height in front of the caster. Visual only.
     */
    private void drawWall(LivingEntity caster, Location feet, Vector dir, Vector right,
                          double travelled, int tick, InfernoPilgrimageConfig cfg) {
        World world = feet.getWorld();
        if (world == null) return;
        Location centre = feet.clone().add(dir.clone().multiply(travelled))
                .add(0, cfg.wallCenterHeight(), 0);
        int half = cfg.wallHalfWidth();
        for (int ix = -half; ix <= half; ix++) {
            for (int iy = 0; iy < cfg.wallHeight(); iy++) {
                Location cell = centre.clone()
                        .add(right.clone().multiply(ix))
                        .add(0, iy - 1, 0);
                ParticleCaster.spawn(caster, world, Utils.FLAME, cell, 3, 0.14, 0.14, 0.14, 0);
                ParticleCaster.spawn(caster, world, Utils.DUST, cell, 0,
                        ix == 0 ? WALL_CORE : WALL_EDGE);
                if ((tick + ix + iy) % SMOKE_EVERY == 0) {
                    ParticleCaster.spawn(caster, world, Utils.LARGE_SMOKE, cell, 1, 0.1, 0.08, 0.1, 0.005);
                }
            }
        }
        // The crown, so the wall has a single unmistakable head to follow down the line.
        Location crown = centre.clone().add(0, cfg.wallHeight() - 1, 0);
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION, crown, 1, 0.2, 0.2, 0.2, 0);
    }

    /**
     * One discrete pulse: a swept slab covering everything the front passed since the last
     * pulse, padded by {@code pulse_reach}. The swept window is what lets a single body
     * standing near the caster sit inside all three pulses and take the full
     * {@code power x hit_count}.
     */
    private void pulse(LivingEntity caster, ItemStack wand, AttackProperties props,
                       Map<UUID, Integer> hits, Location feet, Vector dir, Vector right,
                       double speed, double reach, int pulseIndex, InfernoPilgrimageConfig cfg) {
        double from = cfg.pulseWindowFrom(pulseIndex);
        double to = cfg.pulseWindowTo(pulseIndex);

        double midTravel = (from + to) * 0.5;
        Location mid = feet.clone().add(dir.clone().multiply(midTravel))
                .add(0, cfg.wallCenterHeight(), 0);
        double half = Math.max(1.0, (to - from) * 0.5 + reach);

        for (LivingEntity target : Utils.getNearbyLivingEntities(mid, cfg.pulseWidth(),
                cfg.pulseHeight() + cfg.wallCenterHeight(), half)) {
            if (target.equals(caster) || target.isDead()) continue;

            Vector toTarget = target.getLocation().add(0, 0.9, 0).toVector().subtract(feet.toVector());
            double along = toTarget.dot(dir);
            if (along < from - reach || along > to + reach) continue;
            Vector perp = toTarget.clone().subtract(dir.clone().multiply(along));
            double halfWidth = Math.abs(perp.dot(right));
            double vertical = Math.abs(perp.getY());
            if (halfWidth > cfg.pulseWidth() || vertical > cfg.pulseHeight()) continue;

            int already = hits.getOrDefault(target.getUniqueId(), 0);
            if (already >= cfg.pulseCount()) continue;    // HARD CAP == hit_count
            hits.put(target.getUniqueId(), already + 1);
            props.hit(target);

            SpellDamageUtil.damageWithSpell(target,
                    getPower(caster, target, props.getRemainingPower()),
                    caster, wand, InfernoPilgrimage.this);
            target.setFireTicks(cfg.igniteTicks());       // ONE discrete ignition per pulse
            target.setVelocity(dir.clone().multiply(cfg.knockback()).setY(cfg.knockbackY()));
        }

        mid.getWorld().playSound(mid, Sound.ENTITY_BLAZE_AMBIENT, 0.9f, 0.7f);
        ParticleCaster.spawn(caster, mid.getWorld(), Utils.EXPLOSION_EMITTER, mid, 1, 0.5, 0.3, 0.5, 0);
    }

    /**
     * The ending: the wall burns out over {@code dissipate_ticks} more ticks, walking the
     * front back toward the caster. PARTICLES ONLY - {@code pulse} is never called here, so
     * dissipation cannot add a fourth hit to anything.
     */
    private void dissipate(LivingEntity caster, Location feet, Vector dir, Vector right,
                          int travelTicks, double speed, InfernoPilgrimageConfig cfg) {
        if (caster.isDead()) return;
        double travelled = Math.min(cfg.travelBlocks(), speed * travelTicks);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= cfg.dissipateTicks() || caster.isDead()) {
                    cancel();
                    return;
                }
                double remaining = 1.0 - (tick / (double) cfg.dissipateTicks());
                Location at = feet.clone().add(dir.clone().multiply(travelled * remaining));
                if (tick == 0) {
                    at.getWorld().playSound(at, Sound.ENTITY_BLAZE_DEATH, 0.6f, 0.5f);
                    ParticleCaster.spawn(caster, at.getWorld(), Utils.LARGE_SMOKE, at, 20, 0.4, 0.2, 0.4, 0);
                }
                drawWall(caster, at, dir, right, 0.0, tick + 1, cfg);
                tick++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.FLAME, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.LARGE_SMOKE, location, 30, 0.2, 0.2, 0.2, 0.02);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.infernopilgrimage.book_name"))
                .addCustomLoreLine(lang().get("spells.infernopilgrimage.lore1"))
                .addCustomLoreLine(lang().get("spells.infernopilgrimage.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}