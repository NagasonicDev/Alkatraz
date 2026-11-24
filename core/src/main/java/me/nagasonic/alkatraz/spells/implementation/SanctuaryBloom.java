package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.HarmfulEffects;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Circle 5 LIGHT SUSTAIN: a small bloom of healing that also lifts one curse from everyone
 * standing in it. Allies are the caster plus their scoreboard team, never strangers.
 */
public class SanctuaryBloom extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.LIGHT);

    /** Bukkit has no "is this effect harmful" flag, so the list lives in {@link HarmfulEffects}. */

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /** Heal used when there is no player to read options for (the mob path). */
    private double defaultHeal;

    public SanctuaryBloom(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/sanctuary_bloom_options.yml");
        Alkatraz.getInstance().save("spells/sanctuary_bloom.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/sanctuary_bloom.yml").get();
        loadCommonConfig(spellConfig);
        this.defaultHeal = spellConfig.getDouble("base_heal", 6);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        Location origin = p.getLocation();
        double radius = getModifiedStat(p, "bloom_radius", option(p, "radius", 8));
        double heal = getModifiedStat(p, "healing", option(p, "heal", 6));

        List<LivingEntity> healed = new ArrayList<>();
        healed.add(p);
        healed.addAll(SpellTargeting.allies(p, origin, radius, 5));
        for (LivingEntity target : healed) {
            heal(target, heal);
            liftOneCurse(target);
            if (target instanceof Player ally) {
                Utils.sendActionBar(ally, lang().get("spells.sanctuarybloom.aided_by")
                        .replace("{caster}", p.getName()));
            }
        }
        SpellVfx.nova(PALETTE.core(), p, origin, radius, 36);
        SpellVfx.ring(PALETTE.accent(), p, origin, radius * 0.6, 24, 0.4, 0.2);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        heal(caster, defaultHeal);
        SpellVfx.nova(PALETTE.core(), caster, caster.getLocation(), 4, 20);
    }

    private static void heal(LivingEntity target, double amount) {
        double max = target.getMaxHealth();
        target.setHealth(Math.min(max, target.getHealth() + amount));
    }

    /** Drops the first harmful effect on {@code target}, if it carries one. */
    private static void liftOneCurse(LivingEntity target) {
        HarmfulEffects.clear(target, 1);
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
                .setDisplayName(lang().get("spells.sanctuarybloom.book_name"))
                .addCustomLoreLine(lang().get("spells.sanctuarybloom.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
