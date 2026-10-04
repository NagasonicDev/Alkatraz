package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifier;
import me.nagasonic.alkatraz.spells.modifier.PooledModifierSpellSupport;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debuff — Applies configured negative modifiers to a targeted living entity.
 */
public class Debuff extends Spell {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private static final String DEBUFF_GROUP_ID = "configure_debuffs";

    /**
     * The DARK palette. Debuff's hardcoded {@code Color.fromRGB(128, 0, 128)} purple happened to
     * look dark-ish but was a second, independent colour definition; it is now the palette's
     * {@code accent} shade, so the cast circle, the impact burst and the modifier particles are
     * all guaranteed to be the same DARK purple.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.DARK);

    /** Colour handed to the shared modifier machinery, so its particles match the palette. */
    private static final Color PARTICLE_COLOR = PALETTE.accent();

    /** Outer radius of the aura drawn on the debuffed target when the cast lands, in blocks. */
    private static final double TARGET_AURA_RADIUS = 0.9;
    /** Points per ring of that aura. */
    private static final int TARGET_AURA_POINTS = 16;

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

        int range = (int) getModifiedStat(caster, "target_range", targetRange);
        LivingEntity target = PooledModifierSpellSupport.resolveDebuffTarget(caster, range);

        if (target == null) {
            Utils.sendActionBar(caster, lang().get("spells.debuff.no_target"));
            return;
        }

        int duration = (int) getModifiedStat(caster, "duration", baseDuration);
        String targetName = target instanceof Player p ? p.getName() : target.getType().name();

        String casterMsg = lang().get("spells.debuff.applied", "target", targetName, "duration", String.valueOf(duration));
        String targetMsg = target instanceof Player
                ? lang().get("spells.debuff.target_debuffed", "duration", String.valueOf(duration))
                : "";

        PooledModifierSpellSupport.applyConfiguredModifiers(
                caster, target, this, DEBUFF_GROUP_ID, duration, activeModifiers,
                PARTICLE_COLOR, casterMsg, targetMsg, lang().get("spells.debuff.faded"));

        // The mark on the victim. SpellVfx.selfAura reads the subject's location at draw time, so
        // this is anchored to wherever the target is right now rather than to a cached point.
        SpellVfx.selfAura(this, caster, target, TARGET_AURA_RADIUS, TARGET_AURA_POINTS, 0.0);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        LivingEntity target = PooledModifierSpellSupport.resolveMobDebuffTarget(caster, 16);
        if (target == null) return;

        int durationTicks = baseDuration * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, durationTicks, 1, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 0, false, true));
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.7f);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.debuff.book_name"))
                .addCustomLoreLine(lang().get("spells.debuff.lore1"))
                .addCustomLoreLine(lang().get("spells.debuff.lore2"))
                .addCustomLoreLine(lang().get("spells.debuff.lore3"))
                .addCustomLoreLine("")
                .build();
    }
}
