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
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.SpellGroups;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.util.TeleportSafety;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 9 AIR UTILITY: a wind passage that carries the caster and up to seven team allies over
 * eight seconds. Riders cannot attack or cast offensive spells during the ride; damage to the
 * caster ends it for everyone, and arrival grants Slow Falling.
 */
public class EmpyreanPassage extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.AIR);
    private static final int RIDE_TICKS = 160;
    private static final double ALLY_RADIUS = 24.0;
    private static final int ALLY_CAP = 7;
    private static final double DAMAGE = SpellBudgets.powerFor(9);
    private static final double BLOCK_RADIUS = 1.8;
    private static final int BLOCK_BUDGET = 8;

    private static final Map<UUID, Passage> SESSIONS = new ConcurrentHashMap<>();
    private volatile boolean listenerRegistered;

    private int length;
    private int allies;

    private static final class Passage {
        final UUID owner;
        final World world;
        final Location start;
        final Location end;
        final ItemStack wand;
        final Set<UUID> riders = new LinkedHashSet<>();
        final Map<UUID, Object> rideTokens = new HashMap<>();
        int elapsed;
        BukkitTask task;

        Passage(UUID owner, World world, Location start, Location end, ItemStack wand) {
            this.owner = owner;
            this.world = world;
            this.start = start;
            this.end = end;
            this.wand = wand;
        }
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public EmpyreanPassage(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/empyrean_passage_options.yml");
        Alkatraz.getInstance().save("spells/empyrean_passage.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/empyrean_passage.yml").get();
        loadCommonConfig(spellConfig);
        this.length = spellConfig.getInt("length", 96);
        this.allies = spellConfig.getInt("allies", 7);
        loadOptions();
        registerListenerOnce();
    }

    private void registerListenerOnce() {
        if (listenerRegistered) return;
        listenerRegistered = true;
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }
        settle(p.getUniqueId());
        // The framework wind-up has completed before castAction runs; no local channel.
        spawnPassage(p, wand);
    }

    private void spawnPassage(Player p, ItemStack wand) {
        int reach = Math.max(8, (int) Math.round(
                getModifiedStat(p, "passage_length", option(p, "length", length))));
        int maxAllies = Math.max(0, (int) Math.round(
                getModifiedStat(p, "passage_allies", option(p, "allies", allies))));

        Location start = p.getLocation().clone();
        World world = p.getWorld();
        Location end = start.clone().add(horizontal(p).multiply(reach));
        Block hit = p.getTargetBlockExact(reach);
        if (hit != null) {
            end = hit.getLocation().add(0.5, 1.0, 0.5);
        }
        if (end.distanceSquared(start) < 4.0) {
            Utils.sendActionBar(p, lang().get("spells.empyreanpassage.no_room"));
            cancelCast(p);
            return;
        }

        Passage passage = new Passage(p.getUniqueId(), world, start, end, wand);
        passage.riders.add(p.getUniqueId());
        for (Player ally : SpellTargeting.allies(p, start, ALLY_RADIUS, Math.min(ALLY_CAP, maxAllies))) {
            passage.riders.add(ally.getUniqueId());
            Object token = SpellGroups.carry(p, ally);
            if (token != null) passage.rideTokens.put(ally.getUniqueId(), token);
            Utils.sendActionBar(ally, lang().get("spells.empyreanpassage.ally_joined")
                    .replace("{caster}", p.getName()));
        }
        SESSIONS.put(p.getUniqueId(), passage);
        passage.task = Bukkit.getScheduler().runTaskTimer(Alkatraz.getInstance(),
                () -> tick(passage), 0L, 1L);
        Utils.sendActionBar(p, lang().get("spells.empyreanpassage.opened")
                .replace("{riders}", String.valueOf(passage.riders.size())));
    }

    private void tick(Passage passage) {
        Player owner = Bukkit.getPlayer(passage.owner);
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getWorld() != passage.world) {
            settle(passage.owner);
            return;
        }
        double progress = Math.min(1.0, (double) passage.elapsed / RIDE_TICKS);
        Location center = passageCenter(passage);

        for (UUID id : new ArrayList<>(passage.riders)) {
            Player rider = Bukkit.getPlayer(id);
            if (rider == null || !rider.isOnline() || rider.isDead() || rider.getWorld() != passage.world) {
                dropRider(passage, id);
                continue;
            }
            if (!id.equals(passage.owner)) {
                Object token = passage.rideTokens.get(id);
                if (token == null || !SpellGroups.isCarriedBy(rider, token)) {
                    dropRider(passage, id);
                    continue;
                }
                if (!TeleportSafety.canTeleportAlly(owner, rider, center)) {
                    dropRider(passage, rider.getUniqueId());
                    continue;
                }
            }
            rider.teleport(center);
            rider.setFallDistance(0.0f);
            rider.setVelocity(new Vector(0, 0, 0));
        }
        if (!passage.riders.contains(passage.owner)) return;

        damageNearMobs(passage, owner);
        if (damageBlocks(owner)) {
            TerrainEditor.destroySphere(owner, center, BLOCK_RADIUS, false, BLOCK_BUDGET);
        }

        SpellVfx.ground(PALETTE.core(), owner, center, 2.0, 3.0, 10, 3, owner.getLocation().getYaw(), progress);
        if (passage.elapsed % 4 == 0) {
            ParticleCaster.spawn(owner, passage.world, Utils.END_ROD,
                    center.clone().add(0, 1.0, 0), 3, 0.5, 0.2, 0.5, 0.02);
        }

        passage.elapsed++;
        if (passage.elapsed >= RIDE_TICKS) {
            for (UUID id : new ArrayList<>(passage.riders)) {
                Player rider = Bukkit.getPlayer(id);
                if (rider == null) continue;
                rider.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false));
                Utils.sendActionBar(rider, lang().get("spells.empyreanpassage.arrived"));
            }
            settle(passage.owner);
        }
    }

    private static Location passageCenter(Passage passage) {
        double progress = Math.min(1.0, (double) passage.elapsed / RIDE_TICKS);
        return passage.start.clone().add(
                (passage.end.getX() - passage.start.getX()) * progress,
                (passage.end.getY() - passage.start.getY()) * progress + Math.sin(progress * Math.PI) * 2.0,
                (passage.end.getZ() - passage.start.getZ()) * progress);
    }

    private void damageNearMobs(Passage passage, Player p) {
        Location center = passageCenter(passage);
        for (Entity entity : passage.world.getNearbyEntities(center, 4.0, 4.0, 4.0)) {
            if (!(entity instanceof LivingEntity living) || living.isDead()) continue;
            // The slipstream tears through mobs, never through players. Riders are
            // already excluded above; every other player is off-limits too.
            if (living instanceof Player) continue;
            if (passage.riders.contains(living.getUniqueId())) continue;
            SpellDamageUtil.damageWithSpell(living, DAMAGE, p, passage.wand, this);
        }
    }

    private boolean damageBlocks(Player p) {
        if (getOption("breaks_terrain") == null) return true;
        Object value = getOption("breaks_terrain").getSelectedValue(p).getValue();
        return !(value instanceof Boolean b) || b;
    }

    private void dropRider(Passage passage, UUID id) {
        passage.riders.remove(id);
        releaseRider(passage, id);
    }

    private static void releaseRider(Passage passage, UUID id) {
        Object token = passage.rideTokens.remove(id);
        Player rider = Bukkit.getPlayer(id);
        if (rider == null) return;
        if (token == null) SpellGroups.releaseMember(rider);
        else SpellGroups.releaseMember(rider, token);
    }

    /** Idempotent: stops the ride and releases every carried ally. */
    private static void settle(UUID ownerId) {
        Passage passage = SESSIONS.remove(ownerId);
        if (passage == null) return;
        if (passage.task != null) {
            passage.task.cancel();
            passage.task = null;
        }
        for (UUID id : new ArrayList<>(passage.riders)) {
            if (!id.equals(ownerId)) releaseRider(passage, id);
        }
        passage.riders.clear();
        passage.rideTokens.clear();
    }

    @EventHandler
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!isRider(damager.getUniqueId())) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        event.setCancelled(true);
        Utils.sendActionBar(damager, lang().get("spells.empyreanpassage.attack_blocked"));
    }

    @EventHandler
    public void onPrepare(PlayerSpellPrepareEvent event) {
        Player caster = event.getCaster();
        if (caster == null || !isRider(caster.getUniqueId())) return;
        if (event.getSpell() == null || event.getSpell().getRole() != SpellRole.BURST) return;
        event.setCancelled(true);
        Utils.sendActionBar(caster, lang().get("spells.empyreanpassage.spell_blocked"));
    }

    @EventHandler
    public void onCasterDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Passage passage = SESSIONS.get(player.getUniqueId());
        if (passage == null || event.isCancelled()) return;
        settle(player.getUniqueId());
        Utils.sendActionBar(player, lang().get("spells.empyreanpassage.interrupted"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        leave(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        leave(event.getPlayer().getUniqueId());
    }

    private void leave(UUID id) {
        if (SESSIONS.containsKey(id)) {
            settle(id);
            return;
        }
        for (Passage passage : SESSIONS.values()) {
            if (passage.riders.contains(id)) {
                dropRider(passage, id);
                return;
            }
        }
    }

    private static boolean isRider(UUID id) {
        for (Passage passage : SESSIONS.values()) {
            if (passage.riders.contains(id)) return true;
        }
        return false;
    }

    private static Vector horizontal(Player p) {
        Vector direction = p.getLocation().getDirection();
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-4) direction = new Vector(0, 0, 1);
        return direction.normalize();
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

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.3, 0), scale * 2.6, 34, frame * 4, 0.0);
        SpellVfx.orbit(palette.accent(), caster, origin.clone().add(0, 1.5, 0),
                scale * 1.0, scale * 2.6, 5, frame * 0.22);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        if (tick % 5 == 0) {
            SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.4 + progress * 1.5, 0),
                    1.0 + progress * 7.0, 32, tick * 5, 0.1);
        }
        if (tick % 8 == 0) {
            ParticleCaster.spawn(caster, origin.getWorld(), Utils.END_ROD,
                    origin.clone().add((Math.random() - 0.5) * 6, 1.0 + Math.random() * 3.0,
                            (Math.random() - 0.5) * 6),
                    2, 0.0, 0.15, 0.0, 0.02);
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
                .setDisplayName(lang().get("spells.empyreanpassage.book_name"))
                .addCustomLoreLine(lang().get("spells.empyreanpassage.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}



