package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.gui.implementation.WarpDestinationMenu;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 6th circle teleportation spell. Players set warp points in the spell options
 * menu and pick a destination at cast time. Casting opens a destination picker;
 * picking a point consumes mana and starts a channeled wind-up that teleports
 * the player once complete. Moving or taking damage interrupts the channel.
 */
public class Warp extends Spell implements Listener {

    private static final Map<UUID, WarpData> channels = new ConcurrentHashMap<>();

    private record WarpData(MagicProfile.WarpPoint destination, Location start,
                            MagicProfile profile, BukkitRunnable runnable) {}

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
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
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
        if (channels.containsKey(caster.getUniqueId())) return;
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
     * spell's (possibly modified) mana cost and runs the wind-up animation.
     */
    public void beginChannel(Player caster, MagicProfile.WarpPoint point) {
        UUID uuid = caster.getUniqueId();
        if (caster.isDead() || !caster.isOnline()) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) return;
        if (channels.containsKey(uuid)) return;

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

        Location start = caster.getLocation();
        int windUpTicks = config.windUpTicks(profile.getSpellMastery(this), getMaxMastery());

        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpTicks) {
                    cancel();
                    completeTeleport(caster, start, point, profile);
                    return;
                }
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    abortChannel(uuid, false);
                    return;
                }
                animate(caster, start, point, ticks, windUpTicks);
                ticks++;
            }
        };

        WarpData data = new WarpData(point, start, profile, runnable);
        channels.put(uuid, data);
        runnable.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void completeTeleport(Player caster, Location start, MagicProfile.WarpPoint point, MagicProfile profile) {
        UUID uuid = caster.getUniqueId();
        channels.remove(uuid);

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

        spawnTeleportParticles(caster, start);
        caster.teleport(destination);
        spawnTeleportParticles(caster, destination);
        caster.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);

        profile.setCooldown(this, System.currentTimeMillis());
        if (profile.getSpellMastery(this) < getMaxMastery()) {
            StatUtils.addSpellMastery(caster, this, 1);
        }
    }

    private void animate(Player caster, Location start, MagicProfile.WarpPoint point, int ticks, int windUpTicks) {
        Location eye = caster.getEyeLocation();
        Vector forward = eye.getDirection().normalize().multiply(1.5);
        List<Location> points = ParticleUtils.magicCircle(eye, eye.getYaw(), eye.getPitch(), forward, 3, 0);
        for (Location loc : points) {
            ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 0,
                    new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.4F));
        }

        Location destination = point.toLocation();
        if (destination != null) {
            Location destCenter = destination.clone().add(0, 1.2, 0);
            Vector destForward = destination.getDirection().normalize().multiply(1.5);
            List<Location> destPoints = ParticleUtils.magicCircle(
                    destCenter, destination.getYaw(), destination.getPitch(), destForward, 3, 0);
            for (Location loc : destPoints) {
                ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.6F));
            }
            spawnInboundSpiral(caster, eye, destCenter, ticks);
        }

        int every = Math.max(1, ((Long) Configs.CIRCLE_TICKS.get()).intValue());
        if (ticks % every == 0) {
            float progress = (float) ticks / windUpTicks;
            float pitch = 0.6f + progress * 0.6f;
            caster.getWorld().playSound(eye, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, pitch);
            if (destination != null) {
                destination.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, pitch);
            }
        }
    }

    private void spawnInboundSpiral(Player caster, Location start, Location dest, int ticks) {
        Vector direction = dest.toVector().subtract(start.toVector());
        double length = direction.length();
        if (length < 1) return;
        Vector unit = direction.clone().normalize();
        double progress = (ticks % 16) / 16.0;
        for (int i = 0; i < 8; i++) {
            double t = (progress + (double) i / 8.0) % 1.0;
            Location point = start.clone().add(unit.clone().multiply(length * t));
            double angle = ticks * 0.35 + i;
            point.add(Math.cos(angle) * 0.6, Math.sin(angle) * 0.6, 0);
            ParticleCaster.spawn(caster, start.getWorld(), Particle.PORTAL, point, 1, 0, 0, 0, 0);
        }
    }

    private void spawnTeleportParticles(Player caster, Location loc) {
        ParticleCaster.spawn(caster, loc.getWorld(), Particle.PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.5);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 15, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.6F));
    }

    private void playFailSound(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.5f);
    }

    private void abortChannel(UUID uuid, boolean announce) {
        WarpData data = channels.remove(uuid);
        if (data == null) return;
        data.runnable().cancel();
        if (announce) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.isDead()) {
                Utils.sendActionBar(player, lang().get("spells.warp.interrupted"));
                playFailSound(player);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (!channels.containsKey(uuid)) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (!from.getWorld().equals(to.getWorld())
                || from.distance(to) > config.moveCancelThreshold()) {
            abortChannel(uuid, true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player victim
                && channels.containsKey(victim.getUniqueId())) {
            abortChannel(victim.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        abortChannel(e.getPlayer().getUniqueId(), false);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation();
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5);
            List<Location> points = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 3, 0);
            for (int i = 0; i < 100; i++) {
                for (Location loc : points) {
                    ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 0,
                            new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
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