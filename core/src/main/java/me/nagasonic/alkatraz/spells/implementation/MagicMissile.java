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
import org.bukkit.Color;
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
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Aqua, deliberately. This spell used to be pinned to the {@code NONE} element's neutral
     * grey because a hardcoded {@code Color.AQUA} had made the starter burst read as a WATER
     * spell. That is a fair complaint about the starter burst, and the fix is not to grey out the
     * whole spell - it is to give this spell its own colour, which is what this constant is for.
     * Editing this one line re-tints the travelling head, the tail and the cast circle together.
     */
    private static final Color CAST_COLOR = Color.AQUA;

    /** The palette built around {@link #CAST_COLOR}, resolved once so no tick allocates a colour. */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(CAST_COLOR);

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
            // ParticleUtils.line already drops its own first point, which is the endpoint the
            // direction was measured to. Removing it again here skipped the second point of the
            // flight, so a missile cast at a nearby wall started its visible trail late.
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

                    SpellVfx.projectile(CAST_COLOR, p, loc, travel,
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
                        if (entity.isDead() || entity.equals(p)) continue;
                        if (!(entity instanceof LivingEntity le)) continue;
                        if (props.isCancelled() || props.isCountered()) return;
                        if (props.hasHit(le)) continue;
                        props.hit(le);
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
            // ParticleUtils.line already drops its own first point, which is the endpoint the
            // direction was measured to. Removing it again here skipped the second point of the
            // flight, so a missile cast at a nearby wall started its visible trail late.
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

                    SpellVfx.projectile(CAST_COLOR, caster, loc, travel,
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
        return CastCircle.start(this, CAST_COLOR, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return null;
    }
}
