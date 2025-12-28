package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.dom.Ground;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.components.SpellComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellEntityComponent;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public class EarthThrow extends AttackSpell implements Listener {
    public EarthThrow(String type){
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {
        location.getWorld().spawnParticle(Particle.BLOCK_DUST, location, 15, Material.DIRT.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.BLOCK_DUST, location, 30, Material.DIRT.createBlockData());
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/earth_throw.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earth_throw.yml").get();

        loadCommonConfig(spellConfig);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.PHYSICAL);
            Location loc = p.getEyeLocation();
            Vector direction = loc.getDirection();
            if (p.isOnGround()){
                BlockData data = Bukkit.createBlockData(Ground.getGround(p.getLocation().getBlock().getBiome()));
                FallingBlock b = loc.getWorld().spawnFallingBlock(loc, data);
                b.setHurtEntities(false);
                b.setVelocity(direction.multiply(1).setY(0.3));
                SpellEntityComponent comp = new SpellEntityComponent(
                        this,
                        props,
                        p,
                        wand,
                        SpellComponentType.OFFENSE,
                        b
                );
                SpellComponentHandler.register(comp);
                NBT.modifyPersistentData(b, nbt -> {
                    nbt.setString("componentID", comp.getComponentID().toString());
                });
            }
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
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.fromRGB(78, 47, 0), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    @EventHandler
    private void onLand(EntityChangeBlockEvent e){
        if (e.getEntity() instanceof FallingBlock){
            FallingBlock b = (FallingBlock) e.getEntity();
            if (!NBT.getPersistentData(b, nbt -> nbt.hasTag("componentID"))) return;
            String id = NBT.getPersistentData(b, nbt -> nbt.getString("componentID"));
            if (id != null){
                UUID uuid = UUID.fromString(id);
                SpellComponent comp = SpellComponentHandler.getActiveComponent(uuid);
                if (comp.getSpell() != this) return;
                if (!(comp instanceof SpellEntityComponent eComp)) return;
                SpellProperties p = eComp.getProperties();
                if (!(p instanceof AttackProperties props)) return;
                e.getBlock().setType(Material.AIR);
                Location loc = e.getBlock().getLocation();
                List<Location> locs = ParticleUtils.circle(loc, 3, 1, 0, 0);
                for (Location l : locs){
                    l.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, l, 1);
                }
                for (LivingEntity le : loc.getNearbyLivingEntities(3)){
                    le.damage(props.getRemainingPower());
                    Vector direction = le.getLocation().toVector().subtract(loc.toVector());
                    direction.normalize().multiply(1);
                    direction.setY(1.25);
                    le.setVelocity(direction);
                }
            }
        }
    }

    @EventHandler
    private void onDrop(EntityDropItemEvent e){
        if (e.getEntity() instanceof FallingBlock b){
            if (!NBT.getPersistentData(b, nbt -> nbt.hasTag("componentID"))) return;
            String id = NBT.getPersistentData(b, nbt -> nbt.getString("componentID"));
            if (id != null && !id.isEmpty()){
                UUID uuid = UUID.fromString(id);
                SpellComponent comp = SpellComponentHandler.getActiveComponent(uuid);
                if (comp.getSpell() != this) return;
                if (comp instanceof SpellEntityComponent eComp){
                    SpellProperties p = eComp.getProperties();
                    if (!(p instanceof AttackProperties props)) return;
                    e.setCancelled(true);
                    Location loc = b.getLocation();
                    b.remove();
                    List<Location> locs = ParticleUtils.circle(loc, 3, 1, 0, 0);
                    for (Location l : locs){
                        l.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, l, 5);
                    }
                    for (LivingEntity le : loc.getNearbyLivingEntities(3)){
                        le.damage(props.getRemainingPower());
                        Vector direction = le.getLocation().toVector().subtract(loc.toVector());
                        direction.normalize().multiply(1);
                        direction.setY(1.25);
                        le.setVelocity(direction);
                    }
                }
            }
        }
    }
}
