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
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class WaterPulse extends AttackSpell implements Listener {
    public WaterPulse(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {

    }

    @Override
    public void onCountered(Location location) {

    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/water_pulse.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/water_pulse.yml").get();

        loadCommonConfig(spellConfig);
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.MAGIC);
        Location centre = p.getLocation();

        BukkitRunnable task = new BukkitRunnable() {
            double r = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()){
                    cancel();
                    return;
                }
                r += 0.5;
                List<Location> circle = ParticleUtils.circle(centre, r, 4/r, 0, 0);
                for (Location loc : circle){
                    loc.getWorld().spawnParticle(Particle.WATER_SPLASH, loc, 5, 0, 0, 0,0);
                    SpellParticleComponent comp = new SpellParticleComponent(
                            WaterPulse.this,
                            props,
                            p,
                            wand,
                            SpellComponentType.OFFENSE,
                            loc,
                            0.25,
                            1
                    );
                    SpellComponentHandler.register(comp);
                    for (Block b : Utils.blocksInRadius(loc, 2)){
                        if (b.getType() == Material.FIRE){
                            b.setType(Material.AIR);
                        }else if (b.getType() == Material.LAVA){
                            Levelled blockdata = (Levelled) b.getBlockData();
                            if (blockdata.getLevel() == 0){
                                b.setType(Material.OBSIDIAN);
                            }else{
                                b.setType(Material.COBBLESTONE);
                            }
                        }
                    }
                    for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
                        if (props.isCountered() || props.isCancelled()) {
                            cancel();
                            return;
                        }
                        if (!(entity instanceof LivingEntity)) continue;
                        if (entity.equals(p)) continue;

                        LivingEntity target = (LivingEntity) entity;
                        Vector direction = target.getLocation().toVector().subtract(loc.toVector());
                        direction.normalize().multiply(props.getRemainingPower());
                        direction.setY(0.5);
                        target.setVelocity(direction);
                    }
                }
                if (r == 10){
                    cancel();
                }
            }
        };

        task.runTaskTimer(Alkatraz.getInstance(), 0, 1);
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
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 3, 0);

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
