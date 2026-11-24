package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.TemporaryTerrain;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Circle 9 EARTH TERRAIN: raises a square of land into broad stone terraces, never digging and
 * only filling air or replaceable blocks. Placement is spread across ticks so a 32x32 raise does
 * not stall the server, and every placed block is restored through {@link TemporaryTerrain}.
 */
public class AtlasUnbound extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.EARTH);
    private static final int TERRACE_LEVELS = 4;
    private static final int BLOCKS_PER_TICK = 160;

    private static final class Formation {
        final TemporaryTerrain terrain;
        final UUID owner;
        final World world;
        long expiresAt = Long.MAX_VALUE;
        BukkitTask build;

        Formation(TemporaryTerrain terrain, UUID owner, World world) {
            this.terrain = terrain;
            this.owner = owner;
            this.world = world;
        }
    }

    private final List<Formation> live = new ArrayList<>();
    private BukkitTask sweep;

    private long durationSeconds;
    private int blockBudget;
    private Material material;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public AtlasUnbound(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/atlas_unbound_options.yml");
        Alkatraz.getInstance().save("spells/atlas_unbound.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/atlas_unbound.yml").get();
        loadCommonConfig(spellConfig);
        this.durationSeconds = spellConfig.getLong("duration", 180);
        this.blockBudget = spellConfig.getInt("block_budget", 3600);
        this.material = Utils.materialFromString(spellConfig.getString("material", "STONE")).getType();
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }
        World world = p.getWorld();
        Location center = p.getLocation().clone();
        int side = (int) Math.round(getModifiedStat(p, "terrace_radius", option(p, "radius", 32)));
        int elevation = (int) Math.round(getModifiedStat(p, "terrace_elevation",
                option(p, "elevation", 12)));
        int half = Math.max(1, side / 2);
        int granularity = Math.max(1, elevation / TERRACE_LEVELS);

        int minGround = Integer.MAX_VALUE;
        for (int dx = -half; dx < half; dx++) {
            for (int dz = -half; dz < half; dz++) {
                int x = center.getBlockX() + dx;
                int z = center.getBlockZ() + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                minGround = Math.min(minGround, world.getHighestBlockYAt(x, z));
            }
        }
        if (minGround == Integer.MAX_VALUE) {
            Utils.sendActionBar(p, lang().get("spells.atlasunbound.no_room"));
            cancelCast(p);
            return;
        }

        List<Location> fill = new ArrayList<>();
        for (int dx = -half; dx < half; dx++) {
            for (int dz = -half; dz < half; dz++) {
                int x = center.getBlockX() + dx;
                int z = center.getBlockZ() + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                int groundY = world.getHighestBlockYAt(x, z);
                int tier = Math.min(TERRACE_LEVELS, Math.max(0, (groundY - minGround) / granularity));
                // Lift each column one band above its own height: flat terrain now
                // builds (tier 0 → +granularity), and lower ground lifts more, so the
                // four terraces form instead of the flat ground always being skipped.
                int targetY = minGround + (tier + 1) * granularity;
                if (targetY <= groundY) continue;
                for (int y = groundY + 1; y <= targetY; y++) {
                    fill.add(new Location(world, x, y, z));
                }
            }
        }
        if (fill.isEmpty()) {
            Utils.sendActionBar(p, lang().get("spells.atlasunbound.no_room"));
            cancelCast(p);
            return;
        }

        TemporaryTerrain terrain = new TemporaryTerrain(p, blockBudget);
        Formation formation = new Formation(terrain, p.getUniqueId(), world);
        live.add(formation);
        startBuild(formation, p, fill, center);
        startSweep();
    }

    private void startBuild(Formation formation, Player owner, List<Location> fill, Location center) {
        formation.build = new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                Player caster = Alkatraz.getInstance().getServer().getPlayer(formation.owner);
                if (caster == null || !caster.isOnline() || caster.isDead()) {
                    settle(formation, null, System.currentTimeMillis());
                    return;
                }
                int placed = 0;
                while (index < fill.size() && placed < BLOCKS_PER_TICK
                        && formation.terrain.remainingBudget() > 0) {
                    Location cell = fill.get(index++);
                    if (!isReplaceable(cell.getBlock())) continue;
                    if (formation.terrain.place(cell, material) > 0) placed++;
                }
                if (index < fill.size() && formation.terrain.remainingBudget() > 0) return;

                formation.expiresAt = System.currentTimeMillis()
                        + Math.max(1, durationSeconds) * 1000L;
                cancel();
                formation.build = null;
                if (formation.terrain.size() == 0) {
                    live.remove(formation);
                    cancelCast(caster);
                    return;
                }
                SpellVfx.nova(PALETTE.core(), caster, center, 6.0, 32);
                Utils.sendActionBar(caster, lang().get("spells.atlasunbound.raised")
                        .replace("{seconds}", String.valueOf(durationSeconds)));
            }
        }.runTaskTimer(Alkatraz.getInstance(), 1L, 1L);
    }

    private static boolean isReplaceable(Block block) {
        return block.isEmpty() || block.isLiquid();
    }

    private void startSweep() {
        if (sweep != null) return;
        sweep = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Formation formation : new ArrayList<>(live)) {
                    Player owner = Alkatraz.getInstance().getServer().getPlayer(formation.owner);
                    boolean gone = owner == null || !owner.isOnline() || owner.isDead()
                            || owner.getWorld() != formation.world;
                    if (gone || now >= formation.expiresAt) {
                        settle(formation, gone ? null : owner, now);
                    }
                }
                if (live.isEmpty()) {
                    cancel();
                    sweep = null;
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 20L, 20L);
    }

    /** Idempotent: stops the build and restores every placed block once safe. */
    private void settle(Formation formation, Player owner, long now) {
        if (formation.build != null) {
            formation.build.cancel();
            formation.build = null;
        }
        if (owner == null) {
            formation.terrain.restore();
        } else {
            formation.terrain.restoreWhenClear(now, owner,
                    lang().get("spells.atlasunbound.restore_warning"));
            if (formation.terrain.isDeferred()) return;
        }
        live.remove(formation);
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
        for (Formation formation : new ArrayList<>(live)) {
            settle(formation, null, System.currentTimeMillis());
        }
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 34) / 34.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.15 + phase * 2.6, 0),
                scale * (1.2 + phase * 1.6), 36, frame * 2, 0.2);
        SpellVfx.ring(palette.deep(), caster, origin.clone().add(0, 0.1, 0),
                scale * 2.4, 28, -frame, 0.0);
        SpellVfx.column(palette.core(), caster, origin, 3.4, scale * 0.6, 3, 8, 0.6);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        if (tick % 6 == 0) {
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2, 0),
                    2.0 + progress * 8.0, 36, tick * 2, 0.15);
        }
        if (tick % 8 == 0) {
            ParticleCaster.spawn(caster, origin.getWorld(), Utils.BLOCK,
                    origin.clone().add((Math.random() - 0.5) * 8, 0.6, (Math.random() - 0.5) * 8),
                    8, 0.4, 0.2, 0.4, 0.0, material.createBlockData());
        }
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
                .setDisplayName(lang().get("spells.atlasunbound.book_name"))
                .addCustomLoreLine(lang().get("spells.atlasunbound.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
