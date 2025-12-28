package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.DataManager;
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
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;


public class MagicMissile extends AttackSpell {
    public MagicMissile(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {
        location.getWorld().spawnParticle(Utils.DUST, location, 15, new Particle.DustOptions(Color.AQUA, 0.6F));
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Utils.DUST, location, 30, new Particle.DustOptions(Color.AQUA, 0.6F));
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/magic_missile.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/magic_missile.yml").get();

        loadCommonConfig(spellConfig);
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.MAGIC);
            Location loc1 = p.getEyeLocation();
            Vector direction = p.getEyeLocation().getDirection();
            Location loc2 = p.getEyeLocation().add(direction.multiply(20));
            List<Location> locs = ParticleUtils.line(0.5, loc1, loc2);
            locs.remove(0); //Function puts loc2 as the first index, so if it is a solid block, the missile will not fire.
            locs.add(loc2);
            BukkitRunnable task = new BukkitRunnable() {

                int index = 0;

                @Override
                public void run() {

                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }

                    if (index >= locs.size()) {
                        cancel();
                        return;
                    }

                    Location loc = locs.get(index++);
                    Block b = loc.getBlock();

                    if (!b.isPassable() && !b.isLiquid() && b.isCollidable() && b.isSolid()) {
                        cancel();
                        return;
                    }

                    p.spawnParticle(
                            Particle.REDSTONE,
                            loc,
                            50,
                            new Particle.DustOptions(Color.AQUA, 0.5F)
                    );
                    SpellParticleComponent comp = new SpellParticleComponent(
                            MagicMissile.this,
                            props,
                            p,
                            wand,
                            SpellComponentType.OFFENSE,
                            loc,
                            0.25,
                            1
                    );
                    SpellComponentHandler.register(comp);

                    for (Entity entity : loc.getNearbyEntities(1, 1, 1)) {
                        if (entity.isDead() || entity.equals(p)) break;
                        if (!(entity instanceof LivingEntity le)) break;
                        if (props.isCancelled() || props.isCountered()) break;
                        le.damage(props.getRemainingPower());

                        Vector unitVector = entity.getLocation()
                                .toVector()
                                .subtract(p.getLocation().toVector())
                                .normalize();

                        entity.setVelocity(unitVector.multiply(1));
                    }
                }

            };
            task.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
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
            Location loc = playerLoc.clone().add(forward);

            // Call magicCircle with proper center, yaw, pitch and offset
            List<Location> magicCirclePoints = ParticleUtils.circle(loc, 1, 20, yaw, -pitch + 90);
            magicCirclePoints.add(loc);

            // Spawn particles at all calculated points
            for (int i = 0; i < magicCirclePoints.size(); i++){
                for (Location loc1 : magicCirclePoints) {
                    loc1.getWorld().spawnParticle(Utils.DUST, loc1, 0, new Particle.DustOptions(Color.AQUA, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }
}
