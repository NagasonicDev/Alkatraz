package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellMarks;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Circle 5 NONE UTILITY: reads a sphere of the world and marks the interesting blocks for
 * the caster alone. It never writes a block and never reveals anything to another player -
 * every mark goes out through {@link SpellMarks}, which is per-viewer.
 */
public class RunicSurvey extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /** One scanned block: its spot, what it is, and how far away it was. */
    private record Hit(Location location, String kind, double distanceSq) {
    }

    public RunicSurvey(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/runic_survey_options.yml");
        Alkatraz.getInstance().save("spells/runic_survey.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/runic_survey.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        double radius = getModifiedStat(p, "scan_radius", option(p, "radius", 24));
        int marks = (int) Math.round(getModifiedStat(p, "max_marks", option(p, "marks", 12)));
        List<Hit> hits = scan(p.getLocation(), (int) Math.round(radius));
        if (hits.isEmpty()) {
            Utils.sendActionBar(p, lang().get("spells.runicsurvey.none"));
            return;
        }
        hits.sort(Comparator.comparingDouble(Hit::distanceSq));
        int shown = Math.min(marks, hits.size());
        for (int i = 0; i < shown; i++) {
            Hit hit = hits.get(i);
            SpellMarks.mark(p, hit.location(), PALETTE, lang().get("spells.runicsurvey.mark_" + hit.kind()));
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Mob casts announce nothing: the marks are per-viewer state a mob has no use for.
    }

    /**
     * Every classified block within {@code radius} of {@code centre}, nearest kept per kind once.
     * Reads only - no block is ever written.
     */
    private static List<Hit> scan(Location centre, int radius) {
        List<Hit> found = new ArrayList<>();
        World world = centre.getWorld();
        if (world == null) return found;
        int cx = centre.getBlockX();
        int cy = centre.getBlockY();
        int cz = centre.getBlockZ();
        double radiusSq = (double) radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distanceSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (distanceSq > radiusSq) continue;
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    String kind = classify(block.getType());
                    if (kind == null) continue;
                    found.add(new Hit(block.getLocation(), kind, distanceSq));
                }
            }
        }
        return found;
    }

    /** The four things a survey reports, or null for everything else. */
    private static String classify(Material material) {
        String name = material.name();
        if (name.endsWith("_CHEST") || name.endsWith("SHULKER_BOX")
                || material == Material.CHEST || material == Material.BARREL
                || material == Material.HOPPER || material == Material.FURNACE
                || material == Material.BLAST_FURNACE || material == Material.SMOKER
                || material == Material.DROPPER || material == Material.DISPENSER) {
            return "container";
        }
        if (material == Material.SPAWNER) return "spawner";
        if (material == Material.NETHER_PORTAL || material == Material.END_PORTAL
                || material == Material.END_PORTAL_FRAME || material == Material.END_GATEWAY) {
            return "portal";
        }
        if (name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS) return "ore";
        return null;
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
                .setDisplayName(lang().get("spells.runicsurvey.book_name"))
                .addCustomLoreLine(lang().get("spells.runicsurvey.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
