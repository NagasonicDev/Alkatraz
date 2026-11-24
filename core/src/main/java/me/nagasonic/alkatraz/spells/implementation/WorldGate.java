package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.util.TeleportSafety;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 9 NONE UTILITY: tears a paired portal through the same world. The first cast records
 * the origin, the second within three minutes and the configured range opens both gates for a
 * minute. Only players who step into a ring are carried, and only up to the configured cap.
 */
public class WorldGate extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);
    private static final long DEFAULT_ORIGIN_WINDOW_MS = 3 * 60_000L;
    private static final long DEFAULT_PORTAL_LIFETIME_MS = 60_000L;
    private static final double ENTRY_RADIUS = 2.5;
    private static final long PER_PLAYER_COOLDOWN_MS = 3_000L;
    /** A destination counts as visible only at this range or closer. */
    private static final double SIGHT_RANGE = 96.0;

    private record Origin(Location location, World world, long expiresAt) {
    }

    private static final class Gate {
        final UUID owner;
        final World world;
        final Location a;
        final Location b;
        final long expiresAt;
        final int playerCap;
        final Set<UUID> served = new HashSet<>();
        final Map<UUID, Long> cooldownUntil = new HashMap<>();
        /** travellers waiting at a destination ring until they step out of both rings. */
        final Set<UUID> inRing = new HashSet<>();
        BukkitTask task;

        Gate(UUID owner, World world, Location a, Location b, long expiresAt, int playerCap) {
            this.owner = owner;
            this.world = world;
            this.a = a;
            this.b = b;
            this.expiresAt = expiresAt;
            this.playerCap = playerCap;
        }
    }

    private static final Map<UUID, Origin> ORIGINS = new HashMap<>();
    private static final Map<UUID, Gate> GATES = new HashMap<>();
    /** Ring endpoints each traveller has already stood at, used by the traversal precondition. */
    private static final Map<UUID, Set<String>> VISITED_ENDPOINTS = new ConcurrentHashMap<>();
    private volatile boolean listenerRegistered;

    private long originWindowMs = DEFAULT_ORIGIN_WINDOW_MS;
    private long portalLifetimeMs = DEFAULT_PORTAL_LIFETIME_MS;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public WorldGate(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/world_gate_options.yml");
        Alkatraz.getInstance().save("spells/world_gate.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/world_gate.yml").get();
        loadCommonConfig(spellConfig);
        this.originWindowMs = Math.max(1, spellConfig.getLong("origin_window", 180)) * 1000L;
        this.portalLifetimeMs = Math.max(1, spellConfig.getLong("open_for", 60)) * 1000L;
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
        if (GATES.containsKey(id)) {
            settle(id);
        }
        Origin origin = ORIGINS.get(id);
        if (origin != null && System.currentTimeMillis() > origin.expiresAt()) {
            ORIGINS.remove(id);
            origin = null;
        }
        if (origin == null) {
            recordOrigin(p);
            return;
        }
        openGate(p, origin);
    }

    private void recordOrigin(Player p) {
        Location loc = p.getLocation().clone();
        if (!TeleportSafety.isSafePlayerDestination(loc) || !Protection.blockEdit(p, loc)) {
            Utils.sendActionBar(p, lang().get("spells.worldgate.unsafe_destination"));
            cancelCast(p);
            return;
        }
        ORIGINS.put(p.getUniqueId(), new Origin(loc, p.getWorld(),
                System.currentTimeMillis() + originWindowMs));
        SpellVfx.ring(PALETTE.core(), p, loc, 2.0, 28, 0, 0.1);
        SpellVfx.column(PALETTE.accent(), p, loc, 3.0, 0.6, 3, 8, 0.5);
        Utils.sendActionBar(p, lang().get("spells.worldgate.origin_set"));
    }

    private void openGate(Player p, Origin origin) {
        Location dest = p.getLocation().clone();
        double range = getModifiedStat(p, "gate_range", option(p, "range", 512));
        int playerCap = (int) Math.round(getModifiedStat(p, "gate_players", option(p, "players", 8)));

        if (dest.getWorld() != origin.world()) {
            refuse(p, "wrong_world");
            return;
        }
        if (dest.distance(origin.location()) > range) {
            refuse(p, "too_far");
            return;
        }
        if (dest.distance(origin.location()) < 3.0) {
            refuse(p, "too_close");
            return;
        }
        if (!TeleportSafety.isSafePlayerDestination(dest) || !Protection.blockEdit(p, dest)) {
            refuse(p, "unsafe_destination");
            return;
        }

        ORIGINS.remove(p.getUniqueId());
        Gate gate = new Gate(p.getUniqueId(), origin.world(), origin.location().clone(),
                dest.clone(), System.currentTimeMillis() + portalLifetimeMs,
                Math.max(1, playerCap));
        GATES.put(p.getUniqueId(), gate);
        startTicker(gate);
        Utils.sendActionBar(p, lang().get("spells.worldgate.opened")
                .replace("{players}", String.valueOf(gate.playerCap)));
    }

    private void refuse(Player p, String reason) {
        Utils.sendActionBar(p, lang().get("spells.worldgate." + reason));
        cancelCast(p);
    }

    private void startTicker(Gate gate) {
        gate.task = new BukkitRunnable() {
            @Override
            public void run() {
                Player owner = Bukkit.getPlayer(gate.owner);
                if (owner == null || !owner.isOnline() || owner.isDead()
                        || System.currentTimeMillis() >= gate.expiresAt
                        || gate.served.size() >= gate.playerCap) {
                    settle(gate.owner);
                    return;
                }
                draw(gate, owner);
                carry(gate, owner, gate.a, gate.b);
                carry(gate, owner, gate.b, gate.a);
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 5L);
    }

    private void draw(Gate gate, Player owner) {
        double spin = (System.currentTimeMillis() / 50.0) % 360.0;
        SpellVfx.ring(PALETTE.core(), owner, gate.a.clone().add(0, 1.0, 0), ENTRY_RADIUS, 28,
                spin, 0.0);
        SpellVfx.ring(PALETTE.accent(), owner, gate.b.clone().add(0, 1.0, 0), ENTRY_RADIUS, 28,
                -spin, 0.0);
        ParticleCaster.spawn(owner, gate.world, Utils.PORTAL, gate.a.clone().add(0, 1.0, 0),
                4, 0.4, 0.8, 0.4, 0.02);
        ParticleCaster.spawn(owner, gate.world, Utils.PORTAL, gate.b.clone().add(0, 1.0, 0),
                4, 0.4, 0.8, 0.4, 0.02);
    }

    private void carry(Gate gate, Player owner, Location from, Location to) {
        long now = System.currentTimeMillis();
        List<Player> waiting = new ArrayList<>();
        for (Player traveller : gate.world.getPlayers()) {
            UUID id = traveller.getUniqueId();
            boolean inFrom = inRing(traveller, from);
            boolean inTo = inRing(traveller, to);
            if (!inFrom && !inTo) {
                gate.inRing.remove(id);
                continue;
            }
            markVisited(id, inFrom ? from : to);
            // A traveller we carried is parked at the destination until they step out.
            if (gate.inRing.contains(id) || !inFrom) continue;
            Long readyAt = gate.cooldownUntil.get(id);
            if (readyAt != null && readyAt > now) continue;
            if (!TeleportSafety.isSafePlayerDestination(to)) continue;
            if (!TeleportSafety.canTeleportAlly(owner, traveller, to)) continue;
            if (!knowsEndpoint(traveller, to)) continue;
            waiting.add(traveller);
        }
        // Party/team allies who opted in claim the slots before first-come strangers.
        waiting.sort(Comparator
                .comparingInt((Player traveller) -> SpellTargeting.isConsentingAlly(owner, traveller) ? 0 : 1)
                .thenComparingDouble(traveller -> traveller.getLocation().distanceSquared(from)));
        for (Player traveller : waiting) {
            if (gate.served.size() >= gate.playerCap) return;
            UUID id = traveller.getUniqueId();
            traveller.teleport(to.clone().add(0, 0.1, 0));
            traveller.setFallDistance(0.0f);
            gate.cooldownUntil.put(id, now + PER_PLAYER_COOLDOWN_MS);
            gate.served.add(id);
            gate.inRing.add(id);
            markVisited(id, to);
            if (!id.equals(owner.getUniqueId())) {
                Utils.sendActionBar(traveller, lang().get("spells.worldgate.ally_ack")
                        .replace("{caster}", owner.getName()));
            }
            SpellVfx.nova(PALETTE.core(), traveller, to, 1.6, 18);
        }
    }

    /** High-circle traversal: the endpoint must have been reached before, or be in sight now. */
    private boolean knowsEndpoint(Player traveller, Location endpoint) {
        UUID id = traveller.getUniqueId();
        if (VISITED_ENDPOINTS.getOrDefault(id, Set.of()).contains(endpointKey(endpoint))) return true;
        if (traveller.getWorld() != endpoint.getWorld()) return false;
        Location eye = traveller.getEyeLocation();
        Location aim = endpoint.clone().add(0, 1.0, 0);
        double distance = eye.distance(aim);
        if (distance > SIGHT_RANGE || distance < 1.0E-6) return false;
        RayTraceResult hit = traveller.getWorld().rayTraceBlocks(eye,
                aim.toVector().subtract(eye.toVector()).normalize(), distance, FluidCollisionMode.NEVER);
        return hit == null || hit.getHitPosition().distance(eye.toVector()) >= distance - 1.0;
    }

    private static void markVisited(UUID traveller, Location endpoint) {
        VISITED_ENDPOINTS.computeIfAbsent(traveller, ignored -> ConcurrentHashMap.newKeySet())
                .add(endpointKey(endpoint));
    }

    private static String endpointKey(Location endpoint) {
        return endpoint.getWorld().getUID() + ":" + endpoint.getBlockX()
                + ":" + endpoint.getBlockY() + ":" + endpoint.getBlockZ();
    }

    private static boolean inRing(Player traveller, Location ring) {
        return traveller.getLocation().distance(ring.clone().add(0, 1.0, 0)) <= ENTRY_RADIUS;
    }

    /** Idempotent: closes the gate pair and clears its state. */
    private void settle(UUID ownerId) {
        Gate gate = GATES.remove(ownerId);
        if (gate == null) return;
        if (gate.task != null) {
            gate.task.cancel();
            gate.task = null;
        }
        gate.served.clear();
        gate.cooldownUntil.clear();
        gate.inRing.clear();
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) Utils.sendActionBar(owner, lang().get("spells.worldgate.closed"));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    @Override
    public void cancelTasks() {
        for (UUID id : new ArrayList<>(GATES.keySet())) {
            settle(id);
        }
        ORIGINS.clear();
        VISITED_ENDPOINTS.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        clearFor(id);
        VISITED_ENDPOINTS.remove(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        clearFor(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        clearFor(e.getPlayer().getUniqueId());
    }

    private void clearFor(UUID id) {
        ORIGINS.remove(id);
        settle(id);
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 40) / 40.0;
        double radius = scale * (2.6 - phase * 1.4);
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 1.4, 0), radius, 32, frame * 3, 0.0);
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 1.4, 0), radius * 0.7,
                24, -frame * 5, 0.0);
        SpellVfx.ring(palette.deep(), caster, origin.clone().add(0, 1.4, 0), radius * 1.3,
                28, frame * 2, 0.0);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        if (tick % 5 == 0) {
            SpellVfx.ring(palette.deep(), caster, origin.clone().add(0, 1.2, 0),
                    2.0 + progress * 6.0, 30, tick * 4, 0.0);
            SpellVfx.column(palette.accent(), caster, origin, 6.0 * (1.0 - progress), 0.5,
                    3, 8, 0.7);
        }
        if (tick % 10 == 0) {
            ParticleCaster.spawn(caster, origin.getWorld(), Utils.PORTAL,
                    origin.clone().add(0, 2.0, 0), 10, 1.4, 2.0, 1.4, 0.03);
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
                .setDisplayName(lang().get("spells.worldgate.book_name"))
                .addCustomLoreLine(lang().get("spells.worldgate.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
