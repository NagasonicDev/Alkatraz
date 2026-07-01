package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellEntityComponent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.UnknownNullability;

import java.util.List;
import java.util.UUID;

public class Fireball extends AttackSpell implements Listener {
    public Fireball(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 30);
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/fireball_options.yml");
        Alkatraz.getInstance().save("spells/fireball.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fireball.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double projectileSpeed = getModifiedStat(caster, "projectile_speed", 0.5);

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getPower(caster, getBasePower()) * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")),
                AttackType.MAGIC
        );

        org.bukkit.entity.Fireball fireball = caster.launchProjectile(
                org.bukkit.entity.Fireball.class,
                caster.getEyeLocation().getDirection().multiply(projectileSpeed)
        );

        SpellEntityComponent entityComp = new SpellEntityComponent(
                this, props, caster, wand, SpellComponentType.OFFENSE, fireball
        );
        entityComp.setCollisionRadius(0.5);
        NBT.modifyPersistentData(fireball, nbt -> {
            nbt.setString("component_id", entityComp.getComponentID().toString());
        });
        SpellComponentHandler.register(entityComp);
    }

    @Override
    public void mobCastAction(@UnknownNullability Mob caster, ItemStack wand) {
        if (caster.isDead()) return;
        double wandp = wand == null ? 1 : NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        double power = getPower(caster, getBasePower())
                * wandp;

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
                AttackType.MAGIC
        );
        Vector direction = caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector());

        org.bukkit.entity.Fireball fireball = caster.launchProjectile(
                org.bukkit.entity.Fireball.class,
                direction.multiply(0.1)
        );

        SpellEntityComponent entityComp = new SpellEntityComponent(
                this, props, caster, wand, SpellComponentType.OFFENSE, fireball
        );
        entityComp.setCollisionRadius(0.5);
        NBT.modifyPersistentData(fireball, nbt -> {
            nbt.setString("component_id", entityComp.getComponentID().toString());
        });
        SpellComponentHandler.register(entityComp);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation(); // Player eye location
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();

            // Calculate offset vector pointing forward relative to player orientation
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5); // 1.5 blocks in front

            // Call magicCircle with proper center, yaw, pitch and offset
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

            // Spawn particles at all calculated points
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.ORANGE, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&eScroll of the Apollo &oI")
                .addLoreLine("&8The first scroll, containing the basis of")
                .addLoreLine("&8fire magic.")
                .addCustomLoreLine("")
                .build();
    }

    @EventHandler
    private void onDamage(EntityDamageByEntityEvent e){
        if (!(e.getDamager() instanceof org.bukkit.entity.Fireball fireball)) return;
        String idString = NBT.getPersistentData(fireball, nbt -> nbt.getString("component_id"));
        if (idString == null) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        SpellComponent comp = SpellComponentHandler.getActiveComponent(UUID.fromString(idString));
        if (!(comp instanceof SpellEntityComponent ecomp)) return;
        if (!(ecomp.getProperties() instanceof AttackProperties props)) return;
        double finalDamage;
        finalDamage = getPower(comp.getCaster(), le, props.getRemainingPower());
        e.setDamage(finalDamage);

    }

    @EventHandler
    private void onFireballExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Fireball fireball)) return;
        String idString = NBT.getPersistentData(fireball, nbt -> nbt.getString("component_id"));
        if (idString == null) return;
        SpellComponent comp = SpellComponentHandler.getActiveComponent(UUID.fromString(idString));
        if (comp.getSpell().getClass() != Fireball.class) return;
        if (!(comp instanceof SpellEntityComponent ecomp)) return;
        if (!(ecomp.getProperties() instanceof AttackProperties props)) return;
        if (comp.getCaster() instanceof Player p && (boolean) getOption("breaks_blocks").getSelectedValue(p).getValue()) return;
        e.setCancelled(true);
        double size;
        if (comp.getCaster() instanceof Player p) {
            size = (double) getOption("size").getSelectedValue(p).getValue();
        } else{
            size = (double) getOption("size").getOptionValues().get(getOption("size").getDefIndex()).getValue();
        }
        for (LivingEntity entity : fireball.getLocation().getNearbyLivingEntities(size)){
            entity.damage(getPower(comp.getCaster(), entity, props.getRemainingPower()));
        }
    }
}
