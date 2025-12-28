package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class FireWall extends AttackSpell implements Listener {
    public FireWall(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {

    }

    @Override
    public void onCountered(Location location) {

    }

    private double duration;
    private double length;
    private double height;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/fire_wall.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fire_wall.yml").get();

        loadCommonConfig(spellConfig);
        duration = spellConfig.getDouble("duration");
        length = spellConfig.getDouble("length");
        height = spellConfig.getDouble("height");
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void castAction(Player player, ItemStack wand) {
        AttackProperties props = new AttackProperties(player, Utils.castLocation(player), getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.MAGIC);
        Location start = player.getLocation();

        double spacing = 0.5;
        int durationTicks = (int) Math.ceil(duration * 20); // wall lifetime

        List<WallSegment> wallPoints = new ArrayList<>();

        Location currentPos = start.clone();
        Vector currentDir = player.getEyeLocation().getDirection().normalize();

        float[] lastYaw = { -player.getEyeLocation().getYaw() };
        double turnStrength = 0.015;

        BukkitRunnable wallTask = new BukkitRunnable() {

            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                // === BUILD PHASE ===
                if (ticks < length / spacing && !props.isCountered() && !props.isCancelled()) {

                    float currentYaw = -player.getEyeLocation().getYaw();
                    float yawDelta = currentYaw - lastYaw[0];
                    lastYaw[0] = currentYaw;

                    yawDelta = Math.max(-10, Math.min(10, yawDelta));

                    // Steer ONLY next segment
                    currentDir.rotateAroundY(yawDelta * turnStrength);

                    // Step forward
                    currentPos.add(currentDir.clone().multiply(spacing));

                    // Lock segment with age
                    wallPoints.add(new WallSegment(currentPos.clone()));
                }

                // === RENDER + DAMAGE PHASE ===
                for (WallSegment segment : wallPoints) {
                    segment.age++;

                    double riseProgress = Math.min(1.0, segment.age / 30.0);
                    double currentHeight = height * riseProgress;

                    for (double h = 0; h < currentHeight; h += 0.5) {

                        Location loc = segment.base.clone().add(0, h, 0);

                        if (h == currentHeight - 0.5){
                            player.getWorld().spawnParticle(
                                    Particle.LAVA,
                                    loc,
                                    2,
                                    0.05, 0.05, 0.05,
                                    0
                            );
                        }else{
                            player.getWorld().spawnParticle(
                                    Particle.FLAME,
                                    loc,
                                    2,
                                    0.05, 0.05, 0.05,
                                    0
                            );
                        }
                        SpellParticleComponent comp = new SpellParticleComponent(
                                FireWall.this,
                                props,
                                player,
                                wand,
                                SpellComponentType.OFFENSE,
                                loc,
                                0.25,
                                2
                        );
                        SpellComponentHandler.register(comp);

                        if (loc.getBlock().getType() == Material.AIR) {
                            loc.getBlock().setType(Material.FIRE);
                        }

                        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 0.6, 0.6, 0.6)) {
                            if (!(entity instanceof LivingEntity)) continue;
                            if (entity.equals(player)) continue;

                            LivingEntity target = (LivingEntity) entity;
                            target.damage(props.getRemainingPower());
                            target.setFireTicks(40);
                        }
                    }
                }

                // === LIFETIME END ===
                if (ticks >= durationTicks && ticks >= length / spacing) {
                    cancel();
                }
            }
        };

        wallTask.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    @Override
    public int circleAction(Player p, PlayerSpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = p.getEyeLocation(); // Player eye location
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();

            // Calculate offset vector pointing forward relative to player orientation
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5); // 1.5 blocks in front

            // Call magicCircle with proper center, yaw, pitch and offset
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 4, 0);

            // Spawn particles at all calculated points
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.RED, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    class WallSegment {
        Location base;
        int age;

        WallSegment(Location base) {
            this.base = base;
            this.age = 0;
        }
    }
}
