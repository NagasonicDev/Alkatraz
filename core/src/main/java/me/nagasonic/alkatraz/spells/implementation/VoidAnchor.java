package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.mobs.MagicAiService;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ChannelRegistry;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 8 DARK utility - dark's <b>Necromancy</b> pillar, and with it the
 * alternating ladder is complete: servants at circles 4 ({@code summon_zombies}),
 * 6 ({@code skeletal_knights}) and 8; curses at 3 ({@code dark_tendrils}), 5
 * ({@code shadow_realm}), 7 ({@code umbral_volley}) and 9 ({@code oblivion}).
 *
 * <p>The caster kneels at a chosen spot and tears an <b>anchor</b> out of the
 * world: a channeled rite broken by moving, by being hit, by quitting, or by plugin
 * shutdown. On completion a <b>bound horror</b> is pulled up out of the ground - a
 * wither skeleton that cannot leave its anchor, lashes a tentacle of shadow at
 * everything that comes inside six blocks, and holds what it catches there while its
 * own steel does the killing.
 *
 * <h2>Why {@code AttackSpell}, and where its damage actually lands</h2>
 * The bound horror's melee is real damage this spell causes, so the base class is
 * {@link AttackSpell} and not bare {@code Spell} - the same choice the other two
 * rungs of dark's necromancy ladder make: {@code summon_zombies} (circle 4) and
 * {@code skeletal_knights} (circle 6) both extend it. {@code getPower} is called
 * exactly once per spawn, to scale {@code horror_power} on its way into
 * {@code Attribute.GENERIC_ATTACK_DAMAGE}; that is the pillar's established shape and
 * it is the only route by which a {@code damage} spell option or the caster's DARK
 * affinity can reach the servant's steel.
 *
 * <p>There is still <b>no {@code power:} key</b> and still no
 * {@link me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties}.
 * {@code AttackSpell.loadCommonConfig} reads {@code power} into {@code basePower},
 * which is therefore 0.0 here and is never read: this class never calls the
 * no-argument {@code getPower()} and never passes {@code basePower} anywhere.
 * {@code SpellBudgetTest.utilitySpellsCarryNoDamageKey} asserts the absent key, and
 * {@link #castAction} deals no damage of its own - {@code SpellDamageUtil} is never
 * called from this file. The lash shoves, slows and blinds; every point of damage is
 * the servant's own {@code melee_attack} goal reading the attribute this spell set.
 *
 * <p>{@code onHitBarrier} and {@code onCountered} are abstract on {@code AttackSpell}
 * but unreachable here, because this spell registers no spell component of any kind -
 * the servant's melee is vanilla mob AI, not a spell projectile. They are kept
 * spell-flavoured rather than empty so a future component registration cannot land
 * with no feedback at all.
 *
 * <p>{@code horror_power} is deliberately <b>not</b> a {@code power:} value and is
 * not drawn from the section 6 ladder: the ladder's circle-8 22.7 describes a
 * per-cast burst line, and a servant that swings on vanilla cooldowns for thirty
 * seconds would blow any per-cast budget immediately. The precedent chain is
 * {@code summon_zombies.zombie_power: 2.0} (circle 4, three minions) to
 * {@code skeletal_knights.knight_power: 6.0} (circle 6, three knights) to
 * {@code horror_power: 10.0} (circle 8, <b>one</b> horror). Fewer servants, each
 * individually far stronger, is the right shape for a capstone-tier summon.
 *
 * <h2>Interruption</h2>
 * The channel is registered with the shared {@link ChannelRegistry}, which owns the
 * move / damage / quit / shutdown quadruple. A hand-rolled {@code channels} map plus
 * its own three handlers would duplicate all of that and would miss plugin shutdown,
 * which the repo's cleanup rule requires. {@code move_cancel_threshold} is likewise
 * read by {@link WindUp#moveCancelThreshold}, not here.
 *
 * <h2>What this spell never does</h2>
 * Dark takes power, it never gives it. No healing, no shielding, no
 * {@code ABSORPTION}, no {@code RESISTANCE}, no boss bar, and no effect of any kind on
 * the caster. The two potion effects it applies are {@code SLOW} and
 * {@code BLINDNESS}, and both go on the <em>victims</em> the horror lashes at - never
 * the caster, never the horror. {@code setHealth(...)} is never called anywhere in
 * this class; the only health-shaped line is a {@code GENERIC_MAX_HEALTH}
 * <i>stat</i> on a hostile mob, and {@code getMaxHealth()} is never called either.
 */
public class VoidAnchor extends AttackSpell implements Listener {

    /**
     * This spell's colour, handed to the wind-up telegraph that precedes its payload.
     *
     * <p>Seeded with the `DARK` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the charge-up.
     */
    private static final Color CAST_COLOR = Color.fromRGB(106, 13, 173);

    /**
     * This spell's on-screen identity is <b>near-black with a deep violet ring</b>,
     * deliberately the darkest of the three DARK spells and distinguishable at a
     * glance from {@code umbral_volley}'s bright {@code RGB(96,0,168)} and
     * {@code oblivion}'s crimson. Hoisted so a per-tick draw allocates nothing.
     */
    private static final Color ANCHOR_VIOLET = Color.fromRGB(48, 0, 72);
    private static final Particle.DustOptions ANCHOR_DUST =
            new Particle.DustOptions(ANCHOR_VIOLET, 0.9F);
    private static final Particle.DustOptions SIGIL_DUST =
            new Particle.DustOptions(Color.fromRGB(24, 0, 36), 0.6F);

    /** Radius of the channel's floor sigil, in blocks. */
    private static final double SIGIL_RADIUS = 1.6;
    /** Radius of the circle drawn under a settled horror, in blocks. */
    private static final double ANCHOR_CIRCLE_RADIUS = 1.2;
    /** Points on both rings. */
    private static final int RING_POINTS = 6;
    /** Degrees the floor sigil and its counter-rotating inner ring turn per channel tick. */
    private static final double RING_SPIN_PER_TICK = 6.0;
    /** Inner ring radius as a fraction of the outer ring's. */
    private static final double INNER_RING_FRACTION = 0.7;
    /** Rising smoke motes drawn per channel tick. */
    private static final int CHANNEL_MOTES = 4;
    /** Ticks between the channel's rising tone. */
    private static final int CHANNEL_SOUND_EVERY = 10;
    /** The servant's walk speed; it should never actually wander far. */
    private static final double HORROR_SPEED = 0.28;
    /** Knockback resistance that makes the servant BOUND rather than merely leashed. */
    private static final double HORROR_KNOCKBACK_RESISTANCE = 1.0;
    /** A lash shove's vertical component, so victims are pinned rather than flung. */
    private static final double LASH_LIFT = 0.15;

    /** The persistent NBT flag this spell stamps on its servants. */
    private static final String SUMMON_FLAG = "summoned_horror";
    /**
     * The brain id every dark servant shares. A deliberate reuse rather than a new
     * {@code brains/bound_horror.yml}: the flat summon brain's goals are exactly what
     * an anchored servant needs, the leash in {@link #startLashRunnable} neutralises
     * its {@code follow_entity} goal for this entity, and shipping a new brain file
     * would put a resource outside this spell's ownership.
     */
    private static final String SUMMON_BRAIN = "summoned_knight";

    /** Live servants per caster, so the aggro bridge and the despawn pass can find them. */
    private final Map<UUID, List<UUID>> horrors = new HashMap<>();

    /**
     * Every live lash task, so quit and plugin-disable can stop them. A lash task holds a
     * strong reference to its caster; leaving it scheduled for thirty seconds after a
     * player logs out would pin a Player object and its profile in memory for the whole
     * of that window.
     */
    private final Set<BukkitRunnable> lashTasks = new HashSet<>();

    private int channelTicks;
    private int anchorRange;
    private int horrorCount;
    private int horrorDuration;
    private double horrorHealth;
    private double horrorPower;
    private double anchorLeash;
    private double lashRange;
    private int lashIntervalTicks;
    private int lashHoldTicks;
    private double anchorPullStrength;

    public VoidAnchor(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/void_anchor.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/void_anchor.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        // No _options.yml on purpose: every knob here is a server owner's balance
        // surface, not a player choice, and a UTILITY spell has no budget a player
        // option could push past. Every read below carries a literal default, so a
        // missing key degrades to the documented value instead of 0.0.
        this.channelTicks = Math.max(1, configInt(spellConfig, "channel_ticks", 30));
        this.anchorRange = Math.max(1, configInt(spellConfig, "anchor_range", 24));
        this.horrorCount = Math.max(0, configInt(spellConfig, "horror_count", 1));
        this.horrorDuration = Math.max(1, configInt(spellConfig, "horror_duration", 30));
        this.horrorHealth = configDouble(spellConfig, "horror_health", 30.0);
        this.horrorPower = configDouble(spellConfig, "horror_power", 10.0);
        this.anchorLeash = configDouble(spellConfig, "anchor_leash", 4.0);
        this.lashRange = configDouble(spellConfig, "lash_range", 6.0);
        this.lashIntervalTicks = Math.max(1, configInt(spellConfig, "lash_interval_ticks", 20));
        this.lashHoldTicks = Math.max(1, configInt(spellConfig, "lash_hold_ticks", 10));
        this.anchorPullStrength = configDouble(spellConfig, "anchor_pull_strength", 0.45);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    private static int configInt(YamlConfiguration yml, String key, int def) {
        Object raw = yml.get(key);
        return raw instanceof Number n ? n.intValue() : def;
    }

    private static double configDouble(YamlConfiguration yml, String key, double def) {
        Object raw = yml.get(key);
        return raw instanceof Number n ? n.doubleValue() : def;
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        UUID uuid = caster.getUniqueId();
        // A second cast while channelling is ignored, not stacked.
        if (ChannelRegistry.isChanneling(uuid)) return;

        Location anchor = Utils.resolveTarget(caster, anchorRange);
        if (anchor.getWorld() == null) {
            cancelCast(caster);
            return;
        }
        // The same region check SkeletalKnights makes before it starts channelling, so a
        // refused cast refunds instead of burning the channel.
        if (!Protection.spawnMob(caster, anchor)) {
            cancelCast(caster);
            return;
        }

        int total = channelTicks;
        WindUp windUp = WindUp.begin(this, CAST_COLOR, caster, total, anchor);
        int[] ticks = {0};

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (ticks[0] >= total) {
                    cancel();
                    stop(uuid);
                    completeSummon(caster, anchor);
                    return;
                }
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    stop(uuid);
                    return;
                }
                windUp.tick();
                animateChannel(caster, anchor, ticks[0], total);
                ticks[0]++;
            }
        };

        // Registered BEFORE the task starts, so an interruption arriving on the very
        // first tick already finds the canceller to run.
        ChannelRegistry.register(uuid, windUp, runnable::cancel);
        runnable.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /** Forgets the wind-up without running its canceller - the channel finished on its own. */
    private void stop(UUID uuid) {
        ChannelRegistry.unregister(uuid);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Player-only spell.
    }

    /** The channel's draw: a spinning floor sigil, its counter-rotating inner ring, rising smoke. */
    private void animateChannel(Player caster, Location anchor, int ticks, int total) {
        World world = anchor.getWorld();
        if (world == null) return;

        drawRing(caster, world, anchor, SIGIL_RADIUS, Math.toRadians(ticks * RING_SPIN_PER_TICK), ANCHOR_DUST);
        drawRing(caster, world, anchor, SIGIL_RADIUS * INNER_RING_FRACTION,
                Math.toRadians(-ticks * RING_SPIN_PER_TICK), SIGIL_DUST);

        for (int i = 0; i < CHANNEL_MOTES; i++) {
            double angle = i * Math.PI / 2.0 + ticks * 0.15;
            Location mote = anchor.clone().add(
                    Math.cos(angle) * 1.2,
                    0.1 + (ticks + i * 5) % 20 * 0.1,
                    Math.sin(angle) * 1.2);
            ParticleCaster.spawn(caster, world, Utils.SMOKE, mote, 1, 0.1, 0.1, 0.1, 0.01);
        }

        if (ticks % CHANNEL_SOUND_EVERY == 0) {
            world.playSound(anchor, Sound.BLOCK_BEACON_ACTIVATE, 0.3f,
                    0.6f + 0.4f * ((float) ticks / total));
        }
    }

    /** A flat ring of {@link #RING_POINTS} dust points at {@code radius}, spun by {@code spin} radians. */
    private void drawRing(LivingEntity caster, World world, Location centre, double radius,
                          double spin, Particle.DustOptions dust) {
        Location scratch = centre.clone();
        for (Location point : ParticleUtils.circle(centre, radius, RING_POINTS, 0, 0)) {
            double dx = point.getX() - centre.getX();
            double dz = point.getZ() - centre.getZ();
            scratch.setX(centre.getX() + dx * Math.cos(spin) - dz * Math.sin(spin));
            scratch.setY(point.getY());
            scratch.setZ(centre.getZ() + dx * Math.sin(spin) + dz * Math.cos(spin));
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, dust);
        }
    }

    /** Pulls the servants out of the ground and starts their leash-and-lash runnable. */
    private void completeSummon(Player caster, Location anchor) {
        if (caster.isDead() || !caster.isOnline()) return;

        Location ground = Utils.findTopSolid(anchor, 10);
        if (ground == null) {
            Utils.sendActionBar(caster, lang().get("spells.voidanchor.no_summon_spot"));
            ground = anchor.clone();
        }
        World world = ground.getWorld();
        if (world == null) {
            cancelCast(caster);
            return;
        }

        List<UUID> ids = new ArrayList<>();
        // The ONE getPower for this cast. It scales the servant's melee attribute once,
        // here at spawn, rather than once per swing: the brain's melee_attack goal reads
        // GENERIC_ATTACK_DAMAGE on every hit, so the attribute IS the damage instance and
        // scaling it once is what keeps the option modifiers and the affinity term from
        // being squared. The 2-arg overload is the right one - there is no target at spawn
        // to take a resistance from - and it is the same call summon_zombies and
        // skeletal_knights make for their own minions.
        double meleePower = getPower(caster, horrorPower);
        // The anchor is the solid block findTopSolid stopped on; the servant is torn out
        // of the block ABOVE it, because spawning a mob inside a solid block suffocates it.
        Location spawnAt = ground.clone().add(0, 1, 0);
        for (int i = 0; i < horrorCount; i++) {
            if (!Protection.spawnMob(caster, ground)) continue;

            WitherSkeleton horror = (WitherSkeleton) world.spawnEntity(spawnAt, EntityType.WITHER_SKELETON);
            // No setAdult() call: WitherSkeleton is not an Ageable and has no growth stage
            // at all - it spawns fully grown. The call was a no-op wish, so it is dropped
            // rather than worked around by swapping the mob type, which would break the
            // horror's identity (ENTITY_WITHER_SPAWN, the wither silhouette, the horror name).
            horror.setCustomName(ChatColor.DARK_PURPLE + caster.getName() + "'s Horror");
            horror.setCustomNameVisible(false);
            horror.setRemoveWhenFarAway(false);
            horror.setCanPickupItems(false);
            horror.setSilent(false);

            // setBaseValue only. setHealth(...) is never called in this class, and
            // getMaxHealth() is never called either.
            Objects.requireNonNull(horror.getAttribute(Attribute.GENERIC_MAX_HEALTH))
                    .setBaseValue(horrorHealth);
            Objects.requireNonNull(horror.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED))
                    .setBaseValue(HORROR_SPEED);
            Objects.requireNonNull(horror.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE))
                    .setBaseValue(meleePower);
            // Full knockback resistance is what makes the servant BOUND: nothing short of
            // a teleport can shove it off its anchor.
            Objects.requireNonNull(horror.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE))
                    .setBaseValue(HORROR_KNOCKBACK_RESISTANCE);

            String anchorTag = encode(ground);
            NBT.modifyPersistentData(horror, nbt -> {
                // Mandatory: the brain's follow_entity goal is keyed on summoner_uuid and
                // will not resolve without it.
                nbt.setString("summoner_uuid", caster.getUniqueId().toString());
                nbt.setBoolean(SUMMON_FLAG, true);
                // Lets the lash re-derive the anchor every tick without holding a strong
                // reference to a possibly-unloaded Location.
                nbt.setString("anchor_pos", anchorTag);
            });
            MagicAiService aiService = MagicAiService.getInstance();
            if (aiService != null) {
                aiService.setBrain(horror, SUMMON_BRAIN);
            }
            ids.add(horror.getUniqueId());
            ParticleCaster.spawn(caster, world, Utils.PORTAL, spawnAt, 40, 0.5, 0.5, 0.5, 0.05);
            ParticleCaster.spawn(caster, world, Utils.DUST, spawnAt, 20, 0.4, 0.4, 0.4, 0, ANCHOR_DUST);
            ParticleCaster.spawn(caster, world, Utils.LARGE_SMOKE, spawnAt, 10, 0.3, 0.3, 0.3, 0.05);
            world.playSound(spawnAt, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.6f);
        }

        // An interrupted-by-region cast refunds rather than silently eating 450 mana.
        if (ids.isEmpty()) {
            cancelCast(caster);
            return;
        }

        horrors.put(caster.getUniqueId(), ids);
        startLashRunnable(caster, ids);
    }

    /**
     * The lash: once every {@code lash_interval_ticks}, hold everything nearby in place
     * and pin the servant to its anchor.
     *
     * <p><b>The lash deals no damage.</b> It shoves, slows and blinds; the killing is
     * the horror's own {@code melee_attack} goal resolved through
     * {@code GENERIC_ATTACK_DAMAGE}, so this spell's damage budget stays empty.
     */
    private void startLashRunnable(Player caster, List<UUID> ids) {
        int lifeTicks = horrorDuration * 20;
        int interval = lashIntervalTicks;
        BukkitRunnable[] holder = new BukkitRunnable[1];

        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                elapsed += interval;
                if (elapsed >= lifeTicks) {
                    cancel();
                    if (holder[0] != null) lashTasks.remove(holder[0]);
                    for (UUID id : ids) {
                        Entity found = Bukkit.getEntity(id);
                        if (found != null) despawnHorror(caster, found);
                    }
                    horrors.remove(caster.getUniqueId());
                    return;
                }
                // Safety net for any path that removes a caster without firing onQuit:
                // a scheduled task holding a Player pins its whole profile in memory.
                if (!caster.isOnline() || caster.isDead()) {
                    cancel();
                    if (holder[0] != null) lashTasks.remove(holder[0]);
                    for (UUID id : ids) {
                        Entity found = Bukkit.getEntity(id);
                        if (found != null) despawnHorror(caster, found);
                    }
                    horrors.remove(caster.getUniqueId());
                    return;
                }

                for (UUID id : ids) {
                    if (!(Bukkit.getEntity(id) instanceof WitherSkeleton horror)) continue;
                    if (horror.isDead() || !horror.isValid()) continue;

                    World world = horror.getWorld();
                    if (world == null) continue;

                    // The leash. The brain's follow_entity goal cannot drag it away, and
                    // this catches anything that somehow does. Cross-world guards included:
                    // Location.distance throws rather than returning a number.
                    Location anchor = decode(readAnchorTag(horror));
                    if (anchor != null && anchor.getWorld() != null
                            && anchor.getWorld().equals(world)
                            && horror.getLocation().distance(anchor) > anchorLeash) {
                        Location dragged = horror.getLocation();
                        horror.teleport(anchor.clone().add(0, 0.1, 0));
                        for (Location point : ParticleUtils.line(0.4, anchor, dragged)) {
                            ParticleCaster.spawn(caster, world, Utils.WITCH, point, 1, 0.1, 0.1, 0.1, 0.01);
                        }
                    }

                    if (anchor != null) {
                        drawRing(caster, world, anchor, ANCHOR_CIRCLE_RADIUS, 0.0, ANCHOR_DUST);
                    }

                    lash(caster, horror);
                }
            }
        };
        holder[0] = task;
        lashTasks.add(task);
        task.runTaskTimer(Alkatraz.getInstance(), 0L, interval);
    }

    private void lash(Player caster, WitherSkeleton horror) {
        World world = horror.getWorld();
        if (world == null) return;
        double range = Math.max(0.5, lashRange);

        for (Entity entity : world.getNearbyEntities(horror.getLocation(), range, range, range)) {
            if (!(entity instanceof LivingEntity victim)) continue;
            if (victim.equals(caster) || victim.isDead()) continue;
            if (SummonCombatGuard.isSummon(victim)) continue;
            if (victim instanceof ArmorStand) continue;
            if (victim instanceof Player player) {
                GameMode mode = player.getGameMode();
                if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) continue;
                if (isHiddenFromCaster(caster, player)) continue;
            }

            Vector pull = Utils.safeNormalize(horror.getEyeLocation().toVector()
                    .subtract(victim.getEyeLocation().toVector()));
            if (pull != null) {
                victim.setVelocity(pull.multiply(anchorPullStrength).setY(LASH_LIFT));
            }
            // Both effects go on the VICTIM. Never the caster, never the horror.
            victim.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW, lashHoldTicks, 2, false, false));
            victim.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS, lashHoldTicks, 0, false, false));
        }
    }

    /** A player in stealth at a higher circle level than the caster's is not a valid victim. */
    private boolean isHiddenFromCaster(Player caster, Player victim) {
        MagicProfile victimProfile = ProfileManager.getProfile(victim.getUniqueId(), MagicProfile.class);
        if (victimProfile == null || !victimProfile.isStealth()) return false;
        MagicProfile casterProfile = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);
        return casterProfile == null || victimProfile.getCircleLevel() > casterProfile.getCircleLevel();
    }

    /**
     * The caster's own horror can never hit the caster.
     *
     * <p>{@code SummonCombatGuard.SUMMON_FLAGS} is
     * {@code {summoned_zombie, summoned_knight, summoned_mount}} and is a frozen
     * wave-3 file this spell may not edit, so {@code summoned_horror} is not in the
     * shared guard's list. That is exactly why the guard is implemented here.
     */
    @EventHandler
    private void onHorrorDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof WitherSkeleton horror)) return;
        SummonCombatGuard.SummonTag tag = SummonCombatGuard.readSummonTag(horror, SUMMON_FLAG);
        if (tag == null || !tag.flagged() || tag.summonerUuid() == null) return;
        if (e.getEntity() instanceof Player victim
                && victim.getUniqueId().toString().equals(tag.summonerUuid())) {
            e.setCancelled(true);
        }
    }

    /** SummonZombies' aggro bridge: whatever the caster fights, the horror joins. */
    @EventHandler
    private void onPlayerCombat(EntityDamageByEntityEvent e) {
        Player player = null;
        LivingEntity target = null;

        if (e.getDamager() instanceof Player p && e.getEntity() instanceof LivingEntity lt) {
            player = p;
            target = lt;
        } else if (e.getEntity() instanceof Player p && e.getDamager() instanceof LivingEntity lt) {
            // Skip the case where the attacker is this player's own horror, or the
            // bridge would feed the servant its own summoner.
            if (lt instanceof WitherSkeleton horror) {
                SummonCombatGuard.SummonTag tag = SummonCombatGuard.readSummonTag(horror, SUMMON_FLAG);
                if (tag != null && tag.summonerUuid() != null
                        && tag.summonerUuid().equals(p.getUniqueId().toString())) {
                    return;
                }
            }
            player = p;
            target = lt;
        }

        if (player == null || target == null) return;
        if (SummonCombatGuard.isSummon(target)) return;
        if (target.equals(player)) return;

        List<UUID> ids = horrors.get(player.getUniqueId());
        if (ids == null || ids.isEmpty()) return;

        for (UUID id : ids) {
            if (Bukkit.getEntity(id) instanceof WitherSkeleton horror && !horror.isDead()) {
                horror.setTarget(target);
            }
        }
    }

    /**
     * A quitting caster takes their servants with them. The channel itself needs no
     * handler: {@link ChannelRegistry} already owns the quit interrupt and is registered
     * once from {@code Alkatraz.onEnable}.
     */
    @EventHandler
    private void onQuit(PlayerQuitEvent e) {
        despawnAll(e.getPlayer());
    }

    @EventHandler
    private void onDeath(PlayerDeathEvent e) {
        despawnAll(e.getEntity());
    }

    /** Nothing this spell made outlives the plugin. */
    @EventHandler
    private void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Alkatraz.getInstance()) return;
        for (BukkitRunnable task : lashTasks) task.cancel();
        lashTasks.clear();
        for (List<UUID> ids : horrors.values()) {
            for (UUID id : ids) {
                Entity found = Bukkit.getEntity(id);
                if (found != null) found.remove();
            }
        }
        horrors.clear();
    }

    private void despawnAll(Player caster) {
        List<UUID> ids = horrors.remove(caster.getUniqueId());
        if (ids == null) return;
        for (UUID id : ids) {
            Entity found = Bukkit.getEntity(id);
            if (found != null) despawnHorror(caster, found);
        }
    }

    private void despawnHorror(LivingEntity caster, Entity e) {
        if (e.isDead()) {
            e.remove();
            return;
        }
        ParticleCaster.spawn(caster, e.getWorld(), Utils.LARGE_SMOKE, e.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
        ParticleCaster.spawn(caster, e.getWorld(), Utils.WITCH, e.getLocation(), 10, 0.3, 0.3, 0.3, 0);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 0.8f);
        e.remove();
    }

    private static String readAnchorTag(Entity e) {
        return NBT.getPersistentData(e, nbt -> nbt.getString("anchor_pos"));
    }

    /** {@code "<worldUuid>|<blockX>|<blockY>|<blockZ>"}, the anchor block's own coordinates. */
    private static String encode(Location loc) {
        World world = loc.getWorld();
        return (world == null ? "" : world.getUID().toString())
                + "|" + loc.getBlockX()
                + "|" + loc.getBlockY()
                + "|" + loc.getBlockZ();
    }

    /** The inverse of {@link #encode}; null for an absent, unloaded or malformed tag. */
    private static Location decode(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        String[] parts = tag.split("\\|");
        if (parts.length != 4) return null;
        try {
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (world == null) return null;
            return new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    /**
     * Required by {@code AttackSpell} but unreachable today: this spell registers no spell
     * component, so there is nothing for a barrier to intercept. Written in the anchor's own
     * palette rather than left empty, so that a future component registration inherits a
     * readable tell instead of silence.
     */
    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.DUST, location, 30, ANCHOR_DUST);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.4f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.DUST, location, 60, ANCHOR_DUST);
        ParticleCaster.spawn(null, location.getWorld(), Utils.SMOKE, location, 20, 0.3, 0.3, 0.3, 0.05);
        location.getWorld().playSound(location, Sound.ENTITY_WITHER_HURT, 0.6f, 0.5f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.voidanchor.book_name"))
                .addCustomLoreLine(lang().get("spells.voidanchor.lore1"))
                .addCustomLoreLine(lang().get("spells.voidanchor.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}