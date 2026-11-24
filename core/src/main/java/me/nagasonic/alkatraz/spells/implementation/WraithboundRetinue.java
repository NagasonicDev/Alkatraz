package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.util.SummonLimits;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 6 DARK UTILITY: calls a pair of wraith sentinels that hang off the caster's
 * shoulder, mark the nearest hostile mob and cut into it until their allotted strikes run
 * out. They never touch players, and {@link SummonLimits} owns their removal.
 */
public class WraithboundRetinue extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.DARK);
    /** A sentinel only reaches this far for a target, in blocks. */
    private static final double STRIKE_RANGE = 8.0;
    /** One strike every three seconds. */
    private static final long STRIKE_PERIOD = 60L;

    private static final Map<UUID, Retinue> LIVE = new HashMap<>();

    private static final class Retinue {
        final List<Vex> wraiths = new ArrayList<>();
        final Map<UUID, Integer> strikes = new HashMap<>();
        final Set<UUID> glowing = new HashSet<>();
        BukkitTask watch;
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public WraithboundRetinue(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/wraithbound_retinue_options.yml");
        Alkatraz.getInstance().save("spells/wraithbound_retinue.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/wraithbound_retinue.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int count = (int) Math.round(option(p, "sentinels", 2));
        int seconds = (int) Math.round(getModifiedStat(p, "retinue_duration", option(p, "duration", 45)));
        int maxStrikes = (int) Math.round(option(p, "hit_count", 8));
        summon(p.getUniqueId(), p, count, seconds, maxStrikes);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        summon(caster.getUniqueId(), caster, 2, 45, 8);
    }

    private void summon(UUID ownerId, LivingEntity owner, int count, int seconds, int maxStrikes) {
        settle(ownerId);
        World world = owner.getWorld();
        Retinue retinue = new Retinue();
        for (int i = 0; i < count; i++) {
            Location at = owner.getLocation().add(Math.cos(i * 2.1) * 1.5, 1.6, Math.sin(i * 2.1) * 1.5);
            Vex wraith = world.spawn(at, Vex.class, vex -> {
                vex.setAI(false);
                vex.setInvulnerable(true);
                vex.setSilent(true);
                vex.setPersistent(false);
                vex.setRemoveWhenFarAway(false);
                vex.setCollidable(false);
            });
            if (SummonLimits.register(owner, wraith, count)) {
                retinue.wraiths.add(wraith);
                retinue.strikes.put(wraith.getUniqueId(), 0);
            } else {
                wraith.remove();
            }
        }
        if (retinue.wraiths.isEmpty()) return;
        LIVE.put(ownerId, retinue);

        SpellVfx.column(PALETTE.deep(), owner, owner.getLocation(), 2.0, 0.8, 3, 10, 0.9);
        retinue.watch = new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks += (int) STRIKE_PERIOD;
                if (ticks >= seconds * 20L
                        || (owner instanceof Player player
                        && (!player.isOnline() || player.isDead() || player.getWorld() != world))) {
                    settle(ownerId);
                    return;
                }
                for (Vex wraith : new ArrayList<>(retinue.wraiths)) {
                    strike(retinue, owner, wraith, maxStrikes);
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), STRIKE_PERIOD, STRIKE_PERIOD);
    }

    private void strike(Retinue retinue, LivingEntity owner, Vex wraith, int maxStrikes) {
        if (!wraith.isValid()) {
            retinue.wraiths.remove(wraith);
            return;
        }
        int dealt = retinue.strikes.getOrDefault(wraith.getUniqueId(), 0);
        if (dealt >= maxStrikes) return;
        LivingEntity target = nearestHostile(wraith);
        if (target == null) return;

        me.nagasonic.alkatraz.spells.util.SpellDamageUtil.damageWithSpell(
                target, getPower(owner, target, getBasePower()), owner, null, this);
        target.setGlowing(true);
        retinue.glowing.add(target.getUniqueId());
        retinue.strikes.put(wraith.getUniqueId(), dealt + 1);
        World world = wraith.getWorld();
        SpellVfx.beam(PALETTE.accent(), owner, wraith.getLocation().add(0, 0.4, 0),
                target.getLocation().add(0, 1, 0), 8, 0.3, 0.1);
        ParticleCaster.spawn(owner, world, Utils.SOUL, target.getLocation().add(0, 1, 0), 6);
    }

    private LivingEntity nearestHostile(Vex wraith) {
        LivingEntity best = null;
        double bestDistance = STRIKE_RANGE * STRIKE_RANGE;
        for (Entity entity : wraith.getWorld().getNearbyEntities(wraith.getLocation(), STRIKE_RANGE,
                STRIKE_RANGE, STRIKE_RANGE)) {
            if (entity instanceof Player || !(entity instanceof Monster mob)) continue;
            if (!mob.isValid() || mob.isDead()) continue;
            double distance = mob.getLocation().distanceSquared(wraith.getLocation());
            if (distance > bestDistance) continue;
            bestDistance = distance;
            best = mob;
        }
        return best;
    }

    /** Idempotent: cancels one owner's watcher, removes its wraiths and clears its glow. */
    private void settle(UUID ownerId) {
        Retinue retinue = LIVE.remove(ownerId);
        if (retinue == null) return;
        if (retinue.watch != null) {
            retinue.watch.cancel();
            retinue.watch = null;
        }
        for (Vex wraith : retinue.wraiths) {
            if (wraith.isValid()) wraith.remove();
        }
        retinue.wraiths.clear();
        for (UUID id : retinue.glowing) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) entity.setGlowing(false);
        }
        retinue.glowing.clear();
        retinue.strikes.clear();
    }

    @Override
    public void cancelTasks() {
        for (UUID id : new ArrayList<>(LIVE.keySet())) {
            settle(id);
        }
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 20) / 20.0;
        SpellVfx.column(palette.deep(), caster, origin, scale * (1.0 + phase), scale * 0.5, 3, 8, 1.1);
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.3, 0), scale * 1.4, 18, frame * 4, 0.2);
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
                .setDisplayName(lang().get("spells.wraithboundretinue.book_name"))
                .addCustomLoreLine(lang().get("spells.wraithboundretinue.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
