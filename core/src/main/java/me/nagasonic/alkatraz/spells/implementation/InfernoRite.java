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
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 9 FIRE capstone. For eight seconds, every piece of fire <b>the caster</b> deals
 * detonates whatever else is already burning nearby - so one {@code flame_lance} into a
 * burning pack becomes a screen-wide firestorm.
 *
 * <h2>An eight-second window is not a SUSTAIN field</h2>
 * The rite has <b>no damage timer of any kind</b>. There are exactly two
 * {@link BukkitRunnable}s: the opening ignition ring (12 ticks, particles) and the expiry
 * counter (160 ticks, removes the map entry). Neither calls {@code damageWithSpell}. Every
 * damage event originates in an {@link EntityDamageByEntityEvent} the caster already caused
 * with some other spell - the rite observes, it never initiates.
 *
 * <p>The per-cast ceiling is a charge counter, not a clock: {@code charges--} on each
 * detonation, starting at {@code max_chain_targets} (the yml's {@code hit_count}). A hundred
 * fireballs inside the window still produce three detonations and 85.8 damage. Per
 * individual target it is one detonation, because {@code props.hasHit} retires the body for
 * the whole window.
 *
 * <p>The rite also never <i>ignites</i> anything: a candidate must already have
 * {@code fireTicks > 0}, and a detonation consumes the burn with {@code setFireTicks(0)}. It
 * propagates fires that already exist and eats the fire it carries.
 */
public class InfernoRite extends AttackSpell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);

    /** The detonation itself: the brightest shade, one size above anything else in the roster. */
    private static final Particle.DustOptions RITE_CORE = PALETTE.coreDust();
    /** The arcane half of the rite - the opening ring and nothing else. */
    private static final Particle.DustOptions RITE_ARC = PALETTE.deepDust();

    /** Points on the opening ring. */
    private static final int OPENING_RING_POINTS = 20;

    /** The rite window for every caster currently inside one. Bookkeeping only. */
    private static final Map<UUID, RiteState> rites = new ConcurrentHashMap<>();

    private InfernoRiteConfig config;

    /**
     * The mutable per-cast rite. Deliberately NOT a record: {@code charges} is decremented on
     * every detonation and {@code insideDetonation} is the re-entrancy guard.
     */
    private static final class RiteState {

        private final AttackProperties props;
        private final double chainRadius;
        private final int chainTargetsPerTrigger;
        private final double knockback;
        private final double knockbackY;
        private int charges;
        private boolean insideDetonation;

        RiteState(AttackProperties props, InfernoRiteConfig cfg) {
            this.props = props;
            this.chainRadius = cfg.chainRadius();
            this.chainTargetsPerTrigger = cfg.detonationsPerTrigger();
            this.knockback = cfg.detonationKnockback();
            this.knockbackY = cfg.detonationKnockbackY();
            this.charges = cfg.worstCaseDetonations();
        }
    }

    public InfernoRite(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/inferno_rite.yml");
        Alkatraz.getInstance().saveConfig("spells/inferno_rite_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/inferno_rite.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = InfernoRiteConfig.fromConfig(spellConfig.getValues(false));
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        UUID uuid = caster.getUniqueId();
        if (rites.containsKey(uuid)) return;      // not stackable, so charges cannot be doubled

        InfernoRiteConfig cfg = config;
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster), AttackType.MAGIC);

        rites.put(uuid, new RiteState(props, cfg));
        openRite(caster, cfg);
        armExpiry(caster, uuid, true);

        Utils.sendActionBar(caster, lang().get("spells.infernorite.opened"));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        UUID uuid = caster.getUniqueId();
        if (rites.containsKey(uuid)) return;

        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand), AttackType.MAGIC);

        // A mob's rite is narrower: one detonation per trigger and half the chain radius.
        // The per-cast charge cap is UNCHANGED, so a mob's rite is also inside 85.8.
        InfernoRiteConfig mobCfg = new InfernoRiteConfig(
                config.maxChainTargets(),
                1,
                config.chainRadius() * 0.5,
                config.duration(),
                config.igniteTicks(),
                config.openingRingTicks(),
                config.detonationKnockback(),
                config.detonationKnockbackY());

        rites.put(uuid, new RiteState(props, mobCfg));
        openRite(caster, mobCfg);
        // No action bar and no expiry message: both lang keys are player-facing only, so the
        // expiry runnable is armed with playerFacing false.
        armExpiry(caster, uuid, false);
    }

    /**
     * The opening ignition ring. PARTICLES AND ONE SOUND ONLY - no damage, no
     * {@code getPower} call anywhere in this method.
     */
    private void openRite(LivingEntity caster, InfernoRiteConfig cfg) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.4f, 0.6f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= cfg.openingRingTicks() || caster.isDead()) {
                    cancel();
                    return;
                }
                double progress = (double) ticks / cfg.openingRingTicks();
                double radius = 4.0 * (1.0 - progress);
                Location centre = caster.getLocation().add(0, 0.6 + progress * 1.4, 0);
                for (Location p : ParticleUtils.circle(centre, Math.max(0.3, radius),
                        OPENING_RING_POINTS, centre.getYaw(), 0)) {
                    ParticleCaster.spawn(caster, p.getWorld(), Utils.DUST, p, 0, RITE_ARC);
                }
                ParticleCaster.spawn(caster, centre.getWorld(), Utils.FLAME, centre, 12, 0.5, 0.6, 0.5, 0.02);
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The only other timer in the spell, and it deals nothing: it counts the window out and
     * removes the rite from the map.
     *
     * @param playerFacing whether to send the player-facing expiry message; mobs get neither
     *                     message, because both lang keys are player-facing
     */
    private void armExpiry(LivingEntity caster, UUID uuid, boolean playerFacing) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= config.durationTicks() || caster.isDead()
                        || (caster instanceof Player player && !player.isOnline())) {
                    cancel();
                    rites.remove(uuid);
                    if (playerFacing) {
                        Utils.sendActionBar((Player) caster, lang().get("spells.infernorite.expired"));
                    }
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The load-bearing block. Fires off damage the caster has ALREADY applied, at
     * {@link EventPriority#MONITOR} so it can never observe damage a plugin is about to
     * cancel.
     *
     * <p>{@code insideDetonation} is the re-entrancy guard and it is not optional:
     * {@code detonateChain} calls {@code damageWithSpell}, which produces another
     * {@code EntityDamageByEntityEvent} with the same owner. Without the flag the rite would
     * chain into itself until the charges ran out mid-call, and the 90.0 budget would mean
     * nothing.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCasterFireDamage(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getEntity() instanceof LivingEntity struck) || struck.isDead()) return;

        LivingEntity owner = resolveOwner(e.getDamager());
        if (owner == null) return;
        RiteState state = rites.get(owner.getUniqueId());
        if (state == null) return;
        if (state.charges <= 0) return;
        if (state.insideDetonation) return;
        if (!isFireDamage(e, e.getDamager())) return;

        state.insideDetonation = true;
        try {
            detonateChain(state, owner, struck);
        } finally {
            state.insideDetonation = false;
        }
    }

    /** The owner of a damage event: a player, a mob, or whoever fired the projectile. */
    private static LivingEntity resolveOwner(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Mob m) return m;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof LivingEntity s) return s;
        return null;
    }

    /**
     * Whether this damage was fire. {@code SpellDamageUtil.damageWithSpell} calls
     * {@code target.damage(damage, caster)}, so a {@code flame_lance} hit arrives here with
     * {@code getDamager() == thePlayer} - that is the interaction the capstone is built on.
     */
    private static boolean isFireDamage(EntityDamageEvent e, Entity damager) {
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            return true;
        }
        if (damager instanceof org.bukkit.entity.Fireball) return true;
        if (damager instanceof org.bukkit.entity.WitherSkull) return true;
        return damager != null && damager.getFireTicks() > 0;
    }

    /**
     * The rite's own damage: up to {@code chainTargetsPerTrigger} already-burning bodies
     * nearest the struck one, until the per-cast charge counter runs out.
     */
    private void detonateChain(RiteState state, LivingEntity owner, LivingEntity struck) {
        AttackProperties props = state.props;
        List<LivingEntity> candidates = new ArrayList<>();

        for (LivingEntity candidate : Utils.getNearbyLivingEntities(
                struck.getLocation(), state.chainRadius)) {
            if (candidate.equals(struck) || candidate.equals(owner) || candidate.isDead()) continue;
            if (candidate.getFireTicks() <= 0) continue;      // must ALREADY be burning
            if (props.hasHit(candidate)) continue;            // once per cast, ever
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparingDouble(
                c -> c.getLocation().distanceSquared(struck.getLocation())));

        int spent = 0;
        for (LivingEntity target : candidates) {
            if (spent >= state.chainTargetsPerTrigger || state.charges <= 0) break;
            props.hit(target);
            state.charges--;                                  // THE HARD PER-CAST CAP

            Location at = target.getLocation().add(0, 0.9, 0);
            ParticleCaster.spawn(owner, at.getWorld(), Utils.EXPLOSION_EMITTER, at, 1, 0.3, 0.3, 0.3, 0);
            ParticleCaster.spawn(owner, at.getWorld(), Utils.DUST, at, 0, RITE_CORE);
            ParticleCaster.spawn(owner, at.getWorld(), Utils.LARGE_SMOKE, at, 16, 0.4, 0.5, 0.4, 0.03);
            ParticleCaster.spawn(owner, at.getWorld(), Utils.FLAME, at, 20, 0.4, 0.6, 0.4, 0.02);
            at.getWorld().playSound(at, Sound.ENTITY_BLAZE_DEATH, 1.1f, 0.7f);

            // The wand is null here: there is no wand in scope inside a damage event and
            // inventing one would be a lie. damageWithSpell degrades gracefully on null.
            SpellDamageUtil.damageWithSpell(target,
                    getPower(owner, target, props.getRemainingPower()),
                    owner, null, InfernoRite.this);
            target.setFireTicks(0);                            // the burn is consumed
            Vector push = target.getLocation().toVector()
                    .subtract(struck.getLocation().toVector());
            target.setVelocity(push.normalize().multiply(state.knockback).setY(state.knockbackY));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        rites.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Alkatraz.getInstance()) return;
        // A /alkatraz reload replaces every Spell instance, so the static map needs an
        // explicit clear.
        rites.clear();
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 4.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.FLAME, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.LARGE_SMOKE, location, 30, 0.2, 0.2, 0.2, 0.02);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.infernorite.book_name"))
                .addCustomLoreLine(lang().get("spells.infernorite.lore1"))
                .addCustomLoreLine(lang().get("spells.infernorite.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}