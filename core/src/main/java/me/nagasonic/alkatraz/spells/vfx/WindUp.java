package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The circle 6+ wind-up: a visible, escalating charge in the caster's element
 * colours, optionally anchored to the ground at the point the spell is about to
 * land on, so the effect can be dodged.
 *
 * <h2>The contract</h2>
 * <ul>
 *   <li>A wind-up <b>is</b> the spell's cast time at circle 6+, not additional
 *       to it. Standard durations come from {@link SpellBudgets#windUpTicks(int)};
 *       a spell declaring its own {@code wind_up_duration:} passes it explicitly
 *       to {@link #begin} and is responsible for having satisfied
 *       {@link SpellBudgets#isPaidOverWindUp(int)}.</li>
 *   <li>{@link #isComplete()} is the caller's cue to fire the payload: spells must fire
 *       the payload <b>after</b> the wind-up completes, never while it is ticking.
 *       A wind-up only ever draws and counts - it never fires the effect itself.</li>
 *   <li>The wind-up never fires its payload once {@link #cancel(String)} has run.</li>
 *   <li>{@link Spell#cast} starts exactly one wind-up per circle 6+ cast through
 *       {@link #forFrameworkCast}; a spell implementation must not start a second
 *       inside {@code castAction}. {@link ChannelRegistry} applies one shared
 *       move/damage/quit interrupt rule to every framework wind-up.</li>
 * </ul>
 *
 * <h2>Mana is not refunded on interruption</h2>
 * <b>This is the single most likely thing for a maintainer to get wrong.</b>
 * {@code Spell.cast()} deducts mana at {@code Spell.java:187}, <i>before</i> the
 * cast-time delay, so by the time a wind-up is running the caster has already
 * paid. A wind-up cancelled by {@link ChannelRegistry} - moving too far, taking
 * damage, quitting, or plugin shutdown - therefore keeps the mana cost, and gets
 * no cooldown and no mastery. This matches what every hand-rolled
 * {@code abortChannel} already did; the contract makes it uniform, it does not
 * introduce a new rule. (The only refund path in the spell framework is
 * {@code Spell.cancelCast(Player)}, which a spell calls from inside
 * {@code castAction} when the cast itself cannot proceed - that is a different
 * moment, before the payload.)
 *
 * <h2>Element colours</h2>
 * Every particle is routed through {@link ParticleCaster#spawn} and every
 * particle constant through a {@link Utils} alias - never a raw {@code Particle.*},
 * which does not resolve under the 1.19 mappings half the plugin supports.
 * Shades come from {@link ElementPalette}: {@code core} for the leading edge,
 * {@code accent} and {@code deep} for the trailing structure.
 */
public final class WindUp {

    /**
     * Spell id to circle for every spell that is <b>not</b> required to wind up. Empty since the
     * 0.9.2 rework: every circle 6+ spell in the shipped roster owes a wind-up, and the old
     * per-spell exemptions went with the spells they named.
     *
     * <p>Kept as an (empty) map rather than deleted so {@link #requiresWindUp(String, int)} keeps
     * its shape and a future exemption has one obvious place to live.
     */
    public static final Map<String, Integer> EXEMPT_FROM_WIND_UP;

    static {
        EXEMPT_FROM_WIND_UP = Collections.unmodifiableMap(new HashMap<>());
    }

    /** Blocks a caster may stray from the channel origin before it interrupts them. */
    public static final double DEFAULT_MOVE_CANCEL_THRESHOLD = 0.3D;

    private static final String INTERRUPTED_LANG_KEY = "spells.cast.interrupted";

    private final Spell spell;
    private final LivingEntity caster;
    private final int totalTicks;
    private final Location marker;
    private final Location origin;
    private final ElementPalette.Palette palette;

    private int elapsed = 0;
    private boolean cancelled = false;

    private WindUp(Spell spell, LivingEntity caster, int totalTicks, Location marker) {
        this(spell, caster, totalTicks, marker, null);
    }

    /**
     * @param color the spell's own colour, or {@code null} to take it from the spell's element.
     *              Resolved once here rather than per tick, because the charge redraws every tick
     *              for the whole wind-up and {@link ElementPalette#of(Color)} is cached anyway.
     */
    private WindUp(Spell spell, LivingEntity caster, int totalTicks, Location marker, Color color) {
        this.spell = spell;
        this.caster = caster;
        this.totalTicks = Math.max(0, totalTicks);
        this.marker = marker;
        this.origin = caster.getLocation();
        this.palette = (color != null)
                ? ElementPalette.of(color)
                : ElementPalette.of(spell == null ? null : spell.getElement());
    }

    /**
     * Standard wind-up for this spell at its own circle, honouring the 25% max-mastery
     * discount. This is what a spell should pass when its yml declares no
     * {@code wind_up_duration:}.
     */
    public static int standardTicks(Spell spell, LivingEntity caster) {
        return effectiveTicks(SpellBudgets.windUpTicks(spell.getRequiredCircleLevel()),
                masteryOf(spell, caster), spell.getMaxMastery());
    }

    /**
     * Begins a wind-up with no landing marker - for self-targeted and omni-target
     * effects where there is no ground to telegraph.
     *
     * @param spell       the spell being wound up
     * @param caster      the charging entity
     * @param windUpTicks duration in ticks, already mastery-adjusted by the caller
     *                    when the spell declares its own {@code wind_up_duration:}
     */
    public static WindUp begin(Spell spell, LivingEntity caster, int windUpTicks) {
        return new WindUp(spell, caster, windUpTicks, null);
    }

    /**
     * Begins a wind-up in the casting spell's own colour rather than its element's palette.
     *
     * <p>The charge is the telegraph for the payload, so it has to read as the same effect the
     * caster is about to receive. A {@code NONE}-element spell that draws a coloured payload would
     * otherwise telegraph in grey and land in colour.
     *
     * @param spell       the spell being wound up
     * @param color       the colour the spell declared; {@code null} falls back to element grey
     * @param caster      the charging entity
     * @param windUpTicks duration in ticks, already mastery-adjusted by the caller
     */
    public static WindUp begin(Spell spell, Color color, LivingEntity caster, int windUpTicks) {
        return new WindUp(spell, caster, windUpTicks, null, color);
    }

    /**
     * Begins a wind-up anchored to a landing point, drawing escalating ground
     * geometry there so the effect can be dodged.
     *
     * @param markerLocation where the spell is about to land, or null for
     *                       self/omni-target effects
     */
    public static WindUp begin(Spell spell, LivingEntity caster, int windUpTicks, Location markerLocation) {
        return new WindUp(spell, caster, windUpTicks,
                markerLocation == null ? null : markerLocation.clone());
    }

    /**
     * Begins a marker-anchored wind-up in the casting spell's own colour.
     *
     * @param color         the colour the spell declared; {@code null} falls back to element grey
     * @param markerLocation where the spell is about to land, or null for
     *                       self/omni-target effects
     */
    public static WindUp begin(Spell spell, Color color, LivingEntity caster, int windUpTicks, Location markerLocation) {
        return new WindUp(spell, caster, windUpTicks,
                markerLocation == null ? null : markerLocation.clone(), color);
    }

    /**
     * The framework-owned wind-up for a circle 6+ cast. Its duration is the
     * <em>computed</em> cast delay (wand and mastery already applied), so the
     * telegraph and the payload agree. Callers register this with
     * {@link ChannelRegistry}; spell implementations must not add another.
     */
    public static WindUp forFrameworkCast(Spell spell, Player caster, long castTicks) {
        return begin(spell, caster, (int) Math.max(1L, castTicks));
    }

    /**
     * Advances the wind-up one scheduler tick, drawing this frame. Does nothing
     * once the wind-up is cancelled or complete, so a caller may keep ticking
     * until {@link #isComplete()} without guarding.
     */
    public void tick() {
        if (cancelled || isComplete()) return;
        float progress = progress();
        drawCharge(progress);
        if (marker != null) {
            drawMarker(progress);
        }
        elapsed++;
    }

    /**
     * Charge progress from 0.0 to 1.0, clamped. A non-positive duration reads as
     * 1.0 immediately rather than dividing by zero.
     */
    public float progress() {
        if (totalTicks <= 0) return 1.0f;
        return Math.min(1.0f, Math.max(0.0f, (float) elapsed / totalTicks));
    }

    /** Ticks left before the payload is due. Never negative. */
    public int remainingTicks() {
        return Math.max(0, totalTicks - elapsed);
    }

    /** The caller's cue to fire the payload. Flips exactly on the final {@link #tick()}. */
    public boolean isComplete() {
        return elapsed >= totalTicks;
    }

    /** True once {@link #cancel(String)} has run; the payload must not fire after this. */
    public boolean isCancelled() {
        return cancelled;
    }

    /** Where the spell is about to land, or null when there is no marker. */
    public Location markerLocation() {
        return marker == null ? null : marker.clone();
    }

    /** Where the caster stood when the wind-up began - the movement-interrupt baseline. */
    public Location origin() {
        return origin.clone();
    }

    /** The spell being wound up. */
    public Spell spell() {
        return spell;
    }

    /** The charging entity. */
    public LivingEntity caster() {
        return caster;
    }

    /**
     * Interrupts the wind-up: it can never complete, and the caster is told why.
     * <b>Mana is not refunded</b> - see the class Javadoc.
     *
     * @param langKey language key for the interruption message
     */
    public void cancel(String langKey) {
        if (cancelled) return;
        cancelled = true;
        if (!(caster instanceof Player player)) return;
        try {
            if (Alkatraz.getLangManager() != null) {
                Utils.sendActionBar(player, Alkatraz.getLangManager().get(langKey));
            }
            if (player.getWorld() != null) {
                player.getWorld().playSound(player.getLocation(),
                        org.bukkit.Sound.BLOCK_DISPENSER_FAIL, 0.6f, 0.6f);
            }
        } catch (RuntimeException ignored) {
            // An interruption must never throw back into a Bukkit event handler.
        }
    }

    /** Interrupts using the shared {@code spells.cast.interrupted} message. */
    public void cancel() {
        cancel(INTERRUPTED_LANG_KEY);
    }

    // ================================================================
    // Policy
    // ================================================================

    /**
     * Whether this spell owes a wind-up. False for circles 0-5 and for the three
     * documented exemptions at their own circles, true otherwise. This is the guard
     * the wave-C spell migrations assert against, so it must not drift.
     */
    public static boolean requiresWindUp(Spell spell) {
        if (spell == null) return false;
        return requiresWindUp(spell.getId(), spell.getRequiredCircleLevel());
    }

    /**
     * The id-and-circle half of {@link #requiresWindUp(Spell)}, split out so the
     * policy is testable without a live {@link Spell} instance.
     */
    public static boolean requiresWindUp(String spellId, int circle) {
        if (circle < 6) return false;
        Integer exemptCircle = spellId == null ? null : EXEMPT_FROM_WIND_UP.get(spellId);
        return exemptCircle == null || exemptCircle != circle;
    }

    /**
     * Visual scale for a circle, so a circle-9 wind-up is visibly larger and more
     * intense than a circle-6 one. Circle 6 is the 1.0 baseline and the ceiling is
     * 1.75 at circle 9. The ladder is driven by {@link CastTimeline#particleBudget(int)}
     * so the wind-up's intensity tracks the cast layers' particle budget instead of
     * drifting from it: sub-threshold circles clamp to the baseline, and out-of-range
     * circles clamp at the circle-9 ceiling.
     */
    public static float intensityScale(int circle) {
        int base = CastTimeline.particleBudget(6);
        int top = CastTimeline.particleBudget(9);
        int budget = CastTimeline.particleBudget(circle);
        if (budget <= base || top <= base) return 1.0f;
        float ratio = (float) (budget - base) / (float) (top - base);
        return 1.0f + Math.min(1.0f, ratio) * 0.75f;
    }

    /**
     * Mastery discount, matching the {@code SkeletalKnightsConfig.windUpTicks}
     * precedent: 25% off at max mastery. A spell with {@code maximum_mastery} of 0
     * has no mastery track, so it never shortens - otherwise every 0-mastery spell
     * would get a free discount.
     */
    public static int effectiveTicks(int ticks, int mastery, int maxMastery) {
        if (maxMastery <= 0 || mastery < maxMastery) return Math.max(0, ticks);
        return Math.max(1, (int) (ticks * 0.75D));
    }

    private static int masteryOf(Spell spell, LivingEntity caster) {
        if (!(caster instanceof Player player)) return 0;
        try {
            MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
            return profile == null ? 0 : profile.getSpellMastery(spell);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /**
     * How far a channelling caster may stray, in blocks, before the wind-up is
     * interrupted. Read from the caster's spell yml when it declares
     * {@code move_cancel_threshold}, else {@value #DEFAULT_MOVE_CANCEL_THRESHOLD}.
     *
     * <p>A config read that fails for any reason falls back to the default rather
     * than throwing, because this is called from a movement event.
     */
    public static double moveCancelThreshold(Spell spell) {
        if (spell == null || spell.getId() == null) return DEFAULT_MOVE_CANCEL_THRESHOLD;
        try {
            YamlConfiguration yml = ConfigManager.getDefault("spells/" + spell.getId() + ".yml");
            if (yml != null && yml.contains("move_cancel_threshold")) {
                return yml.getDouble("move_cancel_threshold", DEFAULT_MOVE_CANCEL_THRESHOLD);
            }
        } catch (RuntimeException ignored) {
            // Fall through to the default.
        }
        return DEFAULT_MOVE_CANCEL_THRESHOLD;
    }

    // ================================================================
    // Visuals
    // ================================================================

    /** The caster's palette, resolved once when the wind-up began and never rebuilt. */
    private ElementPalette.Palette palette() {
        return palette;
    }

    /**
     * The per-circle charge profile: circle 6 is the legacy base look; circles 7-9
     * carry their signature charge visuals. Read by {@link #drawCharge} and
     * {@link #drawMarker} via {@code profile(spell.getRequiredCircleLevel())}.
     */
    private static ChargeProfile profile(int circle) {
        return ChargeProfile.of(circle);
    }

    /**
     * The charge itself: a shell of {@code core} dust closing on the caster's eyes,
     * wrapped in {@code accent} rings, with {@code deep} structure that only appears
     * past the halfway point. Particle count and radius both climb with progress, so
     * the last frame before release is unmistakably the loudest.
     */
    private void drawCharge(float progress) {
        World world = caster.getWorld();
        if (world == null) return;
        Location eye = caster.getEyeLocation();
        if (eye == null) return;

        ElementPalette.Palette palette = palette();
        int circle = spell == null ? 6 : spell.getRequiredCircleLevel();
        ChargeProfile prof = profile(circle);
        float scale = intensityScale(circle) * prof.intensity();
        double ease = progress * progress;

        int leadingEdge = (int) Math.round((14 + 22 * ease) * scale);
        double radius = (0.5 + 0.85 * progress) * scale;
        double sink = 0.12 + 0.5 * progress;
        // Circle 8: the charge itself rises while spinning.
        double rise = prof.rising() ? scale * 0.6 * progress : 0;
        Location scratch = eye.clone();
        for (int i = 0; i < leadingEdge; i++) {
            double angle = 2 * Math.PI * i / leadingEdge + elapsed * 0.35;
            double r = radius * (0.75 + 0.25 * ((i % 4) / 3.0));
            scratch.setX(eye.getX() + Math.cos(angle) * r);
            scratch.setY(eye.getY() - sink + Math.sin(angle * 2.0) * 0.2 + rise);
            scratch.setZ(eye.getZ() + Math.sin(angle) * r);
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
        }

        // Trailing accent rings, spinning the other way, widening as it charges.
        // Circle 7 adds an extra ambient ring to the set.
        int rings = (int) Math.round(2 + 2 * progress) + prof.rings();
        for (int ring = 0; ring < rings; ring++) {
            double ringR = (0.35 + ring * 0.32 + 0.4 * progress) * scale;
            double spin = -elapsed * (0.12 + ring * 0.03) * prof.spin();
            int points = (int) Math.round((10 + 8 * progress) * scale);
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points + spin;
                scratch.setX(eye.getX() + Math.cos(angle) * ringR);
                scratch.setY(eye.getY() - sink * 0.6 + Math.sin(angle) * ringR * 0.35);
                scratch.setZ(eye.getZ() + Math.sin(angle) * ringR);
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.accentDust());
            }
        }

        // Circle 7: a helix of accent strands rising out of the charge, climbing
        // `helixHeight` by one `helixStep`-tick step as the wind-up commits.
        if (prof.helix()) {
            int strands = 8;
            double climb = Math.min(prof.helixHeight(),
                    (elapsed / (double) Math.max(1, prof.helixStep())) * 0.4);
            for (int i = 0; i < strands; i++) {
                double angle = 2 * Math.PI * i / strands + elapsed * 0.35;
                double r = radius * 0.6;
                scratch.setX(eye.getX() + Math.cos(angle) * r);
                scratch.setY(eye.getY() - sink + climb + (i / (double) strands) * 0.35);
                scratch.setZ(eye.getZ() + Math.sin(angle) * r);
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.accentDust());
            }
        }

        // Deep structure only past the halfway point: the wind-up visibly commits.
        if (progress >= 0.5) {
            double deep = (progress - 0.5) * 2.0;
            int spokes = (int) Math.round((4 + 6 * deep) * scale);
            double spokeR = (0.7 + 0.9 * progress) * scale;
            for (int i = 0; i < spokes; i++) {
                double angle = 2 * Math.PI * i / spokes + elapsed * 0.18;
                scratch.setX(eye.getX() + Math.cos(angle) * spokeR);
                scratch.setY(eye.getY() - sink);
                scratch.setZ(eye.getZ() + Math.sin(angle) * spokeR);
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.deepDust());
            }
            ParticleCaster.spawn(caster, world, Utils.DUST, eye, (int) Math.round(deep * 6 * scale),
                    0.15, 0.05, 0.15, palette.coreDust());
        }

        // Circle 9: once the charge is past the 0.85 radius, shock rings expand
        // outward at 0.85/0.95/1.05 of it and a bright accent core blooms at the heart.
        if (prof.burst() && radius > 0.85 * scale) {
            for (double f : new double[]{0.85, 0.95, 1.05}) {
                double ringR = radius * f;
                int ringPoints = (int) Math.round((10 + 8 * progress) * scale);
                for (int i = 0; i < ringPoints; i++) {
                    double angle = 2 * Math.PI * i / ringPoints + elapsed * 0.35 + f * 20;
                    scratch.setX(eye.getX() + Math.cos(angle) * ringR);
                    scratch.setY(eye.getY() - sink + Math.sin(angle) * ringR * 0.35);
                    scratch.setZ(eye.getZ() + Math.sin(angle) * ringR);
                    ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.accentDust());
                }
            }
            ParticleCaster.spawn(caster, world, Utils.DUST, eye, 3, palette.accentDust());
        }
    }

    /**
     * The landing marker: an escalating ground glyph at the resolved target. Faint
     * and thin at the start, a solid multi-ring target by the end - the dodge cue.
     */
    private void drawMarker(float progress) {
        World world = marker.getWorld();
        if (world == null) return;
        ElementPalette.Palette palette = palette();
        int circle = spell == null ? 6 : spell.getRequiredCircleLevel();
        ChargeProfile prof = profile(circle);
        float scale = intensityScale(circle) * prof.intensity();

        double outer = (0.9 + 2.4 * progress) * scale;
        double inner = outer * (0.55 + 0.25 * progress);
        Location scratch = marker.clone();
        int points = (int) Math.round((16 + 20 * progress) * scale);
        double spin = elapsed * 0.05 * prof.spin();

        for (int ring = 0; ring < 2; ring++) {
            double ringR = ring == 0 ? outer : inner;
            int ringPoints = ring == 0 ? points : Math.max(6, points / 2);
            double direction = ring == 0 ? spin : -spin * 1.4;
            for (int i = 0; i < ringPoints; i++) {
                double angle = 2 * Math.PI * i / ringPoints + direction;
                scratch.setX(marker.getX() + Math.cos(angle) * ringR);
                scratch.setY(marker.getY() + 0.05 + ring * 0.15);
                scratch.setZ(marker.getZ() + Math.sin(angle) * ringR);
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0,
                        ring == 0 ? palette.coreDust() : palette.accentDust());
            }
        }

        if (progress >= 0.5) {
            double fill = (progress - 0.5) * 2.0;
            ParticleCaster.spawn(caster, world, Utils.DUST, marker, (int) Math.round(4 + 14 * fill * scale),
                    outer * 0.5, 0.02, outer * 0.5, palette.deepDust());
        }
    }

    /**
     * Per-circle charge signature: {@code spin} scales the ring spin,
     * {@code intensity} multiplies the ladder scale, {@code rings} adds ambient
     * rings, and {@code helix}/{@code rising}/{@code burst} enable the circle 7-9
     * effects (helix strands and their height/step, the rising charge, the outward
     * shock rings). Circle 6 is the legacy base look: {@code spin} 1.0, no extras.
     * All visuals still route through {@link ElementPalette} colours.
     */
    private record ChargeProfile(
            float spin,
            float intensity,
            int rings,
            boolean helix,
            double helixHeight,
            int helixStep,
            boolean rising,
            boolean burst
    ) {
        static ChargeProfile of(int circle) {
            switch (Math.max(6, Math.min(9, circle))) {
                case 7: return new ChargeProfile(1.30f, 1.0f, 1, true, 1.6, 8, false, false);
                case 8: return new ChargeProfile(1.15f, 1.0f, 0, false, 0.0, 0, true, false);
                case 9: return new ChargeProfile(1.0f, 1.2f, 0, false, 0.0, 0, false, true);
                default: return new ChargeProfile(1.0f, 1.0f, 0, false, 0.0, 0, false, false);
            }
        }
    }
}