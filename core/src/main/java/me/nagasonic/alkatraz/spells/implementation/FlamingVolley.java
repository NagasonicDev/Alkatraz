package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FlamingVolley extends AttackSpell implements Listener {

    /**
     * Every colour this spell draws comes from the shared FIRE palette.
     *
     * <p>The cast circle used to be {@code Color.RED} - a pure (255, 0, 0) on an
     * {@code element: FIRE} spell, which is the one colour that can never be mistaken for
     * anything else and never reads as fire. {@code fireball} and {@code fire_blast} already
     * draw in orange; this one now agrees with them.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);

    /** Angular divisions across the volley's spread sector. */
    private static final int SPREAD_ARC_STEPS = 12;
    /** Radial divisions from the caster out to the sector rim. */
    private static final int SPREAD_RADIAL_STEPS = 4;

    /**
     * Per-cast rotation of the spread sector, in degrees. Incremented once per volley so
     * successive casts present their spread at a different angle - without it the sector would
     * sit at the identical angle every single time and read as a static decal.
     */
    private static final AtomicInteger SPREAD_SPIN = new AtomicInteger();

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double arrowSpeed;
    private double arrowRange;
    private double arrowSpread;

    public FlamingVolley(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.FLAME, location, 15);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.LAVA, location, 5);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.FLAME, location, 30);
        ParticleCaster.spawn(null, location.getWorld(), Utils.LAVA, location, 10);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/flaming_volley_options.yml");
        Alkatraz.getInstance().save("spells/flaming_volley.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/flaming_volley.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.arrowSpeed = spellConfig.getDouble("arrow_speed");
        this.arrowRange = spellConfig.getDouble("arrow_range");
        this.arrowSpread = spellConfig.getDouble("arrow_spread");
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        int arrowCount = (int) getModifiedStat(caster, "arrow_count",
                ((Number) getOption("arrow_count").getSelectedValue(caster).getValue()).intValue());
        // RAW base power. getPower is applied once per hit, in the collision block.
        // It used to be applied here as well, squaring the option multipliers and
        // the affinity term for every arrow.
        AttackProperties props = new AttackProperties(
                caster,
                caster.getEyeLocation(),
                getBasePower() * getWandPower(wand, caster),
                AttackType.MAGIC
        );
        fireVolley(caster, wand, arrowCount, props);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        AttackProperties props = new AttackProperties(
                caster,
                caster.getEyeLocation(),
                getBasePower() * getWandPowerOrDefault(wand),
                AttackType.MAGIC
        );
        fireVolley(caster, wand, 3, props);
    }

    private void fireVolley(LivingEntity caster, ItemStack wand, int count, AttackProperties props) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.8f);

        Vector aimDir;
        if (caster instanceof Mob m && m.getTarget() != null) {
            aimDir = m.getTarget().getLocation().toVector().subtract(m.getLocation().toVector()).normalize();
        } else {
            aimDir = caster.getEyeLocation().getDirection().normalize();
        }

        List<Double> offsets = new ArrayList<>();
        if (count == 1) {
            offsets.add(0.0);
        } else {
            double halfSpread = Math.toRadians(arrowSpread) / 2.0;
            double step = Math.toRadians(arrowSpread) / (count - 1);
            for (int i = 0; i < count; i++) {
                offsets.add(-halfSpread + step * i);
            }
        }

        for (double yawOffset : offsets) {
            Vector arrowDir = rotateAroundY(aimDir.clone(), yawOffset);
            launchArrow(caster, wand, props, arrowDir);
        }

        drawSpread(caster, aimDir, count);
    }

    /**
     * The volley tell: a ground sector covering exactly the arc the arrows will fan across, in
     * the caster's FIRE colours.
     *
     * <p><b>The sector is the gameplay, not decoration.</b> {@link SpellVfx#cone} sweeps its arc
     * anticlockwise from {@code startAngleDegrees}, and {@link #rotateAroundY} maps a yaw offset
     * {@code A} to a plane angle of {@code phi - A}. {@link #fireVolley} hands out offsets running
     * from {@code -arc/2} to {@code +arc/2}, so the arrows occupy plane angles from
     * {@code phi + arc/2} <i>down</i> to {@code phi - arc/2} - a decreasing set. The sector must
     * therefore start at {@code phi - arc/2} and sweep up, not start at {@code phi + arc/2};
     * starting at the far end would draw a sector covering the mirror image of the volley and
     * tell the player they are safe on the side they are about to be hit on.
     *
     * <p>{@code advance} is not used here: the sector is revealed in full on the release frame,
     * because a partially revealed cone would under-report the danger by exactly the part the
     * player has not seen yet.
     *
     * @param caster  the casting entity
     * @param aimDir  the un-offset aim direction the offsets are applied to
     * @param count   the number of arrows, which decides whether the arc is a fan or a line
     */
    private void drawSpread(LivingEntity caster, Vector aimDir, int count) {
        Location feet = caster.getLocation();
        if (feet.getWorld() == null) return;

        // The horizontal projection: the arrows are launched along aimDir including its pitch,
        // but the sector is a flat ground shape, so only the XZ heading can be matched.
        double heading = Math.toDegrees(Math.atan2(aimDir.getZ(), aimDir.getX()));
        double arcDegrees = (count <= 1) ? 0.0 : Math.abs(arrowSpread);
        double startAngle = heading - arcDegrees / 2.0
                + SPREAD_SPIN.getAndUpdate(previous -> (previous + SPREAD_SPIN_STEP) % 360);

        SpellVfx.cone(this, caster, feet, arrowRange, arcDegrees, startAngle,
                SPREAD_ARC_STEPS, SPREAD_RADIAL_STEPS);
    }

    /** Degrees the spread sector rotates between successive casts. */
    private static final int SPREAD_SPIN_STEP = 23;

    private void launchArrow(LivingEntity caster, ItemStack wand, AttackProperties props, Vector direction) {
        new BukkitRunnable() {
            final Location position = caster.getEyeLocation().clone();
            final Vector step = direction.clone().multiply(arrowSpeed);
            double distanceTravelled = 0;
            int tickCount = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                position.add(step);
                distanceTravelled += arrowSpeed;

                if (distanceTravelled > arrowRange) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                Block current = position.getBlock();
                if (current.isLiquid() || (!current.isPassable() && current.getType().isOccluding())) {
                    spawnImpact(position, caster);
                    cancel();
                    return;
                }

                spawnArrowParticles(position);

                SpellParticleComponent comp = new SpellParticleComponent(
                        FlamingVolley.this,
                        props,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        position,
                        0.5,
                        1
                );
                SpellComponentHandler.register(comp);

                tickCount++;
                if ((tickCount & 1) == 0) return;

                for (Entity entity : position.getWorld().getNearbyEntities(position, 0.6, 0.6, 0.6)) {
                    if (!(entity instanceof LivingEntity target)) continue;
                    if (entity.equals(caster)) continue;
                    // One AttackProperties per cast, shared by every arrow, so a
                    // single counter kills the whole volley AND a target can only be
                    // hit once by the volley. This replaces the per-arrow local
                    // Set<UUID>, which let each of the three arrows land a full hit.
                    if (props.hasHit(target)) continue;
                    props.hit(target);

                    double damage = getPower(caster, target, props.getRemainingPower());
                    SpellDamageUtil.damageWithSpell(target, damage, caster, wand, FlamingVolley.this);
                    if (Protection.ignite(caster, target.getLocation())) {
                        target.setFireTicks(100);
                    }

                    spawnImpact(position, caster);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void spawnArrowParticles(Location center) {
        ParticleCaster.spawn(null, center.getWorld(), Utils.FLAME, center, 2, 0.05, 0.05, 0.05, 0.01);
        ParticleCaster.spawn(null, center.getWorld(), Utils.SMOKE, center, 1, 0.05, 0.05, 0.05, 0.01);
    }

    private void spawnDisperse(Location loc) {
        ParticleCaster.spawn(null, loc.getWorld(), Utils.SMOKE, loc, 8, 0.3, 0.3, 0.3, 0.05);
    }

    private void spawnImpact(Location loc, LivingEntity caster) {
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.FLAME, loc, 20, 0.5, 0.5, 0.5, 0.1);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.LAVA, loc, 10, 0.3, 0.3, 0.3, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.2f);

        if (caster instanceof Player p
                && (boolean) getOption("fire_blocks").getSelectedValue(p).getValue()) {
            if (loc.getBlock().getType() == Material.AIR) {
                if (Protection.ignite(caster, loc)) {
                    loc.getBlock().setType(Material.FIRE);
                }
            }
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.flamingvolley.book_name"))
                .addCustomLoreLine(lang().get("spells.flamingvolley.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }

    private static Vector rotateAroundY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }
}
