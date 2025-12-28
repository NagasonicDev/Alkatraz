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
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpellComponentHandler implements Listener {

    private static final Map<UUID, SpellComponent> activeComponents = new ConcurrentHashMap<>();

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

    public static void detectCollisions(){
        List<SpellComponent> components = new ArrayList<>(activeComponents.values());

        for (SpellComponent comp : components) {
            // Handle entity collisions
            if (comp instanceof SpellEntityComponent eComp) {
                Entity entity = eComp.getEntity();
                Location loc = entity.getLocation();

                for (SpellComponent other : components) {
                    if (other instanceof SpellEntityComponent otherE &&
                            !otherE.equals(eComp)) {

                        Location otherLoc = otherE.getEntity().getLocation();
                        if (loc.distance(otherLoc) <= eComp.getCollisionRadius()) {
                            collide(comp, other, loc, otherLoc);
                        }
                    }else if (other instanceof SpellParticleComponent otherP){
                        Location otherLoc = otherP.getLocation();
                        if (loc.distance(otherLoc) <= eComp.getCollisionRadius()) {
                            collide(comp, other, loc, otherLoc);
                        }
                    } else if (other instanceof SpellBlockComponent otherB) {
                        Location otherLoc = otherB.getBlock().getLocation();
                        if (loc.distance(otherLoc) <= eComp.getCollisionRadius()) {
                            collide(comp, other, loc, otherLoc);
                        }
                    }
                }
            }

            // Handle particle collisions
            if (comp instanceof SpellParticleComponent p1) {
                Location loc1 = p1.getLocation();

                for (SpellComponent other : components) {
                    if (other instanceof SpellParticleComponent p2 &&
                            !p1.equals(p2)) {

                        Location loc2 = p2.getLocation();
                        double distance = loc1.distance(loc2);

                        if (distance <= (p1.getCollisionRadius())) {
                            collide(p1, p2, loc1, loc2);
                        }
                    } else if (other instanceof SpellEntityComponent e) {
                        Location loc2 = e.getEntity().getLocation();
                        if (loc2.distance(loc1) <= p1.getCollisionRadius()){
                            collide(p1, e, loc1, loc2);
                        }
                    } else if (other instanceof SpellBlockComponent b) {
                        Location loc2 = b.getBlock().getLocation();
                        if (loc2.distance(loc1) <= p1.getCollisionRadius()){
                            collide(p1, b, loc1, loc2);
                        }
                    }
                }
            }

            if (comp instanceof SpellBlockComponent bComp){
                Location loc = bComp.getBlock().getLocation();
                for (SpellComponent other : components) {
                    if (other instanceof SpellBlockComponent b && !bComp.equals(b)){
                        Location oLoc = b.getBlock().getLocation();
                        if (loc.distance(oLoc) <= bComp.getCollisionRadius()){
                            collide(bComp, b, loc, oLoc);
                        }
                    } else if (other instanceof SpellEntityComponent e) {
                        Location loc1 = e.getEntity().getLocation();
                        if (loc.distance(loc1) <= bComp.getCollisionRadius()){
                            collide(bComp, e, loc, loc1);
                        }
                    } else if (other instanceof SpellParticleComponent p) {
                        Location loc1 = p.getLocation();
                        if (loc.distance(loc1) <= bComp.getCollisionRadius()){
                            collide(bComp, p, loc, loc1);
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles collisions between two components (entity or particle)
     */
    public static void collide(SpellComponent a, SpellComponent b, Location aLoc, Location bLoc) {
        if (a == b) return;

        Spell sa = a.getSpell();
        Spell sb = b.getSpell();
        if (sa == sb) return;

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

                Player caster = offenseComp.getCaster();
                double damage = attackProps.getRemainingPower();
                double barrierHP = barrierProps.getHitpoints();

                barrierProps.damage(damage);
                barrier.onHit(damage, attack);
                attack.onHitBarrier(barrier, defenseLoc, caster);
                Alkatraz.logFine("hp: " + barrierProps.getHitpoints());

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
                Alkatraz.logFine("ratio: " + ratio);
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

                Player caster = offenseComp.getCaster();
                double damage = attackProps.getRemainingPower();
                double barrierHP = barrierProps.getHitpoints();

                barrierProps.damage(damage);
                barrier.onHit(damage, attack);
                attack.onHitBarrier(barrier, defenseLoc, caster);
                Alkatraz.logFine("hp: " + barrierProps.getHitpoints());

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
                Alkatraz.logFine("ratio: " + ratio);
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
