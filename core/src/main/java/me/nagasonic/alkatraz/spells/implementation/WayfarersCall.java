package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.util.TeleportSafety;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Circle 7 NONE UTILITY: after the framework wind-up, waits five seconds and gathers the
 * team to the caster. The cast-time telegraph and interrupt rule belong to the framework's
 * one wind-up; this class only owns the delayed payload task.
 */
public class WayfarersCall extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);
    /** Post-cast gather delay in ticks; not a second wind-up. */
    private static final int GATHER_TICKS = 100;

    private static final Map<UUID, BukkitTask> GATHERS = new HashMap<>();

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public WayfarersCall(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/wayfarers_call_options.yml");
        Alkatraz.getInstance().save("spells/wayfarers_call.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/wayfarers_call.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int count = (int) Math.round(getModifiedStat(p, "call_count", option(p, "count", 5)));
        double range = getModifiedStat(p, "call_range", option(p, "range", 64));

        UUID caster = p.getUniqueId();
        cancelGather(caster);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                GATHERS.remove(caster);
                if (!p.isOnline() || p.isDead()) return;
                gather(p, count, range);
            }
        }.runTaskLater(Alkatraz.getInstance(), GATHER_TICKS);
        GATHERS.put(caster, task);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    private void gather(Player p, int count, double range) {
        Location anchor = p.getLocation();
        for (Player ally : SpellTargeting.consenting(p, anchor, range, count)) {
            Location spot = beside(anchor, ally.getUniqueId().hashCode());
            Location landing = TeleportSafety.isSafePlayerDestination(spot) ? spot : anchor;
            if (!TeleportSafety.canTeleportAlly(p, ally, landing)) continue;
            ally.teleport(landing);
            Utils.sendActionBar(ally, lang().get("spells.wayfarerscall.ally_ack")
                    .replace("{caster}", p.getName()));
            SpellVfx.ring(PALETTE.core(), p, ally.getLocation(), 1.2, 16, 0, 1.0);
        }
        SpellVfx.nova(PALETTE.core(), p, anchor, 2.4, 24);
        Utils.sendActionBar(p, lang().get("spells.wayfarerscall.answered"));
    }

    /** A landing slot around the caster, spread by the ally's own hash so it is stable. */
    private Location beside(Location anchor, int hash) {
        int slot = Math.floorMod(hash, 8);
        double angle = slot * Math.PI / 4.0;
        return anchor.clone().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
    }

    private void cancelGather(UUID caster) {
        BukkitTask task = GATHERS.remove(caster);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void cancelTasks() {
        for (UUID caster : new ArrayList<>(GATHERS.keySet())) {
            cancelGather(caster);
        }
        GATHERS.clear();
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 24) / 24.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.3, 0),
                scale * (2.0 - phase * 1.4), 28, frame * 4, 0.1);
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 1.0, 0), scale * 0.8, 12, -frame * 4, 0.0);
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
                .setDisplayName(lang().get("spells.wayfarerscall.book_name"))
                .addCustomLoreLine(lang().get("spells.wayfarerscall.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}
