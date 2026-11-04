package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.types.properties.implementation.BarrierProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.SustainEffect;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Circle 9 LIGHT, the element's SUSTAIN capstone: a crown of light that hovers the caster
 * {@code hover_altitude} above the ground and sears the armour off everything standing under
 * it for as long as the mana holds, and never longer than {@code duration_seconds}. Cast it, it
 * drains; cast it again, it lets go.
 *
 * <p><b>No blocks are ever placed, changed or restored.</b> A crown of light is not terrain and
 * light never reshapes land. There is no {@code Block.setType}, no {@code FallingBlock}, no
 * {@code Protection.blockEdit} and no {@code SpellBlockComponent} anywhere in this class, and
 * the yml carries no {@code terrain_budget} key.
 *
 * <h2>Damage contract</h2>
 * One {@link SpellAttackContext} for the whole sustain. {@code AttackProperties}' hit set is
 * never cleared for the life of a cast, so {@code context.hit(target)} would admit the FIRST
 * opening and refuse the other twenty-nine; the opening clock is the {@code elapsedTicks %
 * damageIntervalTicks} test below and the hit is recorded with {@code properties.hit(target)} so
 * a single counter still kills the whole crown. {@code context.power(target)} is called exactly
 * once per target per opening - that is the call {@code SpellAttackContext} throws on a repeat.
 */
public class CrownOfDawn extends AttackSpell implements Listener {

    /** Pure white - the brightest thing in the element. */
    private static final Color CROWN_COLOR = Color.fromRGB(255, 255, 255);
    private static final Particle.DustOptions CROWN_DUST = new Particle.DustOptions(CROWN_COLOR, 1.0F);

    /** The opening tick's launch, in blocks per tick. A LAUNCH, not the old radial push. */
    private static final double LIFT = 2.6;
    /** Largest vertical correction the hover may make in one tick, up or down. */
    private static final double HOVER_STEP = 0.7;
    /** How far below the caster the ground search reaches before the hover holds still. */
    private static final int GROUND_DEPTH = 24;
    /** Light pillar geometry. */
    private static final int PILLAR_LAYERS = 8;
    private static final int PILLAR_POINTS = 10;
    private static final double PILLAR_PHASE = Math.PI / 6.0;
    private static final double PILLAR_RADIUS = 0.6;
    /**
     * Inert hitpoints on the bulwark's properties carrier. Nothing damages it -
     * {@code SpellComponentHandler.resolveDefense} needs a {@code DamageableBarrier} for that,
     * and the crown only exists here so that {@code deflectProjectiles}, which requires a
     * {@code BarrierProperties}, sees a DEFENSE component at all.
     */
    private static final double BULWARK_HITPOINTS = 60.0;
    /** The mob path gets a five second crown on the ground, not the player's sixty second hover. */
    private static final int MOB_TICKS = 100;

    private static CrownOfDawn registeredListener;

    private CrownOfDawnConfig config = CrownOfDawnConfig.fromConfig(null);
    /** The sustain's hard ceiling. Without it the mana alone would buy three times the budget. */
    private double durationSeconds = 60.0;

    public CrownOfDawn(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/crown_of_dawn.yml");
        Alkatraz.getInstance().saveConfig("spells/crown_of_dawn_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/crown_of_dawn.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = CrownOfDawnConfig.fromConfig(spellConfig.getValues(false));
        this.durationSeconds = spellConfig.getDouble("duration_seconds", 60.0);
        if (registeredListener != null && registeredListener != this) {
            HandlerList.unregisterAll(registeredListener);
        }
        registeredListener = this;
        Alkatraz.getInstance().getServer().getPluginManager()
                .registerEvents(this, Alkatraz.getInstance());
    }

    /**
     * The crown's whole life, in ticks. {@code hit_count} must equal the openings it fits;
     * the cap is enforced in the sustain body, not left to the mana to stop at.
     */
    private int lifetimeTicks() {
        return Math.max(1, (int) Math.round(durationSeconds * 20.0));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double upkeep = Math.max(0.0,
                getModifiedStat(caster, "upkeep_mana_per_second", config.upkeepManaPerSecond()));
        double radius = Math.max(1.0, getModifiedStat(caster, "hover_radius", config.hoverRadius()));
        double altitude = getModifiedStat(caster, "hover_altitude", config.hoverAltitude());
        // A zero interval would be a modulo by zero on the first tick. Clamp to one.
        int interval = Math.max(1, config.damageIntervalTicks());

        // ONE context for the whole sustain, created here and never scaled here.
        SpellAttackContext context = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);
        AttackProperties props = context.properties();

        // ONE properties carrier for the whole sustain: its constructor creates a BossBar, and
        // deflectProjectiles only reads DEFENSE components whose properties ARE a
        // BarrierProperties, so one per opening would both leak bars and defeat the bulwark.
        BarrierProperties bulwark = new BarrierProperties(
                caster, caster.getLocation(), BULWARK_HITPOINTS, BarrierType.PHYSICAL, radius);

        BossBar[] bar = new BossBar[1];

        SustainEffect.Toggle toggled = SustainEffect.toggle(caster, upkeep, CrownOfDawn.this, (p, elapsedTicks) -> {
            context.tick();

            Location ground = groundUnder(p);
            double targetY = hoverTargetY(p, ground, altitude);
            hold(p, targetY);

            // AFTER the hold, so the launch is what the opening tick actually ends on. A hold
            // first would cap the opening tick at HOVER_STEP and the launch would never be felt.
            if (elapsedTicks == 0) {
                launch(p, radius);
            }

            // The pillar tracks the caster, foot on the ground the crown hovers over.
            if (ground != null) {
                SpellVfx.column(CROWN_COLOR, p, ground, targetY - ground.getY(), PILLAR_RADIUS,
                        PILLAR_LAYERS, PILLAR_POINTS, PILLAR_PHASE);
            }
            double crownSpin = elapsedTicks * 0.18;
            SpellVfx.orbit(CROWN_COLOR, p, p.getLocation().add(0, 2.2, 0), 1.25, 0.55,
                    3, crownSpin);
            ParticleCaster.spawn(p, p.getWorld(), Utils.END_ROD,
                    p.getLocation().add(0, 2.0, 0), 3, 0.45, 0.8, 0.45, 0.01);

            updateBar(bar, p, upkeep);

            if (elapsedTicks % interval == 0) {
                // BULWARK. deflectProjectiles reads the carrier's cast location, so it moves with
                // the caster rather than staying where the crown was opened.
                bulwark.setCastLocation(p.getLocation());
                SpellComponentHandler.register(new SpellParticleComponent(
                        CrownOfDawn.this, bulwark, p, wand, SpellComponentType.DEFENSE,
                        p.getLocation(), radius, interval));

                // DISPERSAL. The only circle-9 spell that clears its caster's own debuffs.
                p.removePotionEffect(PotionEffectType.WEAKNESS);
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.WITHER);

                sear(p, wand, context, props, radius);
            }

            // THE CAP. duration_seconds is the only thing making power x hit_count true: the
            // mana alone would buy 208s = 104 openings = 832.0, three times the circle-9
            // sustain budget, and SpellBudgetTest only ever reads the yml's two numbers.
            return elapsedTicks + 1 < lifetimeTicks();
        }, (p) -> {
            // Runs on EVERY end path - a second cast, exhausted mana, death, quit, disable.
            bulwark.getHealthBar().removeAll();
            if (bar[0] != null) {
                bar[0].removeAll();
            }
            // p is null when the player has already left the online list; only the work that
            // needs a live player is guarded.
            if (p != null) {
                p.setGravity(true);
                p.setVelocity(new Vector(0, -HOVER_STEP, 0));
            }
        });

        World world = caster.getWorld();
        if (toggled == SustainEffect.Toggle.OPENED) {
            world.playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
            return;
        }
        if (toggled == SustainEffect.Toggle.BLOCKED) {
            // Another circle-9 sustain owns the single slot. It was NOT stopped, so there is no
            // closing burst - this cast is simply refused, and the framework gives the mana back.
            cancelCast(caster);
            Utils.sendActionBar(caster, Alkatraz.getLangManager().get("spells.sustain.blocked"));
            return;
        }

