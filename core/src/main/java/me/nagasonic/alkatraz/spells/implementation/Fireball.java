package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellEntityComponent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public class Fireball extends AttackSpell implements Listener {
    public Fireball(String type) {
        super(type);
    }

    @Override
    protected void setupOptions(){
        SpellOption speedOption = new SpellOption(this, "speed",
                "Adjust fireball velocity", Material.FEATHER);
        OptionValue<Double> slowSpeed = new OptionValue<>(
                "slow", "Slow", "Slower projectile, maximum damage",
                Material.SOUL_SAND, 0.3
        );
        slowSpeed.addImpact(new StatModifierImpact(this, "projectile_speed", 0.3, StatModifierImpact.ModifierType.SET));
        slowSpeed.addImpact(new StatModifierImpact(this, "damage", 1.2, StatModifierImpact.ModifierType.MULTIPLY));
        speedOption.addValue(slowSpeed);

        OptionValue<Double> normalSpeed = new OptionValue<>(
                "normal", "Normal", "Standard projectile speed",
                Material.FEATHER, 0.5
        );
        normalSpeed.addImpact(new StatModifierImpact(this, "projectile_speed", 0.5, StatModifierImpact.ModifierType.SET));
        normalSpeed.addImpact(new StatModifierImpact(this, "damage", 1.0, StatModifierImpact.ModifierType.MULTIPLY));
        speedOption.addValue(normalSpeed);

        OptionValue<Double> fastSpeed = new OptionValue<>(
                "fast", "Fast", "Faster projectile, reduced damage (-20%)",
                Material.SUGAR, 0.8
        );
        fastSpeed.addRequirement(new NumberStatRequirement<>("circleLevel", 2, "Requires Circle Level 2"));
        fastSpeed.addImpact(new StatModifierImpact(this, "projectile_speed", 0.8, StatModifierImpact.ModifierType.SET));
        fastSpeed.addImpact(new StatModifierImpact(this, "damage", 0.8, StatModifierImpact.ModifierType.MULTIPLY));
        speedOption.addValue(fastSpeed);

        OptionValue<Double> blazingSpeed = new OptionValue<>(
                "blazing", "Blazing", "Extremely fast, heavy damage penalty (-40%)",
                Material.BLAZE_POWDER, 1.2
        );
        blazingSpeed.addRequirement(new NumberStatRequirement<>("circleLevel", 4, "Requires Circle Level 4"));
        blazingSpeed.addImpact(new StatModifierImpact(this, "projectile_speed", 1.2, StatModifierImpact.ModifierType.SET));
        blazingSpeed.addImpact(new StatModifierImpact(this, "damage", 0.6, StatModifierImpact.ModifierType.MULTIPLY));
        blazingSpeed.addImpact(new ManaCostImpact(this, 15));
        speedOption.addValue(blazingSpeed);

        addOption(speedOption);

        SpellOption breaksBlocksOption = new SpellOption(this, "breaks_blocks",
                "Whether the fireball should break blocks", Material.DIAMOND_PICKAXE);
        OptionValue<Boolean> breaksBlocks = new OptionValue<>(
                "breaks_blocks", "Breaks Blocks", "Fireball will break blocks on collision", Material.LIME_CONCRETE, true
        );
        breaksBlocksOption.addValue(breaksBlocks);
        OptionValue<Boolean> notBreakBlocks = new OptionValue<>(
                "block_protection", "Blocks Protected", "Fireball will not break blocks.", Material.RED_CONCRETE, false
        );
        breaksBlocksOption.addValue(notBreakBlocks);

        addOption(breaksBlocksOption);

        SpellOption sizeOption = new SpellOption(this, "size",
                "Adjust fireball explosion size", Material.TNT);
        OptionValue<Double> small = new  OptionValue<>(
                "small_size", "Small Size", "Small fireball explosion.", Material.IRON_NUGGET, 0.75
        );
        small.addRequirement(new NumberStatRequirement<>("circleLevel", 3, "Requires Circle Level 3"));
        small.addImpact(new StatModifierImpact(this, "size", 0.75,  StatModifierImpact.ModifierType.SET));
        small.addImpact(new StatModifierImpact(this, "damage", 1.4, StatModifierImpact.ModifierType.SET));
        small.addImpact(new StatModifierImpact(this, "projectile_speed", 1.1, StatModifierImpact.ModifierType.MULTIPLY));
        sizeOption.addValue(small);

        OptionValue<Double> normal = new OptionValue<>(
                "normal_size", "Normal Size", "Normal fireball explosion", Material.IRON_INGOT, 1.0
        );
        normal.addImpact(new StatModifierImpact(this, "size", 1, StatModifierImpact.ModifierType.SET));
        sizeOption.addValue(normal);

        OptionValue<Double> large = new OptionValue<>(
                "large_size", "Large Size", "Large fireball explosion", Material.IRON_BLOCK, 1.25
        );
        large.addRequirement(new NumberStatRequirement<>("circleLevel", 3, "Requires Circle Level 3"));
        large.addImpact(new StatModifierImpact(this, "size", 1.25, StatModifierImpact.ModifierType.SET));
        large.addImpact(new StatModifierImpact(this, "damage", 0.6, StatModifierImpact.ModifierType.MULTIPLY));
        large.addImpact(new StatModifierImpact(this, "projectile_speed", 0.9, StatModifierImpact.ModifierType.SET));
        sizeOption.addValue(large);

        addOption(sizeOption);
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
        Alkatraz.getInstance().save("spells/fireball.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fireball.yml").get();
        loadCommonConfig(spellConfig);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double projectileSpeed = getModifiedStat(caster, "projectile_speed", 0.5);

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power")),
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
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.ORANGE, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
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
        double finalDamage = getPower(comp.getCaster(), le, props.getRemainingPower());
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
        if ((boolean) getOption("breaks_blocks").getSelectedValue(comp.getCaster()).getValue()) return;
        e.setCancelled(true);
        for (LivingEntity entity : fireball.getLocation().getNearbyLivingEntities((double) getOption("size").getSelectedValue(comp.getCaster()).getValue())){
            entity.damage(getPower(comp.getCaster(), entity, props.getRemainingPower()));
        }
    }
}
