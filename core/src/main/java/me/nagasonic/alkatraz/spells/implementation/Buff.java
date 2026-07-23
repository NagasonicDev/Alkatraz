package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.lang.LangManager;
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
 * Buff — Applies configured modifiers to the caster, or to another player while sneaking.
 */
public class Buff extends Spell {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private static final String BUFF_GROUP_ID = "configure_buffs";
    private static final Color PARTICLE_COLOR = Color.fromRGB(0, 191, 255);

    private int baseDuration;
    private int targetRange;

    private final Map<UUID, List<AppliedModifier>> activeModifiers = new ConcurrentHashMap<>();

    public Buff(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/buff_options.yml");
        Alkatraz.getInstance().save("spells/buff.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/buff.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.baseDuration = spellConfig.getInt("base_duration", 30);
        this.targetRange = spellConfig.getInt("target_range", 20);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        int range = (int) getModifiedStat(caster, "target_range", targetRange);
        LivingEntity target = PooledModifierSpellSupport.resolveBuffTarget(caster, range);


        if (target == null) {
            Utils.sendActionBar(caster, lang().get("spells.buff.no_target"));
            return;
        }

        int duration = (int) getModifiedStat(caster, "duration", baseDuration);
        boolean onOther = target instanceof Player p && !p.getUniqueId().equals(caster.getUniqueId());

        String casterMsg = onOther
                ? lang().get("spells.buff.applied_to_target", "%target%", target.getName(), "%duration%", String.valueOf(duration))
                : lang().get("spells.buff.active_self", "%duration%", String.valueOf(duration));
        String targetMsg = lang().get("spells.buff.target_buffed", "%duration%", String.valueOf(duration));

        PooledModifierSpellSupport.applyConfiguredModifiers(
                caster, target, this, BUFF_GROUP_ID, duration, activeModifiers,
                PARTICLE_COLOR, casterMsg, targetMsg, lang().get("spells.buff.expired"));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        int durationTicks = baseDuration * 20;
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 1, false, false));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, 0, false, false));
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
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
                .setDisplayName(lang().get("spells.buff.book_name"))
                .addCustomLoreLine(lang().get("spells.buff.lore1"))
                .addCustomLoreLine(lang().get("spells.buff.lore2"))
                .addCustomLoreLine(lang().get("spells.buff.lore3"))
                .addCustomLoreLine("")
                .build();
    }
}
