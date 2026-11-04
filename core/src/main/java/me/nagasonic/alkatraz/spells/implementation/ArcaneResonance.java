package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.PlayerCastEvent;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SustainEffect;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 9 NONE utility: a sustained standing wave. While it is live, every cast the caster makes
 * <b>fires twice</b>, the second landing at 125% of the first. Cast it again to stop it; it drains
 * {@code upkeep_mana_per_second} for as long as it is on and ends by itself when the mana runs out.
 *
 * <h2>How 125% is applied - the only correct route</h2>
 * There are three ways to make "125%" happen and two of them are defects.
 * <ul>
 *   <li>{@code getPower(...) * 1.25} at the damage site re-applies the option multipliers and the
 *       affinity term, because squaring is not linear - a 2.24 affinity becomes 3.24^2 ~ 10.5x
 *       instead of 5.6x.</li>
 *   <li>Calling {@code getPower} twice per damage instance makes
 *       {@code SpellAttackContext.power(LivingEntity)} throw
 *       {@code IllegalStateException("getPower applied twice to the same target in one tick")}.</li>
 *   <li><b>Installing a {@code damage x 1.25} MULTIPLY {@link StatModifierImpact} on the profile
 *       is the one that works.</b> {@code AttackSpell.getPower} resolves
 *       {@code getModifiedStat(caster, "damage", power)} <i>before</i> its affinity term, and
 *       {@code Spell.getModifiedStat} applies MULTIPLY after SET and before ADD. So the 1.25 lands
 *       exactly once, in the right order: power x 1.25, then affinity, then resistance - one
 *       {@code getPower} call, one pass through the option modifiers, one pass through the
 *       affinity curve.</li>
 * </ul>
 * <b>This class contains zero {@code getPower} calls.</b> It extends bare {@link Spell}, which has
 * no {@code getPower} to reach for even by accident.
 *
 * <h2>How "fires twice" works</h2>
 * Not by touching a damage path: {@link #onCastFired} re-invokes the <i>stored spell's own</i>
 * {@link Spell#castAction} {@code duplicate_delay_ticks} later. That second firing builds its own
 * {@code AttackProperties} inside its own {@code SpellAttackContext.of(...)}, seeded from raw
 * {@code getBasePower() * getWandPower(...)} - two properties, two contexts, two {@code getPower}
 * calls, one per firing. That is precisely the condition {@code SpellAttackContext} throws on if
 * violated, and precisely the condition this design cannot reach. The delay also makes the pair
 * read as a double shot rather than a same-frame stutter, and guarantees the two firings are never
 * in one tick - so their SpellParticleComponent registrations, AttackProperties hit sets and
 * SpellAttackContext tick counters cannot interact at all.
 *
 * <h2>The lifetime, and where the state lives</h2>
 * The toggle itself belongs to {@link SustainEffect}, which owns the per-tick body, the mana upkeep
 * and the single teardown, so death, quit, world change, empty mana and a second cast all converge
 * on one exit path. All five circle-9 sustains share that one slot, so a cast made while another
 * sustain is running comes back {@code BLOCKED} and changes nothing; {@code active} below is
 * therefore also this spell's ownership marker, and the only thing that may answer "is my sustain
 * still up". What {@link SustainEffect} deliberately does not know about is this spell's own
 * state, so {@code active} is a {@code private static Map<UUID, Resonance>} - the BossBar and the
 * exact {@link StatModifierImpact} instances installed on toggle-on. Carrying the instances rather
 * than re-deriving them is what guarantees the symmetric removal, because {@code apply} and
 * {@code unapply} are matched by {@code SpellModifier} record equality.
 *
 * <p>It is <b>not persisted</b>: {@code spellModifiers} is an in-memory list wiped by
 * {@code clearAllSpellModifiers()} at login, so a crash cannot leave anyone permanently at 125%.
 */
public class ArcaneResonance extends Spell implements Listener {

    /** The stat the amplification rides. {@code AttackSpell.getPower} reads it before affinity. */
    private static final String DAMAGE_STAT = "damage";

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** The sustain's own colour, handed to the shared VFX builders so they never pick their own. */
    private static final Color AURA_COLOR = Color.fromRGB(190, 90, 255);

    /** The arming helix and the amplified twin's core flash: the leading edge. */
    private static final Particle.DustOptions RESONANCE_DUST = new Particle.DustOptions(AURA_COLOR, 1.0F);
    /** The amplified twin's echo sphere: a shade back so the twin does not out-shout the original. */
    private static final Particle.DustOptions RESONANCE_ECHO_DUST = new Particle.DustOptions(
            Color.fromRGB(225, 170, 255), 1.0F);

    /** Helix shape at arming: loops, radius, height, points per loop. */
    private static final int ARM_LOOPS = 3;
    private static final double ARM_RADIUS = 1.4;
    private static final double ARM_HEIGHT = 2.0;
    private static final int ARM_POINTS_PER_LOOP = 18;

    /** The echo sphere drawn once per amplified cast. */
    private static final double ECHO_RADIUS = 0.9;
    private static final int ECHO_POINTS = 40;

    /**
     * The live sustain, by caster: what the teardown has to take back down. At most one entry per
     * online player, and it is always populated before {@link SustainEffect} can run the first tick.
     */
    private static final Map<UUID, Resonance> active = new ConcurrentHashMap<>();

    private record Resonance(BossBar bar, List<StatModifierImpact> installed) {
    }

    private ArcaneResonanceConfig config;
    private int durationTicks = 840;

    public ArcaneResonance(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        // No options file: power_multiplier, charge_count, duplicate_delay_ticks and
        // upkeep_mana_per_second are the entire balance surface of a spell whose output is 2.5x
        // another spell's, and a player-side option on any of them is a straight unconditional
        // damage multiplier with no defensive counterpart.
        Alkatraz.getInstance().save("spells/arcane_resonance.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/arcane_resonance.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = ArcaneResonanceConfig.fromConfig(spellConfig.getValues(false));
        this.durationTicks = Math.max(1, (int) Math.round(
                spellConfig.getDouble("duration_seconds", 42.0) * 20.0));
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) {
            cancelCast(caster);
            return;
        }
        UUID uuid = caster.getUniqueId();
        if (ProfileManager.getProfile(caster, MagicProfile.class) == null) {
            cancelCast(caster);
            return;
        }

        // All five circle-9 sustains share ONE slot, so a third outcome exists: BLOCKED means a
        // different sustain owns the slot and nothing at all happened to it. Treating that like a
        // normal toggle-off would collapse a spell that is still running and arm a bar over it.
        SustainEffect.Toggle result = SustainEffect.toggle(caster, config.upkeepManaPerSecond(),
                this, this::sustainBody, player -> teardown(uuid, player));

        if (result == SustainEffect.Toggle.BLOCKED) {
            // Blocked changed nothing, so Spell.cast must refund this cast and skip cooldown/mastery.
            cancelCast(caster);
            Utils.sendActionBar(caster, lang().get("spells.sustain.blocked"));
            return;
        }

        if (result == SustainEffect.Toggle.CLOSED) {
            // Refund what cast() actually took, not the base cost - Spell.cast charges
            // getModifiedManaCost, so a mana_cost modifier would otherwise hand the caster money.
            StatUtils.addMana(caster, getModifiedManaCost(caster));
            spawnResonanceClose(caster);
            return;
        }

        // Iterate the REGISTRY, not a hand-written list, so a spell added in any future wave is
        // amplified without touching this file. Installed ONCE per sustain: reinstalling per cast
        // is what made the old version read as nothing happening at all.
        active.put(uuid, new Resonance(openBar(caster), installAmplification(caster)));

        spawnResonanceArm(caster);
        caster.getWorld().playSound(caster.getEyeLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 0.6f);
        Utils.sendActionBar(caster, lang().get("spells.arcaneresonance.armed",
                "count", String.valueOf(config.chargeCount())));
    }

    /**
     * Installs the {@code damage x power_multiplier} MULTIPLY modifier on every registered spell
     * outside the skip list.
     *
     * @return the exact instances that were applied, for the teardown to remove
     */
    private List<StatModifierImpact> installAmplification(Player caster) {
        List<StatModifierImpact> installed = new ArrayList<>();
        for (Spell target : SpellRegistry.getAllSpellsByID().values()) {
            if (target == null || target == this) continue;
            if (config.isSkipped(target.getId())) continue;
            StatModifierImpact mod = new StatModifierImpact(
                    target, DAMAGE_STAT, config.powerMultiplier(), StatModifierImpact.ModifierType.MULTIPLY);
            mod.apply(caster);
            installed.add(mod);
        }
        return List.copyOf(installed);
    }

    /** The BossBar, already showing its first title so it never renders a raw frame. */
    private BossBar openBar(Player caster) {
        BossBar bar = Bukkit.createBossBar("", BarColor.PINK, BarStyle.SEGMENTED_10);
        bar.addPlayer(caster);
        updateBar(caster, bar);
        return bar;
    }

    /** Remaining seconds of upkeep, and how much of the pool is still behind them. */
    private void updateBar(Player caster, BossBar bar) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        double upkeep = config.upkeepManaPerSecond();
        int seconds = upkeep > 0.0 ? (int) Math.floor(profile.getMana() / upkeep) : 0;
        bar.setTitle(lang().get("spells.arcaneresonance.sustain",
                "seconds", String.valueOf(seconds),
                "upkeep", String.valueOf((int) upkeep)));
        bar.setProgress(Math.max(0.0, Math.min(1.0, profile.getMana() / Math.max(1.0, profile.getMaxMana()))));
    }

    /**
     * The per-tick body. Always returns true: the sustain ends through {@link SustainEffect} alone,
     * so this never has to reason about its own teardown.
     */
    private boolean sustainBody(Player caster, int elapsedTicks) {
        double spin = elapsedTicks * 0.22;
        Location feet = caster.getLocation();
        SpellVfx.selfAura(AURA_COLOR, caster, caster, 1.3, 14, spin);
        SpellVfx.orbit(AURA_COLOR, caster, feet.clone().add(0.0, 1.4, 0.0), 0.6, 1.1, 4, -spin * 2.0);
        if (elapsedTicks == 0) {
            SpellVfx.nova(AURA_COLOR, caster, feet.clone().add(0.0, 1.0, 0.0), 2.2, 48);
        }
        Resonance resonance = active.get(caster.getUniqueId());
        if (resonance != null) {
            updateBar(caster, resonance.bar());
        }
        return elapsedTicks + 1 < durationTicks;
    }

    /**
     * The single exit path, run by {@link SustainEffect} exactly once whatever ended the sustain.
     *
     * <p>{@code caster} is null when the player quit and is already off the online list, so the
     * UUID-keyed entry and the BossBar always come down and only the un-apply is guarded.
     */
    private void teardown(UUID uuid, Player caster) {
        Resonance resonance = active.remove(uuid);
        if (resonance == null) return;
        resonance.bar().removeAll();
        if (caster == null || !caster.isOnline()) return;
        for (StatModifierImpact mod : resonance.installed()) {
            mod.unapply(caster);
        }
        if (!caster.isDead()) {
            Utils.sendActionBar(caster, lang().get("spells.arcaneresonance.expired"));
        }
    }

    /**
     * The amplified twin. MONITOR, so it runs after every other plugin's decision, and it skips a
     * cancelled cast so a call-off does not spend a charge.
     *
     * <p>Charges REFRESH rather than decrement: holding the sustain can never waste it, so nothing
     * here ever reaches zero and no cast can consume the amplification on its way out.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCastFired(PlayerCastEvent e) {
        if (e.isCancelled()) return;
        Player caster = e.getCaster();
        if (caster == null) return;
        if (!active.containsKey(caster.getUniqueId())) return;

        Spell spell = e.getSpell();
        if (spell == null || config.isSkipped(spell.getId()) || !config.isDuplicable(spell.getId())) return;

        ItemStack wand = e.getWand();
        Bukkit.getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
            // The caster may have turned the sustain off inside the delay; nothing is amplified
            // any more, so the twin must not land. `active` is the ownership test - it holds an
            // entry only while THIS spell's sustain is live and teardown removes exactly that
            // entry. SustainEffect.isActive cannot be used here: it answers "is any of the five
            // circle-9 sustains live", so resonance ending and skywrath starting inside the delay
            // would still let the twin land on an unamplified cast.
            if (!active.containsKey(caster.getUniqueId())) return;
            if (!caster.isOnline() || caster.isDead()) return;
            spawnResonanceEcho(caster);
            // Re-invoke the STORED SPELL'S OWN cast path. Its AttackProperties and its own
            // getPower are its business; we never call getPower and never scale its output.
            spell.castAction(caster, wand);
        }, config.duplicateDelayTicks());

        Utils.sendActionBar(caster, lang().get("spells.arcaneresonance.charges",
                "count", String.valueOf(config.chargeCount())));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        SustainEffect.stop(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        SustainEffect.stop(e.getPlayer().getUniqueId());
    }

    private void spawnResonanceArm(Player caster) {
        Location origin = caster.getLocation().add(0, 1, 0);
        for (Location point : ParticleUtils.createHelix(origin, ARM_LOOPS, ARM_RADIUS,
                ARM_HEIGHT, ARM_POINTS_PER_LOOP)) {
            ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, RESONANCE_DUST);
        }
        Location eye = caster.getEyeLocation();
        ParticleCaster.spawn(caster, eye.getWorld(), Utils.END_ROD, eye, 60, 0.5, 0.8, 0.5, 0.02);
    }

    private void spawnResonanceEcho(Player caster) {
        Location eye = caster.getEyeLocation();
        for (Location point : ParticleUtils.sphere(eye, ECHO_RADIUS, ECHO_POINTS)) {
            ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, RESONANCE_ECHO_DUST);
        }
        ParticleCaster.spawn(caster, eye.getWorld(), Utils.WITCH, eye, 20, 0.4, 0.4, 0.4, 0.0);
        caster.getWorld().playSound(eye, Sound.ENTITY_EVOKER_CELEBRATE, 0.5f, 1.6f);
    }

    /** The collapse: the aura flashes inward once and the conduit cue goes flat. The teardown owns
     * the message, so every other end path says the same thing. */
    private void spawnResonanceClose(Player caster) {
        Location eye = caster.getEyeLocation();
        SpellVfx.nova(AURA_COLOR, caster, eye, 0.9, 24);
        caster.getWorld().playSound(eye, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.8f, 0.6f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Arcane Resonance is player-only - it is a modifier on a MagicProfile's damage stat, and a
        // mob has no MagicProfile. Mob casts are consequently never amplified, which is inherited
        // behaviour anyway: AttackSpell.getModifiedStat only applies for a Player caster.
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 2.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.arcaneresonance.book_name"))
                .addCustomLoreLine(lang().get("spells.arcaneresonance.lore1"))
                .addCustomLoreLine(lang().get("spells.arcaneresonance.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
