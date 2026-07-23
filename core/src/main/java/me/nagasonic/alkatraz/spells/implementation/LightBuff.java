package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;

public class LightBuff extends Spell {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double buffRadius;
    private int baseDuration;

    private static final Color PARTICLE_COLOR = Color.fromRGB(255, 255, 100);

    public LightBuff(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/light_buff_options.yml");
        Alkatraz.getInstance().save("spells/light_buff.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/light_buff.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.buffRadius = spellConfig.getDouble("buff_radius");
        this.baseDuration = spellConfig.getInt("base_duration");
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;


        int duration = (int) getModifiedStat(caster, "duration", baseDuration);
        double radius = getModifiedStat(caster, "buff_radius", buffRadius);

        int durationTicks = duration * 20;

        Location cl = caster.getLocation();
        Collection<Player> nearbyPlayers = caster.getWorld().getPlayers().stream()
                .filter(p -> {
                    Location pl = p.getLocation();
                    return pl.getWorld().equals(cl.getWorld()) &&
                            Math.abs(pl.getX() - cl.getX()) <= radius &&
                            Math.abs(pl.getY() - cl.getY()) <= radius &&
                            Math.abs(pl.getZ() - cl.getZ()) <= radius;
                })
                .collect(java.util.stream.Collectors.toList());

        for (Player target : nearbyPlayers) {
            if (target.isDead() || !target.isValid()) continue;

            target.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, 0, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 0, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, durationTicks, 0, false, true));

            target.sendMessage(ChatColor.translateAlternateColorCodes('&', lang().get("spells.lightbuff.blessed_by_target", "%caster%", caster.getName())));
        }

        caster.sendMessage(ChatColor.translateAlternateColorCodes('&',
                lang().get("spells.lightbuff.blessed_count", "%count%", String.valueOf(nearbyPlayers.size()), "%duration%", String.valueOf((int) duration))));

        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);

        spawnBurstParticles(caster.getLocation(), radius);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;


        int durationTicks = baseDuration * 20;

        Location cl2 = caster.getLocation();
        Collection<Player> nearbyPlayers = caster.getWorld().getPlayers().stream()
                .filter(p -> {
                    Location pl = p.getLocation();
                    return pl.getWorld().equals(cl2.getWorld()) &&
                            Math.abs(pl.getX() - cl2.getX()) <= buffRadius &&
                            Math.abs(pl.getY() - cl2.getY()) <= buffRadius &&
                            Math.abs(pl.getZ() - cl2.getZ()) <= buffRadius;
                })
                .collect(java.util.stream.Collectors.toList());

        for (Player target : nearbyPlayers) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, 0, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 0, false, true));
        }

        caster.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, 0, false, false));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 0, false, false));

        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
    }

    private void spawnBurstParticles(Location center, double radius) {
        int particles = 60;
        for (int i = 0; i < particles; i++) {
            double angle = 2 * Math.PI * i / particles;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = center.clone().add(x, 0.5, z);
            loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                    new Particle.DustOptions(PARTICLE_COLOR, 0.8F));
            loc.getWorld().spawnParticle(Particle.SPELL_MOB_AMBIENT, loc, 0, 0.8, 0.8, 0.3, 0);
        }

        for (int i = 0; i < 20; i++) {
            double x = (Math.random() - 0.5) * radius * 2;
            double z = (Math.random() - 0.5) * radius * 2;
            Location loc = center.clone().add(x, Math.random() * 3, z);
            loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                    new Particle.DustOptions(PARTICLE_COLOR, 0.5F));
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location loc = caster.getEyeLocation();
            float yaw = loc.getYaw();
            float pitch = loc.getPitch();
            Vector forward = loc.getDirection().normalize().multiply(1.5);
            List<Location> points = ParticleUtils.magicCircle(loc, yaw, pitch, forward, 3, 0);
            for (int i = 0; i < 100; i++) {
                for (Location point : points) {
                    point.getWorld().spawnParticle(Utils.DUST, point, 0,
                            new Particle.DustOptions(PARTICLE_COLOR, 0.5F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.lightbuff.book_name"))
                .addCustomLoreLine(lang().get("spells.lightbuff.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }
}
