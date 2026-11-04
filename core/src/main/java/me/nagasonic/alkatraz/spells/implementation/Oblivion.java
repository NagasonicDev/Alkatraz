package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.effect.EffectApplicator;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.SustainEffect;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The circle-9 capstone, and dark's <b>sustain</b>. Not an erasure - a hole. Cast it and a
 * black chasm opens where you are looking; cast it again and it closes. While it is open it
 * costs mana every second, drags everything nearby into itself, eats hostile projectiles and
 * opens once every six seconds for {@code duration_seconds}, or for as long as the mana lasts
 * if that runs out first.
 *
 * <h2>What was deleted, and why that mattered</h2>
 * The old Oblivion scaled its one hit off the target's <b>missing</b> health, so a full-health
 * boss took the least damage it could and the spell read as weak exactly where it was pointed.
 * {@code missing_health_multiplier} and {@code burst_damage_cap} are gone, and with them the
 * whole {@link me.nagasonic.alkatraz.spells.util.SpellBudgets#burstBudget} reading of this
 * spell. The budget is now {@code power 28.0 x hit_count 10 = 280.0 = sustainBudget(9)}, and
 * the ten openings are the ten the yml declares, one every {@code damage_interval_ticks}.
 *
 * <h2>Damage contract</h2>
 * ONE {@link SpellAttackContext} lives for the whole sustain, so a single counter ends it. It
 * is {@code tick()}ed once per body tick, which is what makes {@code ctx.power(target)} legal
 * again at the next opening - calling it twice in one tick throws. Damage goes through
 * {@link SpellDamageUtil#damageWithSpell} with the number {@code ctx.power} returned, and that
 * same post-affinity number is what the vampirism sums. It is never scaled a second time.
 *
 * <h2>Teardown</h2>
 * {@link SustainEffect} runs it exactly once on every end path - a second cast, no mana, death,
 * quit, world change, disable - and {@code caster} is null on the quit path, so the BossBar
 * comes down by handle and the curse comes off the victims by UUID rather than by reference.
 */
public class Oblivion extends AttackSpell {

    /**
     * This class's identity on screen: crimson {@code RGB(140, 0, 60)}, unmistakable
     * next to {@code umbral_volley}'s violet and {@code void_anchor}'s near-black.
     */
    private static final Color OBLIVION_CRIMSON = Color.fromRGB(140, 0, 60);
    private static final Particle.DustOptions OBLIVION_DUST =
            new Particle.DustOptions(OBLIVION_CRIMSON, 1.0F);

    /** Shell points on the implosion that opens the hole, and on the collapse that closes it. */
    private static final int NOVA_POINTS = 32;
    /** Motes spiralling inward every tick while the hole is open. */
    private static final int ORBIT_BODIES = 8;
    /** The hole's pull, as a velocity multiplier. Applied inward - the old erasure pushed out. */
    private static final double CHASM_PULL = 0.45;

    private OblivionConfig config;
    private double durationSeconds;

    public Oblivion(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/oblivion.yml");
        Alkatraz.getInstance().saveConfig("spells/oblivion_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/oblivion.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = OblivionConfig.fromConfig(spellConfig.getValues(false));
        this.durationSeconds = spellConfig.getDouble("duration_seconds", 24.0);
    }

    /**
     * The hole's whole life, in ticks. Enforced by {@link Chasm#tick}, so the yml's
     * {@code power x hit_count} is what the spell actually spends rather than a ceiling the mana
     * pool happens to stop at: 9400 mana against 75/s upkeep and 15/s regen is 156.7s of
     * upkeep, which is 26 openings x 28.0 = 728.0, 2.6x the circle-9 SUSTAIN budget.
     */
    private int lifetimeTicks() {
        return Math.max(1, (int) Math.round(durationSeconds * 20.0));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        OblivionConfig cfg = config;
        Chasm chasm = new Chasm(caster, wand,
                Math.max(1, (int) getModifiedStat(caster, "target_range",
                        optionInt(caster, "target_range", cfg.targetRange()))),
                Math.max(1, (int) getModifiedStat(caster, "upkeep_mana_per_second",
                        cfg.upkeepManaPerSecond())),
                Math.max(1, (int) getModifiedStat(caster, "debuff_duration",
                        optionInt(caster, "debuff_duration", cfg.debuffSeconds()))));

        // The toggle runs first, and owns the lifetime. OPENED installed the hole. CLOSED means
        // this cast ended the one it owns - the teardown has already run and there is nothing
        // left to install. BLOCKED means a different circle-9 sustain owns the single slot:
        // nothing was stopped and nothing changed, so this cast must not refund, and must not
        // touch the running instance.
        SustainEffect.Toggle toggle = SustainEffect.toggle(caster, chasm.upkeep, this,
                chasm::tick, chasm::teardown);
        if (toggle == SustainEffect.Toggle.BLOCKED) {
            // Blocked changed nothing, so Spell.cast must refund this cast and skip cooldown/mastery.
            cancelCast(caster);
            Utils.sendActionBar(caster, lang().get("spells.sustain.blocked"));
            return;
        }
        if (toggle == SustainEffect.Toggle.CLOSED) {
            // getModifiedManaCost, not getCost: Spell.cast charged the modified figure, so a
            // caster with a mana_cost option refunds the base 600 and keeps the difference.
            StatUtils.addMana(caster, getModifiedManaCost(caster)); //Give cost to reverse canceling
            SpellVfx.nova(OBLIVION_CRIMSON, caster, caster.getEyeLocation(), 1.2, NOVA_POINTS / 2);
            caster.getEyeLocation().getWorld().playSound(caster.getEyeLocation(),
                    Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.8f);
            return;
        }

        SpellVfx.nova(OBLIVION_CRIMSON, caster, chasm.opening, cfg.chasmRadius() * 2.0, NOVA_POINTS);
        chasm.opening.getWorld().playSound(chasm.opening, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.4f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        LivingEntity target = caster.getTarget();
        if (target.equals(caster) || target.isDead()) return;

        // A mob gets one opening and no field: a 60-second drain from a magic mob is not a
        // thing anything should survive. Defaults only - a mob is never given an option.
        SpellAttackContext ctx = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);
        ctx.tick();
        ctx.hit(target);
        SpellDamageUtil.damageWithSpell(target, ctx.power(target), caster, wand, this);
        curse(caster, target, config.debuffSeconds());
        SpellVfx.nova(OBLIVION_CRIMSON, caster, target.getLocation(),
                config.chasmRadius() * 2.0, NOVA_POINTS);
    }

    /**
     * One living hole: the context, the victims it has cursed and the bar it owns. Created per
     * cast and reachable only through the two callbacks handed to {@link SustainEffect}, so
     * there is nothing to register and nothing to unregister - the toggle owns the lifetime.
     */
    private final class Chasm {

        private final Player caster;
        private final ItemStack wand;
        private final int upkeep;
        private final int curseSeconds;
        private final Location opening;
        private final SpellAttackContext ctx;
        private final Set<UUID> cursed = new HashSet<>();

        /** Built on the first tick, not in the constructor: a toggle-off cast must not make one. */
        private BossBar bar;

        Chasm(Player caster, ItemStack wand, int range, int upkeep, int curseSeconds) {
            this.caster = caster;
            this.wand = wand;
            this.upkeep = upkeep;
            this.curseSeconds = curseSeconds;
            this.opening = Utils.resolveTarget(caster, range);
            this.ctx = SpellAttackContext.of(Oblivion.this, caster, wand, AttackType.MAGIC);
        }

        /**
         * Every tick while the hole is open. Ends on its own at {@link Oblivion#lifetimeTicks()},
         * so the yml's {@code hit_count} is the number of openings it really gets; otherwise it
         * ends through {@link SustainEffect} alone, on a spent cast, or when the mana runs out.
         */
        boolean tick(Player p, int elapsedTicks) {
            ctx.tick();
            if (ctx.isSpent()) return false;
            if (bar == null) bar = openBar();
            updateBar();

            double radius = config.chasmRadius();
            if (elapsedTicks == 0) {
                // The opening IS the first of the ten openings hit_count declares. The nova
                // that draws it was already spawned by castAction, one tick before this.
                openingDamage(p, opening, radius);
            } else {
                // Everything after the first tick the hole follows the caster.
                Location centre = p.getEyeLocation();
                devourProjectiles(p, centre, radius);
                SpellVfx.orbit(OBLIVION_CRIMSON, p, centre, radius * 0.2, radius,
                        ORBIT_BODIES, elapsedTicks * 0.35);
                if (elapsedTicks % Math.max(1, config.damageIntervalTicks()) == 0) {
                    vampirism(p, openingDamage(p, centre, radius));
                }
            }
            return elapsedTicks + 1 < lifetimeTicks();
        }

        /**
         * The single exit path, run by {@link SustainEffect} exactly once whatever ended it.
         *
         * <p>The BossBar comes down by handle and the curse comes off its victims by UUID, so
         * both survive a {@code caster} that is null because the player has already quit.
         */
        void teardown(Player p) {
            if (bar != null) {
                bar.removeAll();
                bar = null;
            }
            for (UUID id : cursed) {
                Player victim = Bukkit.getPlayer(id);
                if (victim == null) continue;
                victim.removePotionEffect(PotionEffectType.WEAKNESS);
                victim.removePotionEffect(PotionEffectType.BLINDNESS);
                victim.removePotionEffect(PotionEffectType.WITHER);
            }
            cursed.clear();
            if (p == null || p.isDead()) return;
            SpellVfx.nova(OBLIVION_CRIMSON, p, p.getEyeLocation(), 1.0, NOVA_POINTS / 3);
        }

        /**
         * One damage opening: drag everything in, hit every one of them once, curse it, and
         * report what the hole actually dealt so the caller can decide what to do with it.
         *
         * <p>Every entity in the radius is visited exactly once per opening, so {@code
         * ctx.power} is called once per target with no dedupe set in the way - it throws if
         * called twice for the same target in one tick, and {@link #ctx} was just ticked.
         *
         * @return the sum of the post-affinity numbers handed to {@link SpellDamageUtil}
         */
        private double openingDamage(LivingEntity source, Location centre, double radius) {
            double reach = radius * 2.0;
            double dealt = 0.0;
            for (LivingEntity le : Utils.getNearbyLivingEntities(centre, reach, reach, reach)) {
                if (le.equals(source) || le.isDead() || !le.isValid()) continue;

                // Inward, always: the old erasure shoved its victim away from the caster.
                Vector inward = Utils.safeNormalize(
                        centre.toVector().subtract(le.getLocation().toVector()).setY(0.0));
                if (inward != null) {
                    le.setVelocity(le.getVelocity().add(inward.multiply(CHASM_PULL)));
                }

                // Registers the cast against the shared properties, so one counter ends the
                // hole. False from the second opening on, which is the point - the hole
                // re-hits by design and the per-tick guard is SpellAttackContext.tick().
                ctx.hit(le);

                // Region gate checked BEFORE the damage, or a protected target would still feed
                // the caster's health for damage that never landed.
                if (!Protection.damage(le, source, le.getLocation())) continue;

                double damage = ctx.power(le);
                SpellDamageUtil.damageWithSpell(le, damage, source, wand, Oblivion.this);
                dealt += damage;
                curse(source, le, curseSeconds);
                cursed.add(le.getUniqueId());
            }
            return dealt;
        }

        private BossBar openBar() {
            BossBar created = Bukkit.createBossBar("", BarColor.RED, BarStyle.SEGMENTED_10);
            created.addPlayer(caster);
            created.setProgress(1.0);
            return created;
        }

        /** How many seconds of upkeep the caster's remaining pool is still worth. */
        private void updateBar() {
            MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
            double pool = profile == null ? 0.0 : profile.getMana();
            bar.setTitle(lang().get("spells.oblivion.sustain",
                    "seconds", String.valueOf((int) Math.floor(pool / upkeep)),
                    "upkeep", String.valueOf(upkeep)));
            double max = profile == null ? 0.0 : profile.getMaxMana();
            bar.setProgress(max <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, pool / max)));
        }
    }

    /** A quarter of every point the hole dealt comes back to the caster, already clamped. */
    private void vampirism(LivingEntity caster, double dealt) {
        if (dealt <= 0.0) return;
        caster.setHealth(Math.min(caster.getMaxHealth(),
                caster.getHealth() + dealt * config.vampirismRatio()));
    }

    /**
     * Hostile projectiles inside the radius are dragged to the centre and eaten. The caster's
     * own shots are spared - the hole should not eat the arrows it is being aimed with.
     */
    private void devourProjectiles(LivingEntity caster, Location centre, double radius) {
        for (Entity entity : centre.getWorld().getNearbyEntities(centre, radius, radius, radius)) {
            if (!(entity instanceof Projectile shot)) continue;
            if (shot.getShooter() instanceof LivingEntity owner && owner.equals(caster)) continue;
            Vector inward = Utils.safeNormalize(
                    centre.toVector().subtract(shot.getLocation().toVector()));
            if (inward != null) shot.setVelocity(inward.multiply(CHASM_PULL * 2.0));
            shot.remove();
        }
    }

    /**
     * Dark's Curse pillar. Re-applied at every opening through {@link EffectApplicator}, not
     * {@code addPotionEffect} directly, or Dispel cannot see any of it.
     */
    private void curse(LivingEntity caster, LivingEntity target, int seconds) {
        int ticks = Math.max(1, seconds) * 20;
        EffectApplicator.applyPotion(caster, target, new PotionEffect(PotionEffectType.WITHER,
                ticks, config.debuffWitherAmplifier(), false, false), this);
        EffectApplicator.applyPotion(caster, target, new PotionEffect(PotionEffectType.WEAKNESS,
                ticks, config.debuffWeaknessAmplifier(), false, false), this);
        EffectApplicator.applyPotion(caster, target, new PotionEffect(PotionEffectType.BLINDNESS,
                ticks, config.debuffBlindnessAmplifier(), false, false), this);
    }

    /** The selected value of a player option, or {@code fallback} when it is absent. */
    private int optionInt(Player caster, String optionId, int fallback) {
        SpellOption option = getOption(optionId);
        if (option == null) return fallback;
        OptionValue<?> selected = option.getSelectedValue(caster);
        if (selected == null) return fallback;
        Object value = selected.getValue();
        return value instanceof Number n ? n.intValue() : fallback;
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.DUST, location, 30, OBLIVION_DUST);
        // ENTITY_ENDERMAN_HURT is the one constant in the 1.19 Sound enum that reads as a
        // struck tentacle. It was previously spelled ENTITY_ENDOK_HURT, which is not a Bukkit
        // constant at all; Spell.playSound swallows a bad yml name but a bad Java constant
        // does not compile.
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_HURT, 1.0f, 0.4f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.DUST, location, 60, OBLIVION_DUST);
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.oblivion.book_name"))
                .addCustomLoreLine(lang().get("spells.oblivion.lore1"))
                .addCustomLoreLine(lang().get("spells.oblivion.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
