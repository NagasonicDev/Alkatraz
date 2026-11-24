package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Circle 5 AIR UTILITY: the caster lands like a feather and drifts further while falling.
 * A movement task rather than elytra, so nothing is left behind if the caster dies or leaves.
 */
public class SkyglassGlide extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.AIR);
    /** Blocks per tick a gliding caster is topped up to, before the option multiplier. */
    private static final double GLIDE_SPEED = 0.35;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private static final Map<UUID, BukkitTask> GLIDE = new HashMap<>();

    public SkyglassGlide(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/skyglass_glide_options.yml");
        Alkatraz.getInstance().save("spells/skyglass_glide.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/skyglass_glide.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int seconds = (int) Math.round(getModifiedStat(p, "glide_duration", option(p, "duration", 18)));
        double boost = getModifiedStat(p, "glide_boost", option(p, "boost", 1.25));
        World world = p.getWorld();

        UUID casterId = p.getUniqueId();
        cancelFor(casterId);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, seconds * 20, 0, true, false, false));
        p.setFallDistance(0f);
        GLIDE.put(casterId, new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || p.getWorld() != world || ticks >= seconds * 20) {
                    cancel();
                    GLIDE.remove(casterId);
                    return;
                }
                ticks += 2;
                p.setFallDistance(0f);
                if (p.isOnGround()) return;
                Vector velocity = p.getVelocity();
                double speed = Math.hypot(velocity.getX(), velocity.getZ());
                double target = GLIDE_SPEED * boost;
                // Top up to the glide speed instead of multiplying, so the boost cannot compound.
                if (speed > 0.02 && speed < target) {
                    double factor = target / speed;
                    p.setVelocity(new Vector(velocity.getX() * factor, velocity.getY(),
                            velocity.getZ() * factor));
                }
                Location at = p.getLocation().add(0, 1, 0);
                ParticleCaster.spawn(p, world, Utils.DUST, at, 1, PALETTE.coreDust());
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 2L));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, true, false, false));
    }

    private void cancelFor(UUID casterId) {
        BukkitTask task = GLIDE.remove(casterId);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void cancelTasks() {
        for (BukkitTask task : GLIDE.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        GLIDE.clear();
    }

    /** Numeric option value, falling back when the option file is absent. */
    private double option(Player p, String id, double fallback) {
        if (getOption(id) == null) return fallback;
        Object value = getOption(id).getSelectedValue(p).getValue();
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.skyglassglide.book_name"))
                .addCustomLoreLine(lang().get("spells.skyglassglide.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
