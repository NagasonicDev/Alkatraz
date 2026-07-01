package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Swift extends Spell {
    public Swift(String type) {
        super(type);
    }
    private double strength;
    private int taskID;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/swift.yml");
        Alkatraz.getInstance().saveConfig("spells/swift_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/swift.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        strength = spellConfig.getDouble("dash_strength");
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
            double dashMultiplier = (Double) getOption("dash_style").getSelectedValue(p).getValue();
            p.setVelocity(p.getEyeLocation().getDirection().normalize().multiply(wandPower * strength * dashMultiplier));
            AtomicInteger i = new AtomicInteger(0);
            taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                if (i.get() < 8){
                    p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.5, 0.5, 0.5, 0.25);
                    i.set(i.get() + 1);
                }else{
                    stop();
                }
            }, 0L, 5L);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (!caster.isDead()){
            double wandPower = wand == null ? 1 : NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
            caster.setVelocity(caster.getEyeLocation().getDirection().normalize().multiply(wandPower * strength));
            AtomicInteger i = new AtomicInteger(0);
            taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                if (i.get() < 8){
                    caster.getWorld().spawnParticle(Particle.CLOUD, caster.getLocation(), 10, 0.5, 0.5, 0.5, 0.25);
                    i.set(i.get() + 1);
                }else{
                    stop();
                }
            }, 0L, 5L);
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation(); // Player eye location
            float yaw = playerLoc.getYaw();
            float pitch = 0;

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
                .setDisplayName("&fHermes' Tachys Manual")
                .addCustomLoreLine("&8The secret to Hermes' instant deliveries.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    private void stop(){
        Bukkit.getServer().getScheduler().cancelTask(this.taskID);
    }

    @Override
    public boolean canMobCast(Mob m) {
        double dif = m.getHealth() /  m.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        if (dif <= 0.3){
            return true;
        }
        return false;
    }
}
