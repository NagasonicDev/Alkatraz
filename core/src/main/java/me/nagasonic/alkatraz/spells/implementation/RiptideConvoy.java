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
import me.nagasonic.alkatraz.spells.util.TeleportSafety;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 6 WATER BURST: rides a surge forward across the ground. The riders are carried
 * with it and anything else it washes over is struck once. No block is placed or changed.
 */
public class RiptideConvoy extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);
    /** The surge runs for this many ticks. */
    private static final int TRAVEL_TICKS = 40;
    /** Riders are picked up from this radius, in blocks. */
    private static final double RIDER_RADIUS = 6.0;
    /** Published PvP damage for the single surge hit. */
    private static final double DEFAULT_PVP_CAP = 2.0;

    private static final Map<UUID, BukkitTask> SURGES = new HashMap<>();

    /** PvP damage cap for one surge hit, from the options YAML. */
    private double pvpDamageCap = DEFAULT_PVP_CAP;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public RiptideConvoy(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/riptide_convoy_options.yml");
        Alkatraz.getInstance().save("spells/riptide_convoy.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/riptide_convoy.yml").get();
        loadCommonConfig(spellConfig);
        pvpDamageCap = ConfigManager.getConfig("spells/riptide_convoy_options.yml").get()
                .getDouble("pvp_damage_cap", DEFAULT_PVP_CAP);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        double length = getModifiedStat(p, "convoy_length", option(p, "length", 28));
        double width = Math.max(1.0, option(p, "width", 5));
        Vector direction = Utils.safeNormalize(p.getLocation().getDirection());
        direction.setY(0);
        if (direction.lengthSquared() < 1e-4) return;
        direction.normalize();

        UUID id = p.getUniqueId();
        BukkitTask previous = SURGES.remove(id);
        if (previous != null) previous.cancel();
        World world = p.getWorld();
        List<Player> riders = new ArrayList<>();
        riders.add(p);
        riders.addAll(SpellTargeting.consenting(p, p.getLocation(), RIDER_RADIUS, 4));
        for (Player rider : riders) {
            if (!rider.equals(p)) Utils.sendActionBar(rider, recipientNotice(p));
        }
        Set<UUID> struck = new HashSet<>();
        double step = length / TRAVEL_TICKS;

        BukkitTask surge = new BukkitRunnable() {
            int ticks;
            final Location cursor = p.getLocation().clone();

            @Override
            public void run() {
                if (ticks++ >= TRAVEL_TICKS || !p.isOnline() || p.isDead() || p.getWorld() != world) {
                    cancel();
                    SURGES.remove(id);
                    return;
                }
                cursor.add(direction.clone().multiply(step));
                carry(p, world, riders, cursor, direction);
                wash(world, p, wand, cursor, width, struck);
                SpellVfx.ring(PALETTE.accent(), p, cursor, width * 0.5, 24, ticks * 12, 0.0);
                SpellVfx.ring(PALETTE.core(), p, cursor, width * 0.35, 16, -ticks * 12, 0.2);
            }
        }.runTaskTimer(Alkatraz.getInstance(), 1L, 1L);
        SURGES.put(id, surge);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        Vector direction = Utils.safeNormalize(caster.getLocation().getDirection());
        caster.setVelocity(direction.multiply(0.8));
    }

    private void carry(Player caster, World world, List<Player> riders, Location cursor, Vector direction) {
        for (Player rider : riders) {
            if (!rider.isOnline() || rider.isDead() || rider.getWorld() != world) continue;
            Location want = cursor.clone();
            if (!TeleportSafety.canTeleportAlly(caster, rider, want)) continue;
            rider.setFireTicks(0);
            if (rider.getLocation().distanceSquared(want) > 9.0) {
                rider.teleport(want);
            } else {
                rider.setVelocity(direction.clone().multiply(0.7).setY(rider.getVelocity().getY()));
            }
        }
    }

    private void wash(World world, Player caster, ItemStack wand, Location cursor, double width, Set<UUID> struck) {
        double reach = width * 0.5 + 0.5;
        for (Entity entity : world.getNearbyEntities(cursor, reach, 3.0, reach)) {
            if (!(entity instanceof LivingEntity target) || target.isDead()) continue;
            if (!struck.add(target.getUniqueId())) continue;
            damageArea(caster, target, getBasePower(), pvpDamageCap, wand);
            if (target instanceof Player player && !player.equals(caster)) {
                Utils.sendActionBar(player, recipientNotice(caster));
            }
        }
    }

    /** Short attribution for a player affected by someone else's surge. */
    private static String recipientNotice(Player caster) {
        String key = "spells.recipient_notice_riptide";
        String message = lang().get(key, "player", caster.getName(), "default", "Riptide Convoy");
        return key.equals(message) ? "Riptide Convoy by " + caster.getName() : message;
    }

    @Override
    public void cancelTasks() {
        for (BukkitTask task : SURGES.values()) {
            if (task != null) task.cancel();
        }
        SURGES.clear();
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 20) / 20.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2, 0),
                scale * (0.8 + phase * 1.6), 28, frame * 8, 0.0);
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.6 + phase, 0),
                scale * 0.6, 16, -frame * 8, 0.0);
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
                .setDisplayName(lang().get("spells.riptideconvoy.book_name"))
                .addCustomLoreLine(lang().get("spells.riptideconvoy.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
