package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.effect.EffectApplicator;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * LightBuff - radiates a blessing over every player near the caster.
 *
 * <h2>Visuals</h2>
 * Each blessed target gets its own {@link SpellVfx#nova} - an expanding shell centred
 * on that player, not one burst at the caster. That is the point of the spell: the
 * player watching has to be able to see who was actually blessed, and a single ring at
 * the caster could not tell them.
 */
public class LightBuff extends Spell {

    /** Ticks the expanding shell takes to open. */
    private static final int NOVA_TICKS = 8;

    /** Shell radius as a fraction of the buff radius at the first frame, and at the last. */
    private static final double NOVA_START_SCALE = 0.35D;
    private static final double NOVA_END_SCALE = 1.0D;

    /** Shell particle count at the first frame, and the extra per tick. */
    private static final int NOVA_START_POINTS = 24;
    private static final int NOVA_POINTS_PER_TICK = 6;

    /** Height of the shell's centre above the target's feet. */
    private static final double NOVA_CENTRE_HEIGHT = 0.9D;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double buffRadius;
    private int baseDuration;

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
        World clWorld = cl.getWorld();
        double clX = cl.getX();
        double clY = cl.getY();
        double clZ = cl.getZ();
        List<Player> nearbyPlayers = new ArrayList<>();
        for (Player p : caster.getWorld().getPlayers()) {
            Location pl = p.getLocation();
            if (!pl.getWorld().equals(clWorld)) continue;
            if (Math.abs(pl.getX() - clX) > radius) continue;
            if (Math.abs(pl.getY() - clY) > radius) continue;
            if (Math.abs(pl.getZ() - clZ) > radius) continue;
            nearbyPlayers.add(p);
        }

        for (Player target : nearbyPlayers) {
            if (target.isDead() || !target.isValid()) continue;

            EffectApplicator.applyPotion(caster, target, new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, 0, false, true), this);
            EffectApplicator.applyPotion(caster, target, new PotionEffect(PotionEffectType.SPEED, durationTicks, 0, false, true), this);
            EffectApplicator.applyPotion(caster, target, new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, durationTicks, 0, false, true), this);

            target.sendMessage(ChatColor.translateAlternateColorCodes('&', lang().get("spells.lightbuff.blessed_by_target", "caster", caster.getName())));

            spawnBlessingNova(caster, target, radius);
        }

        caster.sendMessage(ChatColor.translateAlternateColorCodes('&',
                lang().get("spells.lightbuff.blessed_count", "count", String.valueOf(nearbyPlayers.size()), "duration", String.valueOf((int) duration))));

        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;


        int durationTicks = baseDuration * 20;

        Location cl2 = caster.getLocation();
        World clWorld2 = cl2.getWorld();
        double clX2 = cl2.getX();
        double clY2 = cl2.getY();
        double clZ2 = cl2.getZ();
        List<Player> nearbyPlayers = new ArrayList<>();
        for (Player p : caster.getWorld().getPlayers()) {
            Location pl = p.getLocation();
            if (!pl.getWorld().equals(clWorld2)) continue;
            if (Math.abs(pl.getX() - clX2) > buffRadius) continue;
            if (Math.abs(pl.getY() - clY2) > buffRadius) continue;
            if (Math.abs(pl.getZ() - clZ2) > buffRadius) continue;
            nearbyPlayers.add(p);
        }

        for (Player target : nearbyPlayers) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, 0, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 0, false, true));
        }

        caster.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, 0, false, false));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 0, false, false));

        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);

        for (Player target : nearbyPlayers) {
            if (target.isDead() || !target.isValid()) continue;
            spawnBlessingNova(caster, target, buffRadius);
        }
    }

    /**
     * One expanding shell over a single blessed target.
     *
     * <p>The shell grows from {@link #NOVA_START_SCALE} of the buff radius to the full
     * radius over {@link #NOVA_TICKS} ticks and gains particles as it goes, so the last
     * frame is the loudest - the same escalating-committed read as the shared wind-up.
     * Re-reads the target's location every tick, so a player who moves out of the aura
     * carries the shell with them rather than being left with a burst in mid-air.
     *
     * @param caster the caster, for per-player density falloff
     * @param target the blessed player
     * @param radius the buff radius, used as the shell's finished size
     */
    private void spawnBlessingNova(LivingEntity caster, Player target, double radius) {
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= NOVA_TICKS || target.isDead() || !target.isValid()) {
                    cancel();
                    return;
                }
                double progress = (elapsed + 1) / (double) NOVA_TICKS;
                double scale = NOVA_START_SCALE + (NOVA_END_SCALE - NOVA_START_SCALE) * progress;
                Location centre = target.getLocation().add(0.0D, NOVA_CENTRE_HEIGHT, 0.0D);
                SpellVfx.nova(LightBuff.this, caster, centre, radius * scale,
                        NOVA_START_POINTS + NOVA_POINTS_PER_TICK * elapsed);
                elapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
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
