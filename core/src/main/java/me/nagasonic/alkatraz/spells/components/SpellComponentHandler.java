package me.nagasonic.alkatraz.spells.components;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.types.properties.implementation.BarrierProperties;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpellComponentHandler implements Listener {

    private static final Map<UUID, SpellComponent> activeComponents = new ConcurrentHashMap<>();
    private static int deflectTickCounter = 0;

    public static void register(SpellComponent component) {
        activeComponents.put(component.getComponentID(), component);
    }

    public static void remove(UUID uuid) {
        activeComponents.remove(uuid);
    }

    public static Map<UUID, SpellComponent> getActiveComponents() {
        return activeComponents;
    }

    public static SpellComponent getActiveComponent(UUID uuid){
        return activeComponents.get(uuid);
    }

    /**
     * Start the tick loop to update components
     */
    public static void tick() {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                detectCollisions();
                deflectTickCounter++;
                if (deflectTickCounter >= 2) {
                    deflectProjectiles();
                    deflectTickCounter = 0;
                }
                for (SpellComponent comp : activeComponents.values()){
                    if (comp instanceof SpellParticleComponent p){
                        p.tick();
                    }else if (comp instanceof SpellBlockComponent b){
                        b.tick();
                    }
                }
            }
        };
        task.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    private static void deflectProjectiles() {
        for (SpellComponent comp : activeComponents.values()) {
            if (comp.getType() != SpellComponentType.DEFENSE) continue;
            if (!(comp.getProperties() instanceof BarrierProperties props)) continue;
            if (props.getType() != BarrierType.PHYSICAL && props.getType() != BarrierType.COMBINED) continue;

            Location center = props.getCastLocation();
            double radius = props.getRadius();

            for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (!(entity instanceof Projectile projectile)) continue;

                Vector vel = projectile.getVelocity();
                if (!Double.isFinite(vel.getX()) || !Double.isFinite(vel.getY()) || !Double.isFinite(vel.getZ())) continue;

                Vector fromCenter = projectile.getLocation().toVector().subtract(center.toVector());
                if (fromCenter.lengthSquared() < 0.0001) continue;

                if (vel.dot(fromCenter) > 0) continue;

                double speed = Math.max(vel.length(), 0.5) * 1.2;

                Vector deflected = fromCenter.normalize().multiply(speed);
                deflected.setY(deflected.getY() + 0.3);

                if (!Double.isFinite(deflected.getX()) || !Double.isFinite(deflected.getY()) || !Double.isFinite(deflected.getZ())) continue;

                projectile.setVelocity(deflected);
            }
        }
    }

    public static void detectCollisions(){
        List<SpellComponent> components = new ArrayList<>(activeComponents.values());

        for (int i = 0; i < components.size(); i++) {
            SpellComponent comp = components.get(i);
            if (comp == null) continue;
            Location loc = getLocation(comp);
            if (loc == null) continue;

            for (int j = i + 1; j < components.size(); j++) {
                SpellComponent other = components.get(j);
                if (other == null) continue;
                Location otherLoc = getLocation(other);
                if (otherLoc == null) continue;

                if (loc.getWorld() != otherLoc.getWorld()) continue;

                double collisionRadius = Math.max(
                        comp.getCollisionRadius(),
                        other.getCollisionRadius()
                );
                if (loc.distance(otherLoc) <= collisionRadius) {
                    collide(comp, other, loc, otherLoc);
                }
            }
        }
    }

    private static Location getLocation(SpellComponent comp) {
        if (comp instanceof SpellEntityComponent e) {
            return e.getEntity().getLocation();
        } else if (comp instanceof SpellParticleComponent p) {
            return p.getLocation();
        } else if (comp instanceof SpellBlockComponent b) {
            return b.getBlock().getLocation();
        }
        return null;
    }

    /**
     * Handles collisions between two components (entity or particle)
     */
    public static void collide(SpellComponent a, SpellComponent b, Location aLoc, Location bLoc) {
        if (a == b) return;

        Spell sa = a.getSpell();
        Spell sb = b.getSpell();
        if (sa == sb) return;
        if (a.getCaster() == b.getCaster()) return;

        SpellComponent offenseComp = null;
        SpellComponent defenseComp = null;
        Location offenseLoc = null;
        Location defenseLoc  =null;

        if (a.getType() == SpellComponentType.OFFENSE &&
                b.getType() == SpellComponentType.DEFENSE) {
            offenseComp = a;
            defenseComp = b;
            offenseLoc = aLoc;
            defenseLoc = bLoc;
        } else if (b.getType() == SpellComponentType.OFFENSE &&
                a.getType() == SpellComponentType.DEFENSE) {
            offenseComp = b;
            defenseComp = a;
            offenseLoc = bLoc;
            defenseLoc = aLoc;
        }

        if (offenseComp != null && defenseComp != null &&
                offenseComp.getSpell() instanceof AttackSpell attack &&
                defenseComp.getSpell() instanceof BarrierSpell barrier &&
                offenseComp.getProperties() instanceof AttackProperties attackProps &&
                defenseComp.getProperties() instanceof BarrierProperties barrierProps) {

            if (attackProps.getCollided().contains(barrierProps)) return;
            if (attackProps.getType() == AttackType.MAGIC){
                if (barrierProps.getType() == BarrierType.PHYSICAL) return;
                attackProps.getCollided().add(barrierProps);
                barrierProps.getCollided().add(attackProps);

                LivingEntity caster = offenseComp.getCaster();
                double damage = attackProps.getRemainingPower();
                double barrierHP = barrierProps.getHitpoints();

                barrierProps.damage(damage);
                barrier.onHit(damage, attack);
                attack.onHitBarrier(barrier, defenseLoc, caster);

                if (!barrierProps.isBroken()) {
                    attack.onCountered(offenseLoc);
                    attackProps.counter();
                    if (offenseComp instanceof SpellEntityComponent sec){
                        sec.getEntity().remove();
                    }
                    SpellComponentHandler.remove(offenseComp.getComponentID());
                    return;
                }

                double ratio = damage / barrierHP;
                if (ratio >= 1.25) {
                    attackProps.reducePower(barrierHP / damage);
                    return;
                }

                attack.onCountered(offenseLoc);
                attackProps.counter();
                if (offenseComp instanceof SpellEntityComponent sec){
                    sec.getEntity().remove();
                }
                SpellComponentHandler.remove(offenseComp.getComponentID());
            } else if (attackProps.getType() == AttackType.PHYSICAL){
                if (barrierProps.getType() == BarrierType.MAGIC) return;
                attackProps.getCollided().add(barrierProps);
                barrierProps.getCollided().add(attackProps);

                LivingEntity caster = offenseComp.getCaster();
                double damage = attackProps.getRemainingPower();
                double barrierHP = barrierProps.getHitpoints();

                barrierProps.damage(damage);
                barrier.onHit(damage, attack);
                attack.onHitBarrier(barrier, defenseLoc, caster);

                if (!barrierProps.isBroken()) {
                    attack.onCountered(offenseLoc);
                    attackProps.counter();
                    if (offenseComp instanceof SpellEntityComponent sec){
                        sec.getEntity().remove();
                    }else if (offenseComp instanceof SpellBlockComponent sbc){
                        sbc.getBlock().setType(Material.AIR);
                    }
                    SpellComponentHandler.remove(offenseComp.getComponentID());
                    return;
                }

                double ratio = damage / barrierHP;
                if (ratio >= 1.25) {
                    attackProps.reducePower(barrierHP / damage);
                    return;
                }

                attack.onCountered(offenseLoc);
                attackProps.counter();
                if (offenseComp instanceof SpellEntityComponent sec){
                    sec.getEntity().remove();
                } else if (offenseComp instanceof SpellBlockComponent sbc) {
                    sbc.getBlock().setType(Material.AIR);
                }
                SpellComponentHandler.remove(offenseComp.getComponentID());
            }

        }
    }

}
