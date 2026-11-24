package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.PlayerCastEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 7 DARK UTILITY: hides the caster and consenting allies from hostile mobs for a short
 * span. Mobs lose and cannot keep them as targets while the veil holds; any outgoing attack or
 * offensive cast by a protected player tears the veil from them.
 */
public class VeilOfTheUnseenCourt extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.DARK);
    /** Allies are gathered from this radius, in blocks. */
    private static final double ALLY_RADIUS = 16.0;
    /** Per-tick throttle for the per-protected-player mob-target scan. */
    private static final int SCAN_INTERVAL_TICKS = 5;

    private static final class VeilState {
        final UUID caster;
        final Map<UUID, Long> protectedUntil = new ConcurrentHashMap<>();
        final BukkitTask task;
        int scanTick;

        VeilState(UUID caster, BukkitTask task) {
            this.caster = caster;
            this.task = task;
        }
    }

    /** Active veils keyed by the caster who raised them. */
    private final Map<UUID, VeilState> veils = new ConcurrentHashMap<>();
    /** Reverse index for quick checks: protected player -> caster. */
    private final Map<UUID, UUID> protectedBy = new ConcurrentHashMap<>();
    private boolean listenerRegistered;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public VeilOfTheUnseenCourt(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/veil_of_the_unseen_court_options.yml");
        Alkatraz.getInstance().save("spells/veil_of_the_unseen_court.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/veil_of_the_unseen_court.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, Alkatraz.getInstance());
            listenerRegistered = true;
        }
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int allies = (int) Math.round(option(p, "allies", 4));
        int seconds = (int) Math.round(getModifiedStat(p, "veil_duration", option(p, "duration", 25)));

        settle(p.getUniqueId());

        VeilState state = new VeilState(p.getUniqueId(), null);
        long until = System.currentTimeMillis() + seconds * 1000L;
        protect(state, p.getUniqueId(), until);
        for (Player ally : SpellTargeting.allies(p, p.getLocation(), ALLY_RADIUS, allies)) {
            protect(state, ally.getUniqueId(), until);
            Utils.sendActionBar(ally, recipientNotice(p));
        }

        VeilState running = new VeilState(p.getUniqueId(), new BukkitRunnable() {
            @Override
            public void run() {
                if (!tick(state)) {
                    settle(state.caster);
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 1L, 1L));
        running.protectedUntil.putAll(state.protectedUntil);
        veils.put(p.getUniqueId(), running);

        SpellVfx.selfAura(PALETTE.deep(), p, p, 1.4, 20, 2);
        SpellVfx.ring(PALETTE.accent(), p, p.getLocation(), 2.2, 24, 0, 0.8);
        Utils.sendActionBar(p, lang().get("spells.veiloftheunseencourt.veiled")
                .replace("{seconds}", String.valueOf(seconds)));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    private void protect(VeilState state, UUID playerId, long until) {
        state.protectedUntil.put(playerId, until);
        protectedBy.put(playerId, state.caster);
    }

    private boolean tick(VeilState state) {
        long now = System.currentTimeMillis();
        boolean active = false;
        List<Player> online = new ArrayList<>(state.protectedUntil.size());
        for (Map.Entry<UUID, Long> entry : state.protectedUntil.entrySet()) {
            if (entry.getValue() <= now) {
                removeProtected(state, entry.getKey(), false);
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || player.isDead()) {
                removeProtected(state, entry.getKey(), false);
                continue;
            }
            online.add(player);
            active = true;
        }
        if (state.scanTick++ % SCAN_INTERVAL_TICKS == 0) {
            for (Player player : online) {
                clearMobTargets(player);
            }
        }
        for (Player player : online) {
            SpellVfx.selfAura(PALETTE.core(), player, player, 0.9, 4, 0);
        }
        return active && !state.protectedUntil.isEmpty();
    }

    private void clearMobTargets(Player protectedPlayer) {
        for (org.bukkit.entity.Entity nearby : protectedPlayer.getWorld().getNearbyEntities(
                protectedPlayer.getLocation(), 32, 16, 32)) {
            if (nearby instanceof Mob mob && protectedPlayer.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }

    private boolean isProtected(UUID playerId) {
        UUID caster = protectedBy.get(playerId);
        if (caster == null) return false;
        VeilState state = veils.get(caster);
        if (state == null) {
            protectedBy.remove(playerId);
            return false;
        }
        Long until = state.protectedUntil.get(playerId);
        if (until == null) {
            protectedBy.remove(playerId);
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            removeProtected(state, playerId, false);
            return false;
        }
        return true;
    }

    private void removeProtected(VeilState state, UUID playerId, boolean broken) {
        state.protectedUntil.remove(playerId);
        protectedBy.remove(playerId, state.caster);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Utils.sendActionBar(player, lang().get(broken
                    ? "spells.veiloftheunseencourt.broken"
                    : "spells.veiloftheunseencourt.ended"));
        }
        if (state.protectedUntil.isEmpty()) {
            settle(state.caster);
        }
    }

    private void breakFor(UUID playerId) {
        UUID caster = protectedBy.get(playerId);
        if (caster == null) return;
        VeilState state = veils.get(caster);
        if (state == null) {
            protectedBy.remove(playerId);
            return;
        }
        if (state.protectedUntil.containsKey(playerId)) {
            removeProtected(state, playerId, true);
        }
    }

    /** Idempotent cleanup for a caster's veil. */
    private void settle(UUID casterId) {
        VeilState state = veils.remove(casterId);
        if (state == null) return;
        if (state.task != null) {
            state.task.cancel();
        }
        Set<UUID> ids = new HashSet<>(state.protectedUntil.keySet());
        state.protectedUntil.clear();
        for (UUID id : ids) {
            protectedBy.remove(id, casterId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isProtected(player.getUniqueId())) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOutgoingAttack(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker != null && isProtected(attacker.getUniqueId())) {
            breakFor(attacker.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCast(PlayerCastEvent event) {
        Player caster = event.getCaster();
        if (!isProtected(caster.getUniqueId())) return;
        Spell spell = event.getSpell();
        if (spell == this) return;
        SpellRole role = spell.getRole();
        if (role == SpellRole.BURST || role == SpellRole.SUSTAIN
                || role == SpellRole.DENIAL || role == SpellRole.TERRAIN) {
            breakFor(caster.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        breakFor(event.getPlayer().getUniqueId());
        settle(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        breakFor(event.getEntity().getUniqueId());
        settle(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        breakFor(event.getPlayer().getUniqueId());
    }

    @Override
    public void cancelTasks() {
        for (UUID casterId : new HashSet<>(veils.keySet())) {
            settle(casterId);
        }
        veils.clear();
        protectedBy.clear();
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 30) / 30.0;
        SpellVfx.ring(palette.deep(), caster, origin.clone().add(0, 0.4, 0),
                scale * (1.2 + phase * 1.6), 28, frame * 3, 0.05);
        SpellVfx.column(palette.core(), caster, origin, 2.2, scale * (0.5 + phase * 0.4), 2, 10, 0.3);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2, 0),
                1.0 + progress * 3.0, 20, tick * 2, 0.08);
        if (tick % 10 == 0) {
            SpellVfx.nova(palette.deep(), caster, origin, 0.8 + progress, 12);
        }
    }

    /** Short attribution for a player hidden by someone else's veil. */
    private static String recipientNotice(Player caster) {
        String key = "spells.recipient_notice_veil";
        String message = lang().get(key, "player", caster.getName(), "default", "Veil of the Unseen Court");
        return key.equals(message) ? "Veil of the Unseen Court by " + caster.getName() : message;
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
                .setDisplayName(lang().get("spells.veiloftheunseencourt.book_name"))
                .addCustomLoreLine(lang().get("spells.veiloftheunseencourt.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}
