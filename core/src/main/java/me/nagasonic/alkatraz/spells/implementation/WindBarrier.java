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
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.properties.implementation.BarrierProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WindBarrier extends BarrierSpell implements Listener {

    /** The AIR palette, resolved once so a 5-tickly barrier shell allocates no colour objects. */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.AIR);

    /**
     * Shell geometry. The barrier drew a 100 point sphere on every frame; that count is now named
     * here so the two call sites cannot drift apart, and {@link SpellVfx#nova} draws it as a
     * {@code core} shell over a {@code deep} inner ring so the dome reads as a surface with
     * thickness rather than as one flat ring of motes.
     */
    private static final int SHELL_POINTS = 100;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public WindBarrier(String type) {
        super(type);
    }

    private double radius;
    private double duration;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/wind_barrier_options.yml");
        Alkatraz.getInstance().save("spells/wind_barrier.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/wind_barrier.yml").get();
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration");
        loadCommonConfig(spellConfig);
        loadOptions();
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        Location center = p.getLocation().clone().add(0, 1, 0);
        double activeRadius = (Double) getOption("barrier_size").getSelectedValue(p).getValue();
        double activeDuration = (Double) getOption("barrier_duration").getSelectedValue(p).getValue();
        double activeHitpoints = maxHitpoints;
        boolean followPlayer = (Double) getOption("follow_player").getSelectedValue(p).getValue() > 0;
        BarrierProperties properties = new BarrierProperties(p, center, activeHitpoints, BarrierType.COMBINED, activeRadius);
        properties.getHealthBar().addPlayer(p);

        new BukkitRunnable() {
            int ticksPassed = 0;

            @Override
            public void run() {
                if (followPlayer) {
                    Location newCenter = p.getLocation().clone().add(0, 1, 0);
                    center.setX(newCenter.getX());
                    center.setY(newCenter.getY());
                    center.setZ(newCenter.getZ());
                    properties.setCastLocation(center);
                }

                if (properties.isBroken() || ticksPassed >= activeDuration * 20 / 4) {
                    onBarrierBreak(center);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                // Push nearby entities away (for PHYSICAL/COMBINED barriers)
                if (properties.getType() == BarrierType.PHYSICAL || properties.getType() == BarrierType.COMBINED) {
                    for (Entity entity : center.getWorld().getNearbyEntities(center, activeRadius, activeRadius, activeRadius)) {
                        if (!(entity instanceof LivingEntity living)) continue;
                        if (entity == p) continue;
                        if (SummonCombatGuard.isSummon(entity)) continue;

                        Vector push = entity.getLocation().toVector().subtract(center.toVector());
                        push.setY(0);
                        if (push.lengthSquared() < 0.01) continue;
                        push = Utils.safeNormalize(push).multiply(0.3);
                        living.setVelocity(living.getVelocity().add(push));
                    }
                }

                SpellVfx.nova(WindBarrier.this, p, center, activeRadius, SHELL_POINTS);

                SpellParticleComponent particle = new SpellParticleComponent(
                        WindBarrier.this,
                        properties,
                        p,
                        wand,
                        SpellComponentType.DEFENSE,
                        center,
                        activeRadius,
                        4
                );
                SpellComponentHandler.register(particle);

                ticksPassed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 4);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        Location center = caster.getLocation().clone().add(0, 1, 0);
        BarrierProperties properties = new BarrierProperties(caster, center, maxHitpoints, BarrierType.COMBINED, radius);

        new BukkitRunnable() {
            int ticksPassed = 0;

            @Override
            public void run() {
                if (properties.isBroken() || ticksPassed >= duration * 20 / 4) {
                    onBarrierBreak(center);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                SpellVfx.nova(WindBarrier.this, caster, center, radius, SHELL_POINTS);

                SpellParticleComponent particle = new SpellParticleComponent(
                        WindBarrier.this,
                        properties,
                        caster,
                        wand,
                        SpellComponentType.DEFENSE,
                        center,
                        radius,
                        4
                );
                SpellComponentHandler.register(particle);

                ticksPassed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 4);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.windbarrier.book_name"))
                .addCustomLoreLine(lang().get("spells.windbarrier.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }

    @Override
    public void onHit(double damage, AttackSpell source) {
    }

    @Override
    public void onBarrierBreak(Location center) {
        ParticleCaster.spawn(null, center.getWorld(), Utils.CLOUD, center, 50, radius, 1, radius, 0.2);
        ParticleCaster.spawn(null, center.getWorld(), Utils.DUST, center, 0, PALETTE.coreDust());
        ParticleCaster.spawn(null, center.getWorld(), Utils.EXPLOSION, center, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
    }
}
