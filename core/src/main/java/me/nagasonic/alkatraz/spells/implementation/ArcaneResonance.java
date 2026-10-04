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
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 9 NONE utility: your next three casts each fire <b>twice</b>, at 120% power.
 *
 * <h2>How 120% is applied - the only correct route</h2>
 * There are three ways to make "120%" happen and two of them are defects.
 * <ul>
 *   <li>{@code getPower(...) * 1.2} at the damage site re-applies the option multipliers and the
 *       affinity term, because squaring is not linear - a 2.24 affinity becomes 3.24^2 ~ 10.5x
 *       instead of 4.48x.</li>
 *   <li>Calling {@code getPower} twice per damage instance makes
 *       {@code SpellAttackContext.power(LivingEntity)} throw
 *       {@code IllegalStateException("getPower applied twice to the same target in one tick")}.</li>
 *   <li><b>Installing a {@code damage x 1.2} MULTIPLY {@link StatModifierImpact} on the profile
 *       is the one that works.</b> {@code AttackSpell.getPower} resolves
 *       {@code getModifiedStat(caster, "damage", power)} <i>before</i> its affinity term, and
 *       {@code Spell.getModifiedStat} applies MULTIPLY after SET and before ADD. So the 1.2 lands
 *       exactly once, in the right order: power x 1.2, then affinity, then resistance - one
 *       {@code getPower} call, one pass through the option modifiers, one pass through the
 *       affinity curve.</li>
 * </ul>
 * <b>This class contains zero {@code getPower} calls.</b> It extends bare {@link Spell}, which has
 * no {@code getPower} to reach for even by accident.
 *
 * <h2>How "fires twice" works</h2>
 * Not by touching a damage path: {@link #onCastFired} re-invokes the <i>stored spell's own</i>
 * {@link Spell#castAction} one tick later. That second firing builds its own
 * {@code AttackProperties} inside its own {@code SpellAttackContext.of(...)}, seeded from raw
 * {@code getBasePower() * getWandPower(...)} - two properties, two contexts, two {@code getPower}
 * calls, one per firing. That is precisely the condition {@code SpellAttackContext} throws on if
 * violated, and precisely the condition this design cannot reach. The one-tick delay also makes
 * the pair read as a double shot rather than a same-frame stutter.
 *
 * <h2>The caps, and where the state lives</h2>
 * {@code active} is a {@code private static Map<UUID, Resonance>} - one entry per online player
 * who currently has resonance armed, at most. It is <b>not persisted</b>: {@code spellModifiers} is
 * an in-memory list wiped by {@code clearAllSpellModifiers()} at login, so a crash cannot leave
 * anyone permanently at 120%. It is cleared on consumption, on expiry, on death and on quit - the
 * last two through listeners, which is why this class implements {@link Listener}.
 *
 * <p>It would arguably belong on {@link MagicProfile} beside {@code warpPoints}, the same argument
 * {@code echo_cast}'s history buffer makes. The difference is that resonance's state is
 * <i>strictly transient by design</i> (20 seconds, consumed by the next three casts) whereas a
 * cast history is something a player would reasonably expect to survive a relog. <b>Flagged, not
 * requested.</b>
 */
public class ArcaneResonance extends Spell implements Listener {

    /** The stat the amplification rides. {@code AttackSpell.getPower} reads it before affinity. */
    private static final String DAMAGE_STAT = "damage";

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** The arming helix and the amplified twin's core flash: the leading edge. */
    private static final Particle.DustOptions RESONANCE_DUST = PALETTE.coreDust();
    /** The amplified twin's echo sphere: a shade back so the twin does not out-shout the original. */
    private static final Particle.DustOptions RESONANCE_ECHO_DUST = PALETTE.accentDust();

    /** Helix shape at arming: loops, radius, height, points per loop. */
    private static final int ARM_LOOPS = 3;
    private static final double ARM_RADIUS = 1.4;
    private static final double ARM_HEIGHT = 2.0;
    private static final int ARM_POINTS_PER_LOOP = 18;

    /** The echo sphere drawn once per amplified cast. */
    private static final double ECHO_RADIUS = 0.9;
    private static final int ECHO_POINTS = 40;

    /** Armed resonance, by caster. At most one entry per online player. */
    private static final Map<UUID, Resonance> active = new ConcurrentHashMap<>();

    /**
     * One armed resonance: the remaining charge count, and the exact
     * {@link StatModifierImpact} instances to un-apply when it runs out. Carrying the instances
     * rather than re-deriving them is what guarantees the symmetric removal - {@code apply} and
     * {@code unapply} are matched by {@code SpellModifier} record equality.
     */
    private record Resonance(int charges, List<StatModifierImpact> installed) {
    }

    private ArcaneResonanceConfig config;

    public ArcaneResonance(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        // No options file: power_multiplier, charge_count and charge_expiry_seconds are the entire
        // balance surface of a spell whose output is 2.4x another spell's, and a player-side option
        // on any of them is a straight unconditional damage multiplier with no defensive counterpart.
        Alkatraz.getInstance().save("spells/arcane_resonance.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/arcane_resonance.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = ArcaneResonanceConfig.fromConfig(spellConfig.getValues(false));
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

        // Unstackable. Without this the modifier list would compound on every re-arm.
        if (active.containsKey(uuid)) {
            Utils.sendActionBar(caster, lang().get("spells.arcaneresonance.already_active"));
            cancelCast(caster);
            return;
        }

        // Iterate the REGISTRY, not a hand-written list, so a spell added in any future wave is
        // amplified without touching this file.
        List<StatModifierImpact> installed = new ArrayList<>();
        for (Spell target : SpellRegistry.getAllSpellsByID().values()) {
            if (target == null || target == this) continue;
            if (config.isSkipped(target.getId())) continue;
            StatModifierImpact mod = new StatModifierImpact(
                    target, DAMAGE_STAT, config.powerMultiplier(), StatModifierImpact.ModifierType.MULTIPLY);
            mod.apply(caster);
            installed.add(mod);
        }

        active.put(uuid, new Resonance(config.chargeCount(), List.copyOf(installed)));

        new BukkitRunnable() {
            @Override
            public void run() {
                expire(uuid);
            }
        }.runTaskLater(Alkatraz.getInstance(), config.expiryTicks());

        spawnResonanceArm(caster);
        caster.getWorld().playSound(caster.getEyeLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 0.6f);
        Utils.sendActionBar(caster, lang().get("spells.arcaneresonance.armed",
                "count", String.valueOf(config.chargeCount())));
    }

    /**
     * The amplified twin. MONITOR, so it runs after every other plugin's decision, and it skips a
     * cancelled cast so a call-off does not spend a charge.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCastFired(PlayerCastEvent e) {
        if (e.isCancelled()) return;
        Player caster = e.getCaster();
        if (caster == null) return;
        Resonance resonance = active.get(caster.getUniqueId());
        if (resonance == null) return;

        Spell spell = e.getSpell();
        if (spell == null || config.isSkipped(spell.getId())) return;

        ItemStack wand = e.getWand();

        // One tick later on purpose: PlayerCastEvent fires immediately before the original
        // castAction, so scheduling at 1L puts the twin on the next tick. That reads as a double
        // shot rather than a same-frame stutter, and guarantees the two firings are never in one
        // tick - so their SpellParticleComponent registrations, AttackProperties hit sets and
        // SpellAttackContext tick counters cannot interact at all.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isOnline() || caster.isDead()) return;
                spawnResonanceEcho(caster);
                // Re-invoke the STORED SPELL'S OWN cast path. Its AttackProperties and its own
                // getPower are its business; we never call getPower and never scale its output.
                spell.castAction(caster, wand);
            }
        }.runTaskLater(Alkatraz.getInstance(), 1L);

        consumeCharge(caster.getUniqueId());
    }

    /** Exactly one charge per non-skipped cast, spent whether or not the twin found a target. */
    private void consumeCharge(UUID uuid) {
        Resonance resonance = active.get(uuid);
        if (resonance == null) return;
        if (resonance.charges() <= 1) {
            active.remove(uuid);
            unapply(uuid, resonance);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Utils.sendActionBar(player, lang().get("spells.arcaneresonance.spent"));
            }
            return;
        }
        int left = resonance.charges() - 1;
        // The same StatModifierImpact instances are carried through, so the final un-apply removes
        // exactly what the arming installed - no re-derivation, no drift.
        active.put(uuid, new Resonance(left, resonance.installed()));
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            Utils.sendActionBar(player, lang().get("spells.arcaneresonance.charges",
                    "count", String.valueOf(left)));
        }
    }

    /** Removal path for the expiry timer, the last charge, death and quit. */
    private void expire(UUID uuid) {
        Resonance resonance = active.remove(uuid);
        if (resonance == null) return;
        unapply(uuid, resonance);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            Utils.sendActionBar(player, lang().get("spells.arcaneresonance.expired"));
        }
    }

    /**
     * Removes the installed modifiers.
     *
     * <p>The {@link Resonance} is passed in rather than looked up: every caller has just removed
     * the entry from {@code active}, so a lookup here would always miss and the modifiers would
     * survive until the next login.
     *
     * <p>The {@code !isOnline} guard is bounded and harmless if it ever fires -
     * {@code StatModifierImpact.unapply} needs a {@code Player} to reach the profile and there is
     * no UUID-only accessor - because {@code spellModifiers} is transient and wiped by
     * {@code clearAllSpellModifiers()} at login. {@link #onQuit} covers the realistic path.
     */
    private void unapply(UUID uuid, Resonance resonance) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;
        for (StatModifierImpact mod : resonance.installed()) {
            mod.unapply(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        expire(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        expire(e.getPlayer().getUniqueId());
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