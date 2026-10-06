package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellEntityComponent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.UnknownNullability;

import java.util.UUID;

public class Fireball extends AttackSpell implements Listener {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `FIRE` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(255, 140, 0);

    /**
     * The travelling tell: a tight {@code core} cluster riding the fireball with a short
     * {@code accent}-to-{@code deep} tail. Deliberately narrow - fire_blast owns the fat,
     * short-tailed shell, and the two must not read as the same shot.
     */
    private static final double TRACK_CLUSTER_RADIUS = 0.22;
    private static final int TRACK_CLUSTER_POINTS = 6;
    private static final int TRACK_TAIL_POINTS = 5;
    private static final double TRACK_TAIL_LENGTH = 1.1;
    /** Drawn every other tick; a 2-tick trail reads continuous at normal speeds. */
    private static final long TRACK_PERIOD_TICKS = 2L;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Fireball(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.FLAME, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.FLAME, location, 30);
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/fireball_options.yml");
        Alkatraz.getInstance().save("spells/fireball.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fireball.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double projectileSpeed = getModifiedStat(caster, "projectile_speed", 0.5);

        // RAW base power. getPower is applied exactly once, at impact, below.
        // It used to be applied here AND in onDamage AND in onFireballExplode, and
        // squaring the option multipliers turns large_size's 1.4 into 1.96 while
        // squaring the affinity term turns 2.24 into 3.24^2 = 10.5x.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster),
                AttackType.MAGIC
        );

        org.bukkit.entity.Fireball fireball = caster.launchProjectile(
                org.bukkit.entity.Fireball.class,
                caster.getEyeLocation().getDirection().multiply(projectileSpeed)
        );

        SpellEntityComponent entityComp = new SpellEntityComponent(
                this, props, caster, wand, SpellComponentType.OFFENSE, fireball
        );
        entityComp.setCollisionRadius(0.5);
        NBT.modifyPersistentData(fireball, nbt -> {
            nbt.setString("component_id", entityComp.getComponentID().toString());
        });
        SpellComponentHandler.register(entityComp);
        trackProjectile(caster, fireball);
    }

    @Override
    public void mobCastAction(@UnknownNullability Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand),
                AttackType.MAGIC
        );
        Vector direction = caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector());

        org.bukkit.entity.Fireball fireball = caster.launchProjectile(
                org.bukkit.entity.Fireball.class,
                direction.multiply(0.1)
        );

        SpellEntityComponent entityComp = new SpellEntityComponent(
                this, props, caster, wand, SpellComponentType.OFFENSE, fireball
        );
        entityComp.setCollisionRadius(0.5);
        NBT.modifyPersistentData(fireball, nbt -> {
            nbt.setString("component_id", entityComp.getComponentID().toString());
        });
        SpellComponentHandler.register(entityComp);
        trackProjectile(caster, fireball);
    }

    /**
     * Draws the shot's {@link SpellVfx#projectile} tell for as long as the entity lives.
     * Purely visual: the task stops the moment the fireball is gone, so a cast that never
     * resolves cannot leak a repeating task.
     *
     * @param caster   the casting entity, used for per-player density falloff
     * @param fireball the projectile to follow
     */
    private void trackProjectile(LivingEntity caster, org.bukkit.entity.Fireball fireball) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (fireball.isDead() || !fireball.isValid()) {
                    cancel();
                    return;
                }
                SpellVfx.projectile(CAST_COLOR, caster, fireball.getLocation(),
                        fireball.getVelocity(),
                        TRACK_CLUSTER_RADIUS, TRACK_CLUSTER_POINTS,
                        TRACK_TAIL_POINTS, TRACK_TAIL_LENGTH);
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, TRACK_PERIOD_TICKS);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 2, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.fireball.book_name"))
                .addLoreLine(lang().get("spells.fireball.lore1"))
                .addLoreLine(lang().get("spells.fireball.lore2"))
                .addCustomLoreLine("")
                .build();
    }

    @EventHandler
    private void onDamage(EntityDamageByEntityEvent e){
        if (!(e.getDamager() instanceof org.bukkit.entity.Fireball fireball)) return;
        String idString = NBT.getPersistentData(fireball, nbt -> nbt.getString("component_id"));
        if (idString == null) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        SpellComponent comp = SpellComponentHandler.getActiveComponent(UUID.fromString(idString));
        if (!(comp instanceof SpellEntityComponent ecomp)) return;
        if (!(ecomp.getProperties() instanceof AttackProperties props)) return;
        // AttackProperties.hit returns void; hasHit is the boolean test. The direct
        // hit and the explosion share one AttackProperties, so the guard also stops
        // a target being hit by both in the same explosion.
        if (props.hasHit(le)) return;
        props.hit(le);
        e.setDamage(getPower(comp.getCaster(), le, props.getRemainingPower()));
    }

    @EventHandler
    private void onFireballExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Fireball fireball)) return;
        String idString = NBT.getPersistentData(fireball, nbt -> nbt.getString("component_id"));
        if (idString == null || idString.isEmpty()) return;
        SpellComponent comp = SpellComponentHandler.getActiveComponent(UUID.fromString(idString));
        if (comp == null) return;
        if (comp.getSpell().getClass() != Fireball.class) return;
        if (!(comp instanceof SpellEntityComponent ecomp)) return;
        if (!(ecomp.getProperties() instanceof AttackProperties props)) return;

        LivingEntity caster = comp.getCaster();
        boolean breaksBlocks = caster instanceof Player p
                && (boolean) getOption("breaks_blocks").getSelectedValue(p).getValue();

        if (breaksBlocks) {
            // The vanilla explosion is about to write blocks with ZERO Protection
            // coverage. Gate it, then re-filter the block list per block so a
            // region entered since the cast is not rewritten by the explosion.
            if (!Protection.explosion(caster, fireball.getLocation())) {
                e.setCancelled(true);
                return;
            }
            e.blockList().removeIf(block -> !Protection.blockEdit(caster, block.getLocation()));
        } else {
            e.setCancelled(true);
        }

        double size = caster instanceof Player p
                ? (double) getOption("size").getSelectedValue(p).getValue()
                : (double) getOption("size").getOptionValues().get(getOption("size").getDefIndex()).getValue();

        for (LivingEntity entity : Utils.getNearbyLivingEntities(fireball.getLocation(), size)) {
            if (entity.equals(caster)) continue;
            if (props.hasHit(entity)) continue;
            props.hit(entity);
            SpellDamageUtil.damageWithSpell(entity,
                    getPower(caster, entity, props.getRemainingPower()),
                    caster, comp.getWand(), Fireball.this);
        }
    }
}
