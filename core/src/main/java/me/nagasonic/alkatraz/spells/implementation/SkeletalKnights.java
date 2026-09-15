package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 6th circle summoning spell. Channels a Warp-style wind-up (floor circle and
 * hex glyphs) for {@code wind_up_duration} seconds; moving or taking damage
 * interrupts the summon. On completion, 3 iron-armored skeleton knights
 * mounted on skeleton horses are spawned around the caster. Each knight fights
 * whatever the caster attacks, cannot harm its summoner, and despawns after
 * {@code knight_duration} seconds.
 */
public class SkeletalKnights extends AttackSpell implements Listener {

    private static final Color LIGHT_BLUE = Color.fromRGB(150, 210, 255);
    private static final Color AQUA = Color.fromRGB(0, 210, 255);
    private static final Color DEEP_BLUE = Color.fromRGB(45, 110, 230);
    private static final double FOLLOW_RANGE = 16.0;

    private static final Map<UUID, ChannelData> channels = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> summonedKnights = new HashMap<>();

    private record ChannelData(Player caster, BukkitRunnable runnable) {}

    private SkeletalKnightsConfig config;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public SkeletalKnights(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/skeletal_knights.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/skeletal_knights.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = SkeletalKnightsConfig.fromConfig(spellConfig.getValues(false));
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) return;
        UUID uuid = caster.getUniqueId();
        if (channels.containsKey(uuid)) return;