        // CLOSED - this cast owned the running crown and just let it go.
        // Refund what Spell actually charged, not the base cost.
        StatUtils.addMana(caster, getModifiedManaCost(caster));
        // CLOSING BURST - the crown collapses where it stood rather than simply stopping.
        Location at = caster.getLocation().add(0, 1, 0);
        for (Location point : ParticleUtils.sphere(at, 1.2, 16)) {
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 1, CROWN_DUST);
        }
        world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.7f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        AttackProperties props = new AttackProperties(
                caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand),
                AttackType.MAGIC);
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);
        int interval = Math.max(1, config.damageIntervalTicks());
        // A mob does not hover and does not pay upkeep; it gets a short ground crown instead,
        // because SustainEffect.toggle is a Player toggle and Player#setGravity has no mob path.
        LivingEntity target = caster.getTarget();

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                context.tick();
                if (caster.isDead() || elapsed >= MOB_TICKS || target.isDead() || !target.isValid()) {
                    cancel();
                    return;
                }
                SpellVfx.column(CROWN_COLOR, caster, caster.getLocation(), 2.5, PILLAR_RADIUS,
                        PILLAR_LAYERS, PILLAR_POINTS, PILLAR_PHASE);
                if (elapsed % interval == 0) {
                    props.hit(target);
                    SpellDamageUtil.damageWithSpell(target, context.power(target),
                            caster, wand, CrownOfDawn.this);
                }
                elapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The opening tick: the caster is LAUNCHED, and every non-player inside the crown goes up
     * with them. This is not the old radial push - nothing is shoved away from the caster here,
     * the whole room is lifted.
     */
    private void launch(Player caster, double radius) {
        caster.setGravity(false);
        caster.setVelocity(new Vector(0, LIFT, 0));
        for (LivingEntity living : Utils.getNearbyLivingEntities(caster.getLocation(), radius)) {
            if (living instanceof Player || living.equals(caster)) continue;
            if (living.isDead() || !living.isValid()) continue;
            living.setVelocity(new Vector(living.getVelocity().getX(), LIFT, living.getVelocity().getZ()));
        }
    }

    /** The highest solid block at or below the caster, or null over a void. */
    private static Location groundUnder(Player caster) {
        return Utils.findTopSolid(caster.getLocation(), GROUND_DEPTH);
    }

    /**
     * The Y the caster is held at: the ground beneath them plus {@code hover_altitude}, CEILING
     * CLAMPED. The walk upward stops at the first solid block between their feet and that height
     * and holds them BELOW it - block index {@code y} spans world Y {@code [y, y+1)}, so putting
     * the feet at {@code y + 1.0} is the far face: a one block roof would seat the caster on top
     * of it and a two block roof would bury them inside it while hold() rewrote their Y velocity
     * every tick. The feet go to just under the obstacle, sized for the player's eye height, and
     * never below where the caster already is - the hover only ever rises. Over a void there is
     * no ground to measure from, so the caster simply holds wherever they are rather than
     * climbing forever.
     */
    private static double hoverTargetY(Player caster, Location ground, double altitude) {
        Location feet = caster.getLocation();
        if (ground == null) return feet.getY();
        double wanted = ground.getY() + 1.0 + Math.max(1.0, altitude);
        World world = feet.getWorld();
        if (world == null) return wanted;
        int x = feet.getBlockX();
        int z = feet.getBlockZ();
        int top = (int) Math.floor(wanted);
        for (int y = Math.max(0, feet.getBlockY()); y <= top; y++) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return Math.max(feet.getY(), y - caster.getEyeHeight() - 0.1);
            }
        }
        return wanted;
    }

    /** Gravity off, and one velocity nudge toward the held height. */
    private static void hold(Player caster, double targetY) {
        caster.setGravity(false);
        double step = targetY - caster.getLocation().getY();
        caster.setVelocity(new Vector(0,
                Math.max(-HOVER_STEP, Math.min(HOVER_STEP, step)),
                0));
    }

    /** The boss bar: how many more seconds of upkeep the caster's remaining mana buys. */
    private void updateBar(BossBar[] holder, Player caster, double upkeep) {
        BossBar bar = holder[0];
        if (bar == null) {
            bar = Bukkit.createBossBar("", getMasteryBarColor(), BarStyle.SEGMENTED_10);
            bar.addPlayer(caster);
            holder[0] = bar;
        }
        MagicProfile profile = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);
        double mana = profile == null ? 0.0 : profile.getMana();
        double seconds = upkeep > 0 ? Math.max(0.0, mana / upkeep) : 0.0;
        bar.setTitle(lang().get("spells.crownofdawn.boss_bar",
                "seconds", String.valueOf((int) Math.ceil(seconds))));
        bar.setProgress(Math.min(1.0, seconds / Math.max(1.0, durationSeconds)));
    }

    /** One armour-sear opening: the declared power to everything under the crown, armour off. */
    private void sear(Player caster, ItemStack wand, SpellAttackContext context,
                      AttackProperties props, double radius) {
        for (LivingEntity target : Utils.getNearbyLivingEntities(caster.getLocation(), radius)) {
            if (target.equals(caster) || target.isDead() || !target.isValid()) continue;
            if (NBT.getPersistentData(target, nbt -> nbt.getBoolean("summoned_zombie"))) continue;

            // props.hit, not context.hit: the context's own gate consults a set that is never
            // cleared for the life of a cast, so it would admit one opening and refuse the rest.
            props.hit(target);
            SpellDamageUtil.damageWithSpell(target, context.power(target),
                    caster, wand, CrownOfDawn.this);

            // Region gate ONCE MORE here: damageWithSpell already checks it, but the strip is a
            // separate world effect and a no-pvp region must not lose its armour either.
            if (target instanceof Player victim
                    && Protection.damage(target, caster, target.getLocation())) {
                victim.getInventory().setHelmet(null);
                victim.getInventory().setChestplate(null);
                victim.getInventory().setLeggings(null);
                victim.getInventory().setBoots(null);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Cleanup rule: a caster who leaves mid-hover must not be left gravity-less on relog.
        // SustainEffect's own tick also stops on !isOnline(); this takes the bar down at once.
        SustainEffect.stop(e.getPlayer());
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 4.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.ENTITY_EFFECT, location, 30, CROWN_COLOR);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.8f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.ENTITY_EFFECT, location, 60, CROWN_COLOR);
        ParticleCaster.spawn(null, location.getWorld(), Utils.DUST, location, 40, 0.3, 0.3, 0.3, 0.0, CROWN_DUST);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.6f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.crownofdawn.book_name"))
                .addCustomLoreLine(lang().get("spells.crownofdawn.lore1"))
                .addCustomLoreLine(lang().get("spells.crownofdawn.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
