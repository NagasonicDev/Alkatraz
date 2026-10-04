package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.api.dom.Ground;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.components.*;
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
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Tremor extends AttackSpell implements Listener {

    private static final Map<UUID, Map<Location, Material>> recoverData = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Who raised each falling block. The restore has to be re-gated on the caster,
     * and a FallingBlock is not a LivingEntity, so the caster is recorded alongside
     * the originals rather than read off the event.
     */
    private static final Map<UUID, LivingEntity> recoverCasters = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The signature {@code ground} tell: WIDE, LOW and multi-lane, advancing along the cast
     * direction as the lanes collapse. {@code ground} takes lanes, width and yaw, so tremor uses
     * many lanes across a broad width and earth_spike uses few across a tight one - the same
     * builder, two clearly different pictures.
     *
     * <p>{@code TREMOR_LANES} matches the five gameplay lanes so the dust falls exactly where the
     * damage does.
     */
    private static final int TREMOR_LANES = 5;
    private static final double TREMOR_REACH_EXTRA = 3.0;
    private static final double TREMOR_DUST_LIFT = 0.05;

    /** The mob path's fixed lane count; its lanes are one block apart, unlike the option width. */
    private static final int TREMOR_MOB_STEPS = 11;
    private static final double TREMOR_MOB_LANE_WIDTH = 1.0;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private boolean blockDamage;
    private long recoverTime;

    public Tremor(String type) {
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
        Alkatraz.getInstance().save("spells/tremor.yml");
        Alkatraz.getInstance().saveConfig("spells/tremor_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/tremor.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
        this.blockDamage = spellConfig.getBoolean("block_damage", true);
        this.recoverTime = spellConfig.getLong("recover_time", 0);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this,  Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * getWandPower(wand, p), AttackType.PHYSICAL);
            final Location base = p.getLocation().subtract(0, 0.5, 0);
            Vector dir = p.getEyeLocation().getDirection();
            Vector perp = dir.clone().rotateAroundY(90);
            double laneWidth = (Double) getOption("tremor_width").getSelectedValue(p).getValue();
            int maxSteps = (int) Math.round((Double) getOption("tremor_length").getSelectedValue(p).getValue());
            List<Location> locations = new ArrayList<>();
            locations.add(base.clone());
            locations.add(base.clone().add(perp.clone().multiply(laneWidth)));
            locations.add(base.clone().subtract(perp.clone().multiply(laneWidth)));
            locations.add(base.clone().add(perp.clone().multiply(2 * laneWidth)));
            locations.add(base.clone().subtract(perp.clone().multiply(2 * laneWidth)));
            new BukkitRunnable(){
                private int counter = 0;
                private List<Location> previous = new ArrayList<>(locations);
                private final double reach = maxSteps + 3;

                @Override
                public void run() {
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    List<Location> toAdd = new ArrayList<>();
                    List<Location> dead = new ArrayList<>();
                    if (previous.isEmpty()) {
                        cancel();
                        return;
                    }
                    for (Location prev : previous) {
                        Location top = prev.clone();
                        top.add(dir);
                        top.add(0, 5, 0);
                        Location loc = Utils.findTopSolid(top, 10);
                        if (loc == null){
                            cancel();
                            return;
                        }
                        if (Ground.isGround(loc.getBlock().getType())){
                            SpellBlockComponent component = new SpellBlockComponent(
                                    Tremor.this,
                                    props,
                                    p,
                                    wand,
                                    SpellComponentType.OFFENSE,
                                    loc.getBlock(),
                                    1,
                                    8
                            );
                            SpellComponentHandler.register(component);
                            if (blockDamage) {
                                Map<Location, Material> originals = new java.util.HashMap<>();
                                originals.put(loc, loc.getBlock().getType());
                                BlockData data = Bukkit.createBlockData(loc.getBlock().getType());
                                if (Protection.blockEdit(p, loc)) {
                                    FallingBlock b = loc.getWorld().spawnFallingBlock(loc.add(0, 0.5, 0), data);
                                    registerRecoverData(b.getUniqueId(), originals, p);
                                    b.setHurtEntities(false);
                                    b.setVelocity(new Vector(0, 0.5, 0));
                                    NBT.modifyPersistentData(b, nbt -> {
                                        nbt.setString("spell", getId());
                                    });
                                }
                            }
                            List<Location> locs = ParticleUtils.circle(loc, 1, 1, 0, 0);
                            for (Location l : locs){
                                ParticleCaster.spawn(p, l.getWorld(), Utils.EXPLOSION, l, 1);
                            }

                            toAdd.add(loc);
                        }else{
                            dead.add(prev);
                        }
                    }
                    previous.removeAll(dead);
                    drawTremorLanes(p, base, dir, laneWidth, maxSteps, counter);
                    for (LivingEntity entity : me.nagasonic.alkatraz.util.Utils.getNearbyLivingEntities(base, reach, 20, reach)) {
                        if (entity != p && !props.hasHit(entity) && nearAnyLane(entity, toAdd)) {
                            entity.setVelocity(new Vector(0, 1, 0));
                            SpellDamageUtil.damageWithSpell(entity, getPower(p, entity, props.getRemainingPower()), p, wand, Tremor.this);
                            props.hit(entity);
                        }
                    }
                    previous.clear();
                    previous = toAdd;
                    if (counter >= maxSteps) {
                        cancel();
                    }
                    counter++;
                }
            }.runTaskTimer(Alkatraz.getInstance(), 0, 1);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (!caster.isDead() && caster.getTarget() != null){
            double wandp = getWandPowerOrDefault(wand);
            // RAW base power into AttackProperties - getPower is applied once per
            // damage instance, in the lane loop. It used to be pre-scaled here too,
            // which squared the option modifiers and the affinity term.
            AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                    getBasePower() * wandp, AttackType.PHYSICAL);
            final Location base = caster.getLocation().subtract(0, 0.5, 0);
            Vector dir = Utils.safeNormalize(caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector()));
            Vector perp = dir.clone().rotateAroundY(90);
            List<Location> locations = new ArrayList<>();
            locations.add(base.clone());
            locations.add(base.clone().add(perp));
            locations.add(base.clone().subtract(perp));
            locations.add(base.clone().add(perp.multiply(2)));
            locations.add(base.clone().subtract(perp.multiply(2)));
            new BukkitRunnable(){
                private int counter = 0;
                private List<Location> previous = new ArrayList<>(locations);
                private final double reach = 14.0;

                @Override
                public void run() {
                    if (props.isCountered() || props.isCancelled()) {
                        cancel();
                        return;
                    }
                    List<Location> toAdd = new ArrayList<>();
                    List<Location> dead = new ArrayList<>();
                    if (previous.isEmpty()) {
                        cancel();
                        return;
                    }
                    for (Location prev : previous) {
                        Location top = prev.clone();
                        top.add(dir);
                        top.add(0, 5, 0);
                        Location loc = Utils.findTopSolid(top, 10);
                        if (loc == null){
                            cancel();
                            return;
                        }
                        if (Ground.isGround(loc.getBlock().getType())){
                            SpellBlockComponent component = new SpellBlockComponent(
                                    Tremor.this,
                                    props,
                                    caster,
                                    wand,
                                    SpellComponentType.OFFENSE,
                                    loc.getBlock(),
                                    1,
                                    8
                            );
                            SpellComponentHandler.register(component);
                            if (blockDamage) {
                                Map<Location, Material> originals = new java.util.HashMap<>();
                                originals.put(loc, loc.getBlock().getType());
                                BlockData data = Bukkit.createBlockData(loc.getBlock().getType());
                                if (Protection.blockEdit(caster, loc)) {
                                    FallingBlock b = loc.getWorld().spawnFallingBlock(loc.add(0, 0.5, 0), data);
                                    registerRecoverData(b.getUniqueId(), originals, caster);
                                    b.setHurtEntities(false);
                                    b.setVelocity(new Vector(0, 0.5, 0));
                                    NBT.modifyPersistentData(b, nbt -> {
                                        nbt.setString("spell", getId());
                                    });
                                }
                            }
                            List<Location> locs = ParticleUtils.circle(loc, 1, 1, 0, 0);
                            for (Location l : locs){
                                ParticleCaster.spawn(caster, l.getWorld(), Utils.EXPLOSION, l, 1);
                            }

                            toAdd.add(loc);
                        }else{
                            dead.add(prev);
                        }
                    }
                    previous.removeAll(dead);
                    drawTremorLanes(caster, base, dir, TREMOR_MOB_LANE_WIDTH, TREMOR_MOB_STEPS, counter);
                    for (LivingEntity entity : me.nagasonic.alkatraz.util.Utils.getNearbyLivingEntities(base, reach, 20, reach)) {
                        if (entity != caster && !props.hasHit(entity) && nearAnyLane(entity, toAdd)) {
                            entity.setVelocity(new Vector(0, 1, 0));
                            SpellDamageUtil.damageWithSpell(entity, getPower(caster, entity, props.getRemainingPower()), caster, wand, Tremor.this);
                            props.hit(entity);
                        }
                    }
                    previous.clear();
                    previous = toAdd;
                    if (counter == 11) {
                        cancel();
                    }
                    counter++;
                }
            }.runTaskTimer(Alkatraz.getInstance(), 0, 1);
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    /**
     * Draws tremor's signature {@code ground} tell: a wide, low, multi-lane slab of earth dust
     * advancing down the cast direction, one block of reveal per lane step.
     *
     * <p>Visual only. The slab is flat at the caster's foot level - the lane blocks themselves
     * resolve their own height with {@code findTopSolid}, and the dust is the tell on the ground
     * the caster is standing on rather than a per-lane surface.
     *
     * @param caster    the entity casting, used for per-player density falloff
     * @param base      the caster's ground point
     * @param direction the lane advance direction; only its X/Z yaw is used
     * @param laneWidth the spacing between gameplay lanes, which sets the slab's total width
     * @param maxSteps  how many lane steps this cast runs
     * @param step      the step this tick
     */
    private void drawTremorLanes(LivingEntity caster, Location base, Vector direction,
                                 double laneWidth, int maxSteps, int step) {
        double length = Math.max(1.0, maxSteps + TREMOR_REACH_EXTRA);
        // The outermost gameplay lane sits at 2 * laneWidth either side of the centre lane.
        double width = 4.0 * Math.max(0.1, laneWidth);
        int stations = Math.max(1, (int) Math.ceil(length));
        double advance = Math.min(1.0, (double) step / Math.max(1, maxSteps));
        float yaw = (float) Math.toDegrees(Math.atan2(direction.getZ(), direction.getX()));
        Location ground = base.clone().add(0.0, TREMOR_DUST_LIFT, 0.0);
        SpellVfx.ground(Tremor.this, caster, ground, length, width, stations, TREMOR_LANES, yaw, advance);
    }

    private static boolean nearAnyLane(LivingEntity entity, List<Location> lanes) {
        Location entityLoc = entity.getLocation();
        for (Location lane : lanes) {
            if (entityLoc.distanceSquared(lane) <= 4.0) return true;
        }
        return false;
    }

    private void registerRecoverData(UUID entityUUID, Map<Location, Material> originals, LivingEntity caster) {
        recoverData.put(entityUUID, originals);
        recoverCasters.put(entityUUID, caster);
        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            recoverData.remove(entityUUID);
            recoverCasters.remove(entityUUID);
        }, 40L);
    }

    @EventHandler
    private void onEntityDeath(EntityDeathEvent e) {
        recoverData.remove(e.getEntity().getUniqueId());
        recoverCasters.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    private void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Alkatraz.getInstance()) return;
        recoverData.clear();
        recoverCasters.clear();
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.tremor.book_name"))
                .addCustomLoreLine(lang().get("spells.tremor.lore1"))
                .addCustomLoreLine(lang().get("spells.tremor.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }

    @EventHandler
    private void onDrop(EntityDropItemEvent e){
        if (e.getEntity() instanceof FallingBlock b){
            if (!NBT.getPersistentData(b, nbt -> nbt.hasTag("spell"))) return;
            String id = NBT.getPersistentData(b, nbt -> nbt.getString("spell"));
            if (id != null && !id.isEmpty()){
                if (id != getId()) return;
                e.setCancelled(true);
                UUID entityUUID = b.getUniqueId();
                b.remove();
                if (blockDamage && recoverTime > 0) {
                    Map<Location, Material> originals = recoverData.remove(entityUUID);
                    LivingEntity caster = recoverCasters.remove(entityUUID);
                    if (originals != null && !originals.isEmpty() && caster != null) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // WAS an unconditional setType(original, false) loop with
                                // no Protection re-check - it could rewrite a region the
                                // caster lost build permission in after casting.
                                TerrainEditor.restoreAll(caster, new TerrainEditor.RestoreTicket(originals));
                            }
                        }.runTaskLater(Alkatraz.getInstance(), recoverTime * 20);
                    }
                } else {
                    recoverData.remove(entityUUID);
                    recoverCasters.remove(entityUUID);
                }
            }
        }
    }
}
