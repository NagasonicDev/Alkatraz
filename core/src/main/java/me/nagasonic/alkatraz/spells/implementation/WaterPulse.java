package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
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
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WaterPulse extends AttackSpell implements Listener {

    /**
     * This spell names no colour at all any more. {@link SpellVfx} reads the element straight off
     * the spell - {@code water_pulse.yml} declares {@code element: WATER} - so the wave front is
     * the same shade as every other WATER effect. It used to draw a raw {@code Color.BLUE}, which
     * is not in the WATER family and was the reason the wave looked like a different element.
     */

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public WaterPulse(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SPLASH, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.SPLASH, location, 30);
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/water_pulse.yml");
        Alkatraz.getInstance().saveConfig("spells/water_pulse_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/water_pulse.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        // WAS getBasePower() * getBasePower() * getWandPower(...). Squaring the
        // base is not a documented mechanic and at power 4.2 alone exceeds the
        // circle-2 BURST budget. Raw base power in, one getPower per pulse below.
        AttackProperties props = new AttackProperties(p, Utils.castLocation(p),
                getBasePower() * getWandPower(wand, p), AttackType.MAGIC);
        Location centre = p.getLocation();
        double maxRadius = (Double) getOption("pulse_radius").getSelectedValue(p).getValue();
        double step = (Double) getOption("pulse_speed").getSelectedValue(p).getValue();

        BukkitRunnable task = new BukkitRunnable() {
            double r = 0;
            double rotation = 0;
            int scannedRadius = -1;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()) {
                    cancel();
                    return;
                }
                r += step;
                spawnPulseRing(centre, r, props, p, wand, rotation);
                rotation += RING_ROTATION_PER_TICK;

                int blockRadius = (int) Math.ceil(r + 1);
                scannedRadius = convertWaveBlocks(centre, scannedRadius, blockRadius, p);

                for (Entity entity : centre.getWorld().getNearbyEntities(centre, r + 1, 1, r + 1)) {
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    if (!(entity instanceof LivingEntity)) continue;
                    if (entity.equals(p)) continue;

                    LivingEntity target = (LivingEntity) entity;

                    // water_pulse was knockback-only. It is a BURST now.
                    if (!props.hasHit(target)) {
                        props.hit(target);
                        SpellDamageUtil.damageWithSpell(target,
                                getPower(p, target, props.getRemainingPower()),
                                p, wand, WaterPulse.this);
                    }

                    Vector direction = Utils.safeNormalize(target.getLocation().toVector().subtract(centre.toVector()));
                    // The knockback used to scale by damage power, which made a
                    // damage buff throw the target twice as far. Use a flat push.
                    direction.multiply(0.8);
                    direction.setY(0.5);
                    target.setVelocity(direction);
                }
                if (r >= maxRadius) {
                    cancel();
                }
            }
        };

        task.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        double wandp = getWandPowerOrDefault(wand);
        // RAW base power. getPower is applied once per pulse, below.
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * wandp, AttackType.MAGIC);
        Location centre = caster.getLocation();

        BukkitRunnable task = new BukkitRunnable() {
            double r = 0;
            double rotation = 0;
            int scannedRadius = -1;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()){
                    cancel();
                    return;
                }
                r += 0.5;
                spawnPulseRing(centre, r, props, caster, wand, rotation);
                rotation += RING_ROTATION_PER_TICK;

                int blockRadius = (int) Math.ceil(r + 1);
                scannedRadius = convertWaveBlocks(centre, scannedRadius, blockRadius, caster);

                for (Entity entity : centre.getWorld().getNearbyEntities(centre, r + 1, 1, r + 1)) {
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    if (!(entity instanceof LivingEntity)) continue;
                    if (entity.equals(caster)) continue;

                    LivingEntity target = (LivingEntity) entity;

                    // water_pulse was knockback-only. It is a BURST now.
                    if (!props.hasHit(target)) {
                        props.hit(target);
                        SpellDamageUtil.damageWithSpell(target,
                                getPower(caster, target, props.getRemainingPower()),
                                caster, wand, WaterPulse.this);
                    }

                    Vector direction = Utils.safeNormalize(target.getLocation().toVector().subtract(centre.toVector()));
                    // Flat push - the knockback must not scale with damage power.
                    direction.multiply(0.8);
                    direction.setY(0.5);
                    target.setVelocity(direction);
                }
                if (r == 10){
                    cancel();
                }
            }
        };

        task.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    private static final int RING_POINTS = 48;
    /** Radians the expanding wave front turns per tick. */
    private static final double RING_ROTATION_PER_TICK = 0.25;

    private void spawnPulseRing(Location centre, double r, AttackProperties props, LivingEntity caster,
                                ItemStack wand, double rotation) {
        // Signature shape: an EXPANDING ring. `ringRadius` is monotonically increasing across the
        // cast, which is the whole tell - whirlpool is the same builder with the opposite sign.
        // No shadow ring here: this is a blast front standing on open ground, not ground decal.
        SpellVfx.ring(WaterPulse.this, caster, centre, r, RING_POINTS, rotation, 0.0);

        SpellParticleComponent comp = new SpellParticleComponent(
                WaterPulse.this,
                props,
                caster,
                wand,
                SpellComponentType.OFFENSE,
                centre,
                r,
                1
        );
        SpellComponentHandler.register(comp);
    }

    private int convertWaveBlocks(Location centre, int scannedRadius, int outerRadius, LivingEntity caster) {
        World world = centre.getWorld();
        int cx = centre.getBlockX();
        int cy = centre.getBlockY();
        int cz = centre.getBlockZ();
        int outerSquared = outerRadius * outerRadius;
        int innerSquared = scannedRadius < 0 ? -1 : scannedRadius * scannedRadius;

        for (int x = -outerRadius; x <= outerRadius; x++) {
            int xSquared = x * x;
            if (xSquared > outerSquared) continue;
            for (int y = -outerRadius; y <= outerRadius; y++) {
                int xySquared = xSquared + y * y;
                if (xySquared > outerSquared) continue;
                for (int z = -outerRadius; z <= outerRadius; z++) {
                    int distanceSquared = xySquared + z * z;
                    if (distanceSquared > outerSquared || distanceSquared <= innerSquared) continue;

                    Block b = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (b.getType() == Material.FIRE){
                        if (Protection.blockEdit(caster, b.getLocation())){
                            b.setType(Material.AIR);
                        }
                    }else if (b.getType() == Material.LAVA){
                        Levelled blockdata = (Levelled) b.getBlockData();
                        if (Protection.blockEdit(caster, b.getLocation())){
                            if (blockdata.getLevel() == 0){
                                b.setType(Material.OBSIDIAN);
                            }else{
                                b.setType(Material.COBBLESTONE);
                            }
                        }
                    }
                }
            }
        }
        return outerRadius;
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.waterpulse.book_name"))
                .addCustomLoreLine(lang().get("spells.waterpulse.lore1"))
                .addCustomLoreLine(lang().get("spells.waterpulse.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }
}
