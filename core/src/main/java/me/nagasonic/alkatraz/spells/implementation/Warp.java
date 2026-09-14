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
                animate(caster, start, ticks, windUpTicks);
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

        Location originRift = caster.getLocation().add(caster.getLocation().getDirection().multiply(1.8));
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

    private void animate(Player caster, Location start, int ticks, int windUpTicks) {
        Location eye = caster.getEyeLocation();
        Location center = eye.clone().add(eye.getDirection().multiply(1.8));
        float yaw = eye.getYaw();
        float progress = (float) ticks / windUpTicks;
        int remaining = windUpTicks - ticks;
        double shrink = remaining <= 10 ? Math.max(0.15, remaining / 10.0) : 1.0;

        double hw = (1.2 + 1.2 * progress) * shrink;
        double halfH = hw * 1.1;
        List<Location> outer = ParticleUtils.ellipse(center, yaw, hw * 2, halfH * 2, 28);
        List<Location> inner = ParticleUtils.ellipse(center, yaw, hw * 1.2, halfH * 1.2, 20);

        for (int i = 0; i < outer.size(); i++) {
            ParticleCaster.spawn(caster, center.getWorld(), Particle.PORTAL,
                    outer.get((i + ticks) % outer.size()), 1);
        }
        for (int i = 0; i < inner.size(); i++) {
            ParticleCaster.spawn(caster, center.getWorld(), Utils.DUST,
                    inner.get(Math.floorMod(i - ticks * 2, inner.size())), 0,
                    new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.6F));
        }
        for (int s = 0; s < 6; s++) {
            Location tip = outer.get(s * (outer.size() / 6));
            double frac = 1.0 - ((ticks + s) % 10) / 10.0;
            Vector dir = tip.toVector().subtract(center.toVector()).multiply(frac);
            ParticleCaster.spawn(caster, center.getWorld(), Particle.PORTAL,
                    center.clone().add(dir), 1);
        }

        int every = Math.max(1, ((Long) Configs.CIRCLE_TICKS.get()).intValue());
        if (ticks % every == 0) {
            float pitch = 0.6f + progress * 0.6f;
            caster.getWorld().playSound(eye, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, pitch);
        }
    }

    private void spawnTeleportParticles(Player caster, Location loc) {
        ParticleCaster.spawn(caster, loc.getWorld(), Particle.PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.5);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 15, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.6F));
    }

    private void spawnCollapseBurst(Player caster, Location loc) {
        ParticleCaster.spawn(caster, loc.getWorld(), Particle.PORTAL, loc, 40, 0.4, 0.4, 0.4, 0.05);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 20, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.8F));
    }

    private void spawnArrivalExpand(Player caster, Location destination, float yaw) {
        World world = destination.getWorld();
        if (world == null || !world.isChunkLoaded(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)) return;
        Location center = destination.clone().add(0, 1.1, 0);
        new BukkitRunnable() {
            int expand = 0;

            @Override
            public void run() {
                if (expand >= 10) {
                    cancel();
                    return;
                }
                double hw = 0.6 + expand * 0.18;
                List<Location> ring = ParticleUtils.ellipse(center, yaw, hw * 2, hw * 2.2, 36);
                for (Location loc : ring) {
                    ParticleCaster.spawn(caster, world, Particle.PORTAL, loc, 1);
                }
                expand++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
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
            Location eye = caster.getEyeLocation();
            Location center = eye.clone().add(eye.getDirection().multiply(1.8));
            List<Location> points = ParticleUtils.ellipse(center, eye.getYaw(), 1.6, 2.0, 28);
            for (Location loc : points) {
                ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.4F));
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