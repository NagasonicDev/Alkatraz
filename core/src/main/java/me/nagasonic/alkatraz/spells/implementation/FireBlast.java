package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.components.SpellComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellEntityComponent;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public class FireBlast extends AttackSpell implements Listener {
    public FireBlast(String type){
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 30);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/fire_blast.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fire_blast.yml").get();

        loadCommonConfig(spellConfig);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.MAGIC);
            LargeFireball fire = p.launchProjectile(LargeFireball.class, p.getLocation().getDirection().multiply(0.5));
            SpellEntityComponent comp = new SpellEntityComponent(
                    this,
                    props,
                    p,
                    wand,
                    SpellComponentType.OFFENSE,
                    fire
            );
            comp.setCollisionRadius(0.5);
            SpellComponentHandler.register(comp);
            NBT.modifyPersistentData(fire, nbt -> {
                nbt.setString("componentID", comp.getComponentID().toString());
            });
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
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 3, 0);

            // Spawn particles at all calculated points
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.RED, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    @EventHandler
    private void onDamage(EntityDamageByEntityEvent e){
        if (!(e.getDamager() instanceof org.bukkit.entity.Fireball fireball)) return;
        if (!NBT.getPersistentData(fireball, nbt -> nbt.hasTag("componentID"))) return;
        String idString = NBT.getPersistentData(fireball, nbt -> nbt.getString("componentID"));
        if (idString == null) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        SpellComponent comp = SpellComponentHandler.getActiveComponent(UUID.fromString(idString));
        if (!(comp instanceof SpellEntityComponent ecomp)) return;
        if (!(ecomp.getProperties() instanceof AttackProperties props)) return;
        e.setDamage(getPower(comp.getCaster(), le, props.getRemainingPower()));
    }

    @EventHandler
    private void onHit(ProjectileHitEvent e){
        if (!(e.getEntity() instanceof org.bukkit.entity.Fireball fireball)) return;
        if (!NBT.getPersistentData(fireball, nbt -> nbt.hasTag("componentID"))) return;
        String idString = NBT.getPersistentData(fireball, nbt -> nbt.getString("componentID"));
        if (idString == null) return;
        SpellComponent comp = SpellComponentHandler.getActiveComponent(UUID.fromString(idString));
        if (comp.getSpell() != this) return;
        Location loc = e.getHitBlock() != null ? e.getHitBlock().getLocation() : e.getHitEntity().getLocation();
        List<Block> blocks = Utils.blocksInRadius(loc, 2);
        for (Block block : blocks){
            if (block.getType() == Material.AIR){
                block.setType(Material.FIRE);
            }
        }
    }
}
