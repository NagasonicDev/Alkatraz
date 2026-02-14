package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
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
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;


public class WaterSphere extends AttackSpell {

    public WaterSphere(String type){
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {
        location.getWorld().spawnParticle(Particle.WATER_SPLASH, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.WATER_SPLASH, location, 30);
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/water_sphere.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/water_sphere.yml").get();

        loadCommonConfig(spellConfig);
    }
    
    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.MAGIC);
            List<Location> lineLocs = ParticleUtils.line(
                    2,
                    p.getEyeLocation(),
                    p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(40))
            );

            new BukkitRunnable() {

                int index = 0;

                @Override
                public void run() {
                    if (props.isCancelled() || props.isCountered()){
                        cancel();
                        return;
                    }
                    if (index >= lineLocs.size()) {
                        cancel();
                        return;
                    }

                    Location a = lineLocs.get(index);

                    List<Location> locs = ParticleUtils.sphere(a, 0.75, 24);
                    for (Location loc : locs) {
                        loc.getWorld().spawnParticle(Particle.WATER_DROP, loc, 2, 0, 0, 0, 0);
                        SpellParticleComponent comp = new SpellParticleComponent(
                                WaterSphere.this,
                                props,
                                p,
                                wand,
                                SpellComponentType.OFFENSE,
                                loc,
                                0.25,
                                1
                        );
                        SpellComponentHandler.register(comp);
                        if (props.isCancelled() || props.isCountered()){
                            cancel();
                            return;
                        }
                        Block b = loc.getBlock();
                        if (b.getType() == Material.FARMLAND) {
                            if (!(b.getBlockData() instanceof Farmland farm)) return;
                            farm.setMoisture(farm.getMaximumMoisture());
                            b.setBlockData(farm);
                        }
                    }

                    for (Entity entity : a.getNearbyEntities(1, 1, 1)) {
                        if (props.isCancelled() || props.isCountered()){
                            cancel();
                            return;
                        }
                        if (entity.isDead() || entity.equals(p)) continue;
                        if (!(entity instanceof LivingEntity le)) continue;

                        double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
                        le.damage(props.getRemainingPower());

                        Vector unitVector = entity.getLocation()
                                .toVector()
                                .subtract(p.getLocation().toVector())
                                .normalize();

                        entity.setVelocity(unitVector.multiply(0.1));
                    }

                    index++;
                }

            }.runTaskTimer(Alkatraz.getInstance(), 0L, 2L);
        }
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
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

            // Spawn particles at all calculated points
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.BLUE, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }
}
