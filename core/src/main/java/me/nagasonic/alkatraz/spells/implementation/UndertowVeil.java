package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Circle 5 WATER UTILITY: a veil of water that lets the caster and nearby allies breathe
 * under it and swim faster, and snuffs any fire they are carrying.
 */
public class UndertowVeil extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);
    /** Allies join the veil from this radius, in blocks. */
    private static final double ALLY_RADIUS = 8.0;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public UndertowVeil(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/undertow_veil_options.yml");
        Alkatraz.getInstance().save("spells/undertow_veil.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/undertow_veil.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int seconds = (int) Math.round(getModifiedStat(p, "veil_duration", option(p, "duration", 90)));
        int allies = (int) Math.round(option(p, "allies", 2));

        veil(p, seconds);
        List<Player> others = SpellTargeting.allies(p, p.getLocation(), ALLY_RADIUS, allies);
        for (Player ally : others) {
            veil(ally, seconds);
        }

        SpellVfx.ring(PALETTE.accent(), p, p.getLocation(), 3.5, 28, 0, 0.1);
        SpellVfx.ring(PALETTE.core(), p, p.getLocation(), 2.0, 16, 6, 0.4);
        Utils.sendActionBar(p, lang().get("spells.undertowveil.active")
                .replace("{seconds}", String.valueOf(seconds)));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        veil(caster, 90);
    }

    private void veil(LivingEntity target, int seconds) {
        // Water breathing is the veil; dolphin's grace stands in for the flat +30% swim speed.
        target.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING,
                seconds * 20, 0, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,
                seconds * 20, 0, true, true, true));
        target.setFireTicks(0);
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
                .setDisplayName(lang().get("spells.undertowveil.book_name"))
                .addCustomLoreLine(lang().get("spells.undertowveil.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
