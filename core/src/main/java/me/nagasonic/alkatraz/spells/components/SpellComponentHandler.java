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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpellComponentHandler implements Listener {

    private static final Map<UUID, SpellComponent> activeComponents = new ConcurrentHashMap<>();
    private static int deflectTickCounter = 0;
    private static BukkitTask tickTask = null;

    private static SpellComponent[] snapComp = new SpellComponent[0];
    private static World[] snapWorld = new World[0];
    private static double[] snapX = new double[0];
    private static double[] snapY = new double[0];
    private static double[] snapZ = new double[0];
    private static double[] snapRadius = new double[0];

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
                if (activeComponents.isEmpty()) return;

                int count = fillSnapshot();

                detectCollisions(count);
                deflectTickCounter++;
                if (deflectTickCounter >= 2) {
                    deflectProjectiles(count);
                    deflectTickCounter = 0;
                }
                for (SpellComponent comp : activeComponents.values()){
                    if (comp instanceof SpellParticleComponent p){
                        p.tick();
                    }else if (comp instanceof SpellBlockComponent b){
                        b.tick();
                    }else if (comp instanceof BarrierWallComponent w){
                        w.tick();
                    }else if (comp instanceof SpellEntityComponent e){
                        Entity entity = e.getEntity();
                        if (entity == null || entity.isDead() || !entity.isValid()) {
                            remove(comp.getComponentID());
                        }
                    }
                }
            }
        };
        tickTask = task.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    /**
     * Cancels the tick loop and drops every tracked component. Must be called
     * from {@code onDisable()} so a plugin reload does not leave a zombie task.
     */
    public static void shutdown() {
        BukkitTask current = tickTask;
        tickTask = null;
        if (current != null) {
            current.cancel();
        }
        activeComponents.clear();
        deflectTickCounter = 0;
    }

    private static void deflectProjectiles(int count) {
        Set<SpellProperties> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < count; i++) {
            SpellComponent comp = snapComp[i];
            if (comp.getType() != SpellComponentType.DEFENSE) continue;
            if (!(comp.getProperties() instanceof BarrierProperties props)) continue;
            if (props.getType() != BarrierType.PHYSICAL && props.getType() != BarrierType.COMBINED) continue;
            if (!seen.add(props)) continue;

            Location center = props.getCastLocation();
            if (center == null) continue;
            World world = center.getWorld();
            if (world == null) continue;
            double radius = props.getRadius();
            double cx = center.getX();
            double cy = center.getY();
            double cz = center.getZ();

            for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                if (!(entity instanceof Projectile projectile)) continue;

                Vector vel = projectile.getVelocity();
                if (!Double.isFinite(vel.getX()) || !Double.isFinite(vel.getY()) || !Double.isFinite(vel.getZ())) continue;

                Location pLoc = projectile.getLocation();
                double fx = pLoc.getX() - cx;
                double fy = pLoc.getY() - cy;
                double fz = pLoc.getZ() - cz;
                double lenSq = fx * fx + fy * fy + fz * fz;
                if (lenSq < 0.0001) continue;

                double invLen = 1.0 / Math.sqrt(lenSq);
                fx *= invLen;
                fy *= invLen;
                fz *= invLen;

                if (vel.getX() * fx + vel.getY() * fy + vel.getZ() * fz > 0) continue;

                double speed = Math.max(vel.length(), 0.5) * 1.2;

                Vector deflected = new Vector(fx * speed, fy * speed + 0.3, fz * speed);
                if (!Double.isFinite(deflected.getX()) || !Double.isFinite(deflected.getY()) || !Double.isFinite(deflected.getZ())) continue;

                projectile.setVelocity(deflected);
            }
        }
    }

    public static void detectCollisions(){
        detectCollisions(fillSnapshot());
    }

    private static void detectCollisions(int count){
        for (int i = 0; i < count; i++) {
            SpellComponent comp = snapComp[i];
            SpellComponentType typeI = comp.getType();
            Spell spellI = comp.getSpell();
            LivingEntity casterI = comp.getCaster();
            World worldI = snapWorld[i];
            double xi = snapX[i];
            double yi = snapY[i];
            double zi = snapZ[i];
            double ri = snapRadius[i];

            for (int j = i + 1; j < count; j++) {
                if (snapWorld[j] != worldI) continue;
                SpellComponent other = snapComp[j];
                SpellComponentType typeJ = other.getType();
                if (!((typeI == SpellComponentType.OFFENSE && typeJ == SpellComponentType.DEFENSE)
                        || (typeI == SpellComponentType.DEFENSE && typeJ == SpellComponentType.OFFENSE))) continue;
                if (spellI == other.getSpell()) continue;
                if (casterI == other.getCaster()) continue;

                double dx = snapX[j] - xi;
                double dy = snapY[j] - yi;
                double dz = snapZ[j] - zi;
                double collisionRadius = Math.max(ri, snapRadius[j]);
                if (dx * dx + dy * dy + dz * dz <= collisionRadius * collisionRadius) {
                    collide(comp, other,
                            new Location(worldI, xi, yi, zi),
                            new Location(worldI, snapX[j], snapY[j], snapZ[j]));
                }
            }
        }
    }

    /**
     * Copies every live component into reusable primitive arrays so the
     * all-pairs collision pass can run without allocating. Returns the count.
     */
    private static int fillSnapshot() {
        int count = 0;
        for (SpellComponent comp : activeComponents.values()) {
            if (comp == null) continue;
            World world;
            double x;
            double y;
            double z;
            if (comp instanceof SpellEntityComponent e) {
                Entity entity = e.getEntity();
                if (entity == null) continue;
                Location eLoc = entity.getLocation();
                world = eLoc.getWorld();
                x = eLoc.getX();
                y = eLoc.getY();
                z = eLoc.getZ();
            } else if (comp instanceof SpellParticleComponent p) {
                Location loc = p.getLocation();
                if (loc == null) continue;
                world = loc.getWorld();
                x = loc.getX();
                y = loc.getY();
                z = loc.getZ();
            } else if (comp instanceof SpellBlockComponent b) {
                Block block = b.getBlock();
                if (block == null) continue;
                world = block.getWorld();
                x = block.getX();
                y = block.getY();
                z = block.getZ();
            } else if (comp instanceof BarrierWallComponent w) {
                Location loc = w.getLocation();
                if (loc == null) continue;
                world = loc.getWorld();
                x = loc.getX();
                y = loc.getY();
                z = loc.getZ();
            } else {
                continue;
            }
            if (world == null) continue;

            if (count == snapComp.length) growSnapshot(count + 1);
            snapComp[count] = comp;
            snapWorld[count] = world;
            snapX[count] = x;
            snapY[count] = y;
            snapZ[count] = z;
            snapRadius[count] = comp.getCollisionRadius();
            count++;
        }
        return count;
    }

    private static void growSnapshot(int required) {
        int capacity = Math.max(16, snapComp.length * 2);
        while (capacity < required) capacity *= 2;
        snapComp = Arrays.copyOf(snapComp, capacity);
        snapWorld = Arrays.copyOf(snapWorld, capacity);
        snapX = Arrays.copyOf(snapX, capacity);
        snapY = Arrays.copyOf(snapY, capacity);
        snapZ = Arrays.copyOf(snapZ, capacity);
        snapRadius = Arrays.copyOf(snapRadius, capacity);
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
