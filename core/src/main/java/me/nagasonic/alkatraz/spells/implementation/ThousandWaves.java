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
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 8 WATER burst, channeled. The caster holds for two seconds while a fan of water
 * gathers in front of them, then lets it go: eight crests peel out of the same pool, one
 * every seven ticks, each fanning into its own 45-degree lane and each hitting harder than
 * the last.
 *
 * <h2>Why {@code hit_count} is 1 and not 8</h2>
 * The budget is the constraint. {@code SpellBudgetTest} computes {@code power x hit_count}
 * against {@code burstBudget(8) = 64.1}, and {@code power} is the section 6 ladder value
 * 22.7, so {@code hit_count} may not exceed 2.82. A crest that traverses a corridor crosses
 * <i>every</i> stationary body in it, so eight crests down one lane would declare
 * {@code hit_count: 8} and 181.6 damage - nearly three times the budget.
 *
 * <p>The fan solves it <b>geometrically</b> rather than by weakening the assertion. Eight
 * angularly disjoint lanes mean a body can only ever stand in one of them, and only one
 * crest is ever in a given lane, so the honest count really is 1:
 *
 * <pre>
 * lane angle = 2 * atan((wave_width / 2) / wave_range) = 2 * atan(4.5 / 14) = 35.638 deg
 * sector    = 45.000 deg    8 sectors x 45 = 360, and 35.638 &lt; 45 leaves a 9.362 deg gutter
 * wave_interval_ticks x wave_speed = 7 x 2.0 = 14.0 = wave_range
 * </pre>
 *
 * The last identity is what guarantees a lane is never empty for part of the release and
 * doubly occupied for the rest. All three are asserted by {@code ThousandWavesTest}.
 *
 * <p>The far-field lane angle above is the <em>worst</em> case for separation, not the only
 * one: a fixed-width lane is angularly narrow at 14 blocks and angularly wide at 2, so a
 * body standing on a boundary between two lanes is inside both of their damage boxes up
 * close. {@link #laneOf} therefore assigns every body to its nearest lane rather than
 * testing each lane against it, which is what makes "one lane, one crest, one hit" exact at
 * every range instead of approximately true.
 *
 * <h2>The escalation is between lanes, not stacked on one victim</h2>
 * Crest {@code i} deals {@code power x wave_escalation[i]}. The multipliers run 0.10 to
 * 0.58 - a 5.8x climb - and the whole field across eight distinct bodies totals
 * {@code 22.7 x 2.75 = 62.425}, 97% of the circle-8 budget. So the spell is broad and
 * escalating without any single pair of boots taking a number that would break the ladder.
 *
 * <h2>The channel deals no damage at all</h2>
 * The wind-up runs through {@link WindUp} and interruption belongs to {@link ChannelRegistry},
 * exactly as {@code warp}, {@code skeletal_knights} and {@code sunfire_barrage} do - not a
 * private channel map plus an onMove/onDamage/onQuit handler triple.
 * {@code move_cancel_threshold} is still this spell's own yml key: {@link WindUp} reads it
 * and {@link ChannelRegistry} consults it. There is no
 * {@code AttackProperties} during the wind-up, so a cancelled cast can never produce a
 * stray hit - it costs the caster 450 mana and deals zero.
 *
 * <p>No {@code TerrainEditor}, no block write, no {@code SpellBlockComponent}: the fan's
 * whole geometry is positional, which is water's "moves, never reshapes" exclusion
 * respected at the design level rather than only at the code level.
 */
public class ThousandWaves extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);

    /** The crest front itself. */
    private static final Particle.DustOptions FOAM_DUST = PALETTE.coreDust();
    /** The glyph markers and column during the gather. */
    private static final Particle.DustOptions GLYPH_DUST = PALETTE.accentDust();
    /** The gather's floor ring, a shade back so it never hides the crest. */
    private static final Particle.DustOptions GATHER_DUST = PALETTE.deepDust();

    /** Points on the gather's floor ring. */
    private static final int FLOOR_RING_POINTS = 24;
    /** Hex glyphs during the gather - eight, so the player can count the waves coming. */
    private static final int GATHER_GLYPHS = 8;
    /** Points in the rising column in front of the caster. */
    private static final int COLUMN_POINTS = 10;
    /** How high that column climbs, in blocks. */
    private static final double COLUMN_HEIGHT = 2.4;
    /** The channel's ambient splash cadence, in ticks. */
    private static final int GATHER_SOUND_EVERY = 10;
    /** Points drawn across one crest's band. */
    private static final int CREST_POINTS = 18;
    /** Vertical half-extent of the sweep box, so a crest can catch a body on a ledge. */
    private static final double SWEEP_HEIGHT = 3.0;
    /** Bubbles trailing a crest every fourth tick. */
    private static final int CREST_BUBBLE_EVERY = 4;
    /** One crest sound every third tick, pitched so the fan audibly rises. */
    private static final int CREST_SOUND_EVERY = 3;
    /** Half the pitch step between adjacent crests. */
    private static final double CREST_PITCH_STEP = 0.06;

    private int channelSeconds;
    private double durationSeconds;
    private int waveCount;
    private int waveIntervalTicks;
    private double waveSpeed;
    private double waveRange;
    private double waveWidth;
    private double waveSectorDegrees;
    private double[] waveEscalation;
    private double wavePush;
    private double waveLift;

    public ThousandWaves(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * The escalation curve this fan runs on when the yml's is missing, empty, or contains a
     * non-number. The eight values sum to 2.75, which is what keeps the worst-case field at
     * 62.425 against a 64.1 budget.
     */
    private static final double[] DEFAULT_ESCALATION =
            {0.10, 0.17, 0.24, 0.31, 0.38, 0.45, 0.52, 0.58};

    /**
     * Circle 8 shape, read inline. No {@code saveConfig} call and no config record: every
     * knob below feeds straight into {@code hit_count} or into the budget arithmetic, so
     * selling any of them as an option would mean {@code SpellBudgetTest} was asserting a
     * number the player could invalidate from a menu.
     */
    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/thousand_waves.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/thousand_waves.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.channelSeconds = Math.max(1, spellConfig.getInt("channel_seconds", 2));
        this.durationSeconds = spellConfig.getDouble("duration_seconds", 3.0);
        this.waveCount = Math.max(1, spellConfig.getInt("wave_count", 8));
        this.waveIntervalTicks = Math.max(1, spellConfig.getInt("wave_interval_ticks", 7));
        this.waveSpeed = spellConfig.getDouble("wave_speed", 2.0);
        this.waveRange = spellConfig.getDouble("wave_range", 14.0);
        this.waveWidth = spellConfig.getDouble("wave_width", 9.0);
        this.waveSectorDegrees = spellConfig.getDouble("wave_sector_degrees", 45.0);
        this.waveEscalation = doublesOf(spellConfig.get("wave_escalation"), DEFAULT_ESCALATION);
        this.wavePush = spellConfig.getDouble("wave_push", 0.55);
        this.waveLift = spellConfig.getDouble("wave_lift", 0.3);
    }

    /** Falls back to {@code def} whenever the list is absent, empty, or holds a non-number. */
    private static double[] doublesOf(Object raw, double[] def) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return def;
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Number n)) return def;
            out[i] = n.doubleValue();
        }
        return out;
    }

    /**
     * The gather's length, honouring the 25% max-mastery discount every channeled spell in
     * the roster applies.
     */
    private int channelTicks(int mastery, int maxMastery) {
        double multiplier = (maxMastery > 0 && mastery >= maxMastery) ? 0.75 : 1.0;
        return Math.max(1, (int) Math.round(channelSeconds * 20.0 * multiplier));
    }

    /** How long the release runs, in ticks. */
    private int releaseTicks() {
        return Math.max(1, (int) Math.round(durationSeconds * 20.0));
    }

    /** Index-safe escalation read - a short list clamps to its last entry rather than throwing. */
    private double escalationAt(int index) {
        if (waveEscalation.length == 0) return 1.0;
        return waveEscalation[Math.min(Math.max(index, 0), waveEscalation.length - 1)];
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) return;
        UUID uuid = caster.getUniqueId();
        if (ChannelRegistry.isChanneling(uuid)) return;       // no double channels

        int windUpTicks = channelTicks(profile.getSpellMastery(this), getMaxMastery());
        WindUp windUp = WindUp.begin(this, caster, windUpTicks);

        // ChannelRegistry owns interruption; this class owns its repeating task. The task id
        // is captured in a holder so the canceller stays effectively final.
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
                    releaseFan(caster, wand, getWandPower(wand, caster));
                    return;
                }
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    return;
                }
                windUp.tick();
                animateChannel(caster, ticks, windUpTicks);
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L).getTaskId();
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Player-only: an interruptible channel needs a move/damage signal a mob does not
        // produce. Mobs get the release directly instead.
        if (caster.isDead() || caster.getTarget() == null) return;
        releaseFan(caster, wand, getWandPowerOrDefault(wand), caster.getTarget().getLocation(),
                caster.getTarget().getLocation().getYaw());
    }

    /**
     * The whole fan, shared by both cast paths. ONE {@link AttackProperties} is created here
     * and captured for all eight crests, so a single counter kills the entire volley -
     * which is rule 5 of the damage contract, and the reason the escalation cannot be
     * laundered into a per-crest properties.
     *
     * @param caster    the casting entity
     * @param wand      the casting wand, or null on a mob cast
     * @param wandPower the wand scaling factor, applied once when the properties are seeded
     */
    private void releaseFan(Player caster, ItemStack wand, double wandPower) {
        releaseFan(caster, wand, wandPower, caster.getLocation().clone(),
                caster.getEyeLocation().getYaw());
    }

    private void releaseFan(LivingEntity caster, ItemStack wand, double wandPower,
                            Location origin, float baseYaw) {
        AttackProperties props = new AttackProperties(
                caster, Utils.castLocation(caster), getBasePower() * wandPower, AttackType.MAGIC);

        new BukkitRunnable() {
            int ticks = 0;
            int emitted = 0;
            final List<Crest> live = new ArrayList<>();

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || ticks >= releaseTicks()) {
                    caster.getWorld().playSound(origin, Sound.BLOCK_WATER_AMBIENT, 1.0f, 0.4f);
                    cancel();
                    return;
                }
                if (caster.isDead()
                        || (caster instanceof Player player && !player.isOnline())) {
                    cancel();
                    return;
                }

                if (ticks % waveIntervalTicks == 0 && emitted < waveCount) {
                    live.add(new Crest(emitted));
                    announce(caster, emitted, origin, baseYaw);
                    emitted++;
                }

                Iterator<Crest> it = live.iterator();
                while (it.hasNext()) {
                    Crest crest = it.next();
                    crest.distance += waveSpeed;
                    if (crest.distance > waveRange) {
                        it.remove();
                        continue;
                    }
                    sweepCrest(crest, caster, wand, props, origin, baseYaw);
                    if (ticks % 2 == 0) drawCrest(crest, caster, origin, baseYaw, ticks);
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One crest's damage window. This is the only place in the spell that deals anything.
     *
     * <p>Two clips confine a crest, and both are load-bearing for {@code hit_count: 1}.
     * {@code getNearbyEntities} returns a square box, so the lateral clip is what stops a
     * crest reaching a body {@code wave_width} blocks off its own axis at long range. But a
     * lateral clip alone is <b>not</b> enough near the caster: at four blocks out, a lane is
     * 132 degrees wide and a body standing on the boundary between two lanes sits within
     * {@code wave_width / 2} of <i>both</i> axes, so it would be swept by two crests and
     * {@code hit_count: 1} would be a lie. {@link #laneOf} closes that: a crest only ever
     * touches bodies whose <em>nearest</em> lane is its own, which is exact rather than
     * approximate - the lane index is a pure function of the body's angle, so a body on a
     * bisector resolves to the same single lane every frame, never to two.
     */
    private void sweepCrest(Crest crest, LivingEntity caster, ItemStack wand, AttackProperties props,
                            Location origin, float baseYaw) {
        Vector dir = crestDirection(crest.index, baseYaw);
        Vector perp = new Vector(-dir.getZ(), 0.0, dir.getX()).normalize();
        Location bandCentre = origin.clone().add(dir.clone().multiply(crest.distance));
        double halfWidth = waveWidth / 2.0;
        double multiplier = escalationAt(crest.index);

        for (LivingEntity le : Utils.getNearbyLivingEntities(
                bandCentre, halfWidth, SWEEP_HEIGHT, halfWidth)) {
            if (le.equals(caster) || le.isDead() || !le.isValid()) continue;
            Vector rel = le.getLocation().toVector().subtract(origin.toVector()).setY(0.0);
            if (Math.abs(rel.dot(perp)) > halfWidth) continue;      // the box is square - clip to the lane
            double along = rel.dot(dir);
            if (along < 0.0 || along > waveRange) continue;
            if (laneOf(rel, baseYaw) != crest.index) continue;     // lanes are angularly disjoint
            if (!crest.hit.add(le.getUniqueId())) continue;        // one crest, one hit per body

            SpellDamageUtil.damageWithSpell(le,
                    getPower(caster, le, props.getInitialPower() * multiplier),   // ONE getPower
                    caster, wand, this);
            props.hit(le);
            le.setVelocity(le.getVelocity().add(dir.clone().multiply(wavePush).setY(waveLift)));
            ParticleCaster.spawn(caster, le.getWorld(), Utils.SPLASH,
                    le.getLocation().add(0, 0.4, 0), 12, 0.4, 0.5, 0.4, 0);
        }
    }

    /**
     * The gather, every tick of the channel: a widening floor ring, eight hex glyphs so the
     * player can count what is about to happen, and a column rising in front of them.
     * Particles and one sound only - this deals nothing.
     */
    private void animateChannel(Player caster, int ticks, int windUpTicks) {
        double p = (double) ticks / Math.max(1, windUpTicks);
        double ringRadius = 1.0 + 1.6 * p;
        Location feet = caster.getLocation();

        for (int i = 0; i < FLOOR_RING_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / FLOOR_RING_POINTS + ticks * 0.25;
            Location point = feet.clone().add(
                    Math.cos(angle) * ringRadius, 0.1, Math.sin(angle) * ringRadius);
            ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, GATHER_DUST);
        }
        for (int k = 0; k < GATHER_GLYPHS; k++) {
            double angle = 2.0 * Math.PI * k / GATHER_GLYPHS - ticks * 0.12;
            Location point = feet.clone().add(
                    Math.cos(angle) * ringRadius * 0.8, 0.4, Math.sin(angle) * ringRadius * 0.8);
            ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, GLYPH_DUST);
        }

        Location eye = caster.getEyeLocation();
        World world = eye.getWorld();
        Vector facing = Utils.safeNormalize(eye.getDirection());
        if (facing != null) {
            Location base = eye.clone().add(facing.multiply(1.6));
            for (int i = 0; i < COLUMN_POINTS; i++) {
                double angle = 2.0 * Math.PI * i / COLUMN_POINTS + ticks * 0.3;
                Location point = base.clone().add(
                        Math.cos(angle) * 0.3, COLUMN_HEIGHT * i / (COLUMN_POINTS - 1.0),
                        Math.sin(angle) * 0.3);
                ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, FOAM_DUST);
            }
        }

        if (ticks % GATHER_SOUND_EVERY == 0) {
            caster.getWorld().playSound(feet, Sound.BLOCK_WATER_AMBIENT, 0.4f,
                    (float) (0.6 + 0.4 * p));
        }
    }

    /** One crest's face, repainted every second tick. */
    private void drawCrest(Crest crest, LivingEntity caster, Location origin, float baseYaw, int ticks) {
        Vector dir = crestDirection(crest.index, baseYaw);
        Vector perp = new Vector(-dir.getZ(), 0.0, dir.getX()).normalize();
        Location bandCentre = origin.clone().add(dir.clone().multiply(crest.distance));
        double halfWidth = waveWidth / 2.0;
        World world = bandCentre.getWorld();

        for (int i = 0; i < CREST_POINTS; i++) {
            double offset = (i / (double) (CREST_POINTS - 1) * 2.0 - 1.0) * waveWidth;
            Location point = bandCentre.clone().add(perp.clone().multiply(offset));
            point.add(0, 0.5 + Math.sin(ticks * 0.3 + i * 0.4) * 0.5, 0);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, FOAM_DUST);
        }

        ParticleCaster.spawn(caster, world, Utils.SPLASH, bandCentre, 14, waveWidth, 0.5, 0.5, 0);
        if (ticks % CREST_BUBBLE_EVERY == 0) {
            ParticleCaster.spawn(caster, world, Utils.BUBBLE_COLUMN_UP, bandCentre, 10,
                    halfWidth, 0.4, halfWidth, 0);
        }
        if (ticks % CREST_SOUND_EVERY == 0) {
            world.playSound(bandCentre, Sound.ENTITY_DROWNED_SWIM, 1.2f,
                    (float) (0.5 + CREST_PITCH_STEP * crest.index));
        }
    }

    /** The puff at a lane's mouth as its crest leaves, so the fan announces itself. */
    private void announce(LivingEntity caster, int index, Location origin, float baseYaw) {
        Vector dir = crestDirection(index, baseYaw);
        Location mouth = origin.clone().add(dir.multiply(1.0)).add(0, 0.5, 0);
        ParticleCaster.spawn(caster, mouth.getWorld(), Utils.CLOUD, mouth, 20, 0.5, 0.5, 0.5, 0);
        mouth.getWorld().playSound(mouth, Sound.BLOCK_WATER_AMBIENT, 0.6f,
                (float) (0.8 + 0.02 * index));
    }

    /**
     * Lane {@code index}'s heading, in the caster's yaw frame. Adjacent lanes are
     * {@code wave_sector_degrees} apart, and {@code wave_count x wave_sector_degrees == 360}
     * is what closes the fan with no dead angle and no overlap.
     */
    private Vector crestDirection(int index, float baseYaw) {
        double yaw = Math.toRadians(baseYaw + index * waveSectorDegrees);
        return new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /**
     * Which lane owns a body: the lane whose axis is nearest to it, measured in the caster's
     * yaw frame.
     *
     * <p>This is what makes "one lane, one crest, one hit" true at <em>every</em> range
     * rather than only at {@code wave_range}. The angular width of a fixed-width lane grows
     * without bound as the body approaches the caster - 35.64 degrees at 14 blocks, 132 at 2 -
     * so a purely lateral test lets two neighbouring lanes claim the same body
     * up close. Rounding to the nearest sector instead assigns it to exactly one, and
     * {@code wave_count x wave_sector_degrees == 360} is what guarantees the sector indices
     * are a bijection rather than an unbounded ladder.
     *
     * <p>A body exactly on a bisector is not a special case, and does not need one: the
     * wrap is done in whole degrees, so {@code offset / waveSectorDegrees} can never land
     * on a half value and {@link Math#round} is never asked to break a tie. Quantising first
     * makes the assignment deterministic by construction rather than by a rounding rule.
     *
     * @param rel     the body's offset from the fan's origin, flattened to the xz plane
     * @param baseYaw the fan's anchor yaw, in degrees
     * @return the owning lane index, from 0 to {@code waveCount - 1}
     */
    private int laneOf(Vector rel, float baseYaw) {
        // Wrapped in WHOLE DEGREES on purpose. Math.floorMod only has integral overloads -
        // (int,int), (long,int) and (long,long) - so a floating-point wrap has to be expressed
        // with one of them, and a degree is two orders of magnitude finer than the 45-degree
        // sector, so quantising here cannot move a body out of its lane. floorMod rather than
        // %, because a body behind the caster produces a negative angle and % would hand back a
        // negative sector index.
        long degrees = Math.round(Math.toDegrees(Math.atan2(-rel.getX(), rel.getZ())) - baseYaw);
        long offset = Math.floorMod(degrees + 180L, 360L) - 180L;
        return Math.floorMod((int) Math.round(offset / waveSectorDegrees), waveCount);
    }

    /**
     * One crest in flight. Deliberately not a record: {@code distance} advances every tick
     * and {@code hit} grows as it sweeps.
     */
    private static final class Crest {

        private final int index;
        private double distance;
        private final Set<UUID> hit = new HashSet<>();

        Crest(int index) {
            this.index = index;
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        // Size 4, so the circle-8 gather reads larger than a circle-6 cast circle.
        return CastCircle.start(this, caster, e, 4.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SPLASH, location, 40, 0.5, 0.5, 0.5, 0.2);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.BUBBLE_POP, location, 20, 0.5, 0.5, 0.5, 0);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.SPLASH, location, 60, 1, 1, 1, 0.5);
        ParticleCaster.spawn(null, location.getWorld(), Utils.BUBBLE_COLUMN_UP, location, 30, 1, 1, 1, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.thousandwaves.book_name"))
                .addCustomLoreLine(lang().get("spells.thousandwaves.lore1"))
                .addCustomLoreLine(lang().get("spells.thousandwaves.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}