package me.nagasonic.alkatraz.spells.components;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.DamageableBarrier;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
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
                    }else if (comp instanceof BarrierWallComponent w){
                        w.tick();
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
        } else if (comp instanceof BarrierWallComponent w) {
            return w.getLocation();
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
                offenseComp.getProperties() instanceof AttackProperties attackProps) {

            BarrierDefense def = resolveDefense(defenseComp);
            if (def == null) {
                return;
            }

            DamageableBarrier barrier = def.barrier();
            SpellProperties barrierProps = def.props();
            BarrierSpell bspell = def.spell();

            if (attackProps.getCollided().contains(barrierProps)) return;
            if (attackProps.getType() == AttackType.MAGIC){
                if (barrier.type() == BarrierType.PHYSICAL) return;
            } else if (attackProps.getType() == AttackType.PHYSICAL){
                if (barrier.type() == BarrierType.MAGIC) return;
            } else {
                return;
            }
            attackProps.getCollided().add(barrierProps);
            barrierProps.getCollided().add(attackProps);

            LivingEntity caster = offenseComp.getCaster();
            double damage = attackProps.getRemainingPower();
            double barrierHP = barrier.hitpoints();

            barrier.damage(damage);
            barrier.onHit(damage, attack);
            if (bspell != null) {
                attack.onHitBarrier(bspell, defenseLoc, caster);
            }

            if (!barrier.isBroken()) {
                attack.onCountered(offenseLoc);
                attackProps.counter();
                removeOffenseComponent(offenseComp);
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
            removeOffenseComponent(offenseComp);
            SpellComponentHandler.remove(offenseComp.getComponentID());
        }
    }

    private static void removeOffenseComponent(SpellComponent comp) {
        if (comp instanceof SpellEntityComponent sec) {
            sec.getEntity().remove();
        } else if (comp instanceof SpellBlockComponent sbc) {
            sbc.getBlock().setType(Material.AIR);
        }
    }

    private record BarrierDefense(DamageableBarrier barrier, SpellProperties props, BarrierSpell spell) {}

    private static BarrierDefense resolveDefense(SpellComponent defenseComp) {
        if (defenseComp.getSpell() instanceof BarrierSpell spell
                && defenseComp.getProperties() instanceof BarrierProperties props) {
            return new BarrierDefense(new SpellBarrierAdapter(spell, props), props, spell);
        }
        if (defenseComp.getProperties() instanceof DamageableBarrier d) {
            SpellProperties sp = defenseComp.getProperties();
            if (sp == null) return null;
            return new BarrierDefense(d, sp, null);
        }
        return null;
    }

    private record SpellBarrierAdapter(BarrierSpell spell, BarrierProperties props) implements DamageableBarrier {
        public double hitpoints() { return props.getHitpoints(); }
        public double initialHitpoints() { return props.getInitialHitpoints(); }
        public boolean isBroken() { return props.isBroken(); }
        public BarrierType type() { return props.getType(); }
        public void damage(double amount) { props.damage(amount); }
        public void onHit(double damage, AttackSpell source) { spell.onHit(damage, source); }
        public void onBreak(Location center) { spell.onBarrierBreak(center); }
        public Set<SpellProperties> collided() { return props.getCollided(); }
        public double barrierRadius() { return props.getRadius(); }
        public void setCastLocation(Location location) { props.setCastLocation(location); }
        public LivingEntity getCaster() { return props.getCaster(); }
    }

}
