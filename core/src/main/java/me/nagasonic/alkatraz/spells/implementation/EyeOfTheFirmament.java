package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.SpellRole;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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
 * Circle 8 AIR UTILITY: the caster and up to three team allies fly under control for a short
 * window at 1.2x vanilla fly speed. Melee and offensive spells are blocked until each player
 * lands, and creative flight is restored rather than removed.
 */
public class EyeOfTheFirmament extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.AIR);
    private static final double ALLY_RADIUS = 10.0;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** participant -> owner, so an event can find the session that holds a player. */
    private static final Map<UUID, UUID> TRACKED = new HashMap<>();
    private volatile boolean listenerRegistered;

    private record FlightState(boolean allowFlight, float flySpeed) {
    }

    private static final class Session {
        final UUID owner;
        final World world;
        final long expiresAtMillis;
        final float flySpeed;
        final Map<UUID, FlightState> prior = new HashMap<>();
        final Set<UUID> flown = new HashSet<>();
        BukkitTask task;

        Session(UUID owner, World world, long expiresAtMillis, float flySpeed) {
            this.owner = owner;
            this.world = world;
            this.expiresAtMillis = expiresAtMillis;
            this.flySpeed = flySpeed;
        }
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public EyeOfTheFirmament(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/eye_of_the_firmament_options.yml");
        Alkatraz.getInstance().save("spells/eye_of_the_firmament.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/eye_of_the_firmament.yml").get();
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
        settle(p.getUniqueId());

        int durationSeconds = (int) Math.round(getModifiedStat(p, "flight_duration",
                option(p, "duration", 22)));
        int allyCap = (int) Math.round(getModifiedStat(p, "flight_allies", option(p, "allies", 3)));

        Session session = new Session(p.getUniqueId(), p.getWorld(),
                System.currentTimeMillis() + Math.max(1, durationSeconds) * 1000L,
                (float) Math.max(0.01, ConfigManager.getConfig("spells/eye_of_the_firmament.yml")
                        .get().getDouble("fly_speed", 0.06)));
        SESSIONS.put(p.getUniqueId(), session);
        grant(session, p, durationSeconds, p.getName());
        List<Player> allies = SpellTargeting.allies(p, p.getLocation(), ALLY_RADIUS, Math.max(0, allyCap));
        for (Player ally : allies) {
            grant(session, ally, durationSeconds, p.getName());
        }

        SpellVfx.ring(PALETTE.core(), p, p.getLocation().clone().add(0, 2.2, 0), 3.0, 32, 0, 0.0);
        SpellVfx.column(PALETTE.accent(), p, p.getLocation(), 4.0, 0.6, 4, 8, 0.7);
        startTicker(session);
    }

    private void grant(Session session, Player participant, int durationSeconds, String casterName) {
        UUID id = participant.getUniqueId();
        if (session.prior.containsKey(id)) return;
        session.prior.put(id, new FlightState(participant.getAllowFlight(), participant.getFlySpeed()));
        TRACKED.put(id, session.owner);
        participant.setAllowFlight(true);
        participant.setFlySpeed(session.flySpeed);
        participant.setFallDistance(0.0f);
        Utils.sendActionBar(participant, lang().get("spells.eyeofthefirmament.aided_by")
                .replace("{caster}", casterName)
                .replace("{seconds}", String.valueOf(durationSeconds)));
    }

    private void startTicker(Session session) {
        session.task = new BukkitRunnable() {
            @Override
            public void run() {
                Player owner = Bukkit.getPlayer(session.owner);
                if (owner == null || !owner.isOnline() || owner.isDead()
                        || owner.getWorld() != session.world
                        || System.currentTimeMillis() >= session.expiresAtMillis) {
                    settle(session.owner);
                    return;
                }
                for (UUID id : new ArrayList<>(session.prior.keySet())) {
                    Player participant = Bukkit.getPlayer(id);
                    if (participant == null || !participant.isOnline() || participant.isDead()
                            || participant.getWorld() != session.world) {
                        release(session, id);
                        continue;
                    }
                    if (!participant.isOnGround()) {
                        session.flown.add(id);
                    } else if (session.flown.contains(id)) {
                        release(session, id);
                        continue;
                    }
                    SpellVfx.selfAura(PALETTE.core(), owner, participant, 0.9, 8, id.hashCode() * 0.01);
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 20L, 20L);
    }

    private void release(Session session, UUID id) {
        FlightState state = session.prior.remove(id);
        session.flown.remove(id);
        TRACKED.remove(id);
        Player participant = Bukkit.getPlayer(id);
        if (participant == null) return;
        if (state != null) {
            participant.setFlySpeed(state.flySpeed());
            if (!state.allowFlight()) {
                participant.setAllowFlight(false);
                participant.setFlying(false);
            }
        } else {
            participant.setAllowFlight(false);
            participant.setFlying(false);
        }
        Utils.sendActionBar(participant, lang().get("spells.eyeofthefirmament.ended"));
    }

    /** Idempotent: cancels the window and restores every participant's flight state. */
    private void settle(UUID ownerId) {
        Session session = SESSIONS.remove(ownerId);
        if (session == null) return;
        if (session.task != null) {
            session.task.cancel();
            session.task = null;
        }
        for (UUID id : new ArrayList<>(session.prior.keySet())) {
            release(session, id);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    @Override
    public void cancelTasks() {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            settle(id);
        }
    }

    @EventHandler
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player damager)) return;
        if (!TRACKED.containsKey(damager.getUniqueId())) return;
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        e.setCancelled(true);
        Utils.sendActionBar(damager, lang().get("spells.eyeofthefirmament.melee_blocked"));
    }

    @EventHandler
    public void onPrepare(PlayerSpellPrepareEvent e) {
        Player caster = e.getCaster();
        if (caster == null || !TRACKED.containsKey(caster.getUniqueId())) return;
        if (e.getSpell() == null || e.getSpell().getRole() != SpellRole.BURST) return;
        e.setCancelled(true);
        Utils.sendActionBar(caster, lang().get("spells.eyeofthefirmament.spell_blocked"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        releasePlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        releasePlayer(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        releasePlayer(e.getPlayer().getUniqueId());
    }

    private void releasePlayer(UUID id) {
        if (SESSIONS.containsKey(id)) {
            settle(id);
            return;
        }
        UUID owner = TRACKED.get(id);
        Session session = owner == null ? null : SESSIONS.get(owner);
        if (session != null) release(session, id);
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 32) / 32.0;
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 2.4, 0),
                scale * 1.6, 30, frame * 3, 0.0);
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 2.4, 0),
                scale * (1.6 - phase * 1.0), 24, -frame * 4, 0.0);
        SpellVfx.column(palette.deep(), caster, origin, 3.2, scale * 0.3, 3, 6, 0.5);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        if (tick % 4 == 0) {
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 2.4 - progress * 1.8, 0),
                    0.6 + progress * 3.0, 22, tick * 4, 0.0);
        }
        if (tick % 6 == 0) {
            ParticleCaster.spawn(caster, origin.getWorld(), Utils.END_ROD,
                    origin.clone().add((Math.random() - 0.5) * 3, 2.0, (Math.random() - 0.5) * 3),
                    2, 0.0, -0.25, 0.0, 0.02);
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
                .setDisplayName(lang().get("spells.eyeofthefirmament.book_name"))
                .addCustomLoreLine(lang().get("spells.eyeofthefirmament.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}
