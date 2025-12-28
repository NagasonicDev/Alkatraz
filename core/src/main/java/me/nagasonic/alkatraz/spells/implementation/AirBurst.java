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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class AirBurst extends AttackSpell {
    public AirBurst(String type){
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
        Alkatraz.getInstance().save("spells/air_burst.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/air_burst.yml").get();

        loadCommonConfig(spellConfig);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        final AttackProperties properties = new AttackProperties(caster, Utils.castLocation(caster), getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.MAGIC);

        final List<Location> lineLocs = ParticleUtils.line(
                2,
                caster.getEyeLocation(),
                caster.getEyeLocation().add(
                        caster.getEyeLocation().getDirection().multiply(20)
                )
        );

        final float yaw = caster.getEyeLocation().getYaw();
        final float pitch = -caster.getEyeLocation().getPitch() + 90;
        final int totalPoints = lineLocs.size();

        new BukkitRunnable() {

            private int index = 0;

            @Override
            public void run() {


                if (caster.isDead() || properties.isCountered()) {
                    cancel();
                    return;
                }

                if (index >= totalPoints) {
                    cancel();
                    return;
                }

                Location point = lineLocs.get(index++);

                SpellParticleComponent component = new SpellParticleComponent(
                        AirBurst.this,
                        properties,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        point,
                        1.75,                    // collision radius
                        40                       // lifespan (ticks)
                );

                for (Location loc : ParticleUtils.circle(point, 1.5, 16, yaw, pitch)) {
                    if (properties.isCountered()) {
                        return;
                    }
                    loc.getWorld().spawnParticle(
                            Particle.CLOUD,
                            loc,
                            2,
                            0, 0, 0,
                            0.2
                    );
                }
                for (LivingEntity le : point.getNearbyLivingEntities(1)){
                    le.setVelocity(caster.getEyeLocation().getDirection().multiply(getPower(caster, le, properties.getRemainingPower())));
                }

                SpellComponentHandler.register(component);
            }

        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
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
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.WHITE, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }
}
