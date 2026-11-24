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
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Circle 5 DARK UTILITY: a wisp that follows the caster, keeps a light burning overhead and
 * gathers the caster's own dropped items. Another player's drops are never touched - the
 * thrower on the item is the whole test.
 */
public class SoulLantern extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.DARK);

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private static final Map<UUID, Lantern> LIVE = new HashMap<>();

    private static final class Lantern {
        BukkitTask wisp;
        TemporaryTerrain lamp;
        Location lampAt;
        long elapsedTicks;
    }

    private long durationSeconds;

    public SoulLantern(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/soul_lantern_options.yml");
        Alkatraz.getInstance().save("spells/soul_lantern.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/soul_lantern.yml").get();
        loadCommonConfig(spellConfig);
        this.durationSeconds = spellConfig.getLong("duration", 35);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int light = (int) Math.round(getModifiedStat(p, "lantern_light", option(p, "light", 10)));
        double pull = getModifiedStat(p, "lantern_pull", option(p, "pull", 8));

        // One lamp per caster: recasting replaces only that caster's lamp.
        UUID id = p.getUniqueId();
        settle(id);
        Lantern state = new Lantern();
        LIVE.put(id, state);
        state.wisp = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || state.elapsedTicks >= durationSeconds * 20L) {
                    settle(id);
                    return;
                }
                state.elapsedTicks += 10L;
                Location feet = p.getLocation();
                World world = feet.getWorld();
                if (world == null) return;

                Location anchor = lightAnchor(feet, light, state.lampAt);
                if (anchor != null && (state.lamp == null || !sameBlock(anchor, state.lampAt))) {
                    releaseLamp(state);
                    TemporaryTerrain fresh = new TemporaryTerrain(p, 1);
                    if (fresh.place(anchor, Material.LIGHT) > 0) {
                        state.lamp = fresh;
                        state.lampAt = anchor;
                    }
                }

                Location wispAt = feet.clone().add(0, 2.0, 0);
                ParticleCaster.spawn(p, world, Utils.SOUL, wispAt, 2);
                ParticleCaster.spawn(p, world, Utils.DUST, wispAt, 1, PALETTE.accentDust());

                for (Entity entity : world.getNearbyEntities(feet, 16, 16, 16)) {
                    if (!(entity instanceof Item drop)) continue;
                    if (!p.getUniqueId().equals(drop.getThrower())) continue;
                    Location at = drop.getLocation().add(0, 0.25, 0);
                    ParticleCaster.spawn(p, world, Utils.DUST, at, 1, PALETTE.coreDust());
                    double distanceSq = at.distanceSquared(feet);
                    if (distanceSq > pull * pull || distanceSq < 4.0) continue;
                    Vector toward = feet.toVector().subtract(at.toVector());
                    Utils.safeNormalizeInPlace(toward);
                    drop.setVelocity(toward.multiply(0.35));
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 10L, 10L);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        ParticleCaster.spawn(caster, caster.getWorld(), Utils.SOUL,
                caster.getLocation().add(0, 2, 0), 6);
    }

    /** The highest open block within {@code reach} blocks above the caster, where the lamp sits. */
    private static Location lightAnchor(Location feet, int reach, Location ownLamp) {
        World world = feet.getWorld();
        if (world == null) return null;
        Location best = null;
        for (int dy = 1; dy <= Math.max(1, reach); dy++) {
            Block block = world.getBlockAt(feet.getBlockX(), feet.getBlockY() + dy, feet.getBlockZ());
            Location at = block.getLocation();
            // Our own lamp is not an obstacle: counting it as open keeps the anchor stable.
            if (ownLamp != null && sameBlock(at, ownLamp)) {
                best = at;
                continue;
            }
            if (!block.isEmpty()) break;
            best = at;
        }
        return best;
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ() && a.getWorld() == b.getWorld();
    }

    /** Restores the lamp block if one is placed. Idempotent. */
    private static void releaseLamp(Lantern state) {
        if (state.lamp == null) return;
        state.lamp.restore();
        state.lamp = null;
        state.lampAt = null;
    }

    /** Idempotent: stops one caster's wisp and restores its lamp. */
    private void settle(UUID id) {
        Lantern state = LIVE.remove(id);
        if (state == null) return;
        if (state.wisp != null) {
            state.wisp.cancel();
            state.wisp = null;
        }
        releaseLamp(state);
    }

    @Override
    public void cancelTasks() {
        for (UUID id : new ArrayList<>(LIVE.keySet())) {
            settle(id);
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
                .setDisplayName(lang().get("spells.soullantern.book_name"))
                .addCustomLoreLine(lang().get("spells.soullantern.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
