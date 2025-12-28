package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.properties.implementation.BarrierProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class Barrier extends BarrierSpell implements Listener {

    public Barrier(String type) {
        super(type);
    }

    private double radius;
    private double duration;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/barrier.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/barrier.yml").get();
        radius = spellConfig.getDouble("radius");
        duration = spellConfig.getDouble("duration");
        loadCommonConfig(spellConfig);
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        Location center = p.getLocation().clone().add(0, 1, 0);
        BarrierProperties properties = new BarrierProperties(p, Utils.castLocation(p), getMaxHitpoints(), BarrierType.COMBINED);
        properties.getHealthBar().addPlayer(p);

        BukkitRunnable task = new BukkitRunnable() {

            int ticksPassed = 0;

            @Override
            public void run() {
                if (properties.isBroken() || ticksPassed >= duration * 5) {
                    onBarrierBreak();
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                List<Location> particleLocations = ParticleUtils.sphere(center, radius, 200);

                for (Location loc : particleLocations) {

                    SpellParticleComponent particle =
                            new SpellParticleComponent(
                                    Barrier.this,
                                    properties,
                                    p,
                                    wand,
                                    SpellComponentType.DEFENSE,
                                    loc,
                                    0.6,
                                    4
                            );
                    SpellComponentHandler.register(particle);

                    // Spawn visual particle
                    loc.getWorld().spawnParticle(Particle.SPELL_WITCH, loc, 1, 0, 0, 0, 0);
                }

                ticksPassed++;
            }
        };

        task.runTaskTimer(Alkatraz.getInstance(), 0, 4);
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

    @Override
    public void onHit(double damage, AttackSpell source) {

    }

    @Override
    public void onBarrierBreak() {

    }
}
