package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Circle 7 FIRE BURST: sends two pulses of dragon fire down a lane in front of the caster.
 * The fire never lands on a block, so nothing burns after the lane closes.
 */
public class DragonsWake extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);
    private static final int PULSES = 2;
    private static final int PULSE_GAP = 10;
    /** The lane is drawn for this long, in ticks. */
    private static final int LANE_TICKS = 14;
    /** Published total PvP damage for one cast, split across the two pulses. */
    private static final double DEFAULT_PVP_TOTAL = 4.0;

    private static final Map<UUID, BukkitTask> LANES = new HashMap<>();

    /** Total PvP budget for one cast, from the options YAML. */
    private double pvpTotal = DEFAULT_PVP_TOTAL;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public DragonsWake(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/dragons_wake_options.yml");
        Alkatraz.getInstance().save("spells/dragons_wake.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/dragons_wake.yml").get();
        loadCommonConfig(spellConfig);
        pvpTotal = ConfigManager.getConfig("spells/dragons_wake_options.yml").get()
                .getDouble("pvp_damage_cap", DEFAULT_PVP_TOTAL);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        double length = getModifiedStat(p, "wake_length", option(p, "length", 30));
        double width = Math.max(1.0, option(p, "width", 6));
        Vector direction = Utils.safeNormalize(p.getLocation().getDirection());
        direction.setY(0);
        if (direction.lengthSquared() < 1e-4) return;
        direction.normalize();

        UUID id = p.getUniqueId();
        BukkitTask previous = LANES.remove(id);
        if (previous != null) previous.cancel();
        World world = p.getWorld();
        BukkitTask lane = new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || p.getWorld() != world || ticks > LANE_TICKS) {
                    cancel();
                    LANES.remove(id);
                    return;
                }
                if (ticks == 0 || ticks == PULSE_GAP) {
                    pulse(world, p, wand, length, width, direction);
                }
                draw(world, p, length, width, direction, ticks);
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
        LANES.put(id, lane);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        Vector direction = Utils.safeNormalize(caster.getLocation().getDirection());
        caster.setVelocity(direction.multiply(0.6));
    }

    private void pulse(World world, Player caster, ItemStack wand, double length, double width, Vector direction) {
        Location origin = caster.getLocation();
        Vector side = new Vector(-direction.getZ(), 0, direction.getX());
        for (Entity entity : world.getNearbyEntities(origin, length + 1, 4.0, length + 1)) {
            if (!(entity instanceof LivingEntity target) || target.isDead()) continue;
            Vector delta = target.getLocation().toVector().subtract(origin.toVector());
            double along = delta.dot(direction);
            double off = Math.abs(delta.dot(side));
            if (along < 0.5 || along > length || off > width * 0.5) continue;
            if (Math.abs(delta.getY()) > 3.5) continue;
            damageArea(caster, target, getBasePower(), pvpTotal / PULSES, wand);
        }
        SpellVfx.ring(PALETTE.accent(), caster,
                origin.clone().add(direction.clone().multiply(length * 0.5)), width * 0.5, 24, 0, 0.0);
    }

    private void draw(World world, Player caster, double length, double width, Vector direction, int tick) {
        Location origin = caster.getLocation();
        for (double along = 2.0; along <= length; along += 3.0) {
            Location point = origin.clone().add(direction.clone().multiply(along));
            point.add(0, 0.3 + (tick % 4) * 0.15, 0);
            ParticleCaster.spawn(caster, world, Utils.DRAGON_BREATH, point, 2);
            if (along % 6.0 < 1.0) {
                SpellVfx.ring(PALETTE.deep(), caster, point, width * 0.3, 10, tick * 20, 0.1);
            }
        }
    }

    @Override
    public void cancelTasks() {
        for (BukkitTask task : LANES.values()) {
            if (task != null) task.cancel();
        }
        LANES.clear();
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 20) / 20.0;
        SpellVfx.column(palette.accent(), caster, origin.clone().add(0, 0.6, 0),
                scale * (1.2 + phase * 1.2), scale * 0.45, 3, 8, 1.2);
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.2, 0), scale * 1.1, 20, frame * 5, 0.1);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
    }

    @Override
    public void onCountered(Location location) {
    }

    /** Numeric option value, falling back when the option file is absent. */
    private double option(Player p, String id, double fallback) {
        if (getOption(id) == null) return fallback;
        Object value = getOption(id).getSelectedValue(p).getValue();
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.dragonswake.book_name"))
                .addCustomLoreLine(lang().get("spells.dragonswake.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}
