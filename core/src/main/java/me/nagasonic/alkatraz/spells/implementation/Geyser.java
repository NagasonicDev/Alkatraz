package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Geyser — Circle 3 Water Attack Spell
 *
 * Two-phase spell:
 *
 *  Phase 1 — WARNING (1 second)
 *      A flat spiral unwinds on the ground ~6 blocks in front of the caster,
 *      signalling where the eruption will occur. The spiral tightens inward
 *      toward the epicentre as it plays, building tension.
 *
 *  Phase 2 — ERUPTION
 *      A column of water particles bursts upward from the epicentre over
 *      several ticks. Every LivingEntity inside the geyser's radius takes
 *      damage and is launched skyward. Entities are only damaged once per cast.
 */
public class Geyser extends AttackSpell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `WATER` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(47, 127, 212);

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    // Warn duration in ticks before eruption fires
    private static final int WARN_TICKS = 20;

    /**
     * The single WATER palette. This spell used to name its own {@code (80,180,255)}, which was
     * a sixth blue in an element that already had five. Nothing here may name an RGB value.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);

    /** Cast-circle scale; this spell has always drawn a size-2 glyph. */
    private static final double CIRCLE_SIZE = 2.0;
    /** Vertical slices in the eruption column. */
    private static final int COLUMN_LAYERS = 14;
    /** Particles per column slice. */
    private static final int COLUMN_POINTS_PER_LAYER = 16;
    /** Radians each column slice is rotated against the one below it, for the twisting riser. */
    private static final double COLUMN_PHASE_STEP = 0.45;
    /** How many freshly revealed spiral points are drawn in the leading {@code core} shade. */
    private static final int WARN_LEADING_POINTS = 8;

    public Geyser(String type) {
        super(type);
    }

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/geyser.yml");
        Alkatraz.getInstance().saveConfig("spells/geyser_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/geyser.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    // -------------------------------------------------------------------------
    // Magic Circle Preparation
    // -------------------------------------------------------------------------

@Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, CIRCLE_SIZE, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.geyser.book_name"))
                .addCustomLoreLine(lang().get("spells.geyser.lore1"))
                .addCustomLoreLine(lang().get("spells.geyser.lore2"))
                .addCustomLoreLine(lang().get("spells.geyser.lore3"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }

    // -------------------------------------------------------------------------
    // Casting
    // -------------------------------------------------------------------------

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double height = getModifiedStat(caster, "height",       10.0);
        double radius = getModifiedStat(caster, "radius",        2.5);
        double launchPower = getModifiedStat(caster, "launch_power",  1.0);

        // RAW base power. getPower is applied once, at impact, in playEruption.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster),
                AttackType.MAGIC
        );

        // Target: ~6 blocks in front of the caster on the ground
        Location epicentre = Utils.findTopSolid(
                caster.getLocation().add(
                        Utils.safeNormalize(caster.getLocation().getDirection().setY(0)).multiply(6)
                ),
                10
        );
        if (epicentre == null) epicentre = caster.getLocation();

        // Phase 1 — warning spiral, then erupt
        playWarningThenErupt(caster, wand, epicentre, height, radius, launchPower, props);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;

        double height = 10.0;
        double radius = 2.5;
        double launchPower = 1;
        double wandp = getWandPowerOrDefault(wand);

        // RAW base power. getPower is applied once, at impact, in playEruption.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * wandp,
                AttackType.MAGIC
        );

        // Target: ~6 blocks in front of the caster on the ground
        Location epicentre = Utils.findTopSolid(
                caster.getTarget().getLocation(),
                10
        );
        if (epicentre == null) epicentre = caster.getLocation();

        // Phase 1 — warning spiral, then erupt
        playWarningThenErupt(caster, wand, epicentre, height, radius, launchPower, props);
    }

    // -------------------------------------------------------------------------
    // Phase 1 — Warning Spiral
    // -------------------------------------------------------------------------

    /**
     * Animates a flat spiral on the ground that unwinds outward from the
     * epicentre over WARN_TICKS ticks, then fires the eruption.
     *
     * The spiral goes from radius→0 (inward) so it looks like energy
     * converging toward the epicentre just before it blows.
     */
    private void playWarningThenErupt(LivingEntity caster, ItemStack wand, Location epicentre,
                                      double height, double radius,
                                      double launchPower, AttackProperties props) {

        // Pre-build all spiral points for the warning animation.
        // Flat spiral (height=0), converging inward: initialRadius=radius, endRadius=0.
        List<Location> spiralPoints = ParticleUtils.spiral(epicentre, 0, radius, 0, 2.0, 8.0);
        int totalPoints = spiralPoints.size();

        // How many points to reveal per tick so the spiral finishes in WARN_TICKS
        double pointsPerTick = (double) totalPoints / WARN_TICKS;

        int[] tick = {0};
        int[] taskId = {-1};
        final int[] last = {0};
        final World spiralWorld = epicentre.getWorld();

        taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            tick[0]++;

            if (props.isCancelled() || props.isCountered() || caster.isDead()
                    || (caster instanceof Player p && !p.isOnline())) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                return;
            }

            // Reveal points up to this tick's progress. The newest points are the leading edge and
            // take `core`; the trail behind them is ground structure and steps down to `deep`.
            int upTo = (int) Math.min(tick[0] * pointsPerTick, totalPoints);
            for (int i = last[0]; i < upTo; i++) {
                Location loc = spiralPoints.get(i).clone().add(0, 0.05, 0);
                ParticleCaster.spawn(caster, spiralWorld, Utils.DUST, loc, 1, 0, 0, 0,
                        i >= upTo - WARN_LEADING_POINTS ? PALETTE.coreDust() : PALETTE.deepDust());
            }
            last[0] = upTo;

            // Rumble sound — grows louder as the spiral fills
            if (tick[0] % 4 == 0) {
                float pitch = 0.6f + (tick[0] / (float) WARN_TICKS) * 0.6f;
                spiralWorld.playSound(epicentre, Sound.BLOCK_WATER_AMBIENT, 0.5f, pitch);
            }

            // When the warning is done, erupt
            if (tick[0] >= WARN_TICKS) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                playEruption(caster, wand, epicentre, height, radius, launchPower, props);
            }

        }, 0L, 1L);
    }

    // -------------------------------------------------------------------------
    // Phase 2 — Eruption
    // -------------------------------------------------------------------------

    /**
     * Fires the geyser upward over several ticks.
     *
     * The shape is a {@link SpellVfx#column} whose <i>height</i> grows every tick, which is what
     * preserves the old {@code spiral()} reveal: the column appears slice by slice from the ground
     * up rather than popping into existence at full height. The per-slice phase step turns the
     * stack of rings into a twist, which is the only thing that made the original read as water
     * rather than as a lamp post.
     *
     * Damage and launch are applied on the first tick so entities are hit
     * the moment the eruption begins.
     */
    private void playEruption(LivingEntity caster, ItemStack wand, Location epicentre,
                              double height, double radius,
                              double launchPower, AttackProperties props) {

        World world = epicentre.getWorld();

        // Impact sound and ground burst particles
        world.playSound(epicentre, Sound.ENTITY_GENERIC_SPLASH, 1.4f, 0.6f);
        world.playSound(epicentre, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 0.7f);
        ParticleCaster.spawn(caster, world, Utils.SPLASH, epicentre.clone().add(0, 0.1, 0),
                60, radius * 0.6, 0.1, radius * 0.6, 0.2);

        // Reveal the column over ERUPTION_TICKS — taller geysers take longer to peak
        int ERUPTION_TICKS = (int) Math.max(8, height * 0.8);

        int[] tick = {0};
        int[] taskId = {-1};

        taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            tick[0]++;

            if (props.isCancelled() || props.isCountered() || caster.isDead()
                    || (caster instanceof Player p && !p.isOnline())) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                return;
            }

            // Growing height, fixed slice count: the column is revealed from the foot up.
            double progress = Math.min(1.0, (double) tick[0] / ERUPTION_TICKS);
            SpellVfx.column(CAST_COLOR, caster, epicentre, height * progress, radius * 0.4,
                    COLUMN_LAYERS, COLUMN_POINTS_PER_LAYER, COLUMN_PHASE_STEP);

            // Droplets riding the current, so the riser reads as liquid rather than as a tube.
            ParticleCaster.spawn(caster, world, Utils.SPLASH, epicentre.clone().add(0, height * progress, 0),
                    3, radius * 0.3, 0.15, radius * 0.3, 0.08);

            // Damage + launch on the very first eruption tick
            if (tick[0] == 1) {
                for (LivingEntity target : me.nagasonic.alkatraz.util.Utils.getNearbyLivingEntities(epicentre, radius, height, radius)) {
                    if (target.equals(caster)) continue;
                    if (props.hasHit(target)) continue;
                    props.hit(target);

                    double dmg = getPower(caster, target, props.getRemainingPower());
                    SpellDamageUtil.damageWithSpell(target, dmg, caster, wand, Geyser.this);

                    // Launch straight up — launchPower scales with the height option
                    // so a taller geyser actually carries enemies higher
                    Vector launch = new Vector(0, launchPower * (height / 10.0), 0);
                    target.setVelocity(target.getVelocity().add(launch));
                }
            }

            if (tick[0] >= ERUPTION_TICKS) {
                Bukkit.getScheduler().cancelTask(taskId[0]);

                // Dissipation — spray particles at the top of the column
                Location top = epicentre.clone().add(0, height, 0);
                ParticleCaster.spawn(caster, world, Utils.SPLASH,     top, 40, radius * 0.5, 0.3, radius * 0.5, 0.25);
                ParticleCaster.spawn(caster, world, Utils.BUBBLE_POP, top, 20, radius * 0.3, 0.2, radius * 0.3, 0.1);
                world.playSound(top, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 1.2f);
            }

        }, 0L, 1L);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SPLASH, location, 25, 0.3, 0.3, 0.3, 0.1);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.SPLASH, location, 40, 0.4, 0.4, 0.4, 0.15);
    }
}
