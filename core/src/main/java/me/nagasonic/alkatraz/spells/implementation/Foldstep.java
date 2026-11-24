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
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Circle 6 NONE UTILITY: folds the caster and the consenting allies pressed against them
 * through the space in front of the wand. Every destination is checked by
 * {@link TeleportSafety}, and the cast is refused outright when nothing safe is in reach.
 */
public class Foldstep extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);
    /** Allies ride along from this radius, in blocks. */
    private static final double ALLY_RADIUS = 3.0;
    /** Nothing closer than this is worth folding to, in blocks. */
    private static final double MIN_RANGE = 3.0;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Foldstep(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/foldstep_options.yml");
        Alkatraz.getInstance().save("spells/foldstep.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/foldstep.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        double range = getModifiedStat(p, "fold_range", option(p, "range", 30));
        int allyCap = (int) Math.round(option(p, "allies", 2));
        List<Player> allies = SpellTargeting.consenting(p, p.getLocation(), ALLY_RADIUS, allyCap);
        Location destination = findDestination(p, range);
        if (destination == null) {
            Utils.sendActionBar(p, lang().get("spells.foldstep.no_destination"));
            cancelCast(p);
            return;
        }

        Location origin = p.getLocation();
        SpellVfx.ring(PALETTE.accent(), p, origin, 1.2, 20, 0, 1.0);
        p.teleport(destination);
        for (int i = 0; i < allies.size(); i++) {
            Player ally = allies.get(i);
            Location beside = destination.clone().add((i % 2 == 0 ? 1 : -1) * (1 + i / 2), 0, 0);
            Location landing = TeleportSafety.isSafePlayerDestination(beside) ? beside : destination;
            if (!TeleportSafety.canTeleportAlly(p, ally, landing)) continue;
            ally.teleport(landing);
            SpellVfx.ring(PALETTE.deep(), p, ally.getLocation(), 1.0, 12, 0, 1.0);
        }
        SpellVfx.ring(PALETTE.core(), p, p.getLocation(), 1.6, 20, 0, 1.0);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        Location destination = null;
        Vector direction = Utils.safeNormalize(caster.getLocation().getDirection());
        for (double step = 3.0; step <= 12.0; step += 1.0) {
            Location candidate = caster.getLocation().clone().add(direction.clone().multiply(step));
            if (TeleportSafety.isSafePlayerDestination(candidate)) destination = candidate;
        }
        if (destination != null) caster.teleport(destination);
    }

    /**
     * Steps out along the aim and keeps the last landing that is safe. A solid block ends
     * the run, so the fold never lands the caster behind a wall they were aiming at.
     */
    private Location findDestination(Player p, double range) {
        Location origin = p.getLocation();
        Vector direction = Utils.safeNormalize(origin.getDirection());
        Location last = null;
        for (double step = MIN_RANGE; step <= range; step += 1.0) {
            Location candidate = origin.clone().add(direction.clone().multiply(step));
            if (TeleportSafety.isSafePlayerDestination(candidate)) {
                last = candidate;
                continue;
            }
            if (candidate.getBlock().getType().isSolid()) break;
        }
        return last;
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 24) / 24.0;
        double closing = scale * (2.2 - 1.6 * phase);
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.4 + phase * 1.2, 0),
                closing, 24, frame * 3, 0.2);
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.4, 0),
                scale * 1.6, 16, -frame * 3, 0.0);
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
                .setDisplayName(lang().get("spells.foldstep.book_name"))
                .addCustomLoreLine(lang().get("spells.foldstep.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
