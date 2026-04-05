package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SizedFireball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class AirBurst extends AttackSpell {
    public AirBurst(String type){
        super(type);
    }

    @Override
    protected void setupOptions() {
        SpellOption sizeOption = new SpellOption(this, "blast_size",
                "How large the burst of air is.", Material.FEATHER, 1);
        OptionValue<Double> small = new OptionValue<>("small_burst", "Small Burst",
                "A small burst of air.", Material.IRON_NUGGET, 0.75);
        small.addRequirement(new NumberStatRequirement<>("circleLevel", 3));
        small.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 50));
        small.addImpact(new StatModifierImpact(this, "blast_size", 0.75, StatModifierImpact.ModifierType.SET));
        small.addImpact(new StatModifierImpact(this, "damage", 1.3, StatModifierImpact.ModifierType.MULTIPLY));
        sizeOption.addValue(small);

        OptionValue<Double> normalSize = new OptionValue<>("normal_burst", "Normal Burst",
                "A normal burst of air.", Material.IRON_INGOT, 1.0);
        normalSize.addImpact(new StatModifierImpact(this, "blast_size", 1, StatModifierImpact.ModifierType.SET));
        sizeOption.addValue(normalSize);

        OptionValue<Double> large = new OptionValue<>("large_burst", "Large Burst",
                "A large burst of air.", Material.IRON_BLOCK, 1.3);
        large.addRequirement(new NumberStatRequirement<>("circleLevel", 3));
        large.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 30));
        large.addImpact(new StatModifierImpact(this, "blast_size", 1.3, StatModifierImpact.ModifierType.SET));
        large.addImpact(new StatModifierImpact(this, "damage", 0.7, StatModifierImpact.ModifierType.MULTIPLY));
        sizeOption.addValue(large);

        addOption(sizeOption);

        SpellOption rangeOption = new SpellOption(this, "range",
                "Range of the burst.", Material.SPYGLASS, 2);
        OptionValue<Double> XLow = new OptionValue<>("extra_low_range", "Extra Low Range",
                "Burst does not travel far.", Material.ARROW, 5.0);
        XLow.addImpact(new StatModifierImpact(this, "range", 5.0, StatModifierImpact.ModifierType.SET));
        XLow.addImpact(new StatModifierImpact(this, "blast_size", 1.2, StatModifierImpact.ModifierType.MULTIPLY));
        XLow.addImpact(new StatModifierImpact(this, "damage", 1.15, StatModifierImpact.ModifierType.MULTIPLY));
        XLow.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 100));
        XLow.addRequirement(new NumberStatRequirement<>("circleLevel", 3));
        rangeOption.addValue(XLow);

        OptionValue<Double> low = new OptionValue<>("low_range", "Low Range",
                "Burst travels a smaller distance.", Material.TIPPED_ARROW, 7.5);
        low.addImpact(new StatModifierImpact(this, "range", 7.5, StatModifierImpact.ModifierType.SET));
        low.addImpact(new StatModifierImpact(this, "blast_size", 1.1, StatModifierImpact.ModifierType.MULTIPLY));
        low.addImpact(new StatModifierImpact(this, "damage", 1.075, StatModifierImpact.ModifierType.MULTIPLY));
        low.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 50));
        low.addRequirement(new NumberStatRequirement<>("circleLevel", 2));
        rangeOption.addValue(low);

        OptionValue<Double> normal = new OptionValue<>("normal_range", "Normal Range",
                "Burst travels a normal amount.", Material.SPECTRAL_ARROW, 7.5);
        normal.addImpact(new StatModifierImpact(this, "range", 10, StatModifierImpact.ModifierType.SET));
        rangeOption.addValue(normal);

        OptionValue<Double> extended = new OptionValue<>("extended_range", "Extended Range",
                "Burst travels further than usual.", Material.BOW, 12.5);
        extended.addImpact(new StatModifierImpact(this, "range", 12.5, StatModifierImpact.ModifierType.SET));
        extended.addImpact(new StatModifierImpact(this, "blast_size", 0.9, StatModifierImpact.ModifierType.MULTIPLY));
        extended.addImpact(new StatModifierImpact(this, "damage", 0.925, StatModifierImpact.ModifierType.MULTIPLY));
        extended.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 50));
        extended.addRequirement(new NumberStatRequirement<>("circleLevel", 2));
        rangeOption.addValue(extended);

        addOption(rangeOption);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {
        location.getWorld().spawnParticle(Particle.CLOUD, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.CLOUD, location, 30);
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
                        caster.getEyeLocation().getDirection().multiply(getModifiedStat(caster, "range", 10))
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

                for (Location loc : ParticleUtils.circle(point, getModifiedStat(caster, "blast_size", 1), 16, yaw, pitch)) {
                    if (properties.isCountered()) {
                        return;
                    }
                    loc.getWorld().spawnParticle(
                            Particle.CLOUD,
                            loc,
                            2,
                            0, 0, 0,
                            0.1
                    );
                }
                for (LivingEntity le : point.getNearbyLivingEntities(getModifiedStat(caster, "blast_size", 1))){
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

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&7Tome of Wind &oVol. 1")
                .addCustomLoreLine("&8A beginners guide to wind magic.")
                .addCustomLoreLine("")
                .build();
    }
}
