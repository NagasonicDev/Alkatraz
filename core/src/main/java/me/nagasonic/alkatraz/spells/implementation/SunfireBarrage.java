package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ChannelRegistry;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import java.util.UUID;

/**
 * Circle 8 FIRE burst, channeled. The caster holds still for two seconds while the
 * channel walks their gaze and marks up to five bodies; five pieces of the sun then come
 * down on those marks in sixteen ticks. A body may be struck at most
 * {@code hit_count} (2) times, so the worst case on any one target is 2 x 22.7 = 45.4,
 * inside {@code burstBudget(8) = 64.1}.
 *
 * <h2>Interruption is the shared registry's job</h2>
 * This spell used to want its own {@code private static final Map<UUID, ChannelData> channels}
 * plus an {@code onMove}/{@code onDamage}/{@code onQuit} handler triple. Wave C replaced that
 * shape with {@link WindUp} plus {@link ChannelRegistry} for {@code warp} and
 * {@code skeletal_knights}, precisely so a third channeled spell joins one code path rather
 * than drawing the picture again. So the channel runs through {@link WindUp#begin}, the
 * registry owns the interruption, and this class owns only its own repeating task.
 * {@code move_cancel_threshold} is still read - from the yml, by
 * {@link WindUp#moveCancelThreshold}, which is what {@link ChannelRegistry} consults.
 *
 * <h2>No damage in the channel</h2>
 * {@code windUp.tick()} and {@link #addMark} are particles, sound and bookkeeping.
 * {@link #addMark} writes a UUID to a list. All damage lives inside {@link #strike}, which
 * runs at most {@code strike_count} times and only on the {@code strike_interval_ticks}
 * phase. The channel deals nothing, so a cancelled cast costs the caster 450 mana and
 * deals zero.
 */
