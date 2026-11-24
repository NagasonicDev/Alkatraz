package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Circle 6 EARTH TERRAIN: cracks the stone out of a small volume and hands the caster the
 * real drops. Only the plain stone family is fair game - ores, containers and anything a
 * protection plugin claims are left standing.
 */
public class Quarryheart extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.EARTH);
    private static final Set<Material> HARVESTABLE = Set.of(
            Material.STONE, Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE,
            Material.GRANITE, Material.TUFF, Material.CALCITE, Material.BASALT);
    /** How far the caster may look to pick the volume, in blocks. */
    private static final int TARGET_RANGE = 24;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Quarryheart(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/quarryheart_options.yml");
        Alkatraz.getInstance().save("spells/quarryheart.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/quarryheart.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        Block target = p.getTargetBlockExact(TARGET_RANGE);
        if (target == null) {
            Utils.sendActionBar(p, lang().get("spells.quarryheart.no_target"));
            cancelCast(p);
            return;
        }
        int edge = (int) Math.round(option(p, "radius", 5));
        int limit = (int) Math.round(getModifiedStat(p, "quarry_count", option(p, "count", 24)));
        List<Block> candidates = collect(target, Math.max(1, edge / 2));
        Location centre = target.getLocation().add(0.5, 0.5, 0.5);
        candidates.sort(Comparator.comparingDouble(block -> block.getLocation().distanceSquared(centre)));

        int harvested = 0;
        for (Block block : candidates) {
            if (harvested >= limit) break;
            if (!HARVESTABLE.contains(block.getType())) continue;
            if (!Protection.blockEdit(p, block.getLocation())) continue;
            block.breakNaturally();
            harvested++;
        }

        SpellVfx.ring(PALETTE.accent(), p, centre.clone().add(0, 0.5, 0), 4.0, 28, 0, 0.2);
        Utils.sendActionBar(p, lang().get("spells.quarryheart.harvested")
                .replace("{count}", String.valueOf(harvested)));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        Block below = caster.getLocation().subtract(0, 1, 0).getBlock();
        if (HARVESTABLE.contains(below.getType())) below.breakNaturally();
    }

    private List<Block> collect(Block centre, int half) {
        List<Block> found = new ArrayList<>();
        for (int x = -half; x <= half; x++) {
            for (int y = -half; y <= half; y++) {
                for (int z = -half; z <= half; z++) {
                    found.add(centre.getRelative(x, y, z));
                }
            }
        }
        return found;
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 20) / 20.0;
        SpellVfx.column(palette.deep(), caster, origin.clone().add(0, 1.5 + phase * 2.0, 0),
                3.0, scale * 0.5, 3, 10, 0.4);
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
                .setDisplayName(lang().get("spells.quarryheart.book_name"))
                .addCustomLoreLine(lang().get("spells.quarryheart.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
