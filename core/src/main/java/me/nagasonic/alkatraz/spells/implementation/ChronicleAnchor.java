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
import me.nagasonic.alkatraz.spells.util.TeleportSafety;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 8 NONE UTILITY: records a safe location as an anchor, then returns the caster and a
 * handful of consenting allies to it on recast within the window. Consumed on return and cleaned
 * up on quit, death, world change and plugin disable.
 */
public class ChronicleAnchor extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);
    private static final double ALLY_RADIUS = 8.0;
    private static final long MARK_REFRESH_TICKS = 180L;

    private static final Map<UUID, Anchor> ANCHORS = new ConcurrentHashMap<>();
    private volatile boolean listenerRegistered;

    private record Anchor(Location location, long expiresAtMillis, BukkitTask expiryTask, BukkitTask markTask) {}

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public ChronicleAnchor(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/chronicle_anchor_options.yml");
        Alkatraz.getInstance().save("spells/chronicle_anchor.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/chronicle_anchor.yml").get();
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
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        UUID id = p.getUniqueId();
        Anchor existing = ANCHORS.get(id);
        if (existing != null && System.currentTimeMillis() <= existing.expiresAtMillis()) {
            recall(p, existing);
            return;
        }
        if (existing != null) {
            clearAnchor(id);
        }
        placeAnchor(p);
    }

    private void placeAnchor(Player p) {
        Location loc = p.getLocation().clone();
        if (!TeleportSafety.isSafePlayerDestination(loc)) {
            Utils.sendActionBar(p, lang().get("spells.chronicleanchor.no_safe_location"));
            cancelCast(p);
            return;
        }
        int durationSec = (int) Math.round(getModifiedStat(p, "anchor_duration", option(p, "duration", 120)));
        long expiresAt = System.currentTimeMillis() + durationSec * 1000L;
        UUID id = p.getUniqueId();

        BukkitTask expiry = Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            Anchor a = ANCHORS.get(id);
            if (a != null && System.currentTimeMillis() >= a.expiresAtMillis()) {
                clearAnchor(id);
            }
        }, durationSec * 20L);

        BukkitTask mark = Bukkit.getScheduler().runTaskTimer(Alkatraz.getInstance(), () -> {
            Player viewer = Bukkit.getPlayer(id);
            Anchor a = ANCHORS.get(id);
            if (viewer == null || !viewer.isOnline() || a == null) return;
            SpellMarks.mark(viewer, a.location(), PALETTE, lang().get("spells.chronicleanchor.mark"));
        }, 0L, MARK_REFRESH_TICKS);

        ANCHORS.put(id, new Anchor(loc, expiresAt, expiry, mark));
        SpellVfx.ring(PALETTE.core(), p, loc, 1.4, 24, 0, 1.0);
        SpellVfx.column(PALETTE.accent(), p, loc, 2.0, 0.6, 3, 10, 0.4);
        Utils.sendActionBar(p, lang().get("spells.chronicleanchor.anchor_set")
                .replace("{seconds}", String.valueOf(durationSec)));
    }

    private void recall(Player p, Anchor anchor) {
        Location dest = anchor.location().clone();
        if (dest.getWorld() == null || !dest.getWorld().isChunkLoaded(dest.getBlockX() >> 4, dest.getBlockZ() >> 4)
                || !TeleportSafety.isSafePlayerDestination(dest)) {
            Utils.sendActionBar(p, lang().get("spells.chronicleanchor.destination_unsafe"));
            clearAnchor(p.getUniqueId());
            cancelCast(p);
            return;
        }
        int allyCap = (int) Math.round(getModifiedStat(p, "anchor_allies", option(p, "allies", 3)));
        List<Player> allies = SpellTargeting.consenting(p, p.getLocation(), ALLY_RADIUS, allyCap);

        clearAnchor(p.getUniqueId());
        p.teleport(dest);
        for (int i = 0; i < allies.size(); i++) {
            Player ally = allies.get(i);
            Location beside = beside(dest, i);
            Location landing = TeleportSafety.isSafePlayerDestination(beside) ? beside : dest;
            if (!TeleportSafety.canTeleportAlly(p, ally, landing)) continue;
            ally.teleport(landing);
            Utils.sendActionBar(ally, lang().get("spells.chronicleanchor.ally_ack")
                    .replace("{caster}", p.getName()));
            SpellVfx.ring(PALETTE.deep(), p, ally.getLocation(), 1.0, 12, 0, 1.0);
        }
        SpellVfx.nova(PALETTE.core(), p, dest, 2.2, 24);
        Utils.sendActionBar(p, lang().get("spells.chronicleanchor.returned"));
    }

    private Location beside(Location anchor, int index) {
        double angle = index * Math.PI / 2.0;
        return anchor.clone().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
    }

    private static void clearAnchor(UUID id) {
        Anchor anchor = ANCHORS.remove(id);
        if (anchor == null) return;
        if (anchor.expiryTask() != null) anchor.expiryTask().cancel();
        if (anchor.markTask() != null) anchor.markTask().cancel();
        Player viewer = Bukkit.getPlayer(id);
        if (viewer != null) SpellMarks.clear(viewer);
    }

    private static void clearAll() {
        for (UUID id : ANCHORS.keySet().toArray(new UUID[0])) {
            clearAnchor(id);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    @Override
    public void cancelTasks() {
        clearAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clearAnchor(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        clearAnchor(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        clearAnchor(e.getPlayer().getUniqueId());
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 32) / 32.0;
        double r = scale * (1.0 + phase * 1.2);
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.4, 0), r, 28, frame * 2, 0.1);
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.8, 0), r * 0.7, 20, -frame * 3, 0.0);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double t = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        double r = 0.8 + t * 2.6;
        if (tick % 4 == 0) {
            SpellVfx.ring(palette.deep(), caster, origin, r, 24, tick, 0.0);
        }
        if (tick < 40 && tick % 2 == 0) {
            SpellVfx.column(palette.accent(), caster, origin, 2.4 - t * 1.6, 0.4, 4, 6, 0.6);
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
                .setDisplayName(lang().get("spells.chronicleanchor.book_name"))
                .addCustomLoreLine(lang().get("spells.chronicleanchor.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}
