package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.TeleportSafety;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * Circle 7 NONE utility: an instant, aim-driven warp at twice {@code warp}'s budgeted reach.
 *
 * <h2>There is no channel, and that is the whole spell</h2>
 * {@code warp} opens a destination picker and then runs a 120-tick wind-up that movement or a
 * single point of damage interrupts. This class implements no {@code Listener}, declares no
 * channels map, registers no event handler and calls neither {@code ChannelRegistry.register} nor
 * {@code WindUp.begin}: {@code WindUp.EXEMPT_FROM_WIND_UP} lists {@code waystone_recall} at circle 7
 * for exactly that reason. By the time {@link #castAction} returns, the caster is already standing
 * at the destination, so there is nothing left to interrupt. The only scheduled task is the
 * cosmetic arrival ring, which never gates the teleport.
 *
 * <h2>It cannot exceed the profile's own warp budget</h2>
 * Neither the reach nor the eligible slot set is computed here. Both are read off the
 * <b>registered</b> {@link Warp} instance through its public accessors, which are thin wrappers
 * over {@link WarpConfig}:
 * {@link Warp#maxDistance(MagicProfile)} and {@link Warp#maxSlots(MagicProfile)}. Recall multiplies
 * that distance by its own {@code distance_multiplier} and takes
 * {@code min(that slot budget, max_recall_slots)} - so it can narrow what the profile may do and
 * never widen it, and there is no duplicated formula to drift.
 *
 * <h2>The damage contract, vacuously</h2>
 * No {@code AttackProperties}, no {@code AttackType}, no {@code SpellAttackContext}, no
 * {@code getPower} - this extends bare {@link Spell}, which has none of those. Nothing in this class
 * can reach an entity's health: the waystones are the caster's own profile data and a waystone is a
 * {@link Location}, not a target.
 */
public class WaystoneRecall extends Spell {

    /** The spell whose slot and distance rules this one borrows rather than duplicates. */
    private static final String WARP_SPELL_ID = "warp";

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** The leading edge of the collapse and arrival bursts. */
    private static final Particle.DustOptions RECALL_DUST = PALETTE.coreDust();
    /** The arrival ring, a shade back so it does not out-shout the burst it follows. */
    private static final Particle.DustOptions RECALL_RING = PALETTE.accentDust();

    /** How far in front of the caster's eyes the origin collapse is drawn, in blocks. */
    private static final double ORIGIN_OFFSET = 1.8;
    /** Points around the expanding arrival ring. */
    private static final int RING_POINTS = 36;
    /** Clamp on the acos argument: two unit vectors can dot to 1.0 + an ulp. */
    private static final double DOT_LIMIT = 1.0;

    private WaystoneRecallConfig config;

    public WaystoneRecall(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        // No saveConfig for an options file: this spell ships no options. Its one tuning axis -
        // reach - is deliberately governed by WarpConfig plus a single multiplier, and a
        // player-side second multiplier for the same thing would let a caster stack past the
        // profile's warp budget, which is the one thing this spell is built not to do.
        Alkatraz.getInstance().save("spells/waystone_recall.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/waystone_recall.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = WaystoneRecallConfig.fromConfig(spellConfig.getValues(false));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) {
            cancelCast(caster);
            return;
        }
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) {
            cancelCast(caster);
            return;
        }

        Spell registered = SpellRegistry.getSpell(WARP_SPELL_ID);
        if (!(registered instanceof Warp warpRules)) {
            // warp is always registered; this is a defensive guard, not an expected branch.
            Utils.sendActionBar(caster, lang().get("spells.waystonerecall.blocked"));
            cancelCast(caster);
            return;
        }

        int slotBudget = config.effectiveSlotBudget(warpRules.maxSlots(profile));
        double maxDistance = config.recallDistance(warpRules.maxDistance(profile));

        if (slotBudget < config.minRecallSlots()) {
            Utils.sendActionBar(caster, lang().get("spells.waystonerecall.no_points"));
            cancelCast(caster);
            return;
        }

        Destination destination = resolveDestination(caster, profile, slotBudget, maxDistance);
        if (destination == null) {
            Utils.sendActionBar(caster, lang().get("spells.waystonerecall.nothing_in_sight"));
            cancelCast(caster);
            return;
        }

        MagicProfile.WarpPoint point = destination.point();
        Location dest = destination.location();

        if (!TeleportSafety.isSafePlayerDestination(dest)) {
            Utils.sendActionBar(caster, lang().get("spells.waystonerecall.blocked"));
            cancelCast(caster);
            return;
        }

        // Captured before the move. ParticleCaster scopes visibility off the caster's position at
        // dispatch time, so the collapse puff has to be placed on the origin explicitly - reading
        // the caster's eye location afterwards would put it at the destination.
        Location originRift = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(ORIGIN_OFFSET));

        if (!Protection.teleport(caster, dest)) {
            Utils.sendActionBar(caster, lang().get("spells.waystonerecall.blocked"));
            cancelCast(caster);
            return;
        }

        spawnCollapseBurst(caster, originRift);
        // Protection.teleport is a PERMISSION CHECK, not a move - the caller must still teleport,
        // exactly as Blink.java:79/87 does. WarpPoint.toLocation() already carries the stored yaw
        // and pitch, so this single call also orients the caster; there is deliberately no separate
        // rotation call here.
        caster.teleport(dest);
        caster.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        spawnArrivalBurst(caster, dest);
        spawnArrivalExpand(caster, dest, point.yaw());
    }

    /**
     * The pure scan that picks a destination: nearest-by-angle inside the aim cone, no GUI, no
     * randomness.
     *
     * <p>The comparison is strictly-less, so two waystones at the same angle resolve to the
     * lowest slot index and a player standing between two of their own waystones always recalls
     * to the same one.
     */
    private Destination resolveDestination(Player caster, MagicProfile profile,
                                           int slotBudget, double maxDistance) {
        Location eye = caster.getEyeLocation();
        Vector look = Utils.safeNormalize(eye.getDirection());
        if (look == null) return null;

        Map<Integer, MagicProfile.WarpPoint> points = profile.getWarpPoints();
        Destination best = null;
        for (int slot = 0; slot < slotBudget; slot++) {
            MagicProfile.WarpPoint point = points.get(slot);
            if (point == null) continue;

            Location dest = point.toLocation();
            if (dest == null || !dest.getWorld().equals(caster.getWorld())) continue;

            Location aimAt = dest.clone().add(0, 1, 0);
            double distance = eye.distance(aimAt);
            if (distance <= 0.001 || distance > maxDistance) continue;

            Vector to = Utils.safeNormalize(aimAt.toVector().subtract(eye.toVector()));
            if (to == null) continue;

            double angle = Math.toDegrees(Math.acos(
                    Math.min(DOT_LIMIT, Math.max(-DOT_LIMIT, look.dot(to)))));
            if (angle > config.aimConeDegrees()) continue;

            if (best == null || angle < best.angleDegrees()) {
                best = new Destination(point, dest, angle);
            }
        }
        return best;
    }

    /** One eligible waystone: its stored point, its resolved location, and its angle off the crosshair. */
    private record Destination(MagicProfile.WarpPoint point, Location location, double angleDegrees) {
    }

    private void spawnCollapseBurst(Player caster, Location origin) {
        ParticleCaster.spawn(caster, origin.getWorld(), Utils.PORTAL, origin, 40, 0.4, 0.4, 0.4, 0.05);
        ParticleCaster.spawn(caster, origin.getWorld(), Utils.DUST, origin, 20, 0.3, 0.3, 0.3, 0, RECALL_DUST);
    }

    private void spawnArrivalBurst(Player caster, Location destination) {
        ParticleCaster.spawn(caster, destination.getWorld(), Utils.PORTAL, destination, 40, 0.5, 0.5, 0.5, 0.5);
        ParticleCaster.spawn(caster, destination.getWorld(), Utils.DUST, destination, 15, 0.3, 0.3, 0.3, 0, RECALL_DUST);
    }

    /**
     * The arrival ripple: a widening vertical ellipse at the landing spot. Cosmetic only, and the
     * reason this class still schedules anything at all.
     */
    private void spawnArrivalExpand(Player caster, Location destination, float yaw) {
        World world = destination.getWorld();
        if (world == null || !world.isChunkLoaded(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)) return;
        Location center = destination.clone().add(0, 1.1, 0);
        double yawRad = Math.toRadians(yaw);
        double leftX = Math.cos(yawRad);
        double leftZ = Math.sin(yawRad);
        new BukkitRunnable() {
            int expand = 0;
            final Location scratch = center.clone();

            @Override
            public void run() {
                if (expand >= config.arrivalExpandTicks()) {
                    cancel();
                    return;
                }
                double halfWidth = 0.6 + expand * 0.18;
                for (int i = 0; i < RING_POINTS; i++) {
                    ellipsePoint(center, leftX, leftZ, halfWidth, halfWidth * 1.1, RING_POINTS, i, scratch);
                    ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, RECALL_RING);
                }
                expand++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /** One point on a vertical ellipse in the caster's left-right plane. */
    private static void ellipsePoint(Location center, double leftX, double leftZ,
                                     double halfWidth, double halfHeight, int points, int index,
                                     Location scratch) {
        double angle = 2 * Math.PI * index / points;
        double spanX = Math.cos(angle) * halfWidth;
        double spanY = Math.sin(angle) * halfHeight;
        scratch.setX(center.getX() + leftX * spanX);
        scratch.setY(center.getY() + spanY);
        scratch.setZ(center.getZ() + leftZ * spanX);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Waystone Recall is player-only - a mob has no MagicProfile and therefore no warp points.
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 2.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.waystonerecall.book_name"))
                .addCustomLoreLine(lang().get("spells.waystonerecall.lore1"))
                .addCustomLoreLine(lang().get("spells.waystonerecall.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}
