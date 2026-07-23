package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Radiance extends Spell {
    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double healAmount;
    private double radius;
    private double duration;

    private static final Set<EntityType> UNDEAD_TYPES = new HashSet<>();

    static {
        UNDEAD_TYPES.add(EntityType.ZOMBIE);
        UNDEAD_TYPES.add(EntityType.ZOMBIE_VILLAGER);
        UNDEAD_TYPES.add(EntityType.HUSK);
        UNDEAD_TYPES.add(EntityType.DROWNED);
        UNDEAD_TYPES.add(EntityType.SKELETON);
        UNDEAD_TYPES.add(EntityType.WITHER_SKELETON);
        UNDEAD_TYPES.add(EntityType.STRAY);
        UNDEAD_TYPES.add(EntityType.WITHER);
        UNDEAD_TYPES.add(EntityType.PHANTOM);
        UNDEAD_TYPES.add(EntityType.ZOGLIN);
        UNDEAD_TYPES.add(EntityType.ZOMBIFIED_PIGLIN);
        UNDEAD_TYPES.add(EntityType.SKELETON_HORSE);
        UNDEAD_TYPES.add(EntityType.ZOMBIE_HORSE);
    }

    public Radiance(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/radiance_options.yml");
        Alkatraz.getInstance().save("spells/radiance.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/radiance.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.healAmount = spellConfig.getDouble("heal_amount");
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration", 5.0);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double activeRadius = getModifiedStat(caster, "radius", radius);
        double wandPower = getWandPower(wand);
        double healPerTick = getModifiedStat(caster, "heal", healAmount * wandPower) / duration;
        int totalTicks = (int) (duration * 20);

        World world = caster.getWorld();
        Location center = caster.getLocation().clone();

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        new BukkitRunnable() {
            int tick = 0;
            double ringAngle = 0;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    center.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
                    cancel();
                    return;
                }

                ringAngle += 0.15;

                for (int ring = 0; ring < 3; ring++) {
                    double r = activeRadius * (ring + 1) / 3.0;
                    double angleOffset = ringAngle + ring * 2.0;
                    int points = 12 + ring * 4;

                    for (int i = 0; i < points; i++) {
                        double a = angleOffset + (2 * Math.PI * i / points);
                        double x = Math.cos(a) * r;
                        double z = Math.sin(a) * r;
                        Location ringLoc = center.clone().add(x, 0.5 + Math.sin(tick * 0.1 + ring) * 0.3, z);

                        ringLoc.getWorld().spawnParticle(Particle.GLOW, ringLoc, 1, 0, 0, 0, 0);
                        ringLoc.getWorld().spawnParticle(Utils.DUST, ringLoc, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 255, 200), 0.5F + ring * 0.2F));
                    }
                }

                for (int i = 0; i < 3; i++) {
                    double a = tick * 0.3 + i * 2.0;
                    double r = activeRadius * (0.3 + (Math.sin(tick * 0.05) * 0.5 + 0.5) * 0.7);
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    Location orbLoc = center.clone().add(x, 1.0 + Math.sin(tick * 0.2 + i) * 1.5, z);
                    orbLoc.getWorld().spawnParticle(Particle.END_ROD, orbLoc, 2, 0.1, 0.1, 0.1, 0.02);
                }

                for (int i = 0; i < 5; i++) {
                    double a = Math.random() * 2 * Math.PI;
                    double r = Math.random() * activeRadius;
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    Location radiantLoc = center.clone().add(x, Math.random() * 3 + 0.5, z);
                    radiantLoc.getWorld().spawnParticle(Particle.GLOW, radiantLoc, 1, 0.05, 0.05, 0.05, 0);
                }

                for (Entity entity : world.getNearbyEntities(center, activeRadius, activeRadius, activeRadius)) {
                    if (entity.equals(caster)) {
                        if (tick % 10 == 0) {
                            double maxHealth = caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            caster.setHealth(Math.min(maxHealth, caster.getHealth() + healPerTick));

                            Location eyes = caster.getEyeLocation();
                            eyes.getWorld().spawnParticle(Particle.HEART, eyes.clone().add(0, 0.5, 0), 2, 0.3, 0.3, 0.3, 0);
                        }
                        continue;
                    }

                    if (!(entity instanceof LivingEntity le)) continue;

                    if (isUndead(le)) {
                        if (tick % 10 == 0) {
                            SpellDamageUtil.damageWithSpell(
                                    le, calcPower(healPerTick, le, caster),
                                    caster, wand, Radiance.this
                            );
                            le.getWorld().spawnParticle(Particle.FLAME, le.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
                        }
                    } else if (entity instanceof Player targetPlayer) {
                        if (tick % 10 == 0) {
                            double maxHealth = targetPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            targetPlayer.setHealth(Math.min(maxHealth, targetPlayer.getHealth() + healPerTick));
                            targetPlayer.getWorld().spawnParticle(Particle.HEART, targetPlayer.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
                        }
                    }
                }

                for (int i = 0; i < 2; i++) {
                    float yaw = (float) (Math.random() * 360);
                    float pitch = (float) (Math.random() * 360);
                    Vector forward = new Vector(0, 0, 1);
                    List<Location> circlePoints = ParticleUtils.magicCircle(
                            center.clone().add(0, 0.5 + Math.random() * 2, 0),
                            yaw, pitch, forward, 1.0 + Math.random() * 0.5, 0);
                    for (Location loc : circlePoints) {
                        if (Math.random() < 0.3) {
                            loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                                    new Particle.DustOptions(Color.YELLOW, 0.3F));
                        }
                    }
                }

                tick++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private boolean isUndead(LivingEntity entity) {
        return UNDEAD_TYPES.contains(entity.getType());
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        double totalHeal = healAmount * getWandPowerOrDefault(wand);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= (int)(duration * 20)) {
                    cancel();
                    return;
                }

                if (ticks % 10 == 0) {
                    double healPerTick = totalHeal / duration;
                    double maxHealth = caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    caster.setHealth(Math.min(maxHealth, caster.getHealth() + healPerTick));
                    caster.getWorld().spawnParticle(Particle.GLOW, caster.getLocation(), 10, 2, 1, 2, 0);
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
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
                            new Particle.DustOptions(Color.fromRGB(255, 255, 150), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.radiance.book_name"))
                .addCustomLoreLine(lang().get("spells.radiance.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
