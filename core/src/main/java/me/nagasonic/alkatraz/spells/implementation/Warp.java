package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.gui.implementation.WarpDestinationMenu;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.ChannelRegistry;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * 6th circle teleportation spell. Players set warp points in the spell options
 * menu and pick a destination at cast time. Casting opens a destination picker;
 * picking a point consumes mana and starts a channeled wind-up that teleports
 * the player once complete. Moving or taking damage interrupts the channel.
 *
 * <h2>Where the channel lives</h2>
 * The channel used to be a private {@code channels} map, a hand-rolled
 * {@link BukkitRunnable} animation, and its own {@code onMove}/{@code onDamage}/
 * {@code onQuit} handler triple. All three now live in the shared
 * {@link WindUp} / {@link ChannelRegistry} pair, which is also what
 * {@code skeletal_knights} uses - so the two circle-6 channels are finally
 * different spells sharing one code path rather than one picture drawn twice.
 * This class no longer registers as a Bukkit listener for them.
 *
 * <h2>The deferred cast</h2>
 * {@link #castAction} does <b>not</b> begin the channel. It cancels the cast and
 * opens the destination picker; the channel starts in {@link #beginChannel} when
 * the player picks. Mana is still spent there, at the moment the destination is
 * committed to, exactly as before.
 */
public class Warp extends Spell {

    /**
     * Warp is element NONE, so the ritual draws in neutral grey. It used to carry
     * eleven hand-picked aqua constants that were byte-identical to
     * {@code skeletal_knights}'s, which is why a teleportation and a skeleton
     * summoning were the same aqua picture on screen.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** Radius, in blocks, of the ritual floor drawn during the cast circle. */
    private static final double CIRCLE_RADIUS = 1.0;
    /** Points on each ring of that floor; the old ellipse used 28. */
    private static final int CIRCLE_POINTS = 28;
    /** Glyph markers around that floor; six is the hex both spell classes drew. */
    private static final int GLYPH_COUNT = 6;

    private WarpConfig config;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Warp(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/warp_options.yml");
        Alkatraz.getInstance().save("spells/warp.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/warp.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = WarpConfig.fromConfig(spellConfig.getValues(false));
    }

    public int maxSlots(MagicProfile profile) {
        int mastery = profile == null ? 0 : profile.getSpellMastery(this);
        return config.maxSlots(mastery);
    }

    public int maxSlotsAbsolute() {
        return config.maxSlotsAbsolute();
    }

    public int slotsMasteryRequirement(int slot) {
        return config.masteryForSlot(slot);
    }

    public double maxDistance(MagicProfile profile) {
        if (profile == null) return config.maxDistance(0, 0, getMaxMastery());
        return config.maxDistance(profile.getCircleLevel(), profile.getSpellMastery(this), getMaxMastery());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        cancelCast(caster);
        if (profile == null) return;
        if (ChannelRegistry.isChanneling(caster.getUniqueId())) return;
        if (profile.getWarpPoints().isEmpty()) {
            Utils.sendActionBar(caster, lang().get("spells.warp.no_points"));
            return;
        }
        new WarpDestinationMenu(caster, this).open();
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Warp is player-only.
    }

    /**
     * Begins the channeled teleport toward a chosen warp point. Consumes the
     * spell's (possibly modified) mana cost and runs the shared wind-up, anchored
     * to the destination so the landing spot is telegraphed for the whole channel.
     *
     * <p><b>The 6-second duration is a declared paid-over</b>, not an oversight: at
     * circle 6 the standard wind-up contract is only 20 ticks, and
     * {@code waystone_recall} exists as the instant skip for anyone who does not
     * want to sit through it. {@code SpellBudgets.isPaidOverWindUp(6, 120)} is the
     * assertion that keeps it that way.
     */
    public void beginChannel(Player caster, MagicProfile.WarpPoint point) {
        final UUID uuid = caster.getUniqueId();
        if (caster.isDead() || !caster.isOnline()) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) return;
        if (ChannelRegistry.isChanneling(uuid)) return;

        Location destination = point.toLocation();
        if (destination == null || !destination.getWorld().equals(caster.getWorld())) {
            Utils.sendActionBar(caster, lang().get("spells.warp.too_far"));
            playFailSound(caster);
            return;
        }
        if (caster.getLocation().distance(destination) > maxDistance(profile)) {
            Utils.sendActionBar(caster, lang().get("spells.warp.too_far"));
            playFailSound(caster);
            return;
        }

        StatUtils.subMana(caster, getModifiedManaCost(caster));

        final Location start = caster.getLocation();
        final int windUpTicks = config.windUpTicks(profile.getSpellMastery(this), getMaxMastery());
        final int repaintTicks = (int) circleRepaintTicks();

        // The marker is the destination, so the wind-up draws the landing glyph there
        // rather than an anonymous glow around the caster.
        final WindUp windUp = WindUp.begin(this, caster, windUpTicks, destination);

        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    windUp.cancel();
                    return;
                }
                if (windUp.isComplete()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    completeTeleport(caster, start, point, profile);
                    return;
                }
                windUp.tick();
                if (ticks % repaintTicks == 0) {
                    float pitch = 0.6f + windUp.progress() * 0.6f;
                    caster.getWorld().playSound(caster.getEyeLocation(),
                            Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, pitch);
                }
                ticks++;
            }
        };

        ChannelRegistry.INSTANCE.register(uuid, windUp, runnable::cancel);
        runnable.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void completeTeleport(Player caster, Location start, MagicProfile.WarpPoint point, MagicProfile profile) {
        UUID uuid = caster.getUniqueId();
        ChannelRegistry.unregister(uuid);

        Location destination = point.toLocation();
        if (caster.isDead() || !caster.isOnline()
                || destination == null
                || !destination.getWorld().equals(caster.getWorld())
                || caster.getLocation().distance(destination) > maxDistance(profile)) {
            return;
        }
        if (!Protection.teleport(caster, destination)) {
            Utils.sendActionBar(caster, lang().get("spells.warp.blocked"));
            return;
        }

        Location originRift = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(1.8));
        spawnCollapseBurst(caster, originRift);
        caster.teleport(destination);
        caster.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        spawnTeleportParticles(caster, destination);
        spawnArrivalExpand(caster, destination, point.yaw());

        profile.setCooldown(this, System.currentTimeMillis());
        if (profile.getSpellMastery(this) < getMaxMastery()) {
            StatUtils.addSpellMastery(caster, this, 1);
        }
    }

    private void spawnTeleportParticles(Player caster, Location loc) {
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.5);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 15, 0.3, 0.3, 0.3, 0,
                PALETTE.coreDust());
    }

    private void spawnCollapseBurst(Player caster, Location loc) {
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.PORTAL, loc, 40, 0.4, 0.4, 0.4, 0.05);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 20, 0.3, 0.3, 0.3, 0,
                PALETTE.coreDust());
    }

    /**
     * The arrival ripple: ten frames of a widening vertical ellipse at the landing
     * spot. This is the one place the old {@code ellipsePoint} sweep survives - it
     * is a post-teleport flourish rather than part of the channel ritual, and
     * {@link SpellVfx} has no equivalent shape for it.
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
                if (expand >= 10) {
                    cancel();
                    return;
                }
                double hw = 0.6 + expand * 0.18;
                for (int i = 0; i < 36; i++) {
                    ellipsePoint(center, leftX, leftZ, hw, hw * 1.1, 36, i, scratch);
                    ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, PALETTE.coreDust());
                }
                expand++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void playFailSound(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.5f);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        // The shared channel-ritual floor: a counter-rotating ring pair plus six glyph
        // markers, coloured by this spell's element. `skeletal_knights` calls the same
        // builder with its own radius and point count, in DARK purple, so the two circle-6
        // rituals are now the same family rather than the same aqua picture drawn twice.
        //
        // Visual note: the old body was a 28-point vertical ellipse in front of the
        // caster's eyes. `channelGlyphs` draws a flat floor at the caster's feet, so the
        // circle is now anchored to the ground instead of floating at eye height, and it
        // rotates rather than sitting still. That is the intended consequence of sharing
        // one ritual shape; the vertical sweep itself is not reproduced here.
        final Spell spell = this;
        final long period = circleRepaintTicks();
        return new BukkitRunnable() {
            int frame = 0;

            @Override
            public void run() {
                if (e.isCancelled()) {
                    cancel();
                    return;
                }
                SpellVfx.channelGlyphs(spell, caster, caster.getLocation(),
                        CIRCLE_RADIUS, CIRCLE_POINTS, GLYPH_COUNT, frame * 0.18);
                frame++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, period).getTaskId();
    }

    /** Repaint period, guarded against a missing or zero {@code circle_ticks}. */
    private static long circleRepaintTicks() {
        Object ticks = Configs.CIRCLE_TICKS.get();
        long period = (ticks instanceof Number) ? ((Number) ticks).longValue() : 10L;
        return Math.max(1L, period);
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
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.warp.book_name"))
                .addCustomLoreLine(lang().get("spells.warp.lore1"))
                .addCustomLoreLine(lang().get("spells.warp.lore2"))
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
