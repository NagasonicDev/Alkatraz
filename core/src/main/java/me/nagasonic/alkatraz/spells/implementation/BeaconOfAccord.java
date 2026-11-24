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
 * Circle 6 LIGHT SUSTAIN: plants a beacon of accord that mends everyone standing around it
 * and burns two curses off each of them.
 */
public class BeaconOfAccord extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.LIGHT);
    /** Curses lifted per ally. */
    private static final int CLEANSED = 2;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public BeaconOfAccord(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/beacon_of_accord_options.yml");
        Alkatraz.getInstance().save("spells/beacon_of_accord.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/beacon_of_accord.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        double radius = getModifiedStat(p, "beacon_radius", option(p, "radius", 12));
        int cap = (int) Math.round(getModifiedStat(p, "beacon_allies", 8));
        double heal = Math.max(0.0, getModifiedStat(p, "healing", option(p, "heal", 8)));
        Location origin = p.getLocation();

        List<LivingEntity> healed = new ArrayList<>();
        healed.add(p);
        healed.addAll(SpellTargeting.allies(p, origin, radius, Math.max(0, cap - 1)));

        for (LivingEntity target : healed) {
            mending(target, heal);
            HarmfulEffects.clear(target, CLEANSED);
            if (target instanceof Player ally) {
                Utils.sendActionBar(ally, lang().get("spells.beaconofaccord.aided_by")
                        .replace("{caster}", p.getName()));
            }
        }

        SpellVfx.column(PALETTE.core(), p, origin, 2.4, 1.2, 3, 10, 0.6);
        SpellVfx.ring(PALETTE.accent(), p, origin, radius * 0.5, 40, 0, 0.2);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        mending(caster, 8);
    }

    private void mending(LivingEntity target, double heal) {
        double health = Math.min(target.getMaxHealth(), target.getHealth() + heal);
        target.setHealth(health);
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 24) / 24.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.5 + phase * 2.0, 0),
                scale * (0.6 + phase), 20, frame * 2, 0.2);
        SpellVfx.column(palette.core(), caster, origin, 2.6, scale * 0.6, 3, 8, 0.7);
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
                .setDisplayName(lang().get("spells.beaconofaccord.book_name"))
                .addCustomLoreLine(lang().get("spells.beaconofaccord.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
