package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.util.TemporaryTerrain;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 9 WATER BURST: a tidal front sixty blocks long that shoves living things aside and
 * leaves temporary water behind. Every placed block is restored through {@link TemporaryTerrain}.
 */
public class LeviathansTestament extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);
    /** Published PvP damage for the single tide hit. */
    private static final double DEFAULT_PVP_CAP = 6.0;

    private static final class Tide {
        final UUID owner;
        final World world;
        final Location start;
        final Vector direction;
        final Vector sideways;
        final int length;
        final int width;
        final TemporaryTerrain terrain;
        final Set<UUID> hit = new LinkedHashSet<>();
        int step;
        long restoreAt = Long.MAX_VALUE;
        BukkitTask build;

        Tide(UUID owner, World world, Location start, Vector direction, int length, int width,
             TemporaryTerrain terrain) {
            this.owner = owner;
            this.world = world;
            this.start = start;
            this.direction = direction;
            this.sideways = new Vector(-direction.getZ(), 0, direction.getX());
            this.length = length;
            this.width = width;
            this.terrain = terrain;
        }
    }

    private final List<Tide> live = new ArrayList<>();
    private BukkitTask sweep;

    private int length;
    private int width;
    private int waterSeconds;
    private double pvpDamageCap = DEFAULT_PVP_CAP;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public LeviathansTestament(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/leviathans_testament_options.yml");
        Alkatraz.getInstance().save("spells/leviathans_testament.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/leviathans_testament.yml").get();
        loadCommonConfig(spellConfig);
        this.length = spellConfig.getInt("length", 60);
        this.width = spellConfig.getInt("width", 14);
        this.waterSeconds = spellConfig.getInt("water_seconds", 20);
        pvpDamageCap = ConfigManager.getConfig("spells/leviathans_testament_options.yml").get()
                .getDouble("pvp_damage_cap", DEFAULT_PVP_CAP);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }
        int reach = Math.max(8, (int) Math.round(
                getModifiedStat(p, "tide_length", option(p, "length", length))));
        int breadth = Math.max(3, (int) Math.round(
                getModifiedStat(p, "tide_width", option(p, "width", width))));

        Location start = p.getLocation().clone();
        Tide tide = new Tide(p.getUniqueId(), p.getWorld(), start, horizontal(p), reach, breadth,
                new TemporaryTerrain(p, reach * breadth * 3 + 128));
        live.add(tide);
        tide.build = Bukkit.getScheduler().runTaskTimer(Alkatraz.getInstance(), () -> advance(tide), 0L, 1L);
        startSweep();
        Utils.sendActionBar(p, lang().get("spells.leviathanstestament.opened"));
    }

    private void advance(Tide tide) {
        Player owner = Bukkit.getPlayer(tide.owner);
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getWorld() != tide.world) {
            settle(tide);
            return;
        }
        // The framework wind-up has completed before castAction ran; the build
        // ticker goes straight to terrain work.
        if (tide.step >= tide.length) {
            if (tide.build != null) {
                tide.build.cancel();
                tide.build = null;
            }
            tide.restoreAt = System.currentTimeMillis() + Math.max(1, waterSeconds) * 1000L;
            Utils.sendActionBar(owner, lang().get("spells.leviathanstestament.surfaced")
                    .replace("{seconds}", String.valueOf(waterSeconds)));
            return;
        }

        Location front = tide.start.clone().add(tide.direction.clone().multiply(tide.step));
        boolean damageBlocks = (boolean) getOption("damage_blocks").getSelectedValue(owner).getValue();
        for (int offset = -tide.width / 2; offset <= tide.width / 2; offset++) {
            Location column = front.clone().add(tide.sideways.clone().multiply(offset));
            int x = column.getBlockX();
            int z = column.getBlockZ();
            if (!tide.world.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = tide.world.getHighestBlockYAt(x, z);
            Block block = tide.world.getBlockAt(x, y, z);
            if (block.isEmpty() || block.isLiquid()) {
                tide.terrain.place(block.getLocation(), Material.WATER);
                for (int dy = 1; dy <= 2; dy++) {
                    Block above = tide.world.getBlockAt(x, y + dy, z);
                    if (!above.isEmpty() && !above.isLiquid()) break;
                    if (tide.terrain.place(above.getLocation(), Material.WATER) == 0) break;
                }
            }
        }
        if (damageBlocks) {
            TerrainEditor.destroySphere(owner, front, 2.5, false, 12);
        }

        int surface = tide.world.getHighestBlockYAt(front) + 1;
        Location ride = new Location(tide.world, front.getX(), surface, front.getZ());

        for (Entity entity : tide.world.getNearbyEntities(front, tide.width / 2.0 + 1.0, 4.0,
                tide.width / 2.0 + 1.0)) {
            if (!(entity instanceof LivingEntity living) || living.equals(owner) || living.isDead()) continue;
            if (!tide.hit.add(living.getUniqueId())) continue;
            if (living instanceof Player ally && SpellTargeting.isAlly(owner, ally)) {
                Utils.sendActionBar(ally, lang().get("spells.leviathanstestament.ally_swept")
                        .replace("{caster}", owner.getName()));
                continue;
            }
            damageArea(owner, living, getBasePower(), pvpDamageCap, null);
            living.setFireTicks(0);
            Vector shove = living.getLocation().toVector().subtract(front.toVector()).setY(0);
            if (shove.lengthSquared() > 0.0) {
                living.setVelocity(shove.normalize().multiply(0.6).setY(0.25));
            }
            if (living instanceof Mob mob && mob.getTarget() instanceof Player) {
                mob.setTarget(null);
            }
        }

        SpellVfx.ground(PALETTE.core(), owner, front, 3.0, tide.width + 2.0, 10, 6,
                owner.getLocation().getYaw(), tide.step * 0.05);
        if (tide.step % 3 == 0) {
            ParticleCaster.spawn(owner, tide.world, Utils.DUST, ride.clone().add(0, 0.5, 0),
                    8, tide.width / 2.0, 0.4, 1.0, 0.0f);
        }
        tide.step++;
    }

    private void startSweep() {
        if (sweep != null) return;
        sweep = Bukkit.getScheduler().runTaskTimer(Alkatraz.getInstance(), () -> {
            long now = System.currentTimeMillis();
            for (Tide tide : new ArrayList<>(live)) {
                Player owner = Bukkit.getPlayer(tide.owner);
                boolean gone = owner == null || !owner.isOnline() || owner.isDead()
                        || owner.getWorld() != tide.world;
                if (gone || now >= tide.restoreAt) {
                    settle(tide);
                }
            }
            if (live.isEmpty()) {
                sweep.cancel();
                sweep = null;
            }
        }, 20L, 20L);
    }

    /** Idempotent: stops the front and restores every block. */
    private void settle(Tide tide) {
        live.remove(tide);
        if (tide.build != null) {
            tide.build.cancel();
            tide.build = null;
        }
        tide.terrain.restore();
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
    }

    @Override
    public void onCountered(Location location) {
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    @Override
    public void cancelTasks() {
        if (sweep != null) {
            sweep.cancel();
            sweep = null;
        }
        for (Tide tide : new ArrayList<>(live)) {
            settle(tide);
        }
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.2, 0), scale * 2.8, 36, frame * 3, 0.0);
        SpellVfx.column(palette.accent(), caster, origin, 3.0, scale * 0.9, 4, 8, 0.7);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        if (tick % 5 == 0) {
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2 + progress * 0.6, 0),
                    2.0 + progress * 9.0, 34, -tick * 3, 0.0);
            SpellVfx.column(palette.deep(), caster, origin, 2.0 + progress * 4.0, 1.4, 3, 6, 0.6);
        }
        if (tick % 7 == 0) {
            ParticleCaster.spawn(caster, origin.getWorld(), Utils.DUST,
                    origin.clone().add((Math.random() - 0.5) * 8, 0.3, (Math.random() - 0.5) * 8),
                    6, 0.5, 0.1, 0.5, 0.0f);
        }
    }

    private static Vector horizontal(Player p) {
        Vector direction = p.getLocation().getDirection();
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-4) direction = new Vector(0, 0, 1);
        return direction.normalize();
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
                .setDisplayName(lang().get("spells.leviathanstestament.book_name"))
                .addCustomLoreLine(lang().get("spells.leviathanstestament.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
