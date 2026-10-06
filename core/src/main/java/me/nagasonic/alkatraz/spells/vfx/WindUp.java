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
 *   <li>{@link #isComplete()} is the caller's cue to fire the payload. A wind-up
 *       only ever draws and counts - it never fires the effect itself.</li>
 *   <li>The wind-up never fires its payload once {@link #cancel(String)} has run.</li>
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
     * Spell id to circle for every spell that is <b>not</b> required to wind up,
     * each with its reason. A circle 6+ spell whose id is not in here owes a
     * wind-up; an id in here is exempt <i>only</i> at the circle it is listed at,
     * so moving an exempt spell down the ladder brings it back under the rule.
     *
     * <ul>
     *   <li>{@code waystone_recall} (7) - instantness <i>is</i> its identity; it
     *       exists specifically to replace {@code warp}'s 6-second channel, so
     *       making it channel would undo its reason to exist.</li>
     *   <li>{@code echo_cast} (8) - calls {@code castAction} directly, bypassing
     *       {@code Spell.cast}. Each replayed spell carries its own wind-up, so a
     *       wrapper telegraph would be a double-tell.</li>
     *   <li>{@code arcane_resonance} (9) - amplifies other casts rather than
     *       acting; the amplified spells are the ones that wind up.</li>
     * </ul>
     *
     * <p>{@code warp} and {@code skeletal_knights} (both circle 6) are
     * deliberately <b>not</b> exempt: they keep their 6-second channels as
     * declared paid-overs, because {@code waystone_recall} only makes sense as a
     * skip for something.
     */
    public static final Map<String, Integer> EXEMPT_FROM_WIND_UP;

    static {
        Map<String, Integer> exempt = new HashMap<>();
        exempt.put("waystone_recall", 7);
        exempt.put("echo_cast", 8);
        exempt.put("arcane_resonance", 9);
        EXEMPT_FROM_WIND_UP = Collections.unmodifiableMap(exempt);
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
     * intense than a circle-6 one. Circle 6 is the 1.0 baseline; the ladder climbs
     * to 1.75 at circle 9. Sub-threshold circles clamp to the baseline, and
     * out-of-range circles clamp at the circle-9 ceiling.
     */
    public static float intensityScale(int circle) {
        if (circle <= 6) return 1.0f;
        return Math.min(1.75f, 1.0f + 0.25f * (circle - 6));
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
        float scale = intensityScale(spell == null ? 6 : spell.getRequiredCircleLevel());
        double ease = progress * progress;

        int leadingEdge = (int) Math.round((14 + 22 * ease) * scale);
        double radius = (0.5 + 0.85 * progress) * scale;
        double sink = 0.12 + 0.5 * progress;
        Location scratch = eye.clone();
        for (int i = 0; i < leadingEdge; i++) {
            double angle = 2 * Math.PI * i / leadingEdge + elapsed * 0.35;
            double r = radius * (0.75 + 0.25 * ((i % 4) / 3.0));
            scratch.setX(eye.getX() + Math.cos(angle) * r);
            scratch.setY(eye.getY() - sink + Math.sin(angle * 2.0) * 0.2);
            scratch.setZ(eye.getZ() + Math.sin(angle) * r);
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
        }

        // Trailing accent rings, spinning the other way, widening as it charges.
        int rings = (int) Math.round(2 + 2 * progress);
        for (int ring = 0; ring < rings; ring++) {
            double ringR = (0.35 + ring * 0.32 + 0.4 * progress) * scale;
            double spin = -elapsed * (0.12 + ring * 0.03);
            int points = (int) Math.round((10 + 8 * progress) * scale);
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points + spin;
                scratch.setX(eye.getX() + Math.cos(angle) * ringR);
                scratch.setY(eye.getY() - sink * 0.6 + Math.sin(angle) * ringR * 0.35);
                scratch.setZ(eye.getZ() + Math.sin(angle) * ringR);
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
    }

    /**
     * The landing marker: an escalating ground glyph at the resolved target. Faint
     * and thin at the start, a solid multi-ring target by the end - the dodge cue.
     */
    private void drawMarker(float progress) {
        World world = marker.getWorld();
        if (world == null) return;
        ElementPalette.Palette palette = palette();
        float scale = intensityScale(spell == null ? 6 : spell.getRequiredCircleLevel());

        double outer = (0.9 + 2.4 * progress) * scale;
        double inner = outer * (0.55 + 0.25 * progress);
        Location scratch = marker.clone();
        int points = (int) Math.round((16 + 20 * progress) * scale);
        double spin = elapsed * 0.05;

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
}