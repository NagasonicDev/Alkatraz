package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.dom.Ground;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.components.*;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
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
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Tremor extends AttackSpell implements Listener {
    public Tremor(String type) {
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
        Alkatraz.getInstance().save("spells/tremor.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/tremor.yml").get();

        loadCommonConfig(spellConfig);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this,  Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")), AttackType.PHYSICAL);
            final Location base = p.getLocation().subtract(0, 0.5, 0);
            Vector dir = p.getEyeLocation().getDirection();
            Vector perp = dir.clone().rotateAroundY(90);
            List<Location> locations = new ArrayList<>();
            locations.add(base.clone());
            locations.add(base.clone().add(perp));
            locations.add(base.clone().subtract(perp));
            locations.add(base.clone().add(perp.multiply(2)));
            locations.add(base.clone().subtract(perp.multiply(2)));
            new BukkitRunnable(){
                private int counter = 0;
                private List<Location> previous = new ArrayList<>(locations);

                @Override
                public void run() {
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    List<Location> toAdd = new ArrayList<>();
                    if (previous.isEmpty()) {
                        cancel();
                        return;
                    }
                    for (Location prev : previous) {
                        Location top = prev.clone();
                        top.add(dir);
                        top.add(0, 5, 0);
                        Location loc = Utils.findTopSolid(top, 10);
                        if (loc == null){
                            cancel();
                            return;
                        }
                        if (Ground.isGround(loc.getBlock().getType())){
                            SpellBlockComponent component = new SpellBlockComponent(
                                    Tremor.this,
                                    props,
                                    p,
                                    wand,
                                    SpellComponentType.OFFENSE,
                                    loc.getBlock(),
                                    1,
                                    8
                            );
                            SpellComponentHandler.register(component);
                            BlockData data = Bukkit.createBlockData(loc.getBlock().getType());
                            FallingBlock b = loc.getWorld().spawnFallingBlock(loc.add(0, 0.5, 0), data);
                            b.setHurtEntities(false);
                            b.setVelocity(new Vector(0, 0.5, 0));
                            NBT.modifyPersistentData(b, nbt -> {
                                nbt.setString("spell", getId());
                            });
                            List<Location> locs = ParticleUtils.circle(loc, 1, 1, 0, 0);
                            for (Location l : locs){
                                l.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, l, 1);
                            }
                            for (LivingEntity entity : loc.getNearbyLivingEntities(2)) {
                                if (entity != p && !props.hasHit(entity)) {
                                    entity.setVelocity(new Vector(0, 1, 0));
                                    entity.damage(getPower(p, entity, props.getRemainingPower()));
                                    props.hit(entity);
                                }
                            }

                            toAdd.add(loc);
                        }else{
                            previous.remove(prev);
                        }
                    }
                    previous.clear();
                    previous = toAdd;
                    if (counter == 11) {
                        cancel();
                    }
                    counter++;
                }
            }.runTaskTimer(Alkatraz.getInstance(), 0, 1);
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
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 10, new Particle.DustOptions(Color.fromRGB(78, 47, 0), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(Element.EARTH.getColor() + "Tome of the Blind Earthseer &oSection III")
                .addCustomLoreLine("&8The 3rd lesson of the seeker of the")
                .addCustomLoreLine("&8pinnacle of Earth magic")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }

    @EventHandler
    private void onDrop(EntityDropItemEvent e){
        if (e.getEntity() instanceof FallingBlock b){
            if (!NBT.getPersistentData(b, nbt -> nbt.hasTag("spell"))) return;
            String id = NBT.getPersistentData(b, nbt -> nbt.getString("spell"));
            if (id != null && !id.isEmpty()){
                if (id != getId()) return;
                e.setCancelled(true);
                b.remove();
            }
        }
    }
}
