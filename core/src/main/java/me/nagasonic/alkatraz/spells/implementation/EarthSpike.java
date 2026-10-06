package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.api.dom.Ground;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.components.SpellBlockComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EarthSpike extends AttackSpell implements Listener {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `EARTH` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(160, 82, 45);

    /**
     * The spike cluster's signature {@code ground} shape: TIGHT and small, revealed outward from
     * the centre. That is deliberately the opposite of {@code tremor}'s configuration - tremor is
     * long, wide and multi-lane, this is a 1.5-block pad at 5x5 - so the two ground-shaped earth
     * spells cannot read as the same picture at a glance.
     */
    private static final double SPIKE_REACH = 1.5;
    private static final int SPIKE_SAMPLES = 5;
    private static final int SPIKE_LANES = 5;

    /** Minimum number of ticks the cluster is revealed over, so a 1-high spike still animates. */
    private static final int SPIKE_MIN_REVEAL_TICKS = 1;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private long selfDestructTicks;
    private boolean blockDamage;
    private long recoverTime;

    public EarthSpike(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.BLOCK_DUST, location, 15, Material.DIRT.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.BLOCK_DUST, location, 30, Material.DIRT.createBlockData());
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/earth_spike.yml");
        Alkatraz.getInstance().saveConfig("spells/earth_spike_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earth_spike.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
        this.selfDestructTicks = spellConfig.getLong("self_destruct_ticks", 100);
        this.blockDamage = spellConfig.getBoolean("block_damage", true);
        this.recoverTime = spellConfig.getLong("recover_time", 0);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player player, ItemStack wand) {
        Block target = player.getTargetBlockExact((int) Math.round((Double) getOption("spike_reach").getSelectedValue(player).getValue()));
        if (target == null || !Ground.isGround(target.getType())) {
            Utils.sendActionBar(player, lang().get("spells.earthspike.must_be_cast_on_earth"));
            cancelCast(player);
            return;
        }
        if (!target.getType().isSolid()) {
            cancelCast(player);
            return;
        }
        AttackProperties props = new AttackProperties(player, Utils.castLocation(player), getBasePower() * getWandPower(wand, player), AttackType.PHYSICAL);
        double heightMultiplier = (Double) getOption("spike_height").getSelectedValue(player).getValue();
        boolean selfDestruct = (boolean) getOption("self_destruct").getSelectedValue(player).getValue();

        Map<BlockFace, Integer> columns = new HashMap<>();
        columns.put(BlockFace.SELF, 7);
        columns.put(BlockFace.NORTH, 5);
        columns.put(BlockFace.SOUTH, 5);
        columns.put(BlockFace.EAST, 5);
        columns.put(BlockFace.WEST, 5);
        columns.put(BlockFace.NORTH_EAST, 2);
        columns.put(BlockFace.NORTH_WEST, 2);
        columns.put(BlockFace.SOUTH_EAST, 2);
        columns.put(BlockFace.SOUTH_WEST, 2);

        Map<Location, Material> originals = new HashMap<>();
        int maxHeight = 0;

        for (Map.Entry<BlockFace, Integer> entry : columns.entrySet()) {
            int h = Math.max(1, (int) Math.round(entry.getValue() * heightMultiplier));
            maxHeight = Math.max(maxHeight, h);
        }

        drawSpikeCluster(props, player, target.getLocation().add(0.5, 0.05, 0.5), maxHeight);

        for (Map.Entry<BlockFace, Integer> entry : columns.entrySet()) {
            Block block = target.getRelative(entry.getKey());
            if (props.isCountered() || props.isCancelled()) return;
            int height = Math.max(1, (int) Math.round(entry.getValue() * heightMultiplier));
            World world = block.getWorld();
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();

            if (selfDestruct || recoverTime > 0) {
                for (int dy = -height; dy <= height; dy++) {
                    Location l = new Location(world, x, y + dy, z);
                    originals.putIfAbsent(l, l.getBlock().getType());
                }
            }

            BukkitRunnable task = new BukkitRunnable() {
                int step = 0;
                @Override
                public void run() {
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    if (blockDamage) {
                        for (int i = 0; i <= height; i++) {
                            if (props.isCountered() || props.isCancelled()) {
                                cancel();
                                return;
                            }
                            Block from = world.getBlockAt(x, y - i + step, z);
                            Block to   = world.getBlockAt(x, y - i + step + 1, z);
                            if (Ground.isGround(from.getType()) && Protection.blockEdit(player, to.getLocation())) {
                                to.setType(from.getType(), false);
                            }
                        }
                    }

                    Block topBlock = world.getBlockAt(x, y + step, z);
                    SpellBlockComponent comp = new SpellBlockComponent(
                            EarthSpike.this,
                            props,
                            player,
                            wand,
                            SpellComponentType.OFFENSE,
                            topBlock,
                            1,
                            2
                    );
                    SpellComponentHandler.register(comp);
                    for (Entity entity : world.getNearbyEntities(topBlock.getLocation(), 1.5, 1.5, 1.5)) {
                        if (props.isCountered() || props.isCancelled()) {
                            return;
                        }
                        if (entity instanceof LivingEntity le && !le.equals(player)) {
                            // Deduped per target per cast. Without this a standing
                            // target took one hit per rising step: 35 to 105 depending
                            // on the spike_height option.
                            if (props.hasHit(le)) continue;
                            props.hit(le);
                            SpellDamageUtil.damageWithSpell(le,
                                    getPower(player, le, props.getRemainingPower()),
                                    player, wand, EarthSpike.this);
                            le.setVelocity(new Vector(0, 1, 0));
                        }
                    }
                    if (blockDamage) {
                        Block topClear = world.getBlockAt(x, y - height + step, z);
                        if (Protection.blockEdit(player, topClear.getLocation())) {
                            topClear.setType(Material.AIR, false);
                        }
                    }
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    step++;
                    if (step >= height) cancel();
                }
            };
            task.runTaskTimer(Alkatraz.getInstance(), 0, 1);
        }

        if (selfDestruct) {
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
            }.runTaskLater(Alkatraz.getInstance(), maxHeight + selfDestructTicks);
        } else if (recoverTime > 0 && !originals.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Map.Entry<Location, Material> e : originals.entrySet()) {
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
        Block target = caster.getTarget().getLocation().add(0, -0.5, 0).getBlock();
        if (!Ground.isGround(target.getType())) {
            return;
        }
        if (target == null || !target.getType().isSolid()) return;
        double wandp = getWandPowerOrDefault(wand);
        // RAW base power into AttackProperties - getPower is applied once per damage
        // instance, in the rising-step loop. Pre-scaling here too would square the
        // option modifiers and the affinity term.
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * wandp, AttackType.PHYSICAL);
        Map<BlockFace, Integer> columns = new HashMap<>();
        columns.put(BlockFace.SELF, 7);
        columns.put(BlockFace.NORTH, 5);
        columns.put(BlockFace.SOUTH, 5);
        columns.put(BlockFace.EAST, 5);
        columns.put(BlockFace.WEST, 5);
        columns.put(BlockFace.NORTH_EAST, 2);
        columns.put(BlockFace.NORTH_WEST, 2);
        columns.put(BlockFace.SOUTH_EAST, 2);
        columns.put(BlockFace.SOUTH_WEST, 2);

        boolean selfDestruct = false;
        Map<Location, Material> originals = new HashMap<>();
        int maxHeight = 7;

        drawSpikeCluster(props, caster, target.getLocation().add(0.5, 0.05, 0.5), maxHeight);

        for (Map.Entry<BlockFace, Integer> entry : columns.entrySet()) {
            Block block = target.getRelative(entry.getKey());
            if (props.isCountered() || props.isCancelled()) return;
            int height = entry.getValue();
            World world = block.getWorld();
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();

            if (recoverTime > 0) {
                for (int dy = -height; dy <= height; dy++) {
                    Location l = new Location(world, x, y + dy, z);
                    originals.putIfAbsent(l, l.getBlock().getType());
                }
            }

            BukkitRunnable task = new BukkitRunnable() {
                int step = 0;
                @Override
                public void run() {
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    if (blockDamage) {
                        for (int i = 0; i <= height; i++) {
                            if (props.isCountered() || props.isCancelled()) {
                                cancel();
                                return;
                            }
                            Block from = world.getBlockAt(x, y - i + step, z);
                            Block to   = world.getBlockAt(x, y - i + step + 1, z);
                            if (Ground.isGround(from.getType()) && Protection.blockEdit(caster, to.getLocation())) {
                                to.setType(from.getType(), false);
                            }
                        }
                    }

                    Block topBlock = world.getBlockAt(x, y + step, z);
                    SpellBlockComponent comp = new SpellBlockComponent(
                            EarthSpike.this,
                            props,
                            caster,
                            wand,
                            SpellComponentType.OFFENSE,
                            topBlock,
                            1,
                            2
                    );
                    SpellComponentHandler.register(comp);
                    for (Entity entity : world.getNearbyEntities(topBlock.getLocation(), 1.5, 1.5, 1.5)) {
                        if (props.isCountered() || props.isCancelled()) {
                            return;
                        }
                        if (entity instanceof LivingEntity le && !le.equals(caster)) {
                            // Deduped per target per cast. Without this a standing
                            // target took one hit per rising step: 35 to 105 depending
                            // on the spike_height option.
                            if (props.hasHit(le)) continue;
                            props.hit(le);
                            SpellDamageUtil.damageWithSpell(le,
                                    getPower(caster, le, props.getRemainingPower()),
                                    caster, wand, EarthSpike.this);
                            le.setVelocity(new Vector(0, 1, 0));
                        }
                    }
                    if (blockDamage) {
                        Block topClear = world.getBlockAt(x, y - height + step, z);
                        if (Protection.blockEdit(caster, topClear.getLocation())) {
                            topClear.setType(Material.AIR, false);
                        }
                    }
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    step++;
                    if (step >= height) cancel();
                }
            };
            task.runTaskTimer(Alkatraz.getInstance(), 0, 1);
        }

        if (selfDestruct) {
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
            }.runTaskLater(Alkatraz.getInstance(), maxHeight + selfDestructTicks);
        } else if (recoverTime > 0 && !originals.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Map.Entry<Location, Material> e : originals.entrySet()) {
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
     * Reveals the spike cluster's {@code ground} tell outward from its centre over the same number
     * of ticks the tallest spike takes to rise, so the dust and the spikes appear together.
     *
     * <p>Visual only, and stopped early on a counted or cancelled cast so a countered spike leaves
     * no task behind.
     *
     * @param props   the cast's properties, polled for counter/cancel
     * @param caster  the entity casting, used for per-player density falloff
     * @param ground  the cluster's centre point on the ground
     * @param maxHeight the tallest spike in this cast, in blocks
     */
    private void drawSpikeCluster(AttackProperties props, LivingEntity caster, Location ground, int maxHeight) {
        final int revealTicks = Math.max(SPIKE_MIN_REVEAL_TICKS, maxHeight);
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (props.isCountered() || props.isCancelled() || step > revealTicks) {
                    cancel();
                    return;
                }
                SpellVfx.ground(CAST_COLOR, caster, ground,
                        SPIKE_REACH, SPIKE_REACH, SPIKE_SAMPLES, SPIKE_LANES,
                        caster.getLocation().getYaw(), (double) step / revealTicks);
                step++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.earthspike.book_name"))
                .addCustomLoreLine(lang().get("spells.earthspike.lore1"))
                .addCustomLoreLine(lang().get("spells.earthspike.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }
}
