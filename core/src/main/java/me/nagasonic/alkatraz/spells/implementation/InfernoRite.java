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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
 * Circle 9 FIRE capstone. The rite <b>starts its own fire</b>: on its opening tick it
 * ignites an arc in front of the caster, and for twelve seconds a scheduled task walks the
 * burning bodies, detonating one per pass until the charge counter is empty and
 * re-igniting whatever each detonation touches. The firestorm spreads outward on its own -
 * one cast into a burning pack is a screen-wide firestorm, and so is one cast into a cold
 * one.
 *
 * <h2>The ceiling is a charge counter, not the clock</h2>
 * {@code charges--} on every detonation, starting at {@code hit_count}. A cast is three
 * detonations and 171.6 damage however long the window runs and however much fire is thrown
 * into it. Per individual target it is one detonation, because {@code props.hasHit} retires
 * the body for the whole window.
 *
 * <p>There are exactly two {@link BukkitRunnable}s: the opening (the ignition arc plus a
 * 12-tick particle ring) and the window task, which walks the chain and counts the window
 * out at the same time.
 */
public class InfernoRite extends AttackSpell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);

    /** The detonation itself: the brightest shade, one size above anything else in the roster. */
    private static final Particle.DustOptions RITE_CORE = PALETTE.coreDust();
    /** The arcane half of the rite - the opening ring and nothing else. */
    private static final Particle.DustOptions RITE_ARC = PALETTE.deepDust();

    /** Points on the opening ring. */
    private static final int OPENING_RING_POINTS = 20;

    /** How often the window task walks the burning bodies for one more detonation. */
    /** Pace the three budgeted detonations across the twelve-second burn window. */
    private static final int CHAIN_INTERVAL_TICKS = 80;

    /** cos(60 degrees): the opening arc is the 120 degree cone in front of the caster. */
    private static final double ARC_DOT = 0.5;

    /** The rite window for every caster currently inside one. Bookkeeping only. */
    private static final Map<UUID, RiteState> rites = new ConcurrentHashMap<>();

    private InfernoRiteConfig config;

    /**
     * The mutable per-cast rite. Deliberately NOT a record: {@code charges} is decremented on
     * every detonation and {@code insideDetonation} is the re-entrancy guard. It holds the
     * whole per-cast {@link InfernoRiteConfig} so the values each path narrowed survive: a
     * player's option-modified ones and a mob's narrower ones alike.
     */
    private static final class RiteState {

        private final AttackProperties props;
        private final InfernoRiteConfig cfg;
        private int charges;
        private boolean insideDetonation;

        RiteState(AttackProperties props, InfernoRiteConfig cfg) {
            this.props = props;
            this.cfg = cfg;
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

        // The two options write `chain_radius` and `chain_targets_per_trigger` stat modifiers,
        // so the per-cast config is built from the modified stats rather than the raw yml
        // record. Same shape as the mob variant below, which narrows the same two fields.
        InfernoRiteConfig cfg = new InfernoRiteConfig(
                config.maxChainTargets(),
                (int) getModifiedStat(caster, "chain_targets_per_trigger",
                        config.chainTargetsPerTrigger()),
                getModifiedStat(caster, "chain_radius", config.chainRadius()),
                config.duration(),
                config.igniteTicks(),
                config.openingRingTicks(),
                config.detonationKnockback(),
                config.detonationKnockbackY());
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster), AttackType.MAGIC);

        rites.put(uuid, new RiteState(props, cfg));
        openRite(caster, cfg);
        armWindow(caster, uuid, true);

        Utils.sendActionBar(caster, lang().get("spells.infernorite.opened"));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        UUID uuid = caster.getUniqueId();
        if (rites.containsKey(uuid)) return;

        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand), AttackType.MAGIC);

        // A mob's rite is narrower: it reaches one body per detonation instead of two and its
        // ignition arc is half as wide. The per-cast charge cap is UNCHANGED, so a mob's rite
        // is also inside 171.6 - and it ignites its own targets just as a player's does.
        // No getModifiedStat here: it takes a Player, so a mob reads the raw yml values.
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
        // window runnable is armed with playerFacing false.
        armWindow(caster, uuid, false);
    }

    /**
     * The opening: the ignition arc, then the particle ring that tells the caster where the
     * fire went. PARTICLES, ONE SOUND AND {@code setFireTicks} ONLY - no damage and no
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
                if (ticks == 0) igniteArc(caster, cfg);
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
     * The rite's own ignition: every living body inside {@code chain_radius} inside the cone in
     * front of the caster catches for {@code ignite_ticks}. <b>Nothing has to be burning
     * already</b> - that precondition was the whole bug, and the old rite dealt nothing at all
     * on a cold cast.
     */
    private void igniteArc(LivingEntity caster, InfernoRiteConfig cfg) {
        Location origin = caster.getLocation();
        Vector forward = origin.getDirection().setY(0).normalize();
        for (LivingEntity body : Utils.getNearbyLivingEntities(origin, cfg.chainRadius())) {
            if (body.equals(caster) || body.isDead()) continue;
            Vector to = body.getLocation().toVector().subtract(origin.toVector()).normalize();
            if (forward.dot(to) < ARC_DOT) continue;        // behind or beside the caster
            body.setFireTicks(cfg.igniteTicks());
        }
    }

    /**
     * The window task. Every {@link #CHAIN_INTERVAL_TICKS} ticks it walks the burning bodies
     * for one more detonation; when the window closes it removes the rite from the map.
     *
     * @param playerFacing whether to send the player-facing expiry message; mobs get neither
     *                     message, because both lang keys are player-facing
     */
    private void armWindow(LivingEntity caster, UUID uuid, boolean playerFacing) {
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
                if (ticks % CHAIN_INTERVAL_TICKS == 0) {
                    spreadChain(caster, rites.get(uuid));
                }
                if (ticks % 10 == 0) {
                    RiteState state = rites.get(uuid);
                    if (state != null) {
                        for (LivingEntity body : freshBodies(state.props, caster, caster.getLocation(),
                                state.cfg.chainRadius())) {
                            if (body.getFireTicks() > 0) {
                                Location flame = body.getLocation().add(0, 0.9, 0);
                                ParticleCaster.spawn(caster, flame.getWorld(), Utils.FLAME, flame,
                                        5, 0.25, 0.35, 0.25, 0.01);
                            }
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One pass of the self-driven chain: the nearest un-retired burning body detonates and
     * re-ignites its neighbours, and that re-ignition is what the next pass walks. One
     * detonation per pass is what makes the chain creep outward instead of spending all three
     * charges on whatever happened to stand in front of the caster on tick one.
     *
     * <p>{@code insideDetonation} is the re-entrancy guard and it is not optional: a
     * detonation's own damage and knockback must never walk the chain again inside the same
     * pass, or the charges run out mid-call and the 180.0 budget would mean nothing.
     */
    private void spreadChain(LivingEntity caster, RiteState state) {
        if (state == null || state.charges <= 0 || state.insideDetonation) return;

        LivingEntity target = null;
        for (LivingEntity body : freshBodies(state.props, caster, caster.getLocation(),
                state.cfg.chainRadius())) {
            if (body.getFireTicks() > 0) {                 // the rite's own claim on the body
                target = body;
                break;
            }
        }
        if (target == null) return;

        state.insideDetonation = true;
        try {
            state.props.hit(target);                        // retired for the whole window
            state.charges--;                                // THE HARD PER-CAST CAP
            detonate(state, caster, target);
        } finally {
            state.insideDetonation = false;
        }
    }

    /** One detonation: the damage, the rite's particles, the knockback, and the spread. */
    private void detonate(RiteState state, LivingEntity owner, LivingEntity target) {
        InfernoRiteConfig cfg = state.cfg;
        Location at = target.getLocation().add(0, 0.9, 0);
        ParticleCaster.spawn(owner, at.getWorld(), Utils.EXPLOSION_EMITTER, at, 1, 0.3, 0.3, 0.3, 0);
        ParticleCaster.spawn(owner, at.getWorld(), Utils.DUST, at, 0, RITE_CORE);
        ParticleCaster.spawn(owner, at.getWorld(), Utils.LARGE_SMOKE, at, 16, 0.4, 0.5, 0.4, 0.03);
        ParticleCaster.spawn(owner, at.getWorld(), Utils.FLAME, at, 20, 0.4, 0.6, 0.4, 0.02);
        at.getWorld().playSound(at, Sound.ENTITY_BLAZE_DEATH, 1.1f, 0.7f);

        // The wand is null here: nothing in the window task has one in scope and inventing one
        // would be a lie. damageWithSpell degrades gracefully on null, and it is the only call
        // that keeps the on_spell_hit trigger pipeline and Protection's region gating.
        SpellDamageUtil.damageWithSpell(target,
                getPower(owner, target, state.props.getRemainingPower()),
                owner, null, InfernoRite.this);

        Vector push = target.getLocation().toVector().subtract(owner.getLocation().toVector());
        target.setVelocity(push.normalize().multiply(cfg.detonationKnockback()).setY(cfg.detonationKnockbackY()));

        // The spread. chain_targets_per_trigger is how many bodies one detonation re-ignites,
        // NOT how many it detonates: the charge counter is the only cap on detonations.
        int spread = 0;
        for (LivingEntity neighbour : freshBodies(state.props, owner, target.getLocation(),
                cfg.chainRadius())) {
            if (spread >= cfg.chainTargetsPerTrigger()) break;
            neighbour.setFireTicks(cfg.igniteTicks());
            spread++;
        }
    }

    /**
     * Living bodies near {@code centre} that this cast has not retired yet, nearest first. The
     * caster and the dead are always out, and {@code props.hasHit} keeps a body out for the
     * whole window no matter how often the chain reaches it.
     */
    private static List<LivingEntity> freshBodies(AttackProperties props, LivingEntity owner,
                                                   Location centre, double radius) {
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity candidate : Utils.getNearbyLivingEntities(centre, radius)) {
            if (candidate.equals(owner) || candidate.isDead()) continue;
            if (props.hasHit(candidate)) continue;
            found.add(candidate);
        }
        found.sort(Comparator.comparingDouble(c -> c.getLocation().distanceSquared(centre)));
        return found;
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
