package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Heal extends Spell {

    public Heal(String type){
        super(type);
    }
    private double baseHeal;
    private double maxHeal;
    private int taskID;


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/heal.yml");
        Alkatraz.getInstance().saveConfig("spells/heal_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/heal.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
        baseHeal = spellConfig.getDouble("base_heal");
        maxHeal = spellConfig.getDouble("max_heal");
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            if (p.isSneaking() || p.getTargetEntity((int) getModifiedStat(p, "target_range", 20)) == null || !(p.getTargetEntity((int) getModifiedStat(p, "target_range", 20)) instanceof Player)){
                double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
                double base = (baseHeal * wandPower) * (1 + ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class).getAffinity(Element.LIGHT) / 100);
                double heal = getModifiedStat(p, "heal", base);
                if (heal > maxHeal){
                    heal = maxHeal;
                }
                if (p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() < p.getHealth() + heal){
                    p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                }else p.setHealth(p.getHealth() + heal);
                AtomicInteger l = new AtomicInteger(0);
                List<Location> locs = ParticleUtils.createHelix(p.getLocation(), 2, 0.5, 2, 10);
                taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                    if (l.get() < locs.size()){
                        Location a = null;
                        try {
                            a = locs.get(l.get());
                        } catch (IndexOutOfBoundsException e) {
                        }
                        if (a != null){
                            a.getWorld().spawnParticle(Particle.TOTEM, a, 1, 0, 0, 0, 0);
                            l.addAndGet(1);
                        }
                    }else{ stopCast();}
                }, 0L, 1L);
            }else{
                Player target = (Player) p.getTargetEntity((int) getModifiedStat(p, "target_range", 20));
                double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
                double base = (baseHeal * wandPower) * (1 + ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class).getAffinity(Element.LIGHT) / 100);
                double heal = getModifiedStat(p, "heal", base);
                if (heal > maxHeal){
                    heal = maxHeal;
                }
                if (target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() < target.getHealth() + heal){
                    target.setHealth(target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                }else target.setHealth(target.getHealth() + heal);
                AtomicInteger l = new AtomicInteger(0);
                List<Location> locs = ParticleUtils.createHelix(target.getLocation(), 2, 0.5, 2, 10);
                taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                    if (l.get() < locs.size()){
                        Location a = null;
                        try {
                            a = locs.get(l.get());
                        } catch (IndexOutOfBoundsException e) {
                        }
                        if (a != null){
                            a.getWorld().spawnParticle(Particle.TOTEM, a, 1, 0, 0, 0, 0);
                            l.addAndGet(1);
                        }
                    }else{ stopCast();}
                }, 0L, 1L);
            }
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        double wandp = wand == null ? 1 : NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        double heal = (baseHeal * wandp) * (1 + Utils.getEntityAffinity(Element.LIGHT, caster) / 100);
        if (heal > maxHeal){
            heal = maxHeal;
        }
        if (caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() < caster.getHealth() + heal){
            caster.setHealth(caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        }else caster.setHealth(caster.getHealth() + heal);
        AtomicInteger l = new AtomicInteger(0);
        List<Location> locs = ParticleUtils.createHelix(caster.getLocation(), 2, 0.5, 2, 10);
        taskID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (l.get() < locs.size()){
                Location a = null;
                try {
                    a = locs.get(l.get());
                } catch (IndexOutOfBoundsException e) {
                }
                if (a != null){
                    a.getWorld().spawnParticle(Particle.TOTEM, a, 1, 0, 0, 0, 0);
                    l.addAndGet(1);
                }
            }else{ stopCast();}
        }, 0L, 1L);
    }

    private void stopCast(){
        Bukkit.getServer().getScheduler().cancelTask(taskID);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            if (caster instanceof Player p){
                if (p.isSneaking() || p.getTargetEntity((int) getModifiedStat(p, "target_range", 20)) == null || !(p.getTargetEntity((int) getModifiedStat(p, "target_range", 20)) instanceof Player)){
                    Location playerLoc = p.getLocation();
                    float yaw = playerLoc.getYaw();
                    float pitch = 0;

                    // Call magicCircle with proper center, yaw, pitch and offset
                    List<Location> magicCirclePoints = ParticleUtils.circle(playerLoc, 1, 20, yaw, pitch);
                    magicCirclePoints.add(playerLoc);

                    // Spawn particles at all calculated points
                    for (int i = 0; i < magicCirclePoints.size(); i++){
                        for (Location loc1 : magicCirclePoints) {
                            loc1.getWorld().spawnParticle(Utils.DUST, loc1, 1, new Particle.DustOptions(Color.YELLOW, 0.4F));
                        }
                    }
                }else{
                    Location playerLoc = p.getTargetEntity((int) getModifiedStat(p, "target_range", 20)).getLocation();
                    float yaw = playerLoc.getYaw();
                    float pitch = 0;

                    // Calculate offset vector pointing forward relative to player orientation
                    Vector forward = playerLoc.getDirection().normalize().multiply(1.5); // 1.5 blocks in front
                    Location loc = playerLoc.clone().add(forward);

                    // Call magicCircle with proper center, yaw, pitch and offset
                    List<Location> magicCirclePoints = ParticleUtils.circle(loc, 1, 20, yaw, -pitch + 90);
                    magicCirclePoints.add(loc);

                    // Spawn particles at all calculated points
                    for (int i = 0; i < magicCirclePoints.size(); i++){
                        for (Location loc1 : magicCirclePoints) {
                            loc1.getWorld().spawnParticle(Utils.DUST, loc1, 1, new Particle.DustOptions(Color.YELLOW, 0.4F));
                        }
                    }
                }
            }else{
                Location playerLoc = caster.getLocation();
                float yaw = playerLoc.getYaw();
                float pitch = 0;

                // Call magicCircle with proper center, yaw, pitch and offset
                List<Location> magicCirclePoints = ParticleUtils.circle(playerLoc, 1, 20, yaw, pitch);
                magicCirclePoints.add(playerLoc);

                // Spawn particles at all calculated points
                for (int i = 0; i < magicCirclePoints.size(); i++){
                    for (Location loc1 : magicCirclePoints) {
                        loc1.getWorld().spawnParticle(Utils.DUST, loc1, 1, new Particle.DustOptions(Color.YELLOW, 0.4F));
                    }
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&eAsclepius' Pharmacopoeia &o2nd Edition")
                .addLoreLine("&8The knowledge of the god of medicine.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }

    @Override
    public boolean canMobCast(Mob mob){
        return mob.getHealth() < mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
    }
}
