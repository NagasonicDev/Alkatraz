package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class AirBurst extends AttackSpell {

    /**
     * Knockback strength in blocks/tick, deliberately a FLAT constant rather than
     * the scaled damage. The shove used to be {@code getPower(...)}, which made
     * knockback a function of damage: on the corrected power ladder a damage buff
     * or a wand upgrade threw the target twice as far as the design intended. It
     * also meant the caster - who {@code Utils.getNearbyLivingEntities} happily
     * returns - was shoved backwards by their own blast.
     */
    private static final double BLAST_PUSH = 0.8;

    /**
     * The AIR palette. air_burst previously used pure {@code Particle.WHITE} for both the
     * cast circle and the blast puff; both now come from the element palette like every
     * other AIR spell.
     */
    private static final ElementPalette.Palette AIR = ElementPalette.of(Element.AIR);

    /**
     * Ribbon width, in blocks, of the sweeping blast beam relative to {@code blast_size}.
     * Deliberately FAT: air_burst is a short, wide sweep, whereas swift is a long, thin
     * dash from the same builder, and the aspect ratio is the only thing that tells them
     * apart in silhouette.
     */
    private static final double BEAM_START_WIDTH = 1.6;
    private static final double BEAM_END_WIDTH = 1.15;
    /** Spine segments drawn per blast point. A blast point is about a block apart, so two is enough. */
    private static final int BEAM_STEPS = 2;
    /** Leading-edge motes thrown off the front of the blast each tick. */
    private static final int BLAST_EDGE_MOTES = 6;
    private static final double BLAST_EDGE_SPREAD = 0.35;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public AirBurst(String type){
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.CLOUD, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.CLOUD, location, 30);
    }

    /**
     * The AIR signature shape: a FAT, short beam segment drawn across the slice of the
     * blast line the sweep has just crossed.
     *
     * <p>The sweep is preserved exactly - the runnable still walks {@code lineLocs} one
     * point per tick and still resolves damage at that point - but the visuals are now a
     * continuous tapered beam rather than a chain of independent puffs, because every
     * segment begins exactly where the previous one ended. swift uses the same builder
     * with a very different aspect ratio (thin, long, straight) so the two never read as
     * the same effect.
     *
     * @param from     where the previous blast point was, or the cast origin on the first tick
     * @param to       the blast point being resolved this tick
     * @param blastSize the configured blast size, in blocks
     */
    private void spawnSweepSegment(Location from, Location to, double blastSize) {
        SpellVfx.beam(AirBurst.this, null, from, to, BEAM_STEPS,
                blastSize * BEAM_START_WIDTH, blastSize * BEAM_END_WIDTH);

        // Leading edge: pale, translucent-looking air thrown off the front of the blast.
        // This is what makes the sweep read as moving rather than as a drawn line.
        ParticleCaster.spawn(null, to.getWorld(), Utils.CLOUD, to, BLAST_EDGE_MOTES,
                blastSize * BLAST_EDGE_SPREAD, blastSize * BLAST_EDGE_SPREAD,
                blastSize * BLAST_EDGE_SPREAD, 0.1);
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/air_burst_options.yml");
        Alkatraz.getInstance().save("spells/air_burst.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/air_burst.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        final AttackProperties properties = new AttackProperties(caster, Utils.castLocation(caster), getBasePower() * getWandPower(wand, caster), AttackType.MAGIC);

        final List<Location> lineLocs = ParticleUtils.line(
                2,
                caster.getEyeLocation(),
                caster.getEyeLocation().add(
                        caster.getEyeLocation().getDirection().multiply(getModifiedStat(caster, "range", 10))
                )
        );

        final int totalPoints = lineLocs.size();
        final double blastSize = getModifiedStat(caster, "blast_size", 1);

        new BukkitRunnable() {

            private int index = 0;
            private Location previous;

            @Override
            public void run() {


                if (caster.isDead() || properties.isCountered()) {
                    cancel();
                    return;
                }

                if (index >= totalPoints) {
                    cancel();
                    return;
                }

                Location point = lineLocs.get(index++);

                SpellParticleComponent component = new SpellParticleComponent(
                        AirBurst.this,
                        properties,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        point,
                        1.75,                    // collision radius
                        2                        // lifespan (ticks)
                );

                spawnSweepSegment((previous == null) ? caster.getEyeLocation() : previous, point, blastSize);
                previous = point;

                for (LivingEntity le : me.nagasonic.alkatraz.util.Utils.getNearbyLivingEntities(point, blastSize)) {
                    // air_burst was knockback-only. It is a circle-1 BURST now.
                    if (le.equals(caster)) continue;
                    // The beam sweeps forward one point per tick, so a target standing
                    // in the line is inside several blast points. hasHit is a
                    // CAST-lifetime set, so it dedupes the sweep instead of
                    // re-scaling the same target once per point.
                    if (properties.hasHit(le)) continue;
                    properties.hit(le);

                    SpellDamageUtil.damageWithSpell(le,
                            getPower(caster, le, properties.getRemainingPower()),
                            caster, wand, AirBurst.this);

                    // The shove is a FIXED push, not a damage-scaled one, so a damage
                    // buff no longer throws the target twice as far. The old code
                    // multiplied the direction by getPower, which coupled knockback to
                    // damage.
                    le.setVelocity(caster.getEyeLocation().getDirection().multiply(BLAST_PUSH));
                }

                SpellComponentHandler.register(component);
            }

        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        double wandp = getWandPowerOrDefault(wand);
        // RAW base power - getPower is applied once per hit, below. The old code
        // pre-scaled here with the untargeted overload AND again at impact, which
        // squares both the option multipliers and the affinity term.
        final AttackProperties properties = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * wandp, AttackType.MAGIC);
        Vector direction = caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector());

        final List<Location> lineLocs = ParticleUtils.line(
                2,
                caster.getEyeLocation(),
                caster.getEyeLocation().add(
                        direction.multiply(20)
                )
        );

        final int totalPoints = lineLocs.size();

        new BukkitRunnable() {

            private int index = 0;
            private Location previous;

            @Override
            public void run() {


                if (caster.isDead() || properties.isCountered()) {
                    cancel();
                    return;
                }

                if (index >= totalPoints) {
                    cancel();
                    return;
                }

                Location point = lineLocs.get(index++);

                SpellParticleComponent component = new SpellParticleComponent(
                        AirBurst.this,
                        properties,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        point,
                        1.75,                    // collision radius
                        40                       // lifespan (ticks)
                );

                spawnSweepSegment((previous == null) ? caster.getEyeLocation() : previous, point, 1.0);
                previous = point;

                for (LivingEntity le : me.nagasonic.alkatraz.util.Utils.getNearbyLivingEntities(point, 1)) {
                    if (le.equals(caster)) continue;
                    if (properties.hasHit(le)) continue;
                    properties.hit(le);

                    SpellDamageUtil.damageWithSpell(le,
                            getPower(caster, le, properties.getRemainingPower()),
                            caster, wand, AirBurst.this);

                    // Flat constant, not getPower - see the player branch.
                    le.setVelocity(caster.getEyeLocation().getDirection().multiply(BLAST_PUSH));
                }

                SpellComponentHandler.register(component);
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
                .setDisplayName(lang().get("spells.airburst.book_name"))
                .addCustomLoreLine(lang().get("spells.airburst.lore1"))
                .addCustomLoreLine("")
                .build();
    }
}
