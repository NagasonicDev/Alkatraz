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
 * Circle 7 EARTH DENIAL: raises a walled refuge with two doors around the caster and takes
 * it down again when the duration runs out. Only air and replaceable blocks are built
 * into, and every placed block is restored by {@link TemporaryTerrain}.
 */
public class StoneBastion extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.EARTH);
    private static final int HEIGHT = 5;
    /** Two-block-high doorways, two blocks wide, in the middle of the +-X walls. */
    private static final int DOOR_LOW = 1;
    private static final int DOOR_HIGH = 2;

    private final List<Bastion> live = new ArrayList<>();
    private BukkitTask sweep;

    private record Bastion(TemporaryTerrain terrain, UUID owner, World world, long expiresAt) {}

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public StoneBastion(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/stone_bastion_options.yml");
        Alkatraz.getInstance().save("spells/stone_bastion.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/stone_bastion.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int size = (int) Math.round(getModifiedStat(p, "bastion_size", option(p, "size", 11)));
        int seconds = (int) Math.round(getModifiedStat(p, "bastion_duration", option(p, "duration", 90)));
        raise(p, size, seconds);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    private void raise(Player p, int size, int seconds) {
        int edge = Math.max(5, size | 1);
        int half = edge / 2;
        Location centre = p.getLocation();
        TemporaryTerrain terrain = new TemporaryTerrain(p, edge * edge);

        for (int offset = -half; offset <= half; offset++) {
            for (int h = 0; h < HEIGHT; h++) {
                boolean door = h >= DOOR_LOW && h <= DOOR_HIGH && (offset == -1 || offset == 0);
                if (!door) {
                    build(terrain, centre, half, offset, h);
                    build(terrain, centre, -half, offset, h);
                }
                build(terrain, centre, offset, half, h);
                build(terrain, centre, offset, -half, h);
            }
        }
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                build(terrain, centre, x, z, HEIGHT);
            }
        }

        long expiresAt = System.currentTimeMillis() + seconds * 1000L;
        live.add(new Bastion(terrain, p.getUniqueId(), p.getWorld(), expiresAt));
        SpellVfx.ring(PALETTE.accent(), p, centre, half, 40, 0, 0.2);
        Utils.sendActionBar(p, lang().get("spells.stonebastion.raised")
                .replace("{seconds}", String.valueOf(seconds)));
        startSweep();
    }

    private void build(TemporaryTerrain terrain, Location centre, int x, int z, int y) {
        Location at = centre.clone().add(x, y, z);
        Block block = at.getBlock();
        if (!block.isEmpty() || TemporaryTerrain.isPlayerOccupied(at)) return;
        terrain.place(at, Material.STONE_BRICKS);
    }

    private void startSweep() {
        if (sweep != null) return;
        sweep = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Bastion bastion : new ArrayList<>(live)) {
                    Player owner = Alkatraz.getInstance().getServer().getPlayer(bastion.owner());
                    boolean gone = owner == null || owner.isDead() || owner.getWorld() != bastion.world();
                    if (!gone && now < bastion.expiresAt()) continue;
                    if (gone) {
                        bastion.terrain().restore();
                    } else {
                        bastion.terrain().restoreWhenClear(now, owner,
                                lang().get("spells.stonebastion.restore_warning"));
                        if (bastion.terrain().isDeferred()) continue;
                    }
                    live.remove(bastion);
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
        for (Bastion bastion : live) {
            bastion.terrain().restore();
        }
        live.clear();
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 20) / 20.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2 + phase * 2.4, 0),
                scale * 1.3, 32, frame * 3, 0.2);
        SpellVfx.column(palette.deep(), caster, origin, scale * 1.4, scale * 0.4, 3, 8, 0.5);
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
                .setDisplayName(lang().get("spells.stonebastion.book_name"))
                .addCustomLoreLine(lang().get("spells.stonebastion.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}
