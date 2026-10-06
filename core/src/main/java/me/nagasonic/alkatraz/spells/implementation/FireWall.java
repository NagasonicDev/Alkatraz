package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.DamageableBarrier;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.types.properties.implementation.BarrierProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Color;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FireWall extends AttackSpell implements Listener, DamageableBarrier {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `FIRE` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(255, 140, 0);

    /**
     * The wall's per-segment shape: a {@link SpellVfx#column} revealed slice by slice upward,
     * {@code deep} at the foot and {@code core} at the leading slice. This is the same builder
     * {@code earthen_wall} uses, and the FIRE palette is the whole difference between them -
     * orange, not brown - plus the lava tip below, which no earth wall has.
     */
    private static final double COLUMN_RADIUS = 0.35;
    /** Slice spacing, matching the 0.5-block climb the wall's blocks are placed on. */
    private static final double COLUMN_LAYER_SPACING = 0.5;
    private static final int COLUMN_LAYER_POINTS = 6;
    /** Radians each slice is rotated against the one below, so the wall reads as being wound up. */
    private static final double COLUMN_PHASE_STEP = 0.35;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public FireWall(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.FLAME, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.FLAME, location, 30);
    }

    private double duration;
    private double defLength;
    private double height;
    private double barrierHitpoints;
    private double pushStrength;
    private int igniteTicks;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/fire_wall.yml");
        Alkatraz.getInstance().saveConfig("spells/fire_wall_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fire_wall.yml").get();
        loadCommonConfig(spellConfig);
        // Every one of these previously used getDouble(key) with no default, so a
        // missing key silently produced 0.0 - a wall that deals no damage and lasts
        // zero ticks.
        duration = spellConfig.getDouble("duration", 12.5);
        defLength = spellConfig.getDouble("length", 25.0);
        height = spellConfig.getDouble("height", 3.0);
        barrierHitpoints = spellConfig.getDouble("barrier_hitpoints", 60.0);
        pushStrength = spellConfig.getDouble("push_strength", 0.35);
        igniteTicks = spellConfig.getInt("ignite_ticks", 40);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
        loadOptions();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void castAction(Player player, ItemStack wand) {
        // One shared AttackProperties for the whole wall, so one counter kills all
        // of it. RAW base power - getPower is applied once per entry burst below.
        // It used to be applied ZERO times: the damage passed
        // props.getRemainingPower() straight through, so the damage option and
        // FIRE affinity/resistance were both inert and element: FIRE was a lie.
        AttackProperties props = new AttackProperties(player, Utils.castLocation(player),
                getBasePower() * getWandPower(wand, player), AttackType.MAGIC);
        SpellAttackContext context = SpellAttackContext.of(this, props, player, wand);
        Location start = player.getLocation();

        double spacing = 0.5;
        int durationTicks = (int) Math.ceil(duration * 20); // wall lifetime
        final boolean curveWall = (boolean) getOption("wall_curves").getSelectedValue(player).getValue();
        final boolean placeFireBlocks = (boolean) getOption("fire_blocks").getSelectedValue(player).getValue();

        List<WallSegment> wallPoints = new ArrayList<>();

        // FIRE NEVER SUSTAINS. `insideWall` is the ENTRY ledger for the one ignition
        // burst per entry (spec section 8.1); `scannedThisTick` is rebuilt every tick
        // and `insideWall` is pruned against it once the segment loop closes, so a
        // target who walks out is dropped and re-entering bursts again. It is NOT
        // `props.hasHit` that gates this - see the note at the damage block.
        final Set<UUID> insideWall = new HashSet<>();
        final Set<UUID> scannedThisTick = new HashSet<>();
        // Fire blocks this wall placed, for restore on teardown. Previously they were
        // placed and NEVER removed, so every cast leaked fire into the world forever.
        final Map<Location, Material> fireOriginals = new HashMap<>();
        // ONE defense-side properties object for the whole cast. Its cast location
        // is moved onto the current segment as the wall builds.
        final WallDefenseProperties wallDefense = newDefenseProps(player, start);

        Location currentPos = start.clone();
        Vector currentDir = player.getEyeLocation().getDirection().normalize();

        float[] lastYaw = { -player.getEyeLocation().getYaw() };
        double turnStrength = 0.015;
        final double length = getModifiedStat(player, "wall_length", defLength);
        final World wallWorld = player.getWorld();

        BukkitRunnable wallTask = new BukkitRunnable() {

            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                context.tick();
                scannedThisTick.clear();

                if (props.isCancelled() || props.isCountered() || !player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                // === BUILD PHASE ===
                if (ticks < length / spacing) {
                    if (curveWall) {
                        float currentYaw = -player.getEyeLocation().getYaw();
                        float yawDelta = currentYaw - lastYaw[0];
                        lastYaw[0] = currentYaw;

                        yawDelta = Math.max(-10, Math.min(10, yawDelta));

                        // Steer ONLY next segment
                        currentDir.rotateAroundY(yawDelta * turnStrength);
                    }
                    // Step forward
                    currentPos.add(currentDir.clone().multiply(spacing));

                    // Lock segment with age
                    Location ground0 = Utils.findTopSolid(currentPos.clone().add(0, 3, 0), 10);
                    if (ground0 != null) {
                        wallPoints.add(new WallSegment(ground0));
                    }
                }

                // === RENDER + DAMAGE PHASE ===
                for (WallSegment segment : wallPoints) {
                    segment.age++;

                    double riseProgress = Math.min(1.0, segment.age / 30.0);
                    double currentHeight = height * riseProgress;
                    boolean settled = segment.age >= 30;
                    segment.refreshHeight(currentHeight);

                    if (!settled) {
                        drawSegmentColumn(player, wallWorld, segment.base, currentHeight);

                        for (double h = 0; h < currentHeight; h += 0.5) {

                            Location loc = segment.base.clone().add(0, h, 0);

                            Block b = loc.getBlock();
                            if (placeFireBlocks && b.getType() == Material.AIR) {
                                if (Protection.ignite(player, loc)) {
                                    // Track the original so the wall can be torn down.
                                    // These fire blocks were previously placed and
                                    // NEVER removed - the wall leaked fire forever.
                                    fireOriginals.putIfAbsent(b.getLocation(), Material.AIR);
                                    b.setType(Material.FIRE);
                                }
                            }
                        }

                        SpellParticleComponent comp = new SpellParticleComponent(
                                FireWall.this,
                                props,
                                player,
                                wand,
                                SpellComponentType.OFFENSE,
                                segment.mid,
                                0.25,
                                2
                        );
                        SpellComponentHandler.register(comp);

                        // DEFENSE-side component alongside the OFFENSE one, so
                        // incoming projectiles are deflected and incoming attacks
                        // are consumed instead of passing through the wall.
                        wallDefense.setCastLocation(segment.mid);
                        SpellParticleComponent defense = new SpellParticleComponent(
                                FireWall.this,
                                wallDefense,
                                player,
                                wand,
                                SpellComponentType.DEFENSE,
                                segment.mid,
                                0.6,
                                2
                        );
                        SpellComponentHandler.register(defense);
                    } else if (placeFireBlocks && segment.age % 20 == 0) {
                        for (double h = 0; h < currentHeight; h += 0.5) {
                            Location loc = segment.base.clone().add(0, h, 0);
                            Block b = loc.getBlock();
                            if (b.getType() == Material.AIR) {
                                if (Protection.ignite(player, loc)) {
                                    fireOriginals.putIfAbsent(b.getLocation(), Material.AIR);
                                    b.setType(Material.FIRE);
                                }
                            }
                        }
                    }

                    if (currentHeight <= 0) continue;
                    for (Entity entity : segment.world.getNearbyEntities(
                            segment.mid, 0.6, currentHeight / 2.0, 0.6)) {
                        if (!(entity instanceof LivingEntity)) continue;
                        if (entity.equals(player)) continue;

                        LivingEntity target = (LivingEntity) entity;
                        scannedThisTick.add(target.getUniqueId());

                        // Radial push away from the wall normal, modelled on
                        // Barrier.java:105-109. Without this an enemy walks straight
                        // through, which is the entire point of a wall.
                        Vector push = target.getLocation().toVector().subtract(segment.mid.toVector());
                        push.setY(0);
                        if (push.lengthSquared() >= 0.01) {
                            push = Utils.safeNormalize(push).multiply(pushStrength);
                            target.setVelocity(target.getVelocity().add(push));
                        }

                        // ONE IGNITION BURST PER ENTRY, then nothing until they leave
                        // and re-enter. This is DENIAL, not SUSTAIN, and the DENIAL
                        // rule (spec section 8.1) is a burst per ENTRY - so the ledger
                        // is `insideWall`, and it is the ONLY gate on the burst.
                        //
                        // Do NOT add `if (props.hasHit(target)) continue;` here.
                        // AttackProperties' hit set is never cleared for the life of
                        // the cast, so that gate would make the wall deal exactly one
                        // burst per target for the whole cast and no burst at all on
                        // every re-entry - the opposite of the spec. Same reasoning as
                        // "SUSTAIN cadence: hasHit must not gate the repeats" at the
                        // top of this wave; the conclusion is identical, only the
                        // ledger differs.
                        //
                        // This also keeps `context.power(target)` to one call per
                        // target per tick: a target inside three overlapping segments
                        // bursts on the first segment that sees them and `add` returns
                        // false on the other two, so the per-tick throw cannot fire.
                        if (!insideWall.add(target.getUniqueId())) continue;
                        props.hit(target);

                        SpellDamageUtil.damageWithSpell(target, context.power(target),
                                player, wand, FireWall.this);
                        if (Protection.ignite(player, target.getLocation())) {
                            target.setFireTicks(igniteTicks);
                        }
                    }
                }

                // THE EXIT HALF OF THE ENTRY LEDGER. `scannedThisTick` was rebuilt by
                // the scan above, so anyone not in it has walked out of the wall.
                // Without this prune `insideWall` only ever grows, and a target takes
                // exactly one burst for the entire 12.5s cast - the spec's "nothing
                // further until they leave and re-enter" would never re-arm.
                insideWall.retainAll(scannedThisTick);

                // === LIFETIME END ===
                if (ticks >= durationTicks && ticks >= length / spacing) {
                    // restoreAll re-checks Protection PER BLOCK, so a region the
                    // caster lost build permission in since casting is left alone.
                    TerrainEditor.restoreAll(player, new TerrainEditor.RestoreTicket(fireOriginals));
                    cancel();
                }
            }
        };

        wallTask.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        Entity castTarget = caster.getTarget();
        if (castTarget == null) return;
        // RAW base power. The 2-arg getPower is applied once per entry burst below.
        // It used to run here as well and again per tick on the raw remaining power,
        // so the mob wall squared the option modifiers and never scaled by affinity.
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand), AttackType.MAGIC);
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);
        Location start = caster.getLocation();

        double spacing = 0.5;
        int durationTicks = (int) Math.ceil(duration * 20); // wall lifetime

        List<WallSegment> wallPoints = new ArrayList<>();

        // FIRE NEVER SUSTAINS. `insideWall` is the ENTRY ledger for the one ignition
        // burst per entry (spec section 8.1); `scannedThisTick` is rebuilt every tick
        // and `insideWall` is pruned against it once the segment loop closes, so a
        // target who walks out is dropped and re-entering bursts again. It is NOT
        // `props.hasHit` that gates this - see the player-path note at the damage
        // block for why.
        final Set<UUID> insideWall = new HashSet<>();
        final Set<UUID> scannedThisTick = new HashSet<>();
        // Fire blocks this wall placed, for restore on teardown.
        final Map<Location, Material> fireOriginals = new HashMap<>();
        // ONE defense-side properties object for the whole cast.
        final WallDefenseProperties wallDefense = newDefenseProps(caster, start);

        Location currentPos = start.clone();
        Vector currentDir = Utils.safeNormalize(castTarget.getLocation().toVector().subtract(caster.getLocation().toVector()));

        float[] lastYaw = { -castTarget.getLocation().subtract(caster.getLocation().toVector()).getYaw() };
        double turnStrength = 0.015;
        final World wallWorld = caster.getWorld();

        BukkitRunnable wallTask = new BukkitRunnable() {

            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                context.tick();
                scannedThisTick.clear();

                if (props.isCancelled() || props.isCountered() || caster.isDead() || !caster.isValid()) {
                    cancel();
                    return;
                }
                if (castTarget.isDead() || !castTarget.isValid() || caster.getTarget() == null) {
                    cancel();
                    return;
                }

                // === BUILD PHASE ===
                if (ticks < defLength / spacing) {
                    float currentYaw = -castTarget.getLocation().subtract(caster.getLocation().toVector()).getYaw();
                    float yawDelta = currentYaw - lastYaw[0];
                    lastYaw[0] = currentYaw;

                    yawDelta = Math.max(-10, Math.min(10, yawDelta));

                    currentDir.rotateAroundY(yawDelta * turnStrength);
                    // Step forward
                    currentPos.add(currentDir.clone().multiply(spacing));

                    // Lock segment with age
                    Location ground1 = Utils.findTopSolid(currentPos.clone().add(0, 3, 0), 10);
                    if (ground1 != null) {
                        wallPoints.add(new WallSegment(ground1));
                    }
                }

                // === RENDER + DAMAGE PHASE ===
                for (WallSegment segment : wallPoints) {
                    segment.age++;

                    double riseProgress = Math.min(1.0, segment.age / 30.0);
                    double currentHeight = height * riseProgress;
                    boolean settled = segment.age >= 30;
                    segment.refreshHeight(currentHeight);

                    if (!settled) {
                        drawSegmentColumn(caster, wallWorld, segment.base, currentHeight);

                        for (double h = 0; h < currentHeight; h += 0.5) {

                            Location loc = segment.base.clone().add(0, h, 0);

                            Block b = loc.getBlock();
                            if (b.getType() == Material.AIR) {
                                if (Protection.ignite(caster, loc)) {
                                    // Track the original so the wall can be torn down.
                                    // These fire blocks were previously placed and
                                    // NEVER removed - the wall leaked fire forever.
                                    fireOriginals.putIfAbsent(b.getLocation(), Material.AIR);
                                    b.setType(Material.FIRE);
                                }
                            }
                        }

                        SpellParticleComponent comp = new SpellParticleComponent(
                                FireWall.this,
                                props,
                                caster,
                                wand,
                                SpellComponentType.OFFENSE,
                                segment.mid,
                                0.25,
                                2
                        );
                        SpellComponentHandler.register(comp);

                        // DEFENSE-side component alongside the OFFENSE one, so
                        // incoming projectiles are deflected and incoming attacks
                        // are consumed instead of passing through the wall.
                        wallDefense.setCastLocation(segment.mid);
                        SpellParticleComponent defense = new SpellParticleComponent(
                                FireWall.this,
                                wallDefense,
                                caster,
                                wand,
                                SpellComponentType.DEFENSE,
                                segment.mid,
                                0.6,
                                2
                        );
                        SpellComponentHandler.register(defense);
                    } else if (segment.age % 20 == 0) {
                        for (double h = 0; h < currentHeight; h += 0.5) {
                            Location loc = segment.base.clone().add(0, h, 0);
                            Block b = loc.getBlock();
                            if (b.getType() == Material.AIR) {
                                if (Protection.ignite(caster, loc)) {
                                    fireOriginals.putIfAbsent(b.getLocation(), Material.AIR);
                                    b.setType(Material.FIRE);
                                }
                            }
                        }
                    }

                    if (currentHeight <= 0) continue;
                    for (Entity entity : segment.world.getNearbyEntities(
                            segment.mid, 0.6, currentHeight / 2.0, 0.6)) {
                        if (!(entity instanceof LivingEntity)) continue;
                        if (entity.equals(caster)) continue;

                        LivingEntity target = (LivingEntity) entity;
                        scannedThisTick.add(target.getUniqueId());

                        // Radial push away from the wall normal, modelled on
                        // Barrier.java:105-109. Without this an enemy walks straight
                        // through, which is the entire point of a wall.
                        Vector push = target.getLocation().toVector().subtract(segment.mid.toVector());
                        push.setY(0);
                        if (push.lengthSquared() >= 0.01) {
                            push = Utils.safeNormalize(push).multiply(pushStrength);
                            target.setVelocity(target.getVelocity().add(push));
                        }

                        // ONE IGNITION BURST PER ENTRY, then nothing until they leave
                        // and re-enter. DENIAL, not SUSTAIN: spec section 8.1's allowance
                        // is a burst per ENTRY, so `insideWall` is the only gate. See
                        // the player-path note for why `props.hasHit` must NOT be added
                        // here - the hit set is never cleared for the life of the cast.
                        if (!insideWall.add(target.getUniqueId())) continue;
                        props.hit(target);

                        SpellDamageUtil.damageWithSpell(target, context.power(target),
                                caster, wand, FireWall.this);
                        if (Protection.ignite(caster, target.getLocation())) {
                            target.setFireTicks(igniteTicks);
                        }
                    }
                }

                // THE EXIT HALF OF THE ENTRY LEDGER - identical to the player path.
                insideWall.retainAll(scannedThisTick);

                // === LIFETIME END ===
                if (ticks >= durationTicks && ticks >= defLength / spacing) {
                    // restoreAll re-checks Protection PER BLOCK, so a region the
                    // caster lost build permission in since casting is left alone.
                    TerrainEditor.restoreAll(caster, new TerrainEditor.RestoreTicket(fireOriginals));
                    cancel();
                }
            }
        };

        wallTask.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    // ================================================================
    // DamageableBarrier - the DEFENSE side of the wall.
    //
    // resolveDefense() first checks `spell instanceof BarrierSpell`, which
    // FireWall cannot be (it extends AttackSpell, and Java has single
    // inheritance). Its second branch accepts a DEFENSE component whose
    // *properties* implement DamageableBarrier - and the properties are what
    // resolveDefense inspects, not the spell. So the wall's DEFENSE component
    // carries a WallDefenseProperties, and that is what actually makes an
    // incoming attack get consumed. BarrierProperties alone does NOT satisfy
    // that branch; BarrierSession, the magic item's barrier, is the precedent
    // for a SpellProperties that implements it directly.
    //
    // ONE BarrierProperties per CAST, not per segment per tick. Its
    // constructor calls Bukkit.createBossBar, so constructing one inside the
    // render loop would allocate 49 x 250 bosses per cast and leak every one.
    // The wall has no boss bar of its own, so removeAll() it immediately.
    // ================================================================

    private WallDefenseProperties newDefenseProps(LivingEntity caster, Location center) {
        WallDefenseProperties props = new WallDefenseProperties(caster, center);
        props.getHealthBar().removeAll();
        return props;
    }

    @Override
    public double hitpoints() {
        return barrierHitpoints;
    }

    @Override
    public double initialHitpoints() {
        return barrierHitpoints;
    }

    @Override
    public boolean isBroken() {
        return false;
    }

    @Override
    public BarrierType type() {
        return BarrierType.COMBINED;
    }

    @Override
    public void damage(double amount) {
        // A wall is not destroyed by chip damage; its own damage event is the
        // denial. Hitpoints exist so an incoming attack is CONSUMED rather than
        // passing through, which is what resolveDefense's consumer checks.
    }

    @Override
    public Set<me.nagasonic.alkatraz.spells.types.properties.SpellProperties> collided() {
        return Set.of();
    }

    @Override
    public double barrierRadius() {
        return 0.6;
    }

    @Override
    public void setCastLocation(Location location) {
        // the wall's defense center follows its segments; nothing to store here.
        // WallDefenseProperties carries the moving center instead.
    }

    @Override
    public LivingEntity getCaster() {
        // One FireWall instance serves every cast, so there is no single caster.
        // WallDefenseProperties, which is what gets registered, returns the real
        // one - it holds its own.
        return null;
    }

    /**
     * The DEFENSE-side properties one cast registers. Extends BarrierProperties so
     * {@code SpellComponentHandler.deflectProjectiles}, which hard-filters on
     * {@code instanceof BarrierProperties} plus PHYSICAL|COMBINED, stops filtering
     * the wall out; implements DamageableBarrier so
     * {@code SpellComponentHandler.resolveDefense}'s second branch accepts it.
     */
    private final class WallDefenseProperties extends BarrierProperties implements DamageableBarrier {

        WallDefenseProperties(LivingEntity caster, Location center) {
            super(caster, center, barrierHitpoints, BarrierType.COMBINED, 0.6);
        }

        @Override
        public double hitpoints() {
            return FireWall.this.hitpoints();
        }

        @Override
        public double initialHitpoints() {
            return FireWall.this.initialHitpoints();
        }

        @Override
        public boolean isBroken() {
            return FireWall.this.isBroken();
        }

        @Override
        public BarrierType type() {
            return FireWall.this.type();
        }

        @Override
        public void damage(double amount) {
            FireWall.this.damage(amount);
        }

        @Override
        public Set<me.nagasonic.alkatraz.spells.types.properties.SpellProperties> collided() {
            return getCollided();
        }

        @Override
        public double barrierRadius() {
            return FireWall.this.barrierRadius();
        }

        @Override
        public LivingEntity getCaster() {
            return super.getCaster();
        }
    }

    /**
     * One rising segment's visual: the shared {@link SpellVfx#column} plus a lava tip.
     *
     * <p>The lava tip is deliberately redundant with the column's own {@code core} leading
     * slice - it is the element tell that separates this wall from {@code earthen_wall},
     * which draws the same column from the EARTH palette and has no tip at all.
     *
     * @param caster        the casting entity, used for per-player density falloff
     * @param world         the segment's world
     * @param base          the segment's foot
     * @param currentHeight how far the segment has risen so far, in blocks
     */
    private void drawSegmentColumn(LivingEntity caster, World world, Location base, double currentHeight) {
        if (currentHeight <= 0.0) return;
        int layers = Math.max(1, (int) Math.ceil(currentHeight / COLUMN_LAYER_SPACING));
        SpellVfx.column(CAST_COLOR, caster, base, currentHeight, COLUMN_RADIUS,
                layers, COLUMN_LAYER_POINTS, COLUMN_PHASE_STEP);

        Location tip = base.clone().add(0.0, currentHeight, 0.0);
        ParticleCaster.spawn(caster, world, Utils.LAVA, tip, 2, 0.05, 0.05, 0.05, 0);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.firewall.book_name"))
                .addLoreLine(lang().get("spells.firewall.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }

    class WallSegment {
        Location base;
        int age;
        final World world;
        Location mid;
        double cachedHeight = -1;

        WallSegment(Location base) {
            this.base = base;
            this.age = 0;
            this.world = base.getWorld();
        }

        void refreshHeight(double currentHeight) {
            if (currentHeight == cachedHeight) return;
            cachedHeight = currentHeight;
            mid = base.clone().add(0, currentHeight / 2.0, 0);
        }
    }
}
