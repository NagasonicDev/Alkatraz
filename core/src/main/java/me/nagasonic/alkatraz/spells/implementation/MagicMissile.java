package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;


public class MagicMissile extends AttackSpell {

    /**
     * The single NONE palette. Every colour in this spell comes from here: the travelling
     * head cluster is the leading edge and takes {@code core}, and the projectile tail
     * darkens toward {@code deep} behind it. Nothing in this file may name an RGB value -
     * the old hardcoded {@code Color.AQUA} made the starter burst read as a WATER spell.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** Radius of the travelling head cluster, in blocks. */
    private static final double CLUSTER_RADIUS = 0.35;
    /** Particles in the head cluster. */
    private static final int CLUSTER_POINTS = 10;
    /** Tail particles trailing behind the travelling head. */
    private static final int TAIL_POINTS = 12;
    /** How far behind the head the tail reaches, in blocks. */
    private static final double TAIL_LENGTH = 1.4;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public MagicMissile(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.DUST, location, 15, PALETTE.coreDust());
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.DUST, location, 30, PALETTE.accentDust());
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/magic_missile.yml");
        Alkatraz.getInstance().saveConfig("spells/magic_missile_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/magic_missile.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * getWandPower(wand, p), AttackType.MAGIC);
            Location loc1 = p.getEyeLocation();
            Vector direction = p.getEyeLocation().getDirection();
            final Vector travel = direction.clone();
            Location loc2 = p.getEyeLocation().add(direction.multiply((Double) getOption("missile_range").getSelectedValue(p).getValue()));
            List<Location> locs = ParticleUtils.line(0.5, loc1, loc2);
            locs.remove(0); //Function puts loc2 as the first index, so if it is a solid block, the missile will not fire.
            locs.add(loc2);
            BukkitRunnable task = new BukkitRunnable() {

                int index = 0;

                @Override
                public void run() {

                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }

                    if (index >= locs.size()) {
                        cancel();
                        return;
                    }

                    Location loc = locs.get(index++);
                    Block b = loc.getBlock();

                    if (!b.isPassable() && !b.isLiquid() && b.getType().isOccluding()) {
                        cancel();
                        return;
                    }

                    SpellVfx.projectile(MagicMissile.this, p, loc, travel,
                            CLUSTER_RADIUS, CLUSTER_POINTS, TAIL_POINTS, TAIL_LENGTH);
                    SpellParticleComponent comp = new SpellParticleComponent(
                            MagicMissile.this,
                            props,
                            p,
                            wand,
                            SpellComponentType.OFFENSE,
                            loc,
                            0.25,
                            1
                    );
                    SpellComponentHandler.register(comp);

                    for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
                        // WAS `break` on all four guards. getNearbyEntities returns
                        // a Collection in unspecified order, so the first dead entity,
                        // the caster, or the first non-LivingEntity (armour stand,
                        // dropped item, boat) aborted the whole scan and the missile
                        // passed through everything else undamaged.
                        if (entity.isDead() || entity.equals(p)) continue;
                        if (!(entity instanceof LivingEntity le)) continue;
                        if (props.isCancelled() || props.isCountered()) return;
                        if (props.hasHit(le)) continue;
                        props.hit(le);

                        // THE getPower APPLICATION. It was never called on the player
                        // path - the raw props.getRemainingPower() went straight into
                        // damageWithSpell, so `power`, the wand and affinity were all
                        // inert and the spell dealt a flat 1.0.
                        SpellDamageUtil.damageWithSpell(le,
                                getPower(p, le, props.getRemainingPower()),
                                p, wand, MagicMissile.this);

                        Vector unitVector = entity.getLocation()
                                .toVector()
                                .subtract(p.getLocation().toVector())
                                .normalize();

                        entity.setVelocity(unitVector.multiply(1));
                    }
                }

            };
            task.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (!caster.isDead() && caster.getTarget() != null){
            // Raw power only, so that getPower is applied exactly ONCE per damage
            // instance. It used to be applied here at cast time via the two-arg
            // overload, which takes no target - the target half of the affinity
            // spread was silently dropped, and the same scaling is now done at the
            // hit, where the target is known.
            AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                    getBasePower() * getWandPowerOrDefault(wand), AttackType.MAGIC);
            Location loc1 = caster.getEyeLocation();
            Vector direction = Utils.safeNormalize(caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector()));
            final Vector travel = direction.clone();
            Location loc2 = caster.getEyeLocation().add(direction.multiply(20));
            List<Location> locs = ParticleUtils.line(0.5, loc1, loc2);
            locs.remove(0); //Function puts loc2 as the first index, so if it is a solid block, the missile will not fire.
            locs.add(loc2);
            BukkitRunnable task = new BukkitRunnable() {

                int index = 0;

                @Override
                public void run() {

                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }

                    if (index >= locs.size()) {
                        cancel();
                        return;
                    }

                    Location loc = locs.get(index++);
                    Block b = loc.getBlock();

                    if (!b.isPassable() && !b.isLiquid() && b.getType().isOccluding()) {
                        cancel();
                        return;
                    }

                    SpellVfx.projectile(MagicMissile.this, caster, loc, travel,
                            CLUSTER_RADIUS, CLUSTER_POINTS, TAIL_POINTS, TAIL_LENGTH);
                    SpellParticleComponent comp = new SpellParticleComponent(
                            MagicMissile.this,
                            props,
                            caster,
                            wand,
                            SpellComponentType.OFFENSE,
                            loc,
                            0.25,
                            1
                    );
                    SpellComponentHandler.register(comp);

                    for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
                        // Same fix as the player path: `continue` for the per-entity
                        // guards, `return` for the cancellation guards.
                        if (entity.isDead() || entity.equals(caster)) continue;
                        if (!(entity instanceof LivingEntity le)) continue;
                        if (props.isCancelled() || props.isCountered()) return;
                        if (props.hasHit(le)) continue;
                        props.hit(le);

                        SpellDamageUtil.damageWithSpell(le,
                                getPower(caster, le, props.getRemainingPower()),
                                caster, wand, MagicMissile.this);

                        Vector unitVector = entity.getLocation()
                                .toVector()
                                .subtract(caster.getLocation().toVector())
                                .normalize();

                        entity.setVelocity(unitVector.multiply(1));
                    }
                }

            };
            task.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return null;
    }
}
