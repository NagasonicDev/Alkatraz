package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * The circle-9 capstone. One target, looked at and <b>erased</b>: the hit is worth
 * least against a healthy enemy and almost everything against a dying one, because
 * the damage scales with the target's <b>missing</b> health. Burst someone down to a
 * sliver and Oblivion finishes the sentence; leave them untouched and it is an
 * ordinary, expensive circle-9 hit.
 *
 * <p>The curse it leaves - amplified weakness and blindness - is still ticking twenty
 * seconds after the erasure wave has finished drawing itself out of the air. This is
 * dark's <b>Curse</b> pillar peaking, and it closes the alternating ladder that began
 * at {@code dark_tendrils} in circle 3: curses at 3, 5, 7 and 9; servants at 4, 6 and
 * 8.
 *
 * <h2>Read the missing fraction BEFORE the damage</h2>
 * This ordering is the single most important line in the spell. Read it afterwards and
 * the scaling silently uses post-hit health, which makes the whole payoff a lie. The
 * maximum is read through {@code Attribute.GENERIC_MAX_HEALTH.getValue()} rather than
 * {@code getMaxHealth()}, both because that is the cross-version-safe route and because
 * {@code SummonZombies} already reads the summon's health attribute the same way.
 * {@code setHealth(...)} is never called anywhere in this class.
 *
 * <h2>Damage contract</h2>
 * Rules 1-4 are engaged; rule 5 holds vacuously because there is exactly one projectile.
 * One {@code getPower} per cast, applied to the <em>already-scaled</em> base so
 * affinity and resistance modify the final number exactly once, immediately followed by
 * the single {@link SpellDamageUtil#damageWithSpell}. The 2-arg
 * {@code getPower(caster, power)} form is never used on the same hit and
 * {@code Spell.calcPower} - dead code with zero callers - is never called.
 *
 * <p>Worst case per cast, per single target: {@code min(28.6 x 3.15, 90.0)} = 90.0,
 * exactly {@code SpellBudgets.burstBudget(9)}. Minimum case: 28.6 against a full-health
 * target.
 *
 * <h2>What this spell never does</h2>
 * Dark takes power, it never gives it. No healing, no shielding, no
 * {@code ABSORPTION}, no {@code RESISTANCE}, no boss bar. The two potion effects
 * applied are {@code WEAKNESS} and {@code BLINDNESS}, both on the victim, and neither
 * deals damage - there is deliberately no {@code WITHER}, so nothing escapes the
 * budget accounting.
 */
public class Oblivion extends AttackSpell {

    /**
     * This class's identity on screen: crimson {@code RGB(140, 0, 60)}, unmistakable
     * next to {@code umbral_volley}'s violet {@code RGB(96,0,168)} and
     * {@code void_anchor}'s near-black {@code RGB(48,0,72)}. Hoisted so a per-tick draw
     * allocates no colour object at all.
     */
    private static final Color OBLIVION_CRIMSON = Color.fromRGB(140, 0, 60);
    private static final Particle.DustOptions OBLIVION_DUST =
            new Particle.DustOptions(OBLIVION_CRIMSON, 1.0F);
    /** The wave's rim, one shade back so the sphere reads as a shell and not a blob. */
    private static final Particle.DustOptions EDGE_DUST =
            new Particle.DustOptions(Color.fromRGB(90, 0, 40), 0.6F);

    /** Points on the erasure wave's expanding sphere. */
    private static final int WAVE_POINTS = 40;
    /** Points on the smoke ring drawn every {@link #WAVE_RING_EVERY} ticks. */
    private static final int WAVE_RING_POINTS = 6;
    /** Ticks between the wave's smoke rings. */
    private static final int WAVE_RING_EVERY = 3;
    /** Ticks between the wave's rising tone. */
    private static final int WAVE_SOUND_EVERY = 10;

    private OblivionConfig config;

    public Oblivion(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/oblivion.yml");
        Alkatraz.getInstance().saveConfig("spells/oblivion_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/oblivion.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = OblivionConfig.fromConfig(spellConfig.getValues(false));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        OblivionConfig cfg = config;

        int range = (int) getModifiedStat(caster, "target_range", optionInt(caster, "target_range", cfg.targetRange()));
        Entity found = Utils.getTargetEntity(caster, Math.max(1, range));
        if (!(found instanceof LivingEntity target) || target.equals(caster) || target.isDead()) {
            Utils.sendActionBar(caster, lang().get("spells.oblivion.no_target"));
            return;
        }

        erase(caster, wand, target,
                (int) getModifiedStat(caster, "debuff_duration", optionInt(caster, "debuff_duration", cfg.debuffSeconds())));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        LivingEntity target = caster.getTarget();
        if (target.equals(caster) || target.isDead()) return;
        // Defaults only: a mob is never given an option.
        erase(caster, wand, target, config.debuffSeconds());
    }

    /**
     * The whole spell, shared by both cast paths: read the missing fraction, scale,
     * damage once, curse, draw the wave, push the victim outward.
     */
    private void erase(LivingEntity caster, ItemStack wand, LivingEntity target, int curseSeconds) {
        OblivionConfig cfg = config;

        // READ THE MISSING FRACTION BEFORE ANY DAMAGE IS DEALT. Both reads are reads -
        // nothing here modifies the target - and getMaxHealth() is never called; the
        // maximum comes from the attribute so the value is the one damage will use.
        AttributeInstance maxAttribute = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = maxAttribute == null ? 0.0 : maxAttribute.getValue();
        double currentHp = target.getHealth();
        double missingFraction = maxHp <= 0.0
                ? 0.0
                : Math.max(0.0, Math.min(1.0, (maxHp - currentHp) / maxHp));

        double scaledBase = cfg.scaledDamage(getBasePower(), missingFraction);

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * (caster instanceof Player player
                        ? getWandPower(wand, player)
                        : getWandPowerOrDefault(wand)),
                AttackType.MAGIC);
        props.hit(target);

        // The ONE getPower for this cast, applied to the ALREADY-SCALED base, so
        // affinity and resistance modify the final number exactly once.
        double damage = getPower(caster, target, scaledBase);
        SpellDamageUtil.damageWithSpell(target, damage, caster, wand, Oblivion.this);

        applyCurse(target, curseSeconds);

        Location targetLoc = target.getLocation().clone();
        drawErasureWave(caster, targetLoc);

        // Erasure pushes outward, never inward.
        Vector away = Utils.safeNormalize(targetLoc.toVector().subtract(caster.getLocation().toVector()));
        if (away != null) {
            target.setVelocity(away.multiply(cfg.erasureKnockback()).setY(cfg.erasureLift()));
        }
    }

    /**
     * The whole of dark's identity for this spell.
     *
     * <p>Nothing here is ever removed by this class - the only {@code cancel()} in the
     * file stops the cosmetic erasure wave - so both effects outlive the cast by
     * construction. {@code WEAKNESS} and {@code BLINDNESS} only; neither deals damage,
     * so the single scaled hit stays the whole of the spell's per-cast damage.
     */
    private void applyCurse(LivingEntity target, int curseSeconds) {
        int ticks = Math.max(1, curseSeconds) * 20;
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS, ticks, config.weaknessAmplifier(), false, false));
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS, ticks, config.blindnessAmplifier(), false, false));
    }

    /** The selected value of a player option, or {@code fallback} when it is absent. */
    private int optionInt(Player caster, String optionId, int fallback) {
        SpellOption option = getOption(optionId);
        if (option == null) return fallback;
        OptionValue<?> selected = option.getSelectedValue(caster);
        if (selected == null) return fallback;
        Object value = selected.getValue();
        return value instanceof Number n ? n.intValue() : fallback;
    }

    /**
     * A purely cosmetic expanding shell over the target's last known position.
     *
     * <p>Carries no {@link AttackProperties}, no spell component and no damage, and it
     * holds only the captured {@link Location} - never the entity - so a target that
     * dies or teleports mid-wave cannot leak the runnable.
     */
    private void drawErasureWave(LivingEntity caster, Location centre) {
        OblivionConfig cfg = config;
        int life = Math.max(1, cfg.erasureWaveTicks());

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= life) {
                    cancel();
                    return;
                }
                double radius = cfg.erasureRadiusAt(ticks);

                for (Location point : ParticleUtils.sphere(centre, radius, WAVE_POINTS)) {
                    ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, OBLIVION_DUST);
                }
                if (ticks % WAVE_RING_EVERY == 0) {
                    for (Location point : ParticleUtils.circle(centre, radius, WAVE_RING_POINTS, 0, 0)) {
                        ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, EDGE_DUST);
                        ParticleCaster.spawn(caster, point.getWorld(), Utils.SMOKE, point, 1, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                if (ticks % WAVE_SOUND_EVERY == 0) {
                    centre.getWorld().playSound(centre, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f,
                            0.4f + 0.03f * ticks);
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.DUST, location, 30, OBLIVION_DUST);
        // ENTITY_ENDERMAN_HURT. The erasure wave colliding with a barrier is a struck-tentacle
        // read, and this is the one constant in the 1.19 Sound enum that carries it. It was
        // previously spelled ENTITY_ENDOK_HURT, which is not a Bukkit constant at all - the
        // name only ever existed here, because Spell.playSound resolves sound NAMES through a
        // try/catch that swallows IllegalArgumentException, so a typo in a yml sound key is
        // silent but a typo in a Java constant does not compile.
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_HURT, 1.0f, 0.4f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.DUST, location, 60, OBLIVION_DUST);
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.oblivion.book_name"))
                .addCustomLoreLine(lang().get("spells.oblivion.lore1"))
                .addCustomLoreLine(lang().get("spells.oblivion.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}