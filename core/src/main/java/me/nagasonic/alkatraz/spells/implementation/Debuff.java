package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.CastEvent;
import me.nagasonic.alkatraz.events.PlayerCastEvent;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifier;
import me.nagasonic.alkatraz.spells.modifier.PooledModifierSpellSupport;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debuff — Applies configured negative modifiers to a targeted living entity.
 */
public class Debuff extends Spell {

    private static final String DEBUFF_GROUP_ID = "configure_debuffs";
    private static final Color PARTICLE_COLOR = Color.fromRGB(128, 0, 128);

    private int baseDuration;
    private int targetRange;

    private final Map<UUID, List<AppliedModifier>> activeModifiers = new ConcurrentHashMap<>();

    public Debuff(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/debuff_options.yml");
        Alkatraz.getInstance().save("spells/debuff.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/debuff.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.baseDuration = spellConfig.getInt("base_duration", 20);
        this.targetRange = spellConfig.getInt("target_range", 24);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        PlayerCastEvent castEvent = new PlayerCastEvent(caster, this, null, wand);
        Bukkit.getPluginManager().callEvent(castEvent);
        int range = (int) getModifiedStat(caster, "target_range", targetRange);
        LivingEntity target = PooledModifierSpellSupport.resolveDebuffTarget(caster, range);

        if (target == null) {
            Utils.sendActionBar(caster, "&cNo valid target in sight.");
            return;
        }

        int duration = (int) getModifiedStat(caster, "duration", baseDuration);
        String targetName = target instanceof Player p ? p.getName() : target.getType().name();

        String casterMsg = "&5Debuffs applied to &f" + targetName + " &5for &f" + duration + "&5 seconds!";
        String targetMsg = target instanceof Player
                ? "&5You have been debuffed for &f" + duration + "&5 seconds!"
                : "";

        PooledModifierSpellSupport.applyConfiguredModifiers(
                caster, target, this, DEBUFF_GROUP_ID, duration, activeModifiers,
                PARTICLE_COLOR, casterMsg, targetMsg, "&7The debuff on you has faded.");
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        LivingEntity target = PooledModifierSpellSupport.resolveMobDebuffTarget(caster, 16);
        CastEvent castEvent = new CastEvent(caster, this, null, wand);
        Bukkit.getPluginManager().callEvent(castEvent);
        if (target == null) return;

        int durationTicks = baseDuration * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, durationTicks, 1, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 0, false, true));
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.7f);
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

            for (Location point : points) {
                point.getWorld().spawnParticle(Utils.DUST, point, 0,
                        new Particle.DustOptions(PARTICLE_COLOR, 0.5F));
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&5Tome of Enfeeblement")
                .addCustomLoreLine("&8&oCurse those who stand against you.")
                .addCustomLoreLine("&7Look at a target to apply your")
                .addCustomLoreLine("&7configured debuffs.")
                .addCustomLoreLine("")
                .build();
    }
}
