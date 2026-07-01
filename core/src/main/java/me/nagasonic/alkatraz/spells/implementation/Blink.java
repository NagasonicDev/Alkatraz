package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

public class Blink extends Spell {

    private double blinkDistance;

    public Blink(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/blink_options.yml");
        Alkatraz.getInstance().save("spells/blink.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/blink.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.blinkDistance = spellConfig.getDouble("blink_distance");
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;


        double distance = (Double) getOption("blink_distance").getSelectedValue(caster).getValue();

        Vector direction = caster.getEyeLocation().getDirection().normalize();
        Location start = caster.getEyeLocation();
        Location target = findTeleportLocation(start, direction, distance);

        if (target == null) {
            Utils.sendActionBar(caster, "&cNo valid teleport location found!");
            cancelCast(caster);
            return;
        }

        spawnTeleportParticles(start);
        caster.teleport(target);
        spawnTeleportParticles(target);

        caster.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;


        if (caster.getTarget() == null) return;

        Vector direction = caster.getTarget().getLocation().toVector()
                .subtract(caster.getLocation().toVector()).normalize();
        Location start = caster.getLocation();
        Location target = findTeleportLocation(start, direction, blinkDistance);

        if (target == null) return;

        spawnTeleportParticles(start);
        caster.teleport(target);
        spawnTeleportParticles(target);

        caster.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    private Location findTeleportLocation(Location start, Vector direction, double maxDistance) {
        Location prev = start.clone();
        boolean moved = false;

        for (double d = 0.5; d <= maxDistance; d += 0.5) {
            Location current = start.clone().add(direction.clone().multiply(d));
            Block block = current.getBlock();

            if (block.getType().isSolid()) {
                if (moved) {
                    prev.setYaw(start.getYaw());
                    prev.setPitch(start.getPitch());
                    return prev;
                }
                return null;
            }

            prev = current;
            moved = true;
        }

        Location end = start.clone().add(direction.clone().multiply(maxDistance));
        end.setYaw(start.getYaw());
        end.setPitch(start.getPitch());
        return end;
    }

    private void spawnTeleportParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.5);
        loc.getWorld().spawnParticle(Utils.DUST, loc, 15, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.6F));
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation();
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5);
            List<Location> points = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 3, 0);
            for (int i = 0; i < 100; i++) {
                for (Location loc : points) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                            new Particle.DustOptions(Color.fromRGB(120, 50, 200), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&5Voidwalk Codex")
                .addCustomLoreLine("&8&oStep between the folds of reality.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }
}
