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
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Circle 8 WATER TERRAIN: breaks a controlled river across the landscape for a fixed duration.
 * Water only fills air or replaceable blocks, boats are nudged along the flow, farmland under
 * the banks is irrigated, and every placed block is restored through {@link TemporaryTerrain}.
 */
public class TidalReservoir extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private static final class Channel {
        final TemporaryTerrain terrain;
        final UUID owner;
        final World world;
        final Location mouth;
        final Vector flow;
        final int halfWidth;
        final int width;
        final int y;
        final int length;
        final long expiresAt;
        final Map<Location, Integer> moisture = new LinkedHashMap<>();

        Channel(TemporaryTerrain terrain, UUID owner, World world, Location mouth, Vector flow,
                int width, int y, int length, long expiresAt) {
            this.terrain = terrain;
            this.owner = owner;
            this.world = world;
            this.mouth = mouth;
            this.flow = flow;
            this.width = width;
            this.halfWidth = width / 2;
            this.y = y;
            this.length = length;
            this.expiresAt = expiresAt;
        }
    }

    private final List<Channel> live = new ArrayList<>();
    private BukkitTask sweep;

    private long durationSeconds;
    private int blockBudget;
    private int width;

    public TidalReservoir(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/tidal_reservoir_options.yml");
        Alkatraz.getInstance().save("spells/tidal_reservoir.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/tidal_reservoir.yml").get();
        loadCommonConfig(spellConfig);
        this.durationSeconds = spellConfig.getLong("duration", 90);
        this.blockBudget = spellConfig.getInt("block_budget", 1400);
        this.width = Math.max(1, spellConfig.getInt("width", 6));
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }
        World world = p.getWorld();
        Location base = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection().clone();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-6) {
            cancelCast(p);
            return;
        }
        Utils.safeNormalizeInPlace(dir);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());

        int length = (int) Math.round(getModifiedStat(p, "channel_length", option(p, "length", 36)));
        int duration = (int) Math.round(getModifiedStat(p, "channel_duration",
                option(p, "duration", durationSeconds)));
        int y = base.getBlockY();
        int half = Math.max(0, width / 2);

        TemporaryTerrain terrain = new TemporaryTerrain(p, blockBudget);
        Channel channel = new Channel(terrain, p.getUniqueId(), world, base.clone(), dir.clone(),
                width, y, length, System.currentTimeMillis() + Math.max(1, duration) * 1000L);

        for (int step = 1; step <= length; step++) {
            Location centre = base.clone().add(dir.clone().multiply(step));
            centre.setY(y);
            for (int offset = -half; offset < width - half; offset++) {
                Location cell = centre.clone().add(side.clone().multiply(offset));
                Block block = cell.getBlock();
                if (!(block.isEmpty() || block.isLiquid())) continue;
                if (terrain.place(cell, Material.WATER) > 0) {
                    ParticleCaster.spawn(p, world, Utils.SPLASH, cell.clone().add(0.5, 0.5, 0.5), 2);
                }
                irrigate(channel, cell);
            }
            if (terrain.remainingBudget() == 0) break;
        }

        if (terrain.size() == 0) {
            cancelCast(p);
            return;
        }
        live.add(channel);
        SpellVfx.ring(PALETTE.accent(), p, base, 3.0, 36, 0, 0.2);
        SpellVfx.column(PALETTE.core(), p, base, 2.5, 0.8, 3, 8, 0.5);
        Utils.sendActionBar(p, lang().get("spells.tidalreservoir.raised")
                .replace("{seconds}", String.valueOf(duration)));
        startSweep();
    }

    private void irrigate(Channel channel, Location cell) {
        Block below = cell.clone().subtract(0, 1, 0).getBlock();
        if (below.getType() != Material.FARMLAND) return;
        if (!(below.getBlockData() instanceof Farmland farmland)) return;
        int before = farmland.getMoisture();
        if (before >= 7) return;
        Location key = below.getLocation();
        channel.moisture.putIfAbsent(key, before);
        farmland.setMoisture(7);
        below.setBlockData(farmland, false);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    private void startSweep() {
        if (sweep != null) return;
        sweep = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Channel channel : new ArrayList<>(live)) {
                    Player owner = Alkatraz.getInstance().getServer().getPlayer(channel.owner);
                    boolean gone = owner == null || !owner.isOnline() || owner.isDead()
                            || owner.getWorld() != channel.world;
                    if (gone || now >= channel.expiresAt) {
                        settle(channel);
                        continue;
                    }
                    flow(channel, owner);
                }
                if (live.isEmpty()) {
                    cancel();
                    sweep = null;
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 10L, 10L);
    }

    private void flow(Channel channel, Player owner) {
        Location centre = channel.mouth.clone().add(channel.flow.clone().multiply(channel.length / 2.0));
        double reach = channel.length / 2.0 + 2.0;
        for (Entity entity : channel.world.getNearbyEntities(centre, reach, 6.0, reach)) {
            if (entity instanceof LivingEntity living) {
                living.setFireTicks(0);
                if (living.getLocation().getBlock().isLiquid()) {
                    Vector push = channel.flow.clone().multiply(0.04);
                    living.setVelocity(living.getVelocity().multiply(0.9).add(push));
                }
            }
            if (entity instanceof Boat boat) {
                Vector push = channel.flow.clone().multiply(0.05);
                boat.setVelocity(boat.getVelocity().multiply(0.8).add(push));
            }
        }
        SpellVfx.ring(PALETTE.accent(), owner, channel.mouth.clone().add(0, 0.2, 0),
                1.4, 16, channel.length, 0.0);
    }

    /** Idempotent: restores the water and every irrigated farmland block. */
    private void settle(Channel channel) {
        live.remove(channel);
        channel.terrain.restore();
        for (Map.Entry<Location, Integer> entry : channel.moisture.entrySet()) {
            Block block = entry.getKey().getBlock();
            if (block.getType() != Material.FARMLAND) continue;
            if (!(block.getBlockData() instanceof Farmland farmland)) continue;
            farmland.setMoisture(entry.getValue());
            block.setBlockData(farmland, false);
        }
        channel.moisture.clear();
    }

    @Override
    public void cancelTasks() {
        if (sweep != null) {
            sweep.cancel();
            sweep = null;
        }
        for (Channel channel : new ArrayList<>(live)) {
            settle(channel);
        }
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 26) / 26.0;
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.3 + phase * 1.4, 0),
                scale * 1.5, 30, frame * 3, 0.1);
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.3 + (1.0 - phase) * 1.4, 0),
                scale * 1.1, 24, -frame * 4, 0.0);
        SpellVfx.column(palette.deep(), caster, origin, 2.8, scale * 0.5, 3, 8, 0.6);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        if (tick % 5 == 0) {
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2, 0),
                    1.0 + progress * 4.0, 24, tick * 3, 0.0);
            ParticleCaster.spawn(caster, origin.getWorld(), Utils.SPLASH,
                    origin.clone().add(0, 0.6, 0), 6, 1.2, 0.4, 1.2, 0.05);
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
                .setDisplayName(lang().get("spells.tidalreservoir.book_name"))
                .addCustomLoreLine(lang().get("spells.tidalreservoir.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}