        int windUpTicks = config.windUpTicks(profile.getSpellMastery(this), getMaxMastery());

        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpTicks) {
                    cancel();
                    completeSummon(caster);
                    return;
                }
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    abortChannel(uuid, false);
                    return;
                }
                animate(caster, ticks, windUpTicks);
                ticks++;
            }
        };

        channels.put(uuid, new ChannelData(caster, runnable));
        runnable.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Player-only spell.
    }

    private void completeSummon(Player caster) {
        UUID uuid = caster.getUniqueId();
        channels.remove(uuid);
        if (caster.isDead() || !caster.isOnline()) return;

        int count = config.knightCount();
        double power = getPower(caster, config.knightPower());
        List<UUID> ids = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Location spawnLoc = findSpawnLocation(caster.getLocation());
            if (spawnLoc == null) continue;
            if (!Protection.spawnMob(caster, spawnLoc)) continue;

            SkeletonHorse horse = (SkeletonHorse) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.SKELETON_HORSE);
            horse.setTamed(true);
            horse.setOwner(caster);
            horse.setAdult();
            horse.setRemoveWhenFarAway(false);
            horse.setCustomNameVisible(false);

            Skeleton knight = (Skeleton) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.SKELETON);
            knight.setCustomName(ChatColor.DARK_RED + caster.getName() + "'s Knight");
            knight.setCustomNameVisible(false);
            knight.setRemoveWhenFarAway(false);
            Objects.requireNonNull(knight.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20.0);
            knight.setHealth(20.0);
            Objects.requireNonNull(knight.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).setBaseValue(power);

            ItemStack sword = new ItemStack(Material.IRON_SWORD);
            ItemMeta swordMeta = sword.getItemMeta();
            if (swordMeta != null) {
                swordMeta.setUnbreakable(true);
                sword.setItemMeta(swordMeta);
            }
            knight.getEquipment().setItemInMainHand(sword);
            knight.getEquipment().setItemInOffHand(null);
            knight.getEquipment().setHelmet(armor(Material.IRON_HELMET));
            knight.getEquipment().setChestplate(armor(Material.IRON_CHESTPLATE));
            knight.getEquipment().setLeggings(armor(Material.IRON_LEGGINGS));
            knight.getEquipment().setBoots(armor(Material.IRON_BOOTS));

            NBT.modifyPersistentData(horse, nbt -> {
                nbt.setString("summoner_uuid", caster.getUniqueId().toString());
                nbt.setBoolean("summoned_mount", true);
            });
            NBT.modifyPersistentData(knight, nbt -> {
                nbt.setString("summoner_uuid", caster.getUniqueId().toString());
                nbt.setBoolean("summoned_knight", true);
            });

            horse.addPassenger(knight);
            ids.add(knight.getUniqueId());
            ids.add(horse.getUniqueId());
            spawnSummonBurst(caster, spawnLoc.add(0, 1, 0));
        }

        if (ids.isEmpty()) {
            cancelCast(caster);
            return;
        }

        summonedKnights.put(caster.getUniqueId(), ids);
        startDespawnRunnable(caster, ids);
        spawnArrivalExpand(caster, caster.getLocation(), caster.getEyeLocation().getYaw());
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_SKELETON_HORSE_AMBIENT, 1.0f, 0.8f);
    }

    private static ItemStack armor(Material material) {
        ItemStack piece = new ItemStack(material);
        ItemMeta meta = piece.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            piece.setItemMeta(meta);
        }
        return piece;
    }

    private void startDespawnRunnable(Player caster, List<UUID> ids) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks >= config.knightDuration() * 20) {
                    for (UUID id : ids) {
                        Entity e = Bukkit.getEntity(id);
                        if (e != null) despawnKnight(caster, e);
                    }
                    summonedKnights.remove(caster.getUniqueId());
                    cancel();
                    return;
                }
                if (ticks % 10 == 0) {
                    for (UUID id : ids) {
                        Entity e = Bukkit.getEntity(id);
                        if (!(e instanceof Skeleton knight) || knight.isDead()) continue;
                        LivingEntity target = knight.getTarget();
                        if (target == null || !target.isValid() || target.isDead()) {
                            if (knight.getLocation().distanceSquared(caster.getLocation()) > FOLLOW_RANGE * FOLLOW_RANGE) {
                                knight.setTarget(caster);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private Location findSpawnLocation(Location center) {
        double range = config.summonRange();
        for (int i = 0; i < 10; i++) {
            double x = center.getX() + (Math.random() - 0.5) * range * 2;
            double z = center.getZ() + (Math.random() - 0.5) * range * 2;
            Location loc = new Location(center.getWorld(), x, center.getY(), z);
            loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
            if (loc.getBlock().getType() == Material.AIR && loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                return loc;
            }
        }
        return null;
    }

    private void spawnSummonBurst(LivingEntity caster, Location loc) {
        ParticleCaster.spawn(caster, loc.getWorld(), Particle.PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.05);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 20, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(AQUA, 0.8F));
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.LARGE_SMOKE, loc, 10, 0.3, 0.3, 0.3, 0.05);
    }

    private void despawnKnight(LivingEntity caster, Entity e) {
        if (e.isDead()) {
            e.remove();
            return;
        }
        ParticleCaster.spawn(caster, e.getWorld(), Utils.LARGE_SMOKE, e.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
        ParticleCaster.spawn(caster, e.getWorld(), Utils.WITCH, e.getLocation(), 10, 0.3, 0.3, 0.3, 0);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_SKELETON_DEATH, 0.6f, 0.8f);
        e.remove();
    }

    // ================= Channel animation (ported from Warp) =================

    private void animate(Player caster, int ticks, int windUpTicks) {
        int actSwitch = Math.max(1, windUpTicks / 2);
        int remaining = windUpTicks - ticks;
        double shrink = remaining <= 10 ? Math.max(0.15, remaining / 10.0) : 1.0;
        double fullProgress = (double) ticks / windUpTicks;

        Location feet = caster.getLocation();
        double floorR = Math.min(2.2, 0.8 + 1.4 * (ticks / (double) actSwitch)) * shrink;
        spawnFloorCircle(caster, feet, floorR, ticks);
        spawnHexGlyphs(caster, feet, floorR, ticks);

        if (ticks >= actSwitch) {
            Location eye = caster.getEyeLocation();
            Location center = eye.clone().add(eye.getDirection().multiply(1.8));
            float yaw = eye.getYaw();
            double p2 = (double) (ticks - actSwitch) / (windUpTicks - actSwitch);
            double hw = (1.2 + 1.2 * p2) * shrink;
            double halfH = hw * 1.1;
            List<Location> outer = ParticleUtils.ellipse(center, yaw, hw * 2, halfH * 2, 28);
            List<Location> inner = ParticleUtils.ellipse(center, yaw, hw * 1.2, halfH * 1.2, 20);

            for (int i = 0; i < outer.size(); i++) {
                ParticleCaster.spawn(caster, center.getWorld(), Utils.DUST,
                        outer.get((i + ticks) % outer.size()), 0,
                        new Particle.DustOptions(AQUA, 0.6F));
            }
            for (int i = 0; i < inner.size(); i++) {
                ParticleCaster.spawn(caster, center.getWorld(), Utils.DUST,
                        inner.get(Math.floorMod(i - ticks * 2, inner.size())), 0,
                        new Particle.DustOptions(LIGHT_BLUE, 0.6F));
            }
            for (int s = 0; s < 6; s++) {
                Location tip = outer.get((s * outer.size()) / 6);
                double frac = 1.0 - ((ticks + s) % 10) / 10.0;
                Vector dir = tip.toVector().subtract(center.toVector()).multiply(frac);
                ParticleCaster.spawn(caster, center.getWorld(), Utils.DUST,
                        center.clone().add(dir), 0,
                        new Particle.DustOptions(DEEP_BLUE, 0.6F));
            }
        }

        int every = Math.max(1, ((Long) Configs.CIRCLE_TICKS.get()).intValue());
        if (ticks % every == 0) {
            float pitch = 0.6f + (float) fullProgress * 0.6f;
            caster.getWorld().playSound(caster.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, pitch);
        }
    }

    private void spawnFloorCircle(Player caster, Location feet, double radius, int ticks) {
        float spin = ticks * 6;
        List<Location> outer = ParticleUtils.circle(feet, radius, 15, spin, 0);
        List<Location> inner = ParticleUtils.circle(feet, radius * 0.7, 15, -spin, 0);
        for (Location loc : outer) {
            ParticleCaster.spawn(caster, feet.getWorld(), Utils.DUST, loc, 0,
                    new Particle.DustOptions(LIGHT_BLUE, 0.5F));
        }
        for (Location loc : inner) {
            ParticleCaster.spawn(caster, feet.getWorld(), Utils.DUST, loc, 0,
                    new Particle.DustOptions(AQUA, 0.5F));
        }
    }

    private void spawnHexGlyphs(Player caster, Location feet, double radius, int ticks) {
        double glyphR = radius * 0.85;
        double baseAngle = Math.toRadians(ticks * 3);
        for (int k = 0; k < 6; k++) {
            double a = baseAngle + k * Math.PI / 3.0;
            Location loc = feet.clone().add(Math.cos(a) * glyphR, 0, Math.sin(a) * glyphR);
            ParticleCaster.spawn(caster, feet.getWorld(), Utils.DUST, loc, 0,
                    new Particle.DustOptions(DEEP_BLUE, 0.6F));
        }
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
                    ParticleCaster.spawn(caster, world, Utils.DUST, loc, 0,
                            new Particle.DustOptions(AQUA, 0.8F));
                }
                expand++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void playFailSound(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SKELETON_HORSE_DEATH, 0.5f, 0.5f);
    }

    private void abortChannel(UUID uuid, boolean announce) {
        ChannelData data = channels.remove(uuid);
        if (data == null) return;
        data.runnable().cancel();
        if (announce) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.isDead()) {
                Utils.sendActionBar(player, lang().get("spells.skeletal_knights.interrupted"));
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
        if (!from.getWorld().equals(to.getWorld()) || from.distance(to) > config.moveCancelThreshold()) {
            abortChannel(uuid, true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player victim && channels.containsKey(victim.getUniqueId())) {
            abortChannel(victim.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        abortChannel(e.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    private void onKnightDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Skeleton skeleton)) return;
        if (!NBT.getPersistentData(skeleton, nbt -> nbt.getBoolean("summoned_knight"))) return;
        String summonerId = NBT.getPersistentData(skeleton, nbt -> nbt.getString("summoner_uuid"));
        if (summonerId == null) return;
        if (e.getEntity() instanceof Player p && p.getUniqueId().equals(UUID.fromString(summonerId))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    private void onPlayerCombat(EntityDamageByEntityEvent e) {
        Player player = null;
        LivingEntity target = null;

        if (e.getDamager() instanceof Player p && e.getEntity() instanceof LivingEntity lt) {
            player = p;
            target = lt;
        } else if (e.getEntity() instanceof Player p && e.getDamager() instanceof LivingEntity lt) {
            if (lt instanceof Skeleton skeleton) {
                String summonerId = NBT.getPersistentData(skeleton, nbt -> nbt.getString("summoner_uuid"));
                if (summonerId != null && summonerId.equals(p.getUniqueId().toString())) return;
            }
            player = p;
            target = lt;
        }

        if (player == null || target == null) return;
        List<UUID> ids = summonedKnights.get(player.getUniqueId());
        if (ids == null || ids.isEmpty()) return;

        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Skeleton knight && !knight.isDead()) {
                knight.setTarget(target);
            }
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location eye = caster.getEyeLocation();
            Location center = eye.clone().add(eye.getDirection().multiply(1.8));
            List<Location> points = ParticleUtils.ellipse(center, eye.getYaw(), 1.6, 2.0, 28);
            for (int i = 0; i < points.size(); i++) {
                ParticleCaster.spawn(caster, points.get(i).getWorld(), Utils.DUST, points.get(i), 0,
                        new Particle.DustOptions(i % 2 == 0 ? LIGHT_BLUE : AQUA, 0.4F));
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {}

    @Override
    public void onCountered(Location location) {}

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.skeletal_knights.book_name"))
                .addCustomLoreLine(lang().get("spells.skeletal_knights.lore1"))
                .addCustomLoreLine(lang().get("spells.skeletal_knights.lore2"))
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
