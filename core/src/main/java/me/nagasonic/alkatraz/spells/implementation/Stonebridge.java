package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.TemporaryTerrain;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Circle 5 EARTH TERRAIN: lays a walkable bridge along the caster's facing, at their feet
 * level, and restores it when the bridge expires or the caster leaves.
 */
public class Stonebridge extends Spell {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /** One live bridge, owned by this spell instance so {@link #cancelTasks()} can settle it. */
    private static final class Bridge {
        final TemporaryTerrain terrain;
        final UUID caster;
        final World world;
        final long expiresAtMillis;

        Bridge(TemporaryTerrain terrain, UUID caster, World world, long expiresAtMillis) {
            this.terrain = terrain;
            this.caster = caster;
            this.world = world;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private final List<Bridge> active = new ArrayList<>();
    private BukkitTask sweep;

    private long durationSeconds;
    private int blockBudget;
    private Material material;

    public Stonebridge(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/stonebridge_options.yml");
        Alkatraz.getInstance().save("spells/stonebridge.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/stonebridge.yml").get();
        loadCommonConfig(spellConfig);
        this.durationSeconds = spellConfig.getLong("duration", 45);
        this.blockBudget = spellConfig.getInt("block_budget", 54);
        this.material = Utils.materialFromString(spellConfig.getString("material", "STONE")).getType();
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        Location feet = p.getLocation();
        World world = feet.getWorld();
        if (world == null) {
            cancelCast(p);
            return;
        }
        Vector dir = p.getEyeLocation().getDirection().clone();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-6) {
            cancelCast(p);
            return;
        }
        Utils.safeNormalizeInPlace(dir);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());

        int length = (int) Math.round(getModifiedStat(p, "bridge_length", option(p, "length", 18)));
        int width = (int) Math.round(getModifiedStat(p, "bridge_width", option(p, "width", 3)));
        int half = Math.max(0, (width - 1) / 2);

        TemporaryTerrain terrain = new TemporaryTerrain(p, blockBudget);
        for (int step = 1; step <= length; step++) {
            Location centre = feet.clone().add(dir.clone().multiply(step));
            if (!isClear(centre)) break;
            for (int offset = -half; offset <= half; offset++) {
                Location cell = centre.clone().add(side.clone().multiply(offset)).subtract(0, 1, 0);
                Block block = cell.getBlock();
                if (!block.isEmpty() && !block.isLiquid()) continue;
                if (terrain.place(cell, material) > 0) {
                    ParticleCaster.spawn(p, world, Utils.BLOCK, cell.clone().add(0.5, 0.5, 0.5),
                            2, material.createBlockData());
                }
            }
            if (terrain.remainingBudget() == 0) break;
        }

        if (terrain.size() == 0) {
            cancelCast(p);
            return;
        }
        active.add(new Bridge(terrain, p.getUniqueId(), world, expiresAt()));
        startSweep();
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.getTarget() == null) return;
        Location feet = caster.getLocation();
        World world = feet.getWorld();
        if (world == null) return;
        Vector dir = caster.getTarget().getLocation().toVector().subtract(feet.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-6) return;
        Utils.safeNormalizeInPlace(dir);
        int length = 12;
        int half = 1;
        TemporaryTerrain terrain = new TemporaryTerrain(caster, blockBudget);
        for (int step = 1; step <= length; step++) {
            Location centre = feet.clone().add(dir.clone().multiply(step));
            if (!isClear(centre)) break;
            for (int offset = -half; offset <= half; offset++) {
                Vector side = new Vector(-dir.getZ(), 0, dir.getX());
                Location cell = centre.clone().add(side.multiply(offset)).subtract(0, 1, 0);
                Block block = cell.getBlock();
                if (!block.isEmpty() && !block.isLiquid()) continue;
                terrain.place(cell, material);
            }
            if (terrain.remainingBudget() == 0) break;
        }
        if (terrain.size() == 0) return;
        active.add(new Bridge(terrain, caster.getUniqueId(), world, expiresAt()));
        startSweep();
    }

    /** True when the block at the caster's feet level is open enough to bridge through. */
    private static boolean isClear(Location feet) {
        Block block = feet.getBlock();
        return block.isEmpty() || block.isLiquid();
    }

    private long expiresAt() {
        return System.currentTimeMillis() + durationSeconds * 1000L;
    }

    private void startSweep() {
        if (sweep != null) return;
        sweep = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Bridge> it = active.iterator();
                while (it.hasNext()) {
                    Bridge bridge = it.next();
                    Player owner = Alkatraz.getInstance().getServer().getPlayer(bridge.caster);
                    boolean gone = now >= bridge.expiresAtMillis
                            || owner == null || !owner.isOnline() || owner.isDead()
                            || owner.getWorld() != bridge.world;
                    if (!gone) continue;
                    bridge.terrain.restore();
                    it.remove();
                }
                if (active.isEmpty()) {
                    cancel();
                    sweep = null;
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 20L, 20L);
    }

    @Override
    public void cancelTasks() {
        if (sweep != null) {
            sweep.cancel();
            sweep = null;
        }
        for (Bridge bridge : active) {
            bridge.terrain.restore();
        }
        active.clear();
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
                .setDisplayName(lang().get("spells.stonebridge.book_name"))
                .addCustomLoreLine(lang().get("spells.stonebridge.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
