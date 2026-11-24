package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 7 WATER UTILITY: summons a spectral leviathan mount (an invisible boat with a
 * particle body) that carries the caster and consenting allies. Boats are not LivingEntities,
 * so lifecycle cleanup is owned here and must be idempotent across expiry and every leave path.
 */
public class LeviathansPassage extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);
    private static final String TAG = "alkatraz_leviathans_passage";
    private static final int DEFAULT_DURATION_SECONDS = 45;
    private static final int DEFAULT_PASSENGERS = 2;
    /** Target horizontal speed (blocks/tick) for roughly 1.5x boat travel. */
    private static final double TARGET_SPEED = 0.6;
    private static final double BODY_RADIUS = 1.2;

    private static final Map<UUID, Mount> ACTIVE = new ConcurrentHashMap<>();
    private volatile boolean listenerRegistered = false;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public LeviathansPassage(String type) {
        super(type);
        ensureListener();
    }

    private void ensureListener() {
        if (listenerRegistered) return;
        synchronized (LeviathansPassage.class) {
            if (!listenerRegistered) {
                Bukkit.getPluginManager().registerEvents(this, Alkatraz.getInstance());
                listenerRegistered = true;
            }
        }
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/leviathans_passage_options.yml");
        Alkatraz.getInstance().save("spells/leviathans_passage.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/leviathans_passage.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || p.getWorld() == null) {
            cancelCast(p);
            return;
        }
        int durationSeconds = (int) Math.round(option(p, "duration", DEFAULT_DURATION_SECONDS));
        int extraPassengers = Math.max(0, (int) Math.round(option(p, "passengers", DEFAULT_PASSENGERS)));
        // "up to 2 passengers" in addition to the caster.
        int extra = Math.min(extraPassengers, 2);

        removeMount(p.getUniqueId());

        World world = p.getWorld();
        Location spawn = p.getLocation().clone();
        Boat boat = world.spawn(spawn, Boat.class, b -> {
            b.setSilent(true);
            b.setInvulnerable(true);
            b.addScoreboardTag(TAG);
        });
        if (!boat.isValid()) {
            cancelCast(p);
            return;
        }

        boat.addPassenger(p);
        List<Player> riders = new ArrayList<>();
        riders.add(p);
        for (Player ally : SpellTargeting.allies(p, spawn, 6.0, extra)) {
            if (ally.isOnline() && !ally.isDead() && ally.getWorld() == world) {
                boat.addPassenger(ally);
                riders.add(ally);
            }
        }
        applyBreathing(riders);

        int durationTicks = Math.max(20, durationSeconds * 20);
        BukkitTask task = new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (ticks++ >= durationTicks || !boat.isValid() || boat.isDead()) {
                    removeMount(p.getUniqueId());
                    cancel();
                    return;
                }
                if (!(boat.getWorld() != null)) {
                    removeMount(p.getUniqueId());
                    cancel();
                    return;
                }
                maintainSpeed(boat);
                applyBreathing(currentRiders(boat, p));
                drawBody(p, boat.getLocation());
            }
        }.runTaskTimer(Alkatraz.getInstance(), 1L, 1L);

        ACTIVE.put(p.getUniqueId(), new Mount(boat.getUniqueId(), task));
        Utils.sendActionBar(p, lang().get("spells.leviathanspassage.summoned"));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    private static List<Player> currentRiders(Boat boat, Player owner) {
        List<Player> riders = new ArrayList<>();
        for (Entity passenger : boat.getPassengers()) {
            if (passenger instanceof Player player) riders.add(player);
        }
        if (owner.isOnline() && owner.isInsideVehicle() && owner.getVehicle() == boat && !riders.contains(owner)) {
            riders.add(owner);
        }
        return riders;
    }

    private static void maintainSpeed(Boat boat) {
        Vector vel = boat.getVelocity();
        double hx = vel.getX();
        double hz = vel.getZ();
        double h = Math.sqrt(hx * hx + hz * hz);
        if (h > 0.01 && h < TARGET_SPEED) {
            double scale = TARGET_SPEED / h;
            boat.setVelocity(new Vector(hx * scale, vel.getY(), hz * scale));
        }
    }

    private static void applyBreathing(List<Player> riders) {
        for (Player rider : riders) {
            if (rider.isOnline() && !rider.isDead()) {
                rider.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, false, false, true));
            }
        }
    }

    private void drawBody(Player caster, Location center) {
        if (center == null || center.getWorld() == null) return;
        int frame = (int) (center.getWorld().getFullTime() % 40);
        double phase = (frame % 20) / 20.0 * Math.PI * 2.0;
        SpellVfx.ring(PALETTE.accent(), caster, center.clone().add(0, 0.2, 0),
                BODY_RADIUS + Math.sin(phase) * 0.15, 18, phase, 0.0);
        SpellVfx.ring(PALETTE.core(), caster, center.clone().add(0, 0.6, 0),
                BODY_RADIUS * 0.7, 12, -phase, 0.0);
        SpellVfx.ring(PALETTE.deep(), caster, center.clone().add(0, 1.0, 0),
                BODY_RADIUS * 0.45, 10, phase * 0.5, 0.0);
    }

    private static void removeMount(UUID owner) {
        Mount mount = ACTIVE.remove(owner);
        if (mount == null) return;
        if (mount.task != null) {
            mount.task.cancel();
        }
        Entity entity = Bukkit.getEntity(mount.boatId);
        if (entity != null) {
            entity.eject();
            entity.remove();
        }
    }

    @Override
    public void cancelTasks() {
        for (UUID owner : new ArrayList<>(ACTIVE.keySet())) {
            removeMount(owner);
        }
        sweepTagged();
    }

    @Override
    public void stopAll() {
        cancelTasks();
    }

    private static void sweepTagged() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.eject();
                    entity.remove();
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeMount(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        removeMount(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeMount(event.getPlayer().getUniqueId());
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 24) / 24.0;
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.2, 0),
                scale * (1.2 + phase * 1.4), 28, frame * 6, 0.0);
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.6 + phase * 0.8, 0),
                scale * (0.9 + (1 - phase) * 0.6), 20, -frame * 6, 0.0);
        SpellVfx.ring(palette.deep(), caster, origin.clone().add(0, 0.1, 0),
                scale * 0.7, 16, frame * 3, 0.12);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double t = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.2, 0),
                1.0 + t * 3.0, 24, tick * 8, 0.0);
        if (tick % 5 == 0) {
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.4 + t, 0),
                    0.8 + t * 1.6, 18, -tick * 6, 0.0);
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
                .setDisplayName(lang().get("spells.leviathanspassage.book_name"))
                .addCustomLoreLine(lang().get("spells.leviathanspassage.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }

    private static final class Mount {
        final UUID boatId;
        final BukkitTask task;

        Mount(UUID boatId, BukkitTask task) {
            this.boatId = boatId;
            this.task = task;
        }
    }
}
