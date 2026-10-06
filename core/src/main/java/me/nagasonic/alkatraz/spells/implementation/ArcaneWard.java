package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.properties.implementation.BarrierProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Circle 5 NONE denial. A stationary shell of folded mana that can be hung on
 * <b>another</b> living entity and that stays exactly where it was cast.
 *
 * <h2>Two changes from {@code barrier}, and only two</h2>
 * <ol>
 *   <li>It resolves a {@link LivingEntity} to centre on instead of hard-coding the caster,
 *       so a ward can be planted on an ally. Nothing about the shell, the hitpoints, the
 *       push or the break handling differs.</li>
 *   <li>There is no {@code if (followPlayer)} branch and no
 *       {@link BarrierProperties#setCastLocation} call anywhere in this class.
 *       {@code center} is captured once and never mutated, which is the entire reason the
 *       ward outlives the caster walking away - and therefore also the reason this class
 *       ships no {@code follow_player} option.</li>
 * </ol>
 *
 * <h2>The damage contract, vacuously</h2>
 * This spell deals no damage to anything. It <i>absorbs</i> it:
 * {@code SpellComponentHandler.collide} calls {@link BarrierProperties#damage(double)} with the
 * <i>offender's</i> remaining power, and the bar absorbs from every projectile in an incoming
 * volley until it breaks because a single {@link BarrierProperties} is shared by all 225 shell
 * components of the cast. There is no {@code power} key, no {@code getPower} call and no
 * {@code SpellAttackContext}, which is why it extends {@link BarrierSpell} rather than
 * {@link AttackSpell}.
 *
 * <h2>Colours</h2>
 * The shell and the break burst come from the shared {@link SpellVfx} builders in NONE's neutral
 * grey, exactly as {@code barrier}'s do - the two are the same chassis and should read as the
 * same family. There is no hand-picked amethyst here: {@link ElementPalette} owns every colour in
 * the plugin, and a literal RGB would be the defect the wave-C VFX migration removed.
 */
public class ArcaneWard extends BarrierSpell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `NONE` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(180, 180, 180);

    /** Particles on the expanding shell. */
    private static final int SHELL_POINT_COUNT = 200;
    /** Particles in the break burst. */
    private static final int BREAK_POINT_COUNT = 120;
    /** Shell breathing radius added on top of the configured barrier size, in blocks. */
    private static final double SHELL_POINT_RADIUS = 0.6;
    /** Shell component lifetime; equal to the 4-tick repaint period so each one is replaced. */
    private static final int SHELL_LIFETIME = 4;
    /** The repaint period. */
    private static final long SHELL_PERIOD = 4L;
    /**
     * Runs per second of declared duration at the 4-tick repaint period, so
     * {@code duration * RUNS_PER_SECOND} is the runnable's tick budget. (45.0 s -> 225 runs -> 900
     * ticks of wall clock, of which the shell components themselves live 225 ticks.)
     */
    private static final double RUNS_PER_SECOND = 5.0;

    private double radius;
    private double duration;
    private int targetRange;

    public ArcaneWard(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/arcane_ward.yml");
        Alkatraz.getInstance().saveConfig("spells/arcane_ward_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/arcane_ward.yml").get();
        // max_hitpoints is read by BarrierSpell.loadCommonConfig with no default, so the yml
        // must declare it or the ward is born with zero hitpoints and breaks on the first tick.
        loadCommonConfig(spellConfig);
        loadOptions();

        // Every ad-hoc read carries a default. barrier reads two of these with none, which is the
        // exact FireWall defect class this plan exists to kill.
        this.radius = spellConfig.getDouble("radius", 2.5);
        this.duration = spellConfig.getDouble("duration", 45.0);
        this.targetRange = spellConfig.getInt("target_range", 24);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) {
            cancelCast(caster);
            return;
        }

        LivingEntity target = resolveWardTarget(caster);

        double activeRadius = optionValue(caster, "ward_size", radius);
        double activeDuration = optionValue(caster, "ward_duration", duration);
        double activeHitpoints = optionValue(caster, "ward_hitpoints", getMaxHitpoints());

        // Captured once and never mutated - see the class javadoc. No setCastLocation call exists
        // in this class, so SpellComponentHandler.deflectProjectiles reads this same centre for the
        // ward's whole life.
        final Location center = target.getLocation().clone().add(0, 1, 0);
        final UUID holderId = target.getUniqueId();

        // EXACTLY ONE BarrierProperties per cast. Its constructor calls Bukkit.createBossBar, so
        // building one per tick would orphan 225 BossBars per cast.
        BarrierProperties properties =
                new BarrierProperties(caster, center, activeHitpoints, BarrierType.COMBINED, activeRadius);
        properties.getHealthBar().addPlayer(caster);
        if (target instanceof Player holder && !holderId.equals(caster.getUniqueId())) {
            properties.getHealthBar().addPlayer(holder);
        }

        final double shellCollisionRadius = activeRadius + SHELL_POINT_RADIUS;

        new BukkitRunnable() {
            int ticksPassed = 0;

            @Override
            public void run() {
                if (properties.isBroken() || ticksPassed >= activeDuration * RUNS_PER_SECOND) {
                    onBarrierBreak(caster, center, activeRadius);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                // Push nearby entities away (for PHYSICAL/COMBINED barriers).
                if (properties.getType() == BarrierType.PHYSICAL || properties.getType() == BarrierType.COMBINED) {
                    final Vector centerVec = center.toVector();
                    for (Entity entity : center.getWorld()
                            .getNearbyEntities(center, activeRadius, activeRadius, activeRadius)) {
                        if (!(entity instanceof LivingEntity living)) continue;
                        if (entity == caster) continue;
                        // The ward must never blow its own holder off the ground.
                        if (entity.getUniqueId().equals(holderId)) continue;
                        if (NBT.getPersistentData(entity, nbt -> nbt.getBoolean("summoned_zombie"))) continue;

                        Vector push = entity.getLocation().toVector().subtract(centerVec);
                        push.setY(0);
                        if (push.lengthSquared() < 0.01) continue;
                        push = Utils.safeNormalize(push).multiply(0.3);
                        living.setVelocity(living.getVelocity().add(push));
                    }
                }

                SpellParticleComponent shell = new SpellParticleComponent(
                        ArcaneWard.this,
                        properties,
                        caster,
                        wand,
                        SpellComponentType.DEFENSE,
                        center.clone(),
                        shellCollisionRadius,
                        SHELL_LIFETIME
                );
                SpellComponentHandler.register(shell);

                SpellVfx.nova(CAST_COLOR, caster, center, activeRadius, SHELL_POINT_COUNT);

                ticksPassed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, SHELL_PERIOD);
    }

    /**
     * Whoever the caster is aiming at, or the caster themselves.
     *
     * <p>{@code Utils.getTargetEntity}'s own filter already excludes the caster; the explicit
     * equality guard below is belt and braces, because a ward centred on its own holder would be
     * a no-op that still cost 120 mana.
     */
    private LivingEntity resolveWardTarget(Player caster) {
        int range = (int) getModifiedStat(caster, "target_range", targetRange);
        Entity aimed = Utils.getTargetEntity(caster, range);
        if (aimed instanceof LivingEntity living
                && living.isValid()
                && !living.isDead()
                && !living.getUniqueId().equals(caster.getUniqueId())) {
            return living;
        }
        return caster;
    }

    /**
     * Reads one option's selected numeric value, falling back to the yml scalar.
     *
     * <p>{@code barrier} casts the selected value straight to {@code Double} and would throw an
     * NPE inside {@code castAction} if an options file ever failed to load. A ward that cannot be
     * cast at all is worse than a ward at its default size, so the fallback is the yml value the
     * mob path already uses.
     */
    private double optionValue(Player caster, String optionId, double fallback) {
        SpellOption option = getOption(optionId);
        if (option == null) return fallback;
        Object selected = option.getSelectedValue(caster);
        if (selected == null) return fallback;
        Object value = ((OptionValue<?>) selected).getValue();
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // A mob cannot aim at an ally, so the centre is the mob and there is no target resolution.
        // The option values are player-only, so the hitpoints come from max_hitpoints directly.
        final Location center = caster.getLocation().clone().add(0, 1, 0);
        BarrierProperties properties =
                new BarrierProperties(caster, center, getMaxHitpoints(), BarrierType.COMBINED, radius);

        final double shellCollisionRadius = radius + SHELL_POINT_RADIUS;

        new BukkitRunnable() {
            int ticksPassed = 0;

            @Override
            public void run() {
                if (properties.isBroken() || ticksPassed >= duration * RUNS_PER_SECOND) {
                    onBarrierBreak(caster, center, radius);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                SpellParticleComponent shell = new SpellParticleComponent(
                        ArcaneWard.this,
                        properties,
                        caster,
                        wand,
                        SpellComponentType.DEFENSE,
                        center.clone(),
                        shellCollisionRadius,
                        SHELL_LIFETIME
                );
                SpellComponentHandler.register(shell);

                SpellVfx.nova(CAST_COLOR, caster, center, radius, SHELL_POINT_COUNT);

                ticksPassed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, SHELL_PERIOD);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 2.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.arcaneward.book_name"))
                .addCustomLoreLine(lang().get("spells.arcaneward.lore1"))
                .addCustomLoreLine(lang().get("spells.arcaneward.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }

    @Override
    public void onHit(double damage, AttackSpell source) {
        // Empty on purpose. The attacker already produced particles through onHitBarrier; a second
        // effect here would draw the impact twice for every hit the ward soaks.
    }

    @Override
    public void onBarrierBreak(Location center) {
        onBarrierBreak(null, center, radius);
    }

    private void onBarrierBreak(LivingEntity caster, Location center) {
        onBarrierBreak(caster, center, radius);
    }

    /**
     * The caster is threaded through so the ward's owner always sees the burst at full density;
     * every other player nearby still gets it through the normal distance falloff.
     */
    private void onBarrierBreak(LivingEntity caster, Location center, double breakRadius) {
        SpellVfx.nova(CAST_COLOR, caster, center, breakRadius, BREAK_POINT_COUNT);
    }
}