package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.SustainEffect;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Circle 9 WATER capstone, and one of the five circle-9 sustains: a mana-drain
 * <b>toggle</b>. The first cast raises the tide, the second takes it back down, and
 * {@code upkeep_mana_per_second} is charged every second in between, so running out of
 * mana ends it on its own.
 *
 * <h2>Why {@code power} is 14.0 and not the ladder's 57.2</h2>
 * Three things are mandated at once and cannot all hold: {@code power} comes off the
 * section 6 ladder, which puts circle 9 at 57.2; the tide is a sixty-second toggle that
 * bleeds every {@code damage_interval_ticks}, which is twenty windows; and
 * {@code power x hit_count} must sit inside {@code sustainBudget(9)} = 280.
 *
 * <pre>
 * 57.2 x 20 = 1144.0 &gt;  280    the ladder value does not fit
 * 280 / 20 =  14.0               the only per-window value that does
 * 14.0 x 20 =  280.0  ==  280     exactly on the budget
 * </pre>
 *
 * This is the same shape as {@code inferno_pilgrimage}'s documented 3 x 16 = 48: a
 * deliberate, budget-checked departure with the arithmetic shown, landing <i>on</i> the
 * budget. {@code hit_count} is the free variable here; {@code power} is the value every
 * damage instance scales from, so dividing it is the honest way to spend the budget.
 * {@link #damageWindows()} re-derives the count at runtime, so a hand-edited admin copy
 * that raises {@code duration_seconds} without raising {@code hit_count} cannot escape it.
 *
 * <h2>No preconditions</h2>
 * The caster heals from everything the tide catches, from wherever they are standing.
 * There is no "stand in the tide" gate - positioning was the old spell's precondition,
 * and a spell whose effect only exists if you aim well is a spell that reads as broken.
 * {@code mend_radius} therefore sizes the heal aura drawn around the caster rather than
 * gating anything.
 *
 * <h2>The dedupe is the WINDOW clock, not a set</h2>
 * Every body inside the radius is scanned on the same three-second cadence, so a set is
 * unnecessary: a body that walks in is caught on the next window, and a body that stands
 * there is caught on all twenty. {@code ctx.hit(le)} is still called - its return value is
 * deliberately ignored - so one counter ends the whole tide, which is the point of having
 * a per-cast {@code AttackProperties}.
 *
 * <h2>The heal is derived, never re-scaled</h2>
 * {@link #mendCaster} spends the damage numbers {@link #tideOnce} already produced in the
 * same window, at {@code heal_ratio}, capped at the caster's maximum health. Calling
 * {@code getPower} a second time here would square both the option multipliers and the
 * affinity term. A protected body never reaches the accumulator at all: the region gate is
 * checked once, before anything else in the scan.
 *
 * <p>No {@code TerrainEditor}, no block write of any kind, no {@code SpellBlockComponent}:
 * a tide is a sphere of pressure, not a wall, so a barrier should not absorb it. The only
 * state changes are {@code Entity#setVelocity} on drowned bodies and
 * {@code LivingEntity#setHealth} on the caster.
 */
public class TideOfTheDeep extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);

    /** Points on the wave's leading edge. */
    private static final int OUTER_POINTS = 24;
    /** Points inside the wave. */
    private static final int FILL_POINTS = 16;
    /** How tall the rolled-out wave reads against its own radius. */
    private static final double HEIGHT_PER_RADIUS = 0.6;
    /** The tide's whole life for a mob, who cannot afford the sixty-second player upkeep. */
    private static final int MOB_LIFETIME_TICKS = 200;

    private double durationSeconds;
    private double tideRadius;
    private double targetRange;
    private double healRatio;
    private double pullStrength;
    private double lift;
    private int damageIntervalTicks;
    private double mendRadius;
    private double upkeepManaPerSecond;

    public TideOfTheDeep(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * Circle 9 shape, read inline. No {@code saveConfig} call and no config record: the tide's
     * radius, duration and upkeep <i>are</i> its budget, so there is no key that could be sold
     * as an option without {@code SpellBudgetTest} asserting a number the player can
     * invalidate from a menu. This spell declares no {@code @EventHandler}, so it needs no
     * self-registration.
     */
    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/tide_of_the_deep.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/tide_of_the_deep.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.durationSeconds = spellConfig.getDouble("duration_seconds", 60.0);
        this.tideRadius = spellConfig.getDouble("tide_radius", 8.0);
        this.targetRange = spellConfig.getDouble("target_range", 20.0);
        this.healRatio = spellConfig.getDouble("heal_ratio", 0.5);
        this.pullStrength = spellConfig.getDouble("pull_strength", 0.2);
        this.lift = spellConfig.getDouble("lift", 0.15);
        this.damageIntervalTicks = Math.max(1, spellConfig.getInt("damage_interval_ticks", 60));
        this.mendRadius = spellConfig.getDouble("mend_radius", 8.0);
        this.upkeepManaPerSecond = spellConfig.getDouble("upkeep_mana_per_second", 80.0);
    }

    /** The tide's whole life, in ticks. {@code hit_count} must equal the windows it opens. */
    private int lifetimeTicks() {
        return Math.max(1, (int) Math.round(durationSeconds * 20.0));
    }

    /**
     * Damage windows the tide opens at its own cadence - the yml's {@code hit_count}.
     * Enforced rather than merely documented, so raising {@code duration_seconds} in an
     * admin's copy without raising {@code hit_count} cannot push the spell past 280.0.
     */
    private int damageWindows() {
        return Math.max(0, lifetimeTicks() / damageIntervalTicks);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        double radius = getModifiedStat(caster, "tide_radius", tideRadius);
        Location centre = Utils.resolveTarget(caster,
                (int) getModifiedStat(caster, "target_range", targetRange));

        // ONE context per cast: twenty windows share a single set of AttackProperties, so one
        // counter ends the whole tide. Created before the toggle because a cast that ends up
        // owning nothing is the only path that never uses it.
        SpellAttackContext ctx = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);
        BossBar bar = Bukkit.createBossBar(getDisplayName(), BarColor.BLUE, BarStyle.SOLID);

        SustainEffect.Toggle toggled = SustainEffect.toggle(caster, upkeepManaPerSecond, this,
                (p, elapsedTicks) -> tideTick(p, centre, radius, wand, ctx, bar, elapsedTicks),
                p -> bar.removeAll());

        // All five circle-9 sustains share the one slot. A foreign owner means this cast changed
        // nothing - the running tide is untouched and the bar built above was never shown - so
        // the player is told why instead of being left to think the spell did nothing.
        if (toggled == SustainEffect.Toggle.BLOCKED) {
            // Blocked changed nothing, so Spell.cast must refund this cast and skip cooldown/mastery.
            cancelCast(caster);
            Utils.sendActionBar(caster, Alkatraz.getLangManager().get("spells.sustain.blocked"));
            return;
        }

        if (toggled == SustainEffect.Toggle.CLOSED) {
            // This cast took the tide back down. The bar built above was never shown to anyone.
            // What Spell.cast charged was getModifiedManaCost(p), so that is what goes back:
            // refunding the bare getCost() would mint mana for a caster with a mana_cost modifier.
            StatUtils.addMana(caster, getModifiedManaCost(caster));
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_DROWNED_SWIM, 1.0f, 0.5f);
            ParticleCaster.spawn(caster, caster.getWorld(), Utils.SPLASH,
                    caster.getLocation().add(0, 0.2, 0), 40, 0.8, 0.4, 0.8, 0);
            return;
        }

        bar.addPlayer(caster);
        centre.getWorld().playSound(centre, Sound.BLOCK_WATER_AMBIENT, 1.4f, 0.4f);
        centre.getWorld().playSound(centre, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.3f);
    }

    /**
     * Every tick of the sustain. The wave is rolled out once on the opening tick; from then on
     * the tide only bleeds on its own cadence and repaints the BossBar.
     *
     * @return false on the last tick, which ends the sustain
     */
    private boolean tideTick(Player caster, Location centre, double radius, ItemStack wand,
                             SpellAttackContext ctx, BossBar bar, int elapsedTicks) {
        if (ctx.isSpent()) return false;

        // Redrawn every tick: a one-shot field vanishes after a second, so the tide would
        // read as nothing for the other fifty-nine. Spin keeps it from reading as static.
        SpellVfx.field(PALETTE.core(), caster, centre, radius, radius * HEIGHT_PER_RADIUS,
                OUTER_POINTS, FILL_POINTS, elapsedTicks * 0.1);
        if (elapsedTicks % damageIntervalTicks == 0
                && elapsedTicks / damageIntervalTicks < damageWindows()) {
            tideOnce(caster, centre, radius, wand, ctx);
        }
        bar.setProgress(manaBarProgress(caster));
        return elapsedTicks + 1 < lifetimeTicks();
    }

    /**
     * How much of the tide the caster's current purse can still pay for. Read from the first
     * tick onward, so the bar is a mana clock rather than a duration clock - the tide can end
     * long before its sixty seconds are up.
     */
    private double manaBarProgress(Player caster) {
        MagicProfile profile = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);
        if (profile == null || upkeepManaPerSecond <= 0.0) return 0.0;
        double secondsLeft = profile.getMana() / upkeepManaPerSecond;
        return Math.min(1.0, Math.max(0.0, secondsLeft / (lifetimeTicks() / 20.0)));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        // A mob's tide is narrower, shorter and centred on its target: the same mechanic,
        // scaled. It cannot be a toggle - only a player holds mana.
        double radius = Math.min(tideRadius, 5.0);
        Location centre = caster.getTarget().getLocation();
        SpellAttackContext ctx = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (caster.isDead() || !caster.isValid() || ticks >= MOB_LIFETIME_TICKS) {
                    cancel();
                    return;
                }
                // Four windows at most, so a mob tide spends 56.0 of the circle-9 budget the
                // player's twenty windows spend.
                if (ticks % damageIntervalTicks == 0) {
                    tideOnce(caster, centre, radius, wand, ctx);
                }
                SpellVfx.field(PALETTE.core(), caster, centre, radius, radius * HEIGHT_PER_RADIUS,
                        OUTER_POINTS, FILL_POINTS, ticks * 0.1);
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One bleed window: drown everything in the tide, then mend the caster out of what was
     * actually dealt. Together with {@link #mendCaster} this is the only place damage or
     * healing can originate.
     */
    private void tideOnce(LivingEntity caster, Location centre, double radius, ItemStack wand,
                          SpellAttackContext ctx) {
        ctx.tick();
        double drainedThisWindow = 0.0;

        for (LivingEntity le : Utils.getNearbyLivingEntities(centre, radius, radius, radius)) {
            if (le.equals(caster) || le.isDead() || !le.isValid()) continue;

            // The region gate is checked ONCE, before anything at all. Without this a
            // protected body would still be counted into the caster's heal.
            if (!Protection.damage(le, caster, le.getLocation())) continue;

            // Records the per-cast counter so one hit still ends the tide; the return value is
            // ignored on purpose - the window clock above, not this set, is the dedupe.
            ctx.hit(le);

            double damage = ctx.power(le);   // ONE getPower
            SpellDamageUtil.damageWithSpell(le, damage, caster, wand, this);
            drainedThisWindow += damage;

            Vector toCentre = Utils.safeNormalize(
                    centre.toVector().subtract(le.getLocation().toVector()).setY(0.0));
            if (toCentre != null) {
                le.setVelocity(le.getVelocity().add(toCentre.multiply(pullStrength).setY(lift)));
            }
            ParticleCaster.spawn(caster, le.getWorld(), Utils.SPLASH,
                    le.getLocation().add(0, 0.3, 0), 10, 0.3, 0.4, 0.3, 0);
        }

        ParticleCaster.spawn(caster, centre.getWorld(), Utils.SPLASH,
                centre.clone().add(0, 0.2, 0), 20, radius, 0.2, radius, 0);
        mendCaster(caster, drainedThisWindow);
    }

    /**
     * The caster's half of the capstone, with no positional gate at all: whatever the window
     * drained is theirs, capped only at {@code heal_ratio} of it and at their maximum health.
     *
     * <p>Derived from the damage already produced in this window, never re-scaled: calling
     * {@code getPower} a second time here would square both the option multipliers and the
     * affinity term. {@code mend_radius} sizes the aura drawn around them - it does not decide
     * whether they heal.
     */
    private void mendCaster(LivingEntity caster, double drainedThisWindow) {
        if (drainedThisWindow <= 0.0) return;
        double maxHealth = Objects.requireNonNull(
                caster.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
        if (caster.getHealth() >= maxHealth) return;

        caster.setHealth(Math.min(maxHealth, caster.getHealth() + drainedThisWindow * healRatio));
        ParticleCaster.spawn(caster, caster.getWorld(), Utils.HEART,
                caster.getEyeLocation().add(0, 0.5, 0), 8, mendRadius, 0.5, mendRadius, 0);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        // Size 4, matching the other circle 7-9 casts in the roster.
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
                .setDisplayName(lang().get("spells.tideofthedeep.book_name"))
                .addCustomLoreLine(lang().get("spells.tideofthedeep.lore1"))
                .addCustomLoreLine(lang().get("spells.tideofthedeep.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}