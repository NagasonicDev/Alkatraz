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
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Circle 6 AIR UTILITY: hooks the caster and the allies crowded around them into the sky,
 * then holds them on slow falling so the drop is only the height they asked for.
 */
public class Skyhook extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.AIR);
    /** Allies ride along from this radius, in blocks. */
    private static final double ALLY_RADIUS = 12.0;
    /** Minecraft gravity per tick squared, so the launch clears the requested height. */
    private static final double GRAVITY = 0.16;
    private static final int SLOW_FALLING_TICKS = 240;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Skyhook(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/skyhook_options.yml");
        Alkatraz.getInstance().save("spells/skyhook.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/skyhook.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        double height = getModifiedStat(p, "skyhook_height", option(p, "height", 12));
        int allyCap = (int) Math.round(option(p, "allies", 3));

        List<Player> group = new ArrayList<>();
        group.add(p);
        group.addAll(SpellTargeting.allies(p, p.getLocation(), ALLY_RADIUS, allyCap));
        for (Player member : group) {
            launch(member, height);
            Utils.sendActionBar(member, lang().get("spells.skyhook.aided_by")
                    .replace("{caster}", p.getName()));
        }

        SpellVfx.column(PALETTE.core(), p, p.getLocation(), 2.5, 1.0, 3, 8, 0.7);
        SpellVfx.ring(PALETTE.accent(), p, p.getLocation(), 2.0, 20, 0, 0.1);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        launch(caster, 8);
    }

    private void launch(LivingEntity target, double height) {
        Vector velocity = target.getVelocity();
        target.setVelocity(new Vector(velocity.getX(), Math.sqrt(GRAVITY * Math.max(1.0, height)), velocity.getZ()));
        target.setFallDistance(0f);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                SLOW_FALLING_TICKS, 0, true, true, true));
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 20) / 20.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, phase * 2.5, 0),
                scale * (1.2 - 0.6 * phase), 20, frame * 6, 0.1);
        SpellVfx.column(palette.core(), caster, origin, scale * 1.2, scale * 0.35, 3, 6, 0.9);
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
                .setDisplayName(lang().get("spells.skyhook.book_name"))
                .addCustomLoreLine(lang().get("spells.skyhook.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
