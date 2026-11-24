package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellMarks;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 8 LIGHT SUSTAIN: plants a luminous worldtree that heals up to eight allies inside
 * its radius and holds four absorption on each of them. It never damages anything, and every
 * participant's prior absorption is restored when the aura ends or they leave it.
 */
public class WorldtreeBeacon extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.LIGHT);
    private static final int MAX_ALLIES = 8;
    private static final long MARK_REFRESH_TICKS = 60L;

    private static final Map<UUID, Beacon> LIVE = new HashMap<>();
    private volatile boolean listenerRegistered;

    private static final class Beacon {
        final UUID owner;
        final World world;
        final Location center;
        final double radius;
        final int durationTicks;
        final double healPerSecond;
        final double absorption;
        final Set<UUID> inside = new HashSet<>();
        final Map<UUID, Double> priorAbsorption = new HashMap<>();
        BukkitTask task;
        BukkitTask markTask;

        Beacon(UUID owner, World world, Location center, double radius, int durationTicks,
               double healPerSecond, double absorption) {
            this.owner = owner;
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.durationTicks = durationTicks;
            this.healPerSecond = healPerSecond;
            this.absorption = absorption;
        }
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public WorldtreeBeacon(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/worldtree_beacon_options.yml");
        Alkatraz.getInstance().save("spells/worldtree_beacon.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/worldtree_beacon.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        registerListenerOnce();
    }

    private synchronized void registerListenerOnce() {
        if (listenerRegistered) return;
        Bukkit.getPluginManager().registerEvents(this, Alkatraz.getInstance());
        listenerRegistered = true;
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }
        UUID id = p.getUniqueId();
        settle(id);

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/worldtree_beacon.yml").get();
        double radius = getModifiedStat(p, "beacon_radius", option(p, "radius", 20));
        int durationSeconds = (int) Math.round(getModifiedStat(p, "beacon_duration", option(p, "duration", 18)));
        double heal = spellConfig.getDouble("heal_per_second", 1.0);
        double absorption = spellConfig.getDouble("absorption", 4.0);

        Beacon beacon = new Beacon(id, p.getWorld(), p.getLocation().clone(), Math.max(1.0, radius),
                Math.max(20, durationSeconds * 20), Math.max(0.0, heal), Math.max(0.0, absorption));
        LIVE.put(id, beacon);
        startTicker(beacon);
        startMark(beacon, p);

        SpellVfx.ring(PALETTE.core(), p, beacon.center, 2.0, 28, 0, 0.2);
        SpellVfx.column(PALETTE.accent(), p, beacon.center, 6.0, 0.7, 4, 8, 0.8);
    }

    private void startTicker(Beacon beacon) {
        beacon.task = new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (elapsed >= beacon.durationTicks) {
                    settle(beacon.owner);
                    Player owner = Bukkit.getPlayer(beacon.owner);
                    if (owner != null) {
                        Utils.sendActionBar(owner, lang().get("spells.worldtreebeacon.expired"));
                    }
                    return;
                }
                Player owner = Bukkit.getPlayer(beacon.owner);
                if (owner == null || !owner.isOnline() || owner.isDead()
                        || owner.getWorld() != beacon.world) {
                    settle(beacon.owner);
                    return;
                }
                aura(beacon, owner);
                elapsed += 20;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 20L, 20L);
    }

    private void startMark(Beacon beacon, Player owner) {
        beacon.markTask = Bukkit.getScheduler().runTaskTimer(Alkatraz.getInstance(), () -> {
            Player viewer = Bukkit.getPlayer(beacon.owner);
            if (viewer == null || !viewer.isOnline() || !LIVE.containsKey(beacon.owner)) return;
            SpellMarks.mark(viewer, beacon.center.clone().add(0, 3.0, 0), PALETTE,
                    lang().get("spells.worldtreebeacon.mark"));
        }, 0L, MARK_REFRESH_TICKS);
    }

    private void aura(Beacon beacon, Player owner) {
        List<Player> participants = new ArrayList<>();
        participants.add(owner);
        participants.addAll(SpellTargeting.allies(owner, beacon.center, beacon.radius,
                Math.max(0, MAX_ALLIES - 1)));

        double radiusSq = beacon.radius * beacon.radius;
        Set<UUID> now = new HashSet<>();
        for (Player participant : participants) {
            if (!isInside(participant, beacon.center, radiusSq)) continue;
            UUID id = participant.getUniqueId();
            now.add(id);
            if (!beacon.inside.contains(id)) {
                Utils.sendActionBar(participant, lang().get("spells.worldtreebeacon.aided_by")
                        .replace("{caster}", owner.getName()));
            }
            beacon.priorAbsorption.computeIfAbsent(id, key -> participant.getAbsorptionAmount());
            double health = Math.min(participant.getMaxHealth(), participant.getHealth() + beacon.healPerSecond);
            participant.setHealth(health);
            if (participant.getAbsorptionAmount() < beacon.absorption) {
                participant.setAbsorptionAmount(beacon.absorption);
            }
        }

        for (UUID gone : new ArrayList<>(beacon.inside)) {
            if (!now.contains(gone)) releaseParticipant(beacon, gone);
        }
        beacon.inside.clear();
        beacon.inside.addAll(now);

        SpellVfx.ring(PALETTE.accent(), owner, beacon.center.clone().add(0, 0.4, 0),
                beacon.radius, 40, beacon.inside.size(), 0.1);
        SpellVfx.column(PALETTE.core(), owner, beacon.center, 4.0, 0.5, 3, 8, 0.6);
        ParticleCaster.spawn(owner, beacon.world, Utils.END_ROD,
                beacon.center.clone().add(0, 2.2, 0), 6, 0.6, 0.4, 0.6);
    }

    private static boolean isInside(Player participant, Location center, double radiusSq) {
        return participant.getWorld() == center.getWorld()
                && participant.getLocation().distanceSquared(center) <= radiusSq;
    }

    /** Restores one participant's absorption and forgets them. */
    private static void releaseParticipant(Beacon beacon, UUID id) {
        Double prior = beacon.priorAbsorption.remove(id);
        beacon.inside.remove(id);
        if (prior == null) return;
        Player participant = Bukkit.getPlayer(id);
        if (participant != null && participant.isOnline()) {
            participant.setAbsorptionAmount(prior);
        }
    }

    /** Idempotent: removes the beacon, cancels its ticker and restores every participant. */
    private void settle(UUID ownerId) {
        Beacon beacon = LIVE.remove(ownerId);
        if (beacon == null) return;
        if (beacon.task != null) {
            beacon.task.cancel();
            beacon.task = null;
        }
        if (beacon.markTask != null) {
            beacon.markTask.cancel();
            beacon.markTask = null;
        }
        for (UUID id : new ArrayList<>(beacon.priorAbsorption.keySet())) {
            releaseParticipant(beacon, id);
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) SpellMarks.clear(owner);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    @Override
    public void cancelTasks() {
        for (UUID id : new ArrayList<>(LIVE.keySet())) {
            settle(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        releaseAll(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        releaseAll(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        releaseAll(e.getPlayer().getUniqueId());
    }

    private void releaseAll(UUID id) {
        if (LIVE.containsKey(id)) {
            settle(id);
            return;
        }
        for (Beacon beacon : LIVE.values()) {
            if (beacon.priorAbsorption.containsKey(id)) releaseParticipant(beacon, id);
        }
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 28) / 28.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.3 + phase * 3.0, 0),
                scale * (0.8 + phase * 0.8), 28, frame * 3, 0.15);
        SpellVfx.column(palette.core(), caster, origin, 5.0, scale * 0.5, 5, 8, 0.5);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        double radius = 2.0 + progress * 5.0;
        if (tick % 6 == 0) {
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2, 0), radius, 24,
                    tick * 3, 0.0);
        }
        if (tick % 4 == 0) {
            ParticleCaster.spawn(caster, origin.getWorld(), Utils.END_ROD,
                    origin.clone().add((Math.random() - 0.5) * 4, 3.0, (Math.random() - 0.5) * 4),
                    2, 0.1, -0.1, 0.1);
        }
    }

    /** Numeric option value, falling back when the option file is absent. */
    private double option(Player p, String id, double fallback) {
        if (getOption(id) == null) return fallback;
        Object value = getOption(id).getSelectedValue(p).getValue();
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.worldtreebeacon.book_name"))
                .addCustomLoreLine(lang().get("spells.worldtreebeacon.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}