public class SunfireBarrage extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);

    /** The mark itself and the core of each impact. */
    private static final Particle.DustOptions MARK_DUST = PALETTE.coreDust();
    /** The converging columns of the lead-in, a shade back so the mark stays legible. */
    private static final Particle.DustOptions DESCEND_DUST = PALETTE.accentDust();

    /** Points in each converging column drawn over a marked body during the lead-in. */
    private static final int DESCEND_POINTS = 8;

    private SunfireBarrageConfig config;

    public SunfireBarrage(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/sunfire_barrage.yml");
        Alkatraz.getInstance().saveConfig("spells/sunfire_barrage_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/sunfire_barrage.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = SunfireBarrageConfig.fromConfig(spellConfig.getValues(false));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) return;
        UUID uuid = caster.getUniqueId();
        if (ChannelRegistry.isChanneling(uuid)) return;

        SunfireBarrageConfig cfg = config;
        int windUpTicks = cfg.windUpTicks(profile.getSpellMastery(this), getMaxMastery());

        // Read the selected mark count ONCE, into a local. Re-reading the option every tick
        // is what makes a channel's cost profile impossible to reason about.
        double selectedMarks = getModifiedStat(caster, "max_marks", cfg.maxMarks());
        int maxMarks = (int) Math.max(1.0, Math.min(cfg.maxMarks(), Math.round(selectedMarks)));

        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster), AttackType.MAGIC);

        List<UUID> marks = new ArrayList<>();
        WindUp windUp = WindUp.begin(this, caster, windUpTicks);
        // A zero interval would be a modulo by zero on the first tick; clamp to one.
        final int markEvery = Math.max(1, cfg.markIntervalTicks());

        // ChannelRegistry owns interruption; this class owns the repeating task.
        final int[] taskId = new int[1];
        ChannelRegistry.register(uuid, windUp, () -> {
            if (taskId[0] != 0) Bukkit.getScheduler().cancelTask(taskId[0]);
        });

        taskId[0] = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpTicks) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    completeBarrage(caster, wand, props, marks, cfg);
                    return;
                }
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    return;
                }
                windUp.tick();
                if (ticks % markEvery == 0 && marks.size() < maxMarks) {
                    addMark(caster, marks, cfg);
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L).getTaskId();
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Player-only: an interruptible channel needs a move/damage signal a mob
        // does not produce.
    }

    /**
     * Marks the nearest unmarked body to where the caster is LOOKING, not to the caster -
     * that is what makes the marks follow the gaze. Bookkeeping only: this deals nothing.
     *
     * <p>Marks are UUIDs, not entity references. A target that dies or logs out during the
     * channel is simply skipped at strike time ({@code Bukkit.getEntity(id) == null}), so no
     * stale reference can leak into the scheduler.
     */
    private void addMark(Player caster, List<UUID> marks, SunfireBarrageConfig cfg) {
        Location aim = caster.getEyeLocation()
                .add(caster.getEyeLocation().getDirection().multiply(cfg.markRadius()));

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : Utils.getNearbyLivingEntities(caster.getLocation(), cfg.markRadius())) {
            if (candidate.equals(caster) || candidate.isDead()) continue;
            if (marks.contains(candidate.getUniqueId())) continue;
            double distance = candidate.getLocation().distanceSquared(aim);
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = candidate;
        }
        if (best == null) return;

        marks.add(best.getUniqueId());
        Location at = best.getLocation().add(0, 0.9, 0);
        ParticleCaster.spawn(caster, at.getWorld(), Utils.DUST, at, 0, MARK_DUST);
        ParticleCaster.spawn(caster, at.getWorld(), Utils.LARGE_SMOKE, at, 4, 0.1, 0.1, 0.1, 0.01);
    }

    /**
     * The payoff: five discrete impacts over sixteen ticks. Nothing else in this spell
     * damages anything.
     */
    private void completeBarrage(Player caster, ItemStack wand, AttackProperties props,
                                 List<UUID> marks, SunfireBarrageConfig cfg) {
        if (caster.isDead() || !caster.isOnline() || marks.isEmpty()) return;
        if (props.isCountered() || props.isCancelled()) return;

        // ENTITY_BLAZE_SQUALL is not a Bukkit Sound constant, so it cannot be named here and
        // Spell.playSound would swallow a bad cast_sound name silently. ENTITY_BLAZE_SHOOT is.
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.2f);

        // A zero interval would be a modulo by zero on the first tick; clamp to one.
        final int strikeEvery = Math.max(1, cfg.strikeIntervalTicks());

        new BukkitRunnable() {
            int elapsed = 0;
            int fired = 0;

            @Override
            public void run() {
                if (fired >= cfg.strikeCount()) {
                    cancel();
                    return;
                }
                if (caster.isDead() || !caster.isOnline()
                        || props.isCountered() || props.isCancelled()) {
                    cancel();
                    return;
                }

                if (elapsed < cfg.strikeDelayTicks()) {
                    descend(caster, marks, elapsed, cfg);        // lead-in: markers converge
                } else if ((elapsed - cfg.strikeDelayTicks()) % strikeEvery == 0) {
                    strike(caster, wand, props, marks, fired, cfg);
                    fired++;
                }
                elapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One impact. {@code strike} walks {@code marks} round-robin from {@code fired}, so with
     * five marks and five strikes each body takes exactly one; with fewer marks the surplus
     * re-strikes, and the per-target cap is what stops that becoming a third hit.
     */
    private void strike(Player caster, ItemStack wand, AttackProperties props,
                        List<UUID> marks, int index, SunfireBarrageConfig cfg) {
        if (marks.isEmpty()) return;
        UUID id = marks.get(Math.floorMod(index, marks.size()));
        Entity entity = Bukkit.getEntity(id);
        if (!(entity instanceof LivingEntity target) || target.isDead()) return;

        Location at = target.getLocation().add(0, 0.6, 0);
        at.getWorld().playSound(at, Sound.ENTITY_BLAZE_AMBIENT, 1.2f, 0.6f);
        ParticleCaster.spawn(caster, at.getWorld(), Utils.EXPLOSION_EMITTER, at, 1, 0.3, 0.3, 0.3, 0);
        ParticleCaster.spawn(caster, at.getWorld(), Utils.EXPLOSION, at, 12, 0.4, 0.1, 0.4, 0);
        ParticleCaster.spawn(caster, at.getWorld(), Utils.LARGE_SMOKE, at, 14, 0.3, 0.4, 0.3, 0.03);
        ParticleCaster.spawn(caster, at.getWorld(), Utils.FLAME, at, 16, 0.4, 0.5, 0.4, 0.02);
        ParticleCaster.spawn(caster, at.getWorld(), Utils.DUST, at, 0, MARK_DUST);

        // Re-striking the same body spawns every particle and the sound but deals no damage.
        // That is what makes hit_count: 2 real rather than aspirational: the second pass is
        // a visible scorch on a body you already burned, not a second 22.7.
        if (props.hasHit(target)) return;
        props.hit(target);

        SpellDamageUtil.damageWithSpell(target,
                getPower(caster, target, props.getRemainingPower()),
                caster, wand, SunfireBarrage.this);
        target.setFireTicks(40);
        target.setVelocity(new Vector(0.0, cfg.knockUp(), 0.0));
    }

    /**
     * The delayed-orbital lead-in: for each live mark, a converging column so the player can
     * see where the sun is about to land. Deals no damage. Ever.
     */
    private void descend(Player caster, List<UUID> marks, int elapsed, SunfireBarrageConfig cfg) {
        double progress = (double) elapsed / cfg.strikeDelayTicks();
        for (UUID id : marks) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof LivingEntity target) || target.isDead()) continue;
            Location mid = target.getLocation().add(0, 0.9, 0);
            double height = 12.0 - 12.0 * progress;
            for (int i = 0; i < DESCEND_POINTS; i++) {
                Location p = mid.clone().add(0, height + i * 0.4, 0);
                ParticleCaster.spawn(caster, p.getWorld(), Utils.END_ROD, p, 1, 0.05, 0.05, 0.05, 0.01);
            }
            ParticleCaster.spawn(caster, mid.getWorld(), Utils.DUST, mid, 0, DESCEND_DUST);
            ParticleCaster.spawn(caster, mid.getWorld(), Utils.FLAME, mid, 4, 0.2, 0.3, 0.2, 0.01);
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        // Size 4, so the circle-8 cast reads larger than the circle-5 one.
        return CastCircle.start(this, caster, e, 4.0, 0);
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
                .setDisplayName(lang().get("spells.sunfirebarrage.book_name"))
                .addCustomLoreLine(lang().get("spells.sunfirebarrage.lore1"))
                .addCustomLoreLine(lang().get("spells.sunfirebarrage.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}