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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Circle 8 EARTH TERRAIN: lays a stone causeway along the caster's facing at the caster's Y,
 * bridging gaps for a fixed duration. Every placed block is restored by {@link TemporaryTerrain}.
 */
public class TitansCauseway extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.EARTH);

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private static final class Causeway {
        final TemporaryTerrain terrain;
        final UUID owner;
        final World world;
        final long expiresAt;

        Causeway(TemporaryTerrain terrain, UUID owner, World world, long expiresAt) {
            this.terrain = terrain;
            this.owner = owner;
            this.world = world;
            this.expiresAt = expiresAt;
        }
    }

    private final List<Causeway> live = new ArrayList<>();
    private BukkitTask sweep;

    private long durationSeconds;
    private int blockBudget;
    private Material material;

    public TitansCauseway(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/titans_causeway_options.yml");
        Alkatraz.getInstance().save("spells/titans_causeway.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/titans_causeway.yml").get();
        loadCommonConfig(spellConfig);
        this.durationSeconds = spellConfig.getLong("duration", 120);
        this.blockBudget = spellConfig.getInt("block_budget", 240);
        this.material = Utils.materialFromString(spellConfig.getString("material", "POLISHED_ANDESITE")).getType();
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
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

        int length = (int) Math.round(getModifiedStat(p, "causeway_length", option(p, "length", 48)));
        int width = (int) Math.round(getModifiedStat(p, "causeway_width", option(p, "width", 5)));
        int half = Math.max(0, (width - 1) / 2);
        int y = base.getBlockY();

        TemporaryTerrain terrain = new TemporaryTerrain(p, blockBudget);
        for (int step = 1; step <= length; step++) {
            Location centre = base.clone().add(dir.clone().multiply(step));
            centre.setY(y);
            for (int offset = -half; offset <= half; offset++) {
                Location cell = centre.clone().add(side.clone().multiply(offset));
                Block block = cell.getBlock();
                if (!isReplaceable(block)) continue;
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
        live.add(new Causeway(terrain, p.getUniqueId(), world, System.currentTimeMillis() + durationSeconds * 1000L));
        SpellVfx.ring(PALETTE.accent(), p, base, 2.5, 40, 0, 0.2);
        Utils.sendActionBar(p, lang().get("spells.titanscauseway.raised")
                .replace("{seconds}", String.valueOf(durationSeconds)));
        startSweep();
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
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
                for (Causeway causeway : new ArrayList<>(live)) {
                    Player owner = Alkatraz.getInstance().getServer().getPlayer(causeway.owner);
                    boolean gone = owner == null || !owner.isOnline() || owner.isDead()
                            || owner.getWorld() != causeway.world;
                    if (!gone && now < causeway.expiresAt) continue;
                    if (gone) {
                        causeway.terrain.restore();
                    } else {
                        causeway.terrain.restoreWhenClear(now, owner,
                                lang().get("spells.titanscauseway.restore_warning"));
                        if (causeway.terrain.isDeferred()) continue;
                    }
                    live.remove(causeway);
                }
                if (live.isEmpty()) {
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
        for (Causeway causeway : live) {
            causeway.terrain.restore();
        }
        live.clear();
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 24) / 24.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.15 + phase * 2.0, 0),
                scale * 1.4, 36, frame * 4, 0.2);
        SpellVfx.column(palette.deep(), caster, origin, scale * 1.8, scale * 0.5, 3, 10, 0.5);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        if (tick % 6 != 0) return;
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        double radius = 1.5 + progress * 3.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2, 0), radius, 28, tick * 2, 0.15);
        if (tick < totalTicks / 2) {
            SpellVfx.column(palette.deep(), caster, origin, 2.0, 0.4, 2, 6, 0.4);
        }
    }

    private double option(Player p, String id, double fallback) {
        if (getOption(id) == null) return fallback;
        Object value = getOption(id).getSelectedValue(p).getValue();
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.titanscauseway.book_name"))
                .addCustomLoreLine(lang().get("spells.titanscauseway.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}
