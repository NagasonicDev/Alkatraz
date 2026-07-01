package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SummonZombies extends AttackSpell implements Listener {

    private int zombieCount;
    private int zombieDuration;
    private double zombiePower;
    private double summonRange;
    private static final double FOLLOW_RANGE = 16.0;

    private final Map<UUID, List<UUID>> summonedZombies = new HashMap<>();

    public SummonZombies(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/summon_zombies_options.yml");
        Alkatraz.getInstance().save("spells/summon_zombies.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/summon_zombies.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.zombieCount = spellConfig.getInt("zombie_count");
        this.zombieDuration = spellConfig.getInt("zombie_duration");
        this.zombiePower = spellConfig.getDouble("zombie_power");
        this.summonRange = spellConfig.getDouble("summon_range");
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        int count = (int) getModifiedStat(caster, "zombie_count",
                ((Number) getOption("zombie_count").getSelectedValue(caster).getValue()).intValue());
        double power = getPower(caster, zombiePower);

        List<UUID> zombieIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location spawnLoc = findSpawnLocation(caster.getLocation());
            if (spawnLoc == null) continue;

            Zombie zombie = (Zombie) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            zombie.setAdult();
            zombie.setCustomName(ChatColor.DARK_RED + caster.getName() + "'s Zombie");
            zombie.setCustomNameVisible(false);
            zombie.setRemoveWhenFarAway(false);
            Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20.0);
            zombie.setHealth(20.0);
            zombie.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(power);

            NBT.modifyPersistentData(zombie, nbt -> {
                nbt.setString("summoner_uuid", caster.getUniqueId().toString());
                nbt.setBoolean("summoned_zombie", true);
            });

            zombieIds.add(zombie.getUniqueId());

            spawnSummonParticles(spawnLoc);
        }

        summonedZombies.put(caster.getUniqueId(), zombieIds);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks >= zombieDuration * 20) {
                    for (UUID id : zombieIds) {
                        Entity e = Bukkit.getEntity(id);
                        if (e != null) despawnZombie(e);
                    }
                    summonedZombies.remove(caster.getUniqueId());
                    cancel();
                    return;
                }

                if (ticks % 10 == 0) {
                    for (UUID id : zombieIds) {
                        Entity e = Bukkit.getEntity(id);
                        if (!(e instanceof Zombie zombie) || zombie.isDead()) continue;
                        LivingEntity target = zombie.getTarget();
                        if (target == null || !target.isValid() || target.isDead()) {
                            if (zombie.getLocation().distanceSquared(caster.getLocation()) > 16) {
                                zombie.setTarget(caster);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);

        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.8f, 0.6f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        List<UUID> zombieIds = new ArrayList<>();

        for (int i = 0; i < zombieCount; i++) {
            Location spawnLoc = findSpawnLocation(caster.getLocation());
            if (spawnLoc == null) continue;

            Zombie zombie = (Zombie) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            zombie.setAdult();
            zombie.setCustomNameVisible(false);
            zombie.setRemoveWhenFarAway(false);
            Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20.0);
            zombie.setHealth(20.0);
            zombie.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(zombiePower);

            NBT.modifyPersistentData(zombie, nbt -> {
                nbt.setString("summoner_uuid", caster.getUniqueId().toString());
                nbt.setBoolean("summoned_zombie", true);
            });

            zombieIds.add(zombie.getUniqueId());
            spawnSummonParticles(spawnLoc);
        }

        summonedZombies.put(caster.getUniqueId(), zombieIds);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks >= zombieDuration * 20) {
                    for (UUID id : zombieIds) {
                        Entity e = Bukkit.getEntity(id);
                        if (e != null) despawnZombie(e);
                    }
                    summonedZombies.remove(caster.getUniqueId());
                    cancel();
                    return;
                }

                if (ticks % 10 == 0) {
                    for (UUID id : zombieIds) {
                        Entity e = Bukkit.getEntity(id);
                        if (!(e instanceof Zombie zombie) || zombie.isDead()) continue;
                        LivingEntity target = zombie.getTarget();
                        if (target == null || !target.isValid() || target.isDead()) {
                            if (zombie.getLocation().distanceSquared(caster.getLocation()) > 16) {
                                zombie.setTarget(caster);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private Location findSpawnLocation(Location center) {
        for (int i = 0; i < 10; i++) {
            double x = center.getX() + (Math.random() - 0.5) * summonRange * 2;
            double z = center.getZ() + (Math.random() - 0.5) * summonRange * 2;
            Location loc = new Location(center.getWorld(), x, center.getY(), z);
            loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
            if (loc.getBlock().getType() == Material.AIR && loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                return loc;
            }
        }
        return null;
    }

    private void spawnSummonParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.SPELL_WITCH, loc, 20, 0.5, 0.5, 0.5, 0);
        loc.getWorld().spawnParticle(Particle.SMOKE_LARGE, loc, 10, 0.3, 0.3, 0.3, 0.05);
    }

    private void despawnZombie(Entity e) {
        if (!e.isDead()) {
            e.getWorld().spawnParticle(Particle.SMOKE_LARGE, e.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
            e.getWorld().spawnParticle(Particle.SPELL_WITCH, e.getLocation(), 10, 0.3, 0.3, 0.3, 0);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ZOMBIE_DEATH, 0.6f, 0.8f);
            e.remove();
        }
    }

    @EventHandler
    private void onZombieDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Zombie zombie)) return;
        String summonerId = NBT.getPersistentData(zombie, nbt -> nbt.getString("summoner_uuid"));
        if (summonerId == null) return;
        if (!NBT.getPersistentData(zombie, nbt -> nbt.getBoolean("summoned_zombie"))) return;

        if (e.getEntity() instanceof Player p) {
            UUID summonerUUID = UUID.fromString(summonerId);
            if (p.getUniqueId().equals(summonerUUID)) {
                e.setCancelled(true);
            }
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
            if (lt instanceof Zombie zombie) {
                String summonerId = NBT.getPersistentData(zombie, nbt -> nbt.getString("summoner_uuid"));
                if (summonerId != null && summonerId.equals(p.getUniqueId().toString())) return;
            }
            player = p;
            target = lt;
        }

        if (player == null || target == null) return;

        List<UUID> zombieIds = summonedZombies.get(player.getUniqueId());
        if (zombieIds == null || zombieIds.isEmpty()) return;

        for (UUID id : zombieIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Zombie zombie && !zombie.isDead()) {
                zombie.setTarget(target);
            }
        }
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
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                            new Particle.DustOptions(Color.fromRGB(50, 100, 50), 0.4F));
                }
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
                .setDisplayName("&2Necronomicon Ex Mortis")
                .addCustomLoreLine("&8&oThe dead shall serve the living.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }
}
