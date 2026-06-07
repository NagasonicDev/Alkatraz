package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    // Warn duration in ticks before eruption fires
    private static final int WARN_TICKS = 20;

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
    }

    // -------------------------------------------------------------------------
    // Magic Circle Preparation
    // -------------------------------------------------------------------------

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location eye = caster.getEyeLocation();
            Vector forward = eye.getDirection().normalize().multiply(1.5);
            List<Location> points = ParticleUtils.magicCircle(eye, eye.getYaw(), eye.getPitch(), forward, 2, 0);
            for (Location loc : points) {
                loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 180, 255), 0.4F));
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&9Water Sutra &oVol. III")
                .addCustomLoreLine("&8The final part of a trilogy, containing")
                .addCustomLoreLine("&8the final step of mastering the basics of")
                .addCustomLoreLine("&8water magic.")
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
        double basePower = getPower(caster, getBasePower())
                * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                basePower,
                AttackType.MAGIC
        );

        // Target: ~6 blocks in front of the caster on the ground
        Location epicentre = Utils.findTopSolid(
                caster.getLocation().add(
                        caster.getLocation().getDirection().setY(0).normalize().multiply(6)
                ),
                10
        );

        // Phase 1 — warning spiral, then erupt
        playWarningThenErupt(caster, epicentre, height, radius, launchPower, props);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        double height = 10.0;
        double radius = 2.5;
        double launchPower = 1;
        double wandp = wand == null ? 1 : NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        double power = getPower(caster, getBasePower())
                * wandp;

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
                AttackType.MAGIC
        );

        // Target: ~6 blocks in front of the caster on the ground
        Location epicentre = Utils.findTopSolid(
                caster.getTarget().getLocation(),
                10
        );

        // Phase 1 — warning spiral, then erupt
        playWarningThenErupt(caster, epicentre, height, radius, launchPower, props);
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
    private void playWarningThenErupt(LivingEntity caster, Location epicentre,
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

        taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            tick[0]++;

            // Reveal points up to this tick's progress
            int upTo = (int) Math.min(tick[0] * pointsPerTick, totalPoints);
            for (int i = 0; i < upTo; i++) {
                Location loc = spiralPoints.get(i).clone().add(0, 0.05, 0);
                epicentre.getWorld().spawnParticle(
                        Utils.DUST, loc, 1, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 180, 255), 0.55F));
            }

            // Rumble sound — grows louder as the spiral fills
            if (tick[0] % 4 == 0) {
                float pitch = 0.6f + (tick[0] / (float) WARN_TICKS) * 0.6f;
                epicentre.getWorld().playSound(epicentre, Sound.BLOCK_WATER_AMBIENT, 0.5f, pitch);
            }

            // When the warning is done, erupt
            if (tick[0] >= WARN_TICKS) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                playEruption(caster, epicentre, height, radius, launchPower, props);
            }

        }, 0L, 1L);
    }

    // -------------------------------------------------------------------------
    // Phase 2 — Eruption
    // -------------------------------------------------------------------------

    /**
     * Fires the geyser upward over several ticks. The column is built using
     * the spiral() helper — a tight rising spiral from ground to full height
     * — so the water looks like it's twisting as it erupts rather than just
     * popping into existence all at once.
     *
     * Damage and launch are applied on the first tick so entities are hit
     * the moment the eruption begins.
     */
    private void playEruption(LivingEntity caster, Location epicentre,
                              double height, double radius,
                              double launchPower, AttackProperties props) {

        World world = epicentre.getWorld();

        // Impact sound and ground burst particles
        world.playSound(epicentre, Sound.ENTITY_GENERIC_SPLASH, 1.4f, 0.6f);
        world.playSound(epicentre, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 0.7f);
        world.spawnParticle(Particle.WATER_SPLASH, epicentre.clone().add(0, 0.1, 0),
                60, radius * 0.6, 0.1, radius * 0.6, 0.2);

        // Build column — tight spiral rising from 0 to height, expands slightly
        List<Location> columnPoints = ParticleUtils.spiral(epicentre, height, 0.1, radius * 0.4, 3.0, 6.0);

        // Reveal column over ERUPTION_TICKS — taller geysers take longer to peak
        int ERUPTION_TICKS = (int) Math.max(8, height * 0.8);
        double pointsPerTick = (double) columnPoints.size() / ERUPTION_TICKS;

        Set<UUID> damaged = new HashSet<>();
        int[] tick = {0};
        int[] taskId = {-1};

        taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            tick[0]++;

            // Reveal column slice by slice
            int upTo = (int) Math.min(tick[0] * pointsPerTick, columnPoints.size());
            for (int i = 0; i < upTo; i++) {
                Location loc = columnPoints.get(i);
                world.spawnParticle(Particle.WATER_SPLASH,     loc, 3, 0.15, 0, 0.15, 0.08);
                world.spawnParticle(Particle.BUBBLE_POP, loc, 1, 0.1,  0, 0.1,  0.02);
            }

            // Damage + launch on the very first eruption tick
            if (tick[0] == 1) {
                for (LivingEntity target : epicentre.getNearbyLivingEntities(radius, height, radius)) {
                    if (target.equals(caster)) continue;
                    if (damaged.contains(target.getUniqueId())) continue;
                    damaged.add(target.getUniqueId());

                    double dmg = getPower(caster, target, props.getRemainingPower());
                    target.damage(dmg, caster);

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
                world.spawnParticle(Particle.WATER_SPLASH,     top, 40, radius * 0.5, 0.3, radius * 0.5, 0.25);
                world.spawnParticle(Particle.BUBBLE_POP, top, 20, radius * 0.3, 0.2, radius * 0.3, 0.1);
                world.playSound(top, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 1.2f);
            }

        }, 0L, 1L);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Particle.WATER_SPLASH, location, 25, 0.3, 0.3, 0.3, 0.1);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.WATER_SPLASH, location, 40, 0.4, 0.4, 0.4, 0.15);
    }
}
