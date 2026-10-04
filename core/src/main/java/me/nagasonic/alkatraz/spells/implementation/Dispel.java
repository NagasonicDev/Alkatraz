package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.effect.ActiveEffect;
import me.nagasonic.alkatraz.spells.effect.DispelBands;
import me.nagasonic.alkatraz.spells.effect.EffectApplicator;
import me.nagasonic.alkatraz.spells.effect.EffectKind;
import me.nagasonic.alkatraz.spells.effect.EffectRegistry;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifier;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dispel — LIGHT, circle 3, UTILITY. The roster's anti-magic tool, and the only
 * spell that removes rather than applies.
 *
 * <p><b>It deals no damage at all.</b> Every burst is particles: a bright gold
 * flash when an effect goes, a dull grey pulse when one holds firm. That is
 * what lets it declare {@code role: UTILITY} and carry no {@code power:} key —
 * a magnitude on a spell with no budget axis is a value nothing would check.
 *
 * <p><b>One target per cast</b>, and that is a balance decision rather than a
 * performance one: it bounds the blocking {@code Skin.fromURL} HTTP call inside
 * {@link Disguise#dispel(Player)} to exactly one request per cast, matching the
 * risk the disguise apply path already carries.
 *
 * <p><b>A crosshair target wins even if it carries nothing dispellable.</b> A
 * wasted cast is a real cost, and it is what stops a player spraying dispel at
 * empty space to trigger a free self-cleanse. Only when the crosshair finds
 * nothing does the target become the caster.
 *
 * <p>Candidate set, in resolution order: every non-concealment registry entry,
 * then up to {@code vanilla_potion_limit} vanilla potion effects highest
 * amplifier first, then concealment if {@code illusion_reset}. Each is resolved
 * by {@link DispelBands}; success reverses it and emits a burst, failure emits
 * a grey pulse. Both counts report through one action-bar line.
 */
public class Dispel extends Spell {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /** The gold flash a stripped effect gets. */
    private static final Color SUCCESS_COLOR = Color.fromRGB(255, 236, 140);

    /** The dull grey pulse an effect that held firm gets. */
    private static final Color RESIST_COLOR = Color.fromRGB(90, 90, 90);

    private static final Particle.DustOptions SUCCESS_DUST = new Particle.DustOptions(SUCCESS_COLOR, 1.0f);
    private static final Particle.DustOptions RESIST_DUST = new Particle.DustOptions(RESIST_COLOR, 1.0f);

    /** How high the cast circle is drawn. Matches the shared default. */
    private static final double CIRCLE_SIZE = 3.0;

    private double baseTargetRange;
    private int vanillaPotionLimit;
    private boolean illusionReset;

    public Dispel(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/dispel.yml");
        Alkatraz.getInstance().saveConfig("spells/dispel_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/dispel.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();

        this.baseTargetRange = spellConfig.getDouble("target_range", 24.0);
        this.vanillaPotionLimit = spellConfig.getInt("vanilla_potion_limit", 4);
        this.illusionReset = spellConfig.getBoolean("illusion_reset", true);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        LivingEntity target = resolveTarget(caster);

        MagicProfile profile = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);

        // The circle is the base and the option system contributes the single
        // mastery band on top. Spell.getModifiedStat sums ADD, so with the adept
        // tier's only impact being ADD 1.0 this is identical to
        // getModifiedStat(caster, "dispel_bands", circle) - spelled out this way so
        // the arithmetic in DispelBands stays unit-testable.
        double bands = DispelBands.dispelBands(profile.getCircleLevel(), 0)
                + getModifiedStat(caster, "dispel_bands", 0.0);
        boolean selfCast = DispelBands.isSelfCast(caster.getUniqueId(), target.getUniqueId());

        int cleared = 0;
        int resisted = 0;
        // Potion types already resolved by the registry pass. The vanilla pass
        // must not offer them a second time at the weaker 1 + amplifier band, or a
        // circle-2 effect a circle-1 dispel just failed to strip would come back
        // through the fallback and succeed.
        Set<PotionEffectType> contested = new HashSet<>();

        // ---- 1. Registered Alkatraz effects, at their stamped circle ---------
        for (ActiveEffect effect : EffectRegistry.effectsOn(target.getUniqueId())) {
            if (effect.kind() == EffectKind.CONCEALMENT) continue;
            if (!DispelBands.wins(selfCast, bands, DispelBands.stampedBands(effect.sourceCircle()))) {
                resisted++;
                spawnBurst(target, false);
                continue;
            }
            if (!reverse(effect, target)) {
                resisted++;
                spawnBurst(target, false);
                continue;
            }
            if (effect.kind() == EffectKind.POTION && effect.handle() instanceof PotionEffectType type) {
                contested.add(type);
            }
            EffectRegistry.remove(effect);
            cleared++;
            spawnBurst(target, true);
        }

        // ---- 2. Vanilla potions, capped, highest amplifier first -------------
        int limit = Math.max(0, vanillaPotionLimit);
        List<PotionEffect> potions = new ArrayList<>(target.getActivePotionEffects());
        potions.removeIf(potion -> contested.contains(potion.getType()));
        potions.sort(Comparator.comparingInt(PotionEffect::getAmplifier).reversed());
        for (int i = 0; i < Math.min(limit, potions.size()); i++) {
            PotionEffect potion = potions.get(i);
            if (!DispelBands.wins(selfCast, bands, DispelBands.potionBands(potion.getAmplifier()))) {
                resisted++;
                spawnBurst(target, false);
                continue;
            }
            target.removePotionEffect(potion.getType());
            cleared++;
            spawnBurst(target, true);
        }

        // ---- 3. Concealment, if illusion_reset ------------------------------
        if (illusionReset) {
            for (ActiveEffect effect : EffectRegistry.effectsOn(target.getUniqueId())) {
                if (effect.kind() != EffectKind.CONCEALMENT) continue;
                if (!reverse(effect, target)) {
                    resisted++;
                    spawnBurst(target, false);
                    continue;
                }
                EffectRegistry.remove(effect);
                cleared++;
                spawnBurst(target, true);
            }
        }

        report(caster, cleared, resisted);
    }

    /**
     * The single target for this cast.
     *
     * <p><b>A crosshair target wins even if it carries nothing dispellable.</b>
     * That is deliberate: it makes a wasted cast a real cost, and it is what
     * stops a player aiming at empty sky to trigger a free self-cleanse. Only
     * when the crosshair finds nothing does the target become the caster.
     *
     * @param caster the caster
     * @return the aimed-at living entity, or the caster when nothing was aimed at
     */
    private LivingEntity resolveTarget(Player caster) {
        int range = (int) getModifiedStat(caster, "target_range", baseTargetRange);
        Entity aimed = Utils.getTargetEntity(caster, range);
        if (aimed instanceof LivingEntity living && living.isValid() && !living.isDead()) {
            return living;
        }
        return caster;
    }

    /**
     * Undoes one stamped effect. Returns false if the reversal refused, which
     * counts as a resist rather than a silent success.
     *
     * <p>Modifier kinds hand the undo back to the modifier that recorded it,
     * because a {@code StatAppliedModifier} needs the originating spell to know
     * which profile stat to clear and that spell is identified by the id stamped
     * on the effect. Concealment hands it to the owning illusion, which knows
     * its own teardown.
     *
     * <p>{@code Spell} declares neither {@code dispel} nor {@code cancelCast},
     * so the reversal dispatches on the downcast instead of on a base-class hook.
     *
     * @param effect the stamped effect
     * @param target the entity carrying it
     * @return true if the effect was actually removed
     */
    private boolean reverse(ActiveEffect effect, LivingEntity target) {
        if (effect.handle() instanceof PotionEffectType type) {
            target.removePotionEffect(type);
            return true;
        }
        if (effect.handle() instanceof AppliedModifier modifier) {
            Spell source = SpellRegistry.getSpell(effect.sourceSpellId());
            if (source == null) return false;
            modifier.remove(target, source);
            return true;
        }
        if (EffectApplicator.isStealthHandle(effect.handle())) {
            Spell source = SpellRegistry.getSpell(effect.sourceSpellId());
            if (!(source instanceof Stealth stealth) || !(target instanceof Player victim)) return false;
            stealth.dispel(victim);
            return true;
        }
        if (EffectApplicator.isDisguiseHandle(effect.handle())) {
            Spell source = SpellRegistry.getSpell(effect.sourceSpellId());
            if (!(source instanceof Disguise disguise) || !(target instanceof Player victim)) return false;
            // false means the illusion resisted: a blank or unparseable stored id,
            // or the Mojang skin fetch threw. Either way the profile is untouched.
            return disguise.dispel(victim);
        }
        return false;
    }

    /**
     * Both counts on one action-bar line. A cast that found nothing at all gets
     * its own message rather than "0 effect(s)".
     *
     * <p>One {@code sendActionBar} call, not two: the action bar is a single
     * slot, so a second call would replace the first and a cast that cleared
     * and resisted at the same time would report only the resist.
     *
     * @param caster   the caster
     * @param cleared  effects actually stripped
     * @param resisted effects that held firm
     */
    private void report(Player caster, int cleared, int resisted) {
        if (cleared == 0 && resisted == 0) {
            Utils.sendActionBar(caster, ColorFormat.format(lang().get("spells.dispel.nothing")));
            return;
        }
        List<String> parts = new ArrayList<>(2);
        if (cleared > 0) {
            parts.add(ColorFormat.format(lang().get("spells.dispel.cleared",
                    "cleared", String.valueOf(cleared))));
        }
        if (resisted > 0) {
            parts.add(ColorFormat.format(lang().get("spells.dispel.resisted",
                    "resisted", String.valueOf(resisted))));
        }
        Utils.sendActionBar(caster, String.join("  ", parts));
    }

    /**
     * The feedback burst. Particles only - this spell deals no damage, ever.
     *
     * @param target  the entity the effect was on
     * @param success true for a gold flash, false for a dull grey pulse
     */
    private void spawnBurst(LivingEntity target, boolean success) {
        Location at = target.getLocation().add(0, 1, 0);
        ParticleCaster.spawn(target, at.getWorld(), Utils.DUST, at, 1, success ? SUCCESS_DUST : RESIST_DUST);
        ParticleCaster.spawn(target, at.getWorld(),
                success ? Utils.END_ROD : Utils.SMOKE, at, 24, 0.4, 0.6, 0.4, 0.02);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Not implemented for mobs. Both concealment reversals are player-shaped
        // (Stealth needs a profile flag and a task id, Disguise needs a player
        // inventory and a sessionserver round trip), so there is no mob path worth
        // half-writing.
    }

    @Override
    public boolean canMobCast(Mob mob) {
        return false;
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, CIRCLE_SIZE, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.dispel.book_name"))
                .addCustomLoreLine(lang().get("spells.dispel.lore1"))
                .addCustomLoreLine(lang().get("spells.dispel.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }
}