package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.api.dom.Ground;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.components.SpellComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellEntityComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EarthThrow extends AttackSpell implements Listener {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `EARTH` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(160, 82, 45);
    private static final Map<UUID, Map<Location, Material>> recoverData = new ConcurrentHashMap<>();
    private static final long RECOVER_DATA_TTL_TICKS = 600L;

    /**
     * The hurled lump's projectile tell. A {@code core} head cluster on the falling block itself,
     * with an {@code accent}-to-{@code deep} tail trailing behind it - every colour from
     * {@code ElementPalette.of(EARTH)}, which is what keeps an earth lump off the same orange
     * several non-earth spells were drawing.
     */
    private static final double LUMP_CLUSTER_RADIUS = 0.2;
    private static final int LUMP_CLUSTER_POINTS = 4;
    private static final int LUMP_TAIL_POINTS = 4;
    private static final double LUMP_TAIL_LENGTH = 0.9;

    /** Hard stop on the trail task, so a lump that never lands cannot trail forever. */
    private static final int LUMP_TRAIL_MAX_TICKS = 120;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private boolean blockDamage;
    private long recoverTime;

    public EarthThrow(String type){
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
        Alkatraz.getInstance().save("spells/earth_throw.yml");
        Alkatraz.getInstance().saveConfig("spells/earth_throw_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/earth_throw.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
        this.blockDamage = spellConfig.getBoolean("block_damage", true);
        this.recoverTime = spellConfig.getLong("recover_time", 0);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * getWandPower(wand, p), AttackType.PHYSICAL);
            Location loc = p.getEyeLocation();
            Vector direction = loc.getDirection();
            if (p.isOnGround()){
                Block block = p.getLocation().subtract(0,0.5,0).getBlock();
                if (Ground.isGround(block.getType())) {
                    BlockData data = Bukkit.createBlockData(block.getType());
                    if (blockDamage) {
                        if (!Protection.blockEdit(p, block.getLocation())) return;
                        Map<Location, Material> originals = new java.util.HashMap<>();
                        originals.put(block.getLocation(), block.getType());
                        FallingBlock b = loc.getWorld().spawnFallingBlock(loc, data);
                        registerRecoverData(b.getUniqueId(), originals);
                        b.setHurtEntities(false);
                        b.setVelocity(direction.multiply((Double) getOption("throw_force").getSelectedValue(p).getValue()).setY(0.3));
                    SpellEntityComponent comp = new SpellEntityComponent(
                            this,
                            props,
                            p,
                            wand,
                            SpellComponentType.OFFENSE,
                            b
                    );
                    SpellComponentHandler.register(comp);
                    NBT.modifyPersistentData(b, nbt -> {
                        nbt.setString("componentID", comp.getComponentID().toString());
                    });
                    trailLump(b, p, direction);
                    }
                }
            }
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (!caster.isDead() && caster.getTarget() != null){
            double wandp = getWandPowerOrDefault(wand);
            // RAW base power into AttackProperties - getPower is applied once per
            // damage instance, in onLand/onDrop. Pre-scaling here too would square
            // the option modifiers and the affinity term.
            AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                    getBasePower() * wandp, AttackType.PHYSICAL);
            Location loc = caster.getEyeLocation();
            Vector direction = Utils.safeNormalize(caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector()));
            if (caster.isOnGround()){
                Block block = caster.getLocation().subtract(0,0.5,0).getBlock();
                if (Ground.isGround(block.getType())) {
                    BlockData data = Bukkit.createBlockData(block.getType());
                    if (blockDamage) {
                        if (!Protection.blockEdit(caster, block.getLocation())) return;
                        Map<Location, Material> originals = new java.util.HashMap<>();
                        originals.put(block.getLocation(), block.getType());
                    FallingBlock b = loc.getWorld().spawnFallingBlock(loc, data);
                    registerRecoverData(b.getUniqueId(), originals);
                    b.setHurtEntities(false);
                    b.setVelocity(direction.multiply(1).setY(0.3));
                    SpellEntityComponent comp = new SpellEntityComponent(
                            this,
                            props,
                            caster,
                            wand,
                            SpellComponentType.OFFENSE,
                            b
                    );
                    SpellComponentHandler.register(comp);
                    NBT.modifyPersistentData(b, nbt -> {
                        nbt.setString("componentID", comp.getComponentID().toString());
                    });
                    trailLump(b, caster, direction);
                    }
                }
            }
        }
    }

    /**
     * Draws the signature {@code projectile} tell on a thrown lump: a head cluster riding the
     * falling block and a tail lagging behind its live velocity, so the lump reads as travelling
     * rather than as a static block hanging in the air.
     *
     * <p>Visual only. The task stops as soon as the entity dies or is removed (which is what
     * {@code onLand} and {@code onDrop} cause), and hard-stops after
     * {@link #LUMP_TRAIL_MAX_TICKS} ticks so a lump that somehow never resolves cannot leave a
     * repeating task behind.
     *
     * @param lump      the thrown falling block
     * @param caster    the entity that threw it, used for per-player density falloff
     * @param direction the throw direction, used until the lump reports its own velocity
     */
    private void trailLump(FallingBlock lump, LivingEntity caster, Vector direction) {
        new BukkitRunnable() {
            private int ticks;
            private Vector travel = direction.clone();

            @Override
            public void run() {
                if (ticks++ >= LUMP_TRAIL_MAX_TICKS || lump.isDead() || !lump.isValid()) {
                    cancel();
                    return;
                }
                Vector velocity = lump.getVelocity();
                if (velocity.lengthSquared() > 1.0E-6) {
                    travel = Utils.safeNormalize(velocity);
                }
                SpellVfx.projectile(CAST_COLOR, caster, lump.getLocation(), travel,
                        LUMP_CLUSTER_RADIUS, LUMP_CLUSTER_POINTS, LUMP_TAIL_POINTS, LUMP_TAIL_LENGTH);
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
                .setDisplayName(lang().get("spells.earththrow.book_name"))
                .addCustomLoreLine(lang().get("spells.earththrow.lore1"))
                .addCustomLoreLine(lang().get("spells.earththrow.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    private void registerRecoverData(UUID entityUUID, Map<Location, Material> originals) {
        recoverData.put(entityUUID, originals);
        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(),
                () -> recoverData.remove(entityUUID, originals), RECOVER_DATA_TTL_TICKS);
    }

    @EventHandler
    private void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Alkatraz.getInstance()) return;
        recoverData.clear();
    }

    @EventHandler
    private void onLand(EntityChangeBlockEvent e){
        if (e.getEntity() instanceof FallingBlock){
            FallingBlock b = (FallingBlock) e.getEntity();
            String id = NBT.<String>getPersistentData(b, nbt -> nbt.hasTag("componentID") ? nbt.getString("componentID") : null);
            if (id == null || id.isEmpty()) return;
            UUID uuid = UUID.fromString(id);
            SpellComponent comp = SpellComponentHandler.getActiveComponent(uuid);
            if (comp == null) return;
            if (comp.getSpell() != this) return;
            if (!(comp instanceof SpellEntityComponent eComp)) return;
            SpellProperties p = eComp.getProperties();
            if (!(p instanceof AttackProperties props)) return;
            if (blockDamage) {
                if (Protection.blockEdit(comp.getCaster(), e.getBlock().getLocation())) {
                    Map<Location, Material> track = recoverData.computeIfAbsent(
                            ((FallingBlock) e.getEntity()).getUniqueId(), k -> new java.util.HashMap<>());
                    track.putIfAbsent(e.getBlock().getLocation(), e.getBlock().getType());
                    e.getBlock().setType(Material.AIR);
                }
            }
            Location loc = e.getBlock().getLocation();
            UUID entityUUID = ((FallingBlock) e.getEntity()).getUniqueId();
            Map<Location, Material> originals = recoverData.remove(entityUUID);
            double radius = comp.getCaster() instanceof Player player ? (Double) getOption("impact_radius").getSelectedValue(player).getValue() : 3.0;
            List<Location> locs = ParticleUtils.circle(loc, radius, 1, 0, 0);
            for (Location l : locs){
                ParticleCaster.spawn(comp.getCaster(), l.getWorld(), Utils.EXPLOSION, l, 1);
            }
            for (LivingEntity le : me.nagasonic.alkatraz.util.Utils.getNearbyLivingEntities(loc, radius)){
                // WAS props.getRemainingPower() raw - earth_throw called getPower ZERO
                // times on the player path, so `power`, the wand and EARTH
                // affinity/resistance were all inert.
                if (props.hasHit(le)) continue;
                props.hit(le);
                SpellDamageUtil.damageWithSpell(le, getPower(comp.getCaster(), le, props.getRemainingPower()),
                        comp.getCaster(), comp.getWand(), EarthThrow.this);
                Vector direction = le.getLocation().toVector().subtract(loc.toVector());
                direction = Utils.safeNormalize(direction);
                direction.setY(1.25);
                le.setVelocity(direction);
            }
            if (recoverTime > 0 && originals != null && !originals.isEmpty()) {
                LivingEntity doer = comp.getCaster();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        TerrainEditor.restoreAll(doer, new TerrainEditor.RestoreTicket(originals));
                    }
                }.runTaskLater(Alkatraz.getInstance(), recoverTime * 20);
            }
        }
    }

    @EventHandler
    private void onDrop(EntityDropItemEvent e){
        if (e.getEntity() instanceof FallingBlock b){
            String id = NBT.<String>getPersistentData(b, nbt -> nbt.hasTag("componentID") ? nbt.getString("componentID") : null);
            if (id != null && !id.isEmpty()){
                UUID uuid = UUID.fromString(id);
                SpellComponent comp = SpellComponentHandler.getActiveComponent(uuid);
                if (comp == null) return;
                if (comp.getSpell() != this) return;
                if (comp instanceof SpellEntityComponent eComp){
                    SpellProperties p = eComp.getProperties();
                    if (!(p instanceof AttackProperties props)) return;
                    e.setCancelled(true);
                    Location loc = b.getLocation();
                    UUID entityUUID = b.getUniqueId();
                    b.remove();
                    Map<Location, Material> originals = recoverData.remove(entityUUID);
                    if (recoverTime > 0 && originals != null && !originals.isEmpty()) {
                        LivingEntity dropper = comp.getCaster();
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                TerrainEditor.restoreAll(dropper, new TerrainEditor.RestoreTicket(originals));
                            }
                        }.runTaskLater(Alkatraz.getInstance(), recoverTime * 20);
                    }
                    double radius = comp.getCaster() instanceof Player player ? (Double) getOption("impact_radius").getSelectedValue(player).getValue() : 3.0;
                    List<Location> locs = ParticleUtils.circle(loc, radius, 1, 0, 0);
                    for (Location l : locs){
                        ParticleCaster.spawn(comp.getCaster(), l.getWorld(), Utils.EXPLOSION, l, 5);
                    }
                    for (LivingEntity le : me.nagasonic.alkatraz.util.Utils.getNearbyLivingEntities(loc, radius)){
                        // WAS props.getRemainingPower() raw - the player path called
                        // getPower ZERO times, so `power`, the wand and EARTH
                        // affinity/resistance were all inert.
                        if (props.hasHit(le)) continue;
                        props.hit(le);
                        SpellDamageUtil.damageWithSpell(le, getPower(comp.getCaster(), le, props.getRemainingPower()),
                                comp.getCaster(), comp.getWand(), EarthThrow.this);
                        Vector direction = le.getLocation().toVector().subtract(loc.toVector());
                        direction = Utils.safeNormalize(direction);
                        direction.setY(1.25);
                        le.setVelocity(direction);
                    }
                }
            }
        }
    }
}
