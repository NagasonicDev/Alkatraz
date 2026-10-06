package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifier;
import me.nagasonic.alkatraz.spells.modifier.PooledModifierSpellSupport;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buff - applies the caster's configured modifier group to themself, or to another
 * player while sneaking.
 *
 * <h2>Visuals</h2>
 * A {@link SpellVfx#selfAura} lands on the buffed target and nowhere else. This spell
 * used to paint in azure {@code (0, 191, 255)} while being a LIGHT spell whose sibling
 * {@code light_buff} painted in yellow - the effect read as WATER, not LIGHT. Every
 * colour now comes from {@link ElementPalette#of(Element)}, so the aura, the cast
 * circle and the pooled modifier particles are all the same LIGHT palette.
 */
public class Buff extends Spell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `LIGHT` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(255, 255, 135);

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private static final String BUFF_GROUP_ID = "configure_buffs";

    /** Radius of the aura's outer ring, in blocks. */
    private static final double AURA_RADIUS = 1.4D;

    /** Particles per aura ring. */
    private static final int AURA_POINTS = 18;

    /** Ticks the aura holds on the buffed target before it fades. */
    private static final int AURA_TICKS = 20;

    /** Radians the aura's outer ring turns per tick. */
    private static final double AURA_SPIN = 0.22D;

    /** Radius a mob buff target is searched for within. */
    private static final double MOB_TARGET_RANGE = 10.0D;

    /** Fraction of max health a mob must be below before a caster mob will buff it. */
    private static final double MOB_TARGET_HEALTH_FRACTION = 0.7D;

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
                ? lang().get("spells.buff.applied_to_target", "target", target.getName(), "duration", String.valueOf(duration))
                : lang().get("spells.buff.active_self", "duration", String.valueOf(duration));
        String targetMsg = lang().get("spells.buff.target_buffed", "duration", String.valueOf(duration));

        PooledModifierSpellSupport.applyConfiguredModifiers(
                caster, target, this, BUFF_GROUP_ID, duration, activeModifiers,
                ElementPalette.of(Element.LIGHT).core(), casterMsg, targetMsg, lang().get("spells.buff.expired"));

        // Only the buffed target is lit: a self buff draws on the caster, a sneak buff
        // on the other player, and the caster who buffed someone else is left unlit so
        // the aura always reads as "this entity carries the buff".
        spawnBuffAura(caster, target);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        int durationTicks = baseDuration * 20;
        LivingEntity target = findBuffTarget(caster);
        if (target == null) target = caster;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 1, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, 0, false, false));
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        spawnBuffAura(caster, target);
    }

    private LivingEntity findBuffTarget(Mob caster) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : caster.getNearbyEntities(MOB_TARGET_RANGE, MOB_TARGET_RANGE, MOB_TARGET_RANGE)) {
            if (!(e instanceof Mob m)) continue;
            if (m.equals(caster)) continue;
            if (!m.isValid() || m.isDead()) continue;
            if (m.getHealth() >= m.getMaxHealth() * MOB_TARGET_HEALTH_FRACTION) continue;
            if (!(m instanceof Monster)) continue;
            double dist = m.getLocation().distanceSquared(caster.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = m;
            }
        }
        return best;
    }

    /**
     * Holds a counter-rotating aura on the buffed entity for {@link #AURA_TICKS} ticks.
     *
     * <p>{@link SpellVfx#selfAura} reads the subject's location at draw time, so this is
     * a per-tick call and the aura follows a moving target without any bookkeeping here.
     *
     * @param caster  the caster, for per-player density falloff
     * @param subject the entity carrying the buff
     */
    private void spawnBuffAura(LivingEntity caster, LivingEntity subject) {
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= AURA_TICKS || subject.isDead() || !subject.isValid()) {
                    cancel();
                    return;
                }
                SpellVfx.selfAura(CAST_COLOR, caster, subject, AURA_RADIUS, AURA_POINTS,
                        AURA_SPIN * (elapsed + 1));
                elapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 2L);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.buff.book_name"))
                .addLoreLine(lang().get("spells.buff.lore1"))
                .addLoreLine(lang().get("spells.buff.lore2"))
                .addLoreLine(lang().get("spells.buff.lore3"))
                .addCustomLoreLine("")
                .build();
    }
}
