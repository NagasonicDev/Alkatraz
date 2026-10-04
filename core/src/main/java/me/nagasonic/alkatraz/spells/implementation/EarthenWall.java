package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.api.dom.Ground;
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
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.hooks.Protection;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EarthenWall extends AttackSpell implements Listener {

    /**
     * The wall's signature {@code column}, drawn at the head of the rising path.
     *
     * <p>{@code fire_wall} shares this builder, and the palette is the only thing that tells them
     * apart on screen - so this spells nothing but shape and lets {@code EARTH}'s dark browns
     * carry the colour, which is precisely what keeps it off fire-orange.
     */
    private static final double WALL_COLUMN_RADIUS = 0.35;
    private static final int WALL_COLUMN_POINTS = 6;
    private static final double WALL_COLUMN_PHASE_STEP = 0.5;
    private static final double WALL_COLUMN_LIFT = 0.05;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double duration;
    private double height;
    private double length;
    private long selfDestructTicks;
    private boolean blockDamage;
    private long recoverTime;
    private double denialDamageCap;

    public EarthenWall(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.BLOCK, location, 15, Material.DIRT.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.BLOCK, location, 30, 1, 1, 1, 0.1, Material.STONE.createBlockData());
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 1);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/earthen_wall_options.yml");
        Alkatraz.getInstance().save("spells/earthen_wall.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earthen_wall.yml").get();
        loadCommonConfig(spellConfig);
        this.duration = spellConfig.getDouble("duration");
        this.height = spellConfig.getDouble("height");
        this.length = spellConfig.getDouble("length");
        this.selfDestructTicks = spellConfig.getLong("self_destruct_ticks", 100);
        this.blockDamage = spellConfig.getBoolean("block_damage", true);
        this.recoverTime = spellConfig.getLong("recover_time", 0);
        this.denialDamageCap = spellConfig.getDouble("denial_damage_cap", 9.0);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
        loadOptions();
    }

    /**
     * The most total damage one wall may deal to a single target per cast. A DENIAL
     * spell is judged on blocked block-seconds, so its incidental damage is bounded
     * by this rather than by the number of rising steps.
     */
    private double getDenialDamageCap() {
        return denialDamageCap;
    }

    @Override
    public void castAction(Player player, ItemStack wand) {
        AttackProperties props = new AttackProperties(player, Utils.castLocation(player),
                getBasePower() * getWandPower(wand, player), AttackType.MAGIC);
        Location start = player.getLocation();

        double spacing = 0.5;
        boolean selfDestruct = (boolean) getOption("self_destruct").getSelectedValue(player).getValue();

        List<WallSegment> wallPoints = new ArrayList<>();
        Map<Location, Material> originals = new HashMap<>();

        // DENIAL CAPTURE: how much damage this cast has already dealt to each
        // target, so the wall's incidental damage cannot run away. The design
        // total is 9.0 per target per cast (0.5/hit x 18).
        final Map<UUID, Double> dealt = new HashMap<>();
        final double cap = getDenialDamageCap();

        Location currentPos = start.clone();
        Vector currentDir = player.getEyeLocation().getDirection().normalize();
        currentDir.setY(0);
        Utils.safeNormalizeInPlace(currentDir);

        float[] lastYaw = {-player.getEyeLocation().getYaw()};
        double turnStrength = 0.015;
        double finalLength = getModifiedStat(player, "wall_length",
                (Double) getOption("wall_length").getSelectedValue(player).getValue());
        double finalHeight = getModifiedStat(player, "wall_height",
                (Double) getOption("wall_height").getSelectedValue(player).getValue());
        int heightBlocks = (int) Math.ceil(finalHeight);
        int totalSegments = (int) Math.ceil(finalLength / spacing);

        new BukkitRunnable() {
            int ticks = 0;
            boolean buildDone = false;
            boolean sinking = false;

            @Override
            public void run() {
                ticks++;

                // === BUILD PHASE — steer and add new segments ===
                if (!buildDone && !props.isCountered() && !props.isCancelled()) {
                    float currentYaw = -player.getEyeLocation().getYaw();
                    float yawDelta = currentYaw - lastYaw[0];
                    lastYaw[0] = currentYaw;
                    yawDelta = Math.max(-10, Math.min(10, yawDelta));
                    currentDir.rotateAroundY(yawDelta * turnStrength);
                    currentPos.add(currentDir.clone().multiply(spacing));
                    Location ground = Utils.findTopSolid(currentPos.clone().add(0, 3, 0), 10);
                    if (ground != null) {
                        WallSegment segment = new WallSegment(ground);
                        if (selfDestruct || recoverTime > 0) {
                            segment.trackOriginals(heightBlocks, originals);
                        }
                        wallPoints.add(segment);
                    }
                    if (wallPoints.size() >= totalSegments) {
                        buildDone = true;
                    }
                }

                // === RENDER PHASE — rise or sink blocks ===
                for (WallSegment segment : wallPoints) {
                    if (!sinking) {
                        if (segment.step < heightBlocks) {
                            World world = segment.base.getWorld();
                            int x = segment.base.getBlockX();
                            int y = segment.base.getBlockY();
                            int z = segment.base.getBlockZ();

                            if (blockDamage) {
                                // WAS: boolean allowed = Protection.blockEdit(player, segment.columnCenter);
                                // hoisted OUT of the inner loop, so the whole column was
                                // written or skipped on the BASE block's answer and a wall
                                // spanning a region boundary wrote into the denied region.
                                // The gate is now per block.
                                for (int i = 0; i <= heightBlocks; i++) {
                                    Block from = world.getBlockAt(x, y - i + segment.step, z);
                                    Block to = world.getBlockAt(x, y - i + segment.step + 1, z);
                                    if (!Protection.blockEdit(player, to.getLocation())) continue;
                                    if (Ground.isGround(from.getType())) {
                                        to.setType(from.getType(), false);
                                    }
                                }

                                Block clearBlock = world.getBlockAt(x, y - heightBlocks + segment.step, z);
                                if (Protection.blockEdit(player, clearBlock.getLocation())) {
                                    clearBlock.setType(Material.AIR, false);
                                }
                            }

                            Block topBlock = world.getBlockAt(x, y + segment.step, z);
                            SpellParticleComponent comp = new SpellParticleComponent(
                                    EarthenWall.this, props, player, wand,
                                    SpellComponentType.OFFENSE, topBlock.getLocation(), 0.3, 2);
                            SpellComponentHandler.register(comp);

                            for (Entity entity : topBlock.getWorld().getNearbyEntities(topBlock.getLocation(), 0.5, 0.5, 0.5)) {
                                if (!(entity instanceof LivingEntity)) continue;
                                if (entity.equals(player)) continue;
                                LivingEntity target = (LivingEntity) entity;

                                // THE GATE IS THE PER-TARGET ACCUMULATOR, NOT hasHit.
                                // This is the 15-45 defect: `EarthenWall` has no
                                // dedup at all today, so a standing target takes one
                                // hit per rising step of every overlapping segment.
                                // Spec section 7 prices the fix at `power: 0.5`/hit and
                                // a 9.0 per-cast total - 18 hits - so repeats are
                                // REQUIRED, and `denial_damage_cap` is what bounds
                                // them. Adding `if (props.hasHit(target)) continue;`
                                // here would leave the accumulator written once and
                                // never read again, i.e. dead config, and would drop
                                // the wall to a single 0.5 hit per target per cast.
                                // `props.hit` is still recorded so the shared
                                // properties know who has been hit (rules 2 and 5),
                                // but it does not gate anything.

                                // ...and capped, so the wall cannot exceed 9.0 per target.
                                double already = dealt.getOrDefault(target.getUniqueId(), 0.0);
                                double room = cap - already;
                                if (room <= 0) continue;

                                // ONE getPower per hit, applied through the context's
                                // remaining power. `power` now reaches damage.
                                //
                                // This is the raw 3-arg overload rather than
                                // `context.power(target)` and that is deliberate:
                                // segments rise in lockstep, so a target standing
                                // between two of them is scanned twice in ONE tick,
                                // and `context.power` throws on a second application
                                // to one target in one tick. Each call here is a
                                // DISTINCT damage instance, so section 4 rule 1 - one
                                // getPower per damage instance - still holds. Do not
                                // "fix" this into `context.power(target)`.
                                double damage = Math.min(getPower(player, target, props.getRemainingPower()), room);
                                dealt.put(target.getUniqueId(), already + damage);
                                props.hit(target);
                                SpellDamageUtil.damageWithSpell(target, damage, player, wand, EarthenWall.this);
                                target.setVelocity(new Vector(0, 0.5, 0));
                            }

                            segment.step++;
                            drawWallColumnHead(player, wallPoints);
                        } else {
                            segment.risen = true;
                        }
                    } else {
                        segment.sinkAge++;
                        double sinkProgress = Math.min(1.0, segment.sinkAge / 20.0);
                        int visibleHeight = Math.max(0, heightBlocks - (int) Math.floor(heightBlocks * sinkProgress));
                        World world = segment.base.getWorld();
                        for (int h = 0; h < heightBlocks; h++) {
                            if (h >= visibleHeight) {
                                Block block = world.getBlockAt(segment.base.getBlockX(),
                                        segment.base.getBlockY() + h, segment.base.getBlockZ());
                                if (block.getType() == segment.wallMat) {
                                    // Re-gate per block: the caster may have lost build
                                    // permission here since the cast.
                                    if (Protection.blockEdit(player, block.getLocation())) {
                                        block.setType(segment.originalAt(h), false);
                                    }
                                }
                            }
                        }
                    }
                }

                // === TRANSITION TO SINK (self-destruct only) ===
                if (selfDestruct && buildDone && !sinking) {
                    boolean allRisen = true;
                    for (WallSegment segment : wallPoints) {
                        if (!segment.risen) {
                            allRisen = false;
                            break;
                        }
                    }
                    if (allRisen) {
                        long standTicks = totalSegments + selfDestructTicks;
                        if (ticks >= standTicks) {
                            sinking = true;
                        }
                    }
                }

                // === CLEANUP ===
                if (selfDestruct && sinking) {
                    boolean allSunk = true;
                    for (WallSegment segment : wallPoints) {
                        if (segment.sinkAge < 25) {
                            allSunk = false;
                            break;
                        }
                    }
                    if (allSunk) {
                        for (Map.Entry<Location, Material> e : originals.entrySet()) {
                            // WAS: e.getKey().getBlock().setType(e.getValue(), false);
                            // restoreAll re-checks Protection PER BLOCK.
                            Location loc = e.getKey();
                            if (loc == null || loc.getWorld() == null) continue;
                            Block block = loc.getBlock();
                            if (block.getType() == e.getValue()) continue;
                            if (!Protection.blockEdit(player, loc)) continue;
                            block.setType(e.getValue(), false);
                        }
                        cancel();
                    }
                }

                if (!selfDestruct && buildDone) {
                    boolean allRisen = true;
                    for (WallSegment segment : wallPoints) {
                        if (!segment.risen) {
                            allRisen = false;
                            break;
                        }
                    }
                    if (allRisen && ticks > totalSegments + 60) {
                        cancel();
                    }
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 1);

        if (recoverTime > 0 && !selfDestruct) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Map.Entry<Location, Material> e : originals.entrySet()) {
                        // WAS: e.getKey().getBlock().setType(e.getValue(), false);
                        // restoreAll re-checks Protection PER BLOCK.
                        Location loc = e.getKey();
                        if (loc == null || loc.getWorld() == null) continue;
                        Block block = loc.getBlock();
                        if (block.getType() == e.getValue()) continue;
                        if (!Protection.blockEdit(player, loc)) continue;
                        block.setType(e.getValue(), false);
                    }
                }
            }.runTaskLater(Alkatraz.getInstance(), recoverTime * 20);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.getTarget() == null) return;
        double wandp = getWandPowerOrDefault(wand);
        // RAW base power into AttackProperties - getPower is applied once per damage
        // instance, in the rising-segment loop. Pre-scaling here too would square the
        // option modifiers and the affinity term.
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * wandp, AttackType.MAGIC);
        Location start = caster.getLocation();

        double spacing = 0.5;
        int heightBlocks = (int) Math.ceil(height);
        int totalSegments = (int) Math.ceil(length / spacing);
        List<WallSegment> wallPoints = new ArrayList<>();
        Map<Location, Material> originals = new HashMap<>();

        // DENIAL CAPTURE (mob path): same contract as the player path - per CAST,
        // so mobs get their own 9.0 rather than spending the caster's.
        final Map<UUID, Double> dealt = new HashMap<>();
        final double cap = getDenialDamageCap();

        Location currentPos = start.clone();
        Vector currentDir = caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector());
        currentDir.setY(0);
        Utils.safeNormalizeInPlace(currentDir);

        new BukkitRunnable() {
            int ticks = 0;
            boolean buildDone = false;
            boolean sinking = false;

            @Override
            public void run() {
                ticks++;

                // === BUILD PHASE ===
                if (!buildDone && !props.isCountered() && !props.isCancelled()) {
                    currentPos.add(currentDir.clone().multiply(spacing));
                    Location ground = Utils.findTopSolid(currentPos.clone().add(0, 3, 0), 10);
                    if (ground != null) {
                        WallSegment segment = new WallSegment(ground);
                        segment.trackOriginals(heightBlocks, originals);
                        wallPoints.add(segment);
                    }
                    if (wallPoints.size() >= totalSegments) {
                        buildDone = true;
                    }
                }

                // === RENDER PHASE — rise or sink blocks ===
                for (WallSegment segment : wallPoints) {
                    if (!sinking) {
                        if (segment.step < heightBlocks) {
                            World world = segment.base.getWorld();
                            int x = segment.base.getBlockX();
                            int y = segment.base.getBlockY();
                            int z = segment.base.getBlockZ();

                            if (blockDamage) {
                                // Per-block gate - WAS hoisted onto segment.columnCenter
                                // outside the inner loop, so a column was written or
                                // skipped entirely on its base block's answer.
                                for (int i = 0; i <= heightBlocks; i++) {
                                    Block from = world.getBlockAt(x, y - i + segment.step, z);
                                    Block to = world.getBlockAt(x, y - i + segment.step + 1, z);
                                    if (!Protection.blockEdit(caster, to.getLocation())) continue;
                                    if (Ground.isGround(from.getType())) {
                                        to.setType(from.getType(), false);
                                    }
                                }

                                Block clearBlock = world.getBlockAt(x, y - heightBlocks + segment.step, z);
                                if (Protection.blockEdit(caster, clearBlock.getLocation())) {
                                    clearBlock.setType(Material.AIR, false);
                                }
                            }

                            Block topBlock = world.getBlockAt(x, y + segment.step, z);
                            for (Entity entity : topBlock.getWorld().getNearbyEntities(topBlock.getLocation(), 0.5, 0.5, 0.5)) {
                                if (!(entity instanceof LivingEntity)) continue;
                                if (entity.equals(caster)) continue;
                                LivingEntity target = (LivingEntity) entity;

                                // The per-target accumulator is the gate - see the
                                // player path for the full reasoning. Deliberately NOT
                                // hasHit: repeats are required and the 9.0 cap is what
                                // bounds them, and segments rise in lockstep so one
                                // target is scanned twice per tick - which is why this
                                // is the raw 3-arg getPower and not context.power.
                                double already = dealt.getOrDefault(target.getUniqueId(), 0.0);
                                double room = cap - already;
                                if (room <= 0) continue;

                                double damage = Math.min(getPower(caster, target, props.getRemainingPower()), room);
                                dealt.put(target.getUniqueId(), already + damage);
                                props.hit(target);
                                SpellDamageUtil.damageWithSpell(target, damage, caster, wand, EarthenWall.this);
                                target.setVelocity(new Vector(0, 0.5, 0));
                            }

                            segment.step++;
                            drawWallColumnHead(caster, wallPoints);
                        } else {
                            segment.risen = true;
                        }
                    } else {
                        segment.sinkAge++;
                        double sinkProgress = Math.min(1.0, segment.sinkAge / 20.0);
                        int visibleHeight = Math.max(0, heightBlocks - (int) Math.floor(heightBlocks * sinkProgress));
                        World world = segment.base.getWorld();
                        for (int h = 0; h < heightBlocks; h++) {
                            if (h >= visibleHeight) {
                                Block block = world.getBlockAt(segment.base.getBlockX(),
                                        segment.base.getBlockY() + h, segment.base.getBlockZ());
                                if (block.getType() == segment.wallMat) {
                                    // Re-gate per block: the caster may have lost build
                                    // permission here since the cast.
                                    if (Protection.blockEdit(caster, block.getLocation())) {
                                        block.setType(segment.originalAt(h), false);
                                    }
                                }
                            }
                        }
                    }
                }

                // === TRANSITION TO SINK ===
                if (buildDone && !sinking) {
                    boolean allRisen = true;
                    for (WallSegment segment : wallPoints) {
                        if (!segment.risen) {
                            allRisen = false;
                            break;
                        }
                    }
                    if (allRisen) {
                        int destroyTick = totalSegments + (int) Math.ceil(duration * 20);
                        if (ticks >= destroyTick) {
                            sinking = true;
                        }
                    }
                }

                // === CLEANUP ===
                if (sinking) {
                    boolean allSunk = true;
                    for (WallSegment segment : wallPoints) {
                        if (segment.sinkAge < 25) {
                            allSunk = false;
                            break;
                        }
                    }
                    if (allSunk) {
                        for (Map.Entry<Location, Material> e : originals.entrySet()) {
                            // WAS: e.getKey().getBlock().setType(e.getValue(), false);
                            // restoreAll re-checks Protection PER BLOCK.
                            Location loc = e.getKey();
                            if (loc == null || loc.getWorld() == null) continue;
                            Block block = loc.getBlock();
                            if (block.getType() == e.getValue()) continue;
                            if (!Protection.blockEdit(caster, loc)) continue;
                            block.setType(e.getValue(), false);
                        }
                        cancel();
                    }
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 1);

        if (recoverTime > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Map.Entry<Location, Material> e : originals.entrySet()) {
                        // WAS: e.getKey().getBlock().setType(e.getValue(), false);
                        // restoreAll re-checks Protection PER BLOCK.
                        Location loc = e.getKey();
                        if (loc == null || loc.getWorld() == null) continue;
                        Block block = loc.getBlock();
                        if (block.getType() == e.getValue()) continue;
                        if (!Protection.blockEdit(caster, loc)) continue;
                        block.setType(e.getValue(), false);
                    }
                }
            }.runTaskLater(Alkatraz.getInstance(), recoverTime * 20);
        }
    }

    /**
     * Draws the wall's signature {@code column} at the head of the path - the newest segment, at
     * exactly how far it has risen so far.
     *
     * <p>Only the head, not every segment: a 40-segment wall drawn at full density would emit 240
     * particles a tick, and the head is the part the eye follows along the wall anyway. Visual
     * only - no block, entity or property is touched.
     *
     * @param caster     the entity casting, used for per-player density falloff
     * @param wallPoints the wall's segments in build order
     */
    private void drawWallColumnHead(LivingEntity caster, List<WallSegment> wallPoints) {
        if (wallPoints.isEmpty()) return;
        WallSegment head = wallPoints.get(wallPoints.size() - 1);
        if (head.step <= 0 || head.base.getWorld() == null) return;
        Location base = head.base.clone().add(0.0, WALL_COLUMN_LIFT, 0.0);
        SpellVfx.column(EarthenWall.this, caster, base, Math.max(0.5, head.step), WALL_COLUMN_RADIUS,
                head.step, WALL_COLUMN_POINTS, WALL_COLUMN_PHASE_STEP);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.earthenwall.book_name"))
                .addCustomLoreLine(lang().get("spells.earthenwall.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }

    class WallSegment {
        Location base;
        Location columnCenter;
        int step;
        int sinkAge;
        Material wallMat;
        boolean risen;
        Material[] originalMats;

        WallSegment(Location base) {
            this.base = base;
            this.columnCenter = base.getBlock().getLocation();
            this.step = 0;
            this.sinkAge = 0;
            this.risen = false;
            Material groundMat = base.getBlock().getType();
            this.wallMat = Ground.isGround(groundMat) ? groundMat : Material.STONE;
        }

        void trackOriginals(int heightBlocks, Map<Location, Material> originals) {
            originalMats = new Material[heightBlocks];
            World world = base.getWorld();
            int x = base.getBlockX();
            int y = base.getBlockY();
            int z = base.getBlockZ();
            for (int dy = -heightBlocks; dy <= heightBlocks; dy++) {
                Block b = world.getBlockAt(x, y + dy, z);
                originals.putIfAbsent(b.getLocation(), b.getType());
            }
            for (int h = 0; h < heightBlocks; h++) {
                originalMats[h] = world.getBlockAt(x, y + h, z).getType();
            }
        }

        Material originalAt(int h) {
            if (originalMats == null || h >= originalMats.length) return Material.AIR;
            Material m = originalMats[h];
            return m == null ? Material.AIR : m;
        }
    }
}
