package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.properties.implementation.BarrierProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import de.tr7zw.changeme.nbtapi.NBT;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Barrier extends BarrierSpell implements Listener {

    /**
     * The single NONE palette. The old break-flash was hardcoded {@link Color#RED}, which on
     * an elementless defensive barrier read as the barrier taking damage rather than popping.
     * The break is now the palette's darkest shade: structure ending, not an injury.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** Particles on the expanding shell; the inner ring uses half of this. */
    private static final int SHELL_POINT_COUNT = 200;
    /** Particles in the break burst. */
    private static final int BREAK_POINT_COUNT = 120;
    /** Shell breathing radius added on top of the configured barrier size, in blocks. */
    private static final double SHELL_POINT_RADIUS = 0.6;
    private static final int SHELL_LIFETIME = 4;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Barrier(String type) {
        super(type);
    }

    private double radius;
    private double duration;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/barrier.yml");
        Alkatraz.getInstance().saveConfig("spells/barrier_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/barrier.yml").get();
        radius = spellConfig.getDouble("radius");
        duration = spellConfig.getDouble("duration");
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        Location center = p.getLocation().clone().add(0, 1, 0);
        double activeRadius = (Double) getOption("barrier_size").getSelectedValue(p).getValue();
        double activeDuration = (Double) getOption("barrier_duration").getSelectedValue(p).getValue();
        double activeHitpoints = (Double) getOption("barrier_hitpoints").getSelectedValue(p).getValue();
        boolean followPlayer = (Double) getOption("follow_player").getSelectedValue(p).getValue() > 0;
        BarrierProperties properties = new BarrierProperties(p, center, activeHitpoints, BarrierType.COMBINED, activeRadius);
        properties.getHealthBar().addPlayer(p);

        final double shellCollisionRadius = activeRadius + SHELL_POINT_RADIUS;

        new BukkitRunnable() {
            int ticksPassed = 0;

            @Override
            public void run() {
                if (followPlayer) {
                    Location pLoc = p.getLocation();
                    center.setX(pLoc.getX());
                    center.setY(pLoc.getY() + 1);
                    center.setZ(pLoc.getZ());
                    properties.setCastLocation(center);
                }

                if (properties.isBroken() || ticksPassed >= activeDuration * 5) {
                    onBarrierBreak(center, activeRadius);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                // Push nearby entities away (for PHYSICAL/COMBINED barriers)
                if (properties.getType() == BarrierType.PHYSICAL || properties.getType() == BarrierType.COMBINED) {
                    final Vector centerVec = center.toVector();
                    for (Entity entity : center.getWorld().getNearbyEntities(center, activeRadius, activeRadius, activeRadius)) {
                        if (!(entity instanceof LivingEntity living)) continue;
                        if (entity == p) continue;
                        if (NBT.getPersistentData(entity, nbt -> nbt.getBoolean("summoned_zombie"))) continue;

                        Vector push = entity.getLocation().toVector().subtract(centerVec);
                        push.setY(0);
                        if (push.lengthSquared() < 0.01) continue;
                        push = Utils.safeNormalize(push).multiply(0.3);
                        living.setVelocity(living.getVelocity().add(push));
                    }
                }

                SpellParticleComponent shell = new SpellParticleComponent(
                        Barrier.this,
                        properties,
                        p,
                        wand,
                        SpellComponentType.DEFENSE,
                        center,
                        shellCollisionRadius,
                        SHELL_LIFETIME
                );
                SpellComponentHandler.register(shell);

                SpellVfx.nova(Barrier.this, p, center, activeRadius, SHELL_POINT_COUNT);

                ticksPassed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 4);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        Location center = caster.getLocation().clone().add(0, 1, 0);
        BarrierProperties properties = new BarrierProperties(caster, center, getMaxHitpoints(), BarrierType.COMBINED, radius);

        final double shellCollisionRadius = radius + SHELL_POINT_RADIUS;

        BukkitRunnable task = new BukkitRunnable() {

            int ticksPassed = 0;

            @Override
            public void run() {
                if (properties.isBroken() || ticksPassed >= duration * 5) {
                    onBarrierBreak(center);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                SpellParticleComponent shell = new SpellParticleComponent(
                        Barrier.this,
                        properties,
                        caster,
                        wand,
                        SpellComponentType.DEFENSE,
                        center,
                        shellCollisionRadius,
                        SHELL_LIFETIME
                );
                SpellComponentHandler.register(shell);

                SpellVfx.nova(Barrier.this, caster, center, radius, SHELL_POINT_COUNT);

                ticksPassed++;
            }
        };

        task.runTaskTimer(Alkatraz.getInstance(), 0, 4);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.barrier.book_name"))
                .addCustomLoreLine(lang().get("spells.barrier.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }

    @Override
    public void onHit(double damage, AttackSpell source) {

    }

    @Override
    public void onBarrierBreak(Location center) {
        onBarrierBreak(center, radius);
    }

    private void onBarrierBreak(Location center, double breakRadius) {
        SpellVfx.nova(null, null, center, breakRadius, BREAK_POINT_COUNT);
    }
}
