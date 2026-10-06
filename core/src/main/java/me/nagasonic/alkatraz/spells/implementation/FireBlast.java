package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellEntityComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FireBlast extends AttackSpell implements Listener {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `FIRE` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(255, 140, 0);

    /**
     * The travelling tell: a WIDE, fat {@code core} shell with a stubby tail, which is the
     * opposite read to fireball's narrow cluster with a long tail. Same
     * {@link SpellVfx#projectile} builder, deliberately different parameters, so the two
     * fire projectiles are distinguishable at a glance.
     */
    private static final double BLAST_CLUSTER_RADIUS = 0.7;
    private static final int BLAST_CLUSTER_POINTS = 16;
    private static final int BLAST_TAIL_POINTS = 6;
    private static final double BLAST_TAIL_LENGTH = 0.5;
    /** Drawn every other tick; the shell is wide enough that it reads solid either way. */
    private static final long TRACK_PERIOD_TICKS = 2L;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private boolean blockDamage;

    public FireBlast(String type){
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

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/fire_blast.yml");
        Alkatraz.getInstance().saveConfig("spells/fire_blast_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fire_blast.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
        this.blockDamage = spellConfig.getBoolean("block_damage", true);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AttackProperties props = new AttackProperties(p, Utils.castLocation(p), getBasePower() * getWandPower(wand, p), AttackType.MAGIC);
            LargeFireball fire = p.launchProjectile(LargeFireball.class, p.getLocation().getDirection().multiply((Double) getOption("blast_speed").getSelectedValue(p).getValue()));
            SpellEntityComponent comp = new SpellEntityComponent(
                    this,
                    props,
                    p,
                    wand,
                    SpellComponentType.OFFENSE,
                    fire
            );
            comp.setCollisionRadius(0.5);
            SpellComponentHandler.register(comp);
            NBT.modifyPersistentData(fire, nbt -> {
                nbt.setString("componentID", comp.getComponentID().toString());
            });
            trackProjectile(p, fire);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (!caster.isDead() && caster.getTarget() != null){
            // RAW base power. getPower is applied once, in onDamage, below. It used
            // to be applied here as well, which squared the option modifiers and the
            // affinity term for every mob-cast fire blast.
            AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                    getBasePower() * getWandPowerOrDefault(wand), AttackType.MAGIC);
            LargeFireball fire = caster.launchProjectile(LargeFireball.class, caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector()).multiply(0.1));
            SpellEntityComponent comp = new SpellEntityComponent(
                    this,
                    props,
                    caster,
                    wand,
                    SpellComponentType.OFFENSE,
                    fire
            );
            comp.setCollisionRadius(0.5);
            SpellComponentHandler.register(comp);
            NBT.modifyPersistentData(fire, nbt -> {
                nbt.setString("componentID", comp.getComponentID().toString());
            });
            trackProjectile(caster, fire);
        }
    }

    /**
     * Draws the blast's wide {@link SpellVfx#projectile} shell for as long as the entity
     * lives. Purely visual, and it stops the moment the projectile is gone so an
     * unresolved cast cannot leak a repeating task.
     *
     * @param caster the casting entity, used for per-player density falloff
     * @param fire   the projectile to follow
     */
    private void trackProjectile(LivingEntity caster, LargeFireball fire) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (fire.isDead() || !fire.isValid()) {
                    cancel();
                    return;
                }
                SpellVfx.projectile(CAST_COLOR, caster, fire.getLocation(),
                        fire.getVelocity(),
                        BLAST_CLUSTER_RADIUS, BLAST_CLUSTER_POINTS,
                        BLAST_TAIL_POINTS, BLAST_TAIL_LENGTH);
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, TRACK_PERIOD_TICKS);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.fireblast.book_name"))
                .addLoreLine(lang().get("spells.fireblast.lore1"))
                .addLoreLine(lang().get("spells.fireblast.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    @EventHandler
    private void onDamage(EntityDamageByEntityEvent e){
        if (!(e.getDamager() instanceof org.bukkit.entity.Fireball fireball)) return;
        String idString = NBT.<String>getPersistentData(fireball, nbt -> nbt.hasTag("componentID") ? nbt.getString("componentID") : null);
        if (idString == null) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        SpellComponent comp = SpellComponentHandler.getActiveComponent(UUID.fromString(idString));
        if (!(comp instanceof SpellEntityComponent ecomp)) return;
        if (!(ecomp.getProperties() instanceof AttackProperties props)) return;
        // One AttackProperties per cast: guard so a target hit by both the impact
        // and the explosion's spread takes the scaled damage exactly once.
        if (props.hasHit(le)) return;
        props.hit(le);
        e.setDamage(getPower(comp.getCaster(), le, props.getRemainingPower()));
    }

    @EventHandler
    private void onHit(ProjectileHitEvent e){
        if (!(e.getEntity() instanceof org.bukkit.entity.Fireball fireball)) return;
        String idString = NBT.<String>getPersistentData(fireball, nbt -> nbt.hasTag("componentID") ? nbt.getString("componentID") : null);
        if (idString == null) return;
        SpellComponent comp = SpellComponentHandler.getActiveComponent(UUID.fromString(idString));
        if (comp == null) return;
        if (comp.getSpell() != this) return;
        Location loc = e.getHitBlock() != null ? e.getHitBlock().getLocation() : e.getHitEntity().getLocation();
        double radius = comp.getCaster() instanceof Player player ? (Double) getOption("fire_spread").getSelectedValue(player).getValue() : 3.0;
        if (blockDamage) {
            List<Block> blocks = Utils.blocksInRadius(loc, (int) Math.round(radius));
            Set<Long> ignitedColumns = new HashSet<>();
            for (Block block : blocks){
                if (block.getType() == Material.AIR){
                    long column = (((long) block.getX()) << 32) ^ (block.getZ() & 0xFFFFFFFFL);
                    if (!ignitedColumns.add(column)) continue;
                    if (Protection.ignite(comp.getCaster(), block.getLocation())) {
                        block.setType(Material.FIRE);
                    }
                }
            }
        }
    }
}
