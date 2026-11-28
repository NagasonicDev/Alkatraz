package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Common wiring for the six 9th-circle domain spells. */
public abstract class DomainCastSpell extends Spell {

    protected DomainCastSpell(String type) {
        super(type);
    }

    /** Resolves the configured/mastery-adjusted domain radius for this spell. */
    protected abstract double resolveRadius(Player player);

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/" + getId() + "_options.yml");
        Alkatraz.getInstance().save("spells/" + getId() + ".yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/" + getId() + ".yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int radius = (int) Math.round(resolveRadius(p));
        DomainManager.getInstance().begin(this, wand, p, radius);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Domains are player-cast capstones; mob casts have no persistent domain.
    }

    @Override
    public void cancelTasks() {
        DomainManager.getInstance().forceEndSpell(getId());
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 24) / 24.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.5 + phase, 0),
                scale * (0.5 + phase), 24, frame * 2, 0.2);
        SpellVfx.field(palette.core(), caster, origin, scale, 1.2, 12, 6, frame * 0.25);
    }

    /** Numeric option value, falling back when the option file is absent. */
    protected double option(Player p, String id, double fallback) {
        if (getOption(id) == null) return fallback;
        Object value = getOption(id).getSelectedValue(p).getValue();
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public ItemStack getSpellBook() {
        String key = "spells." + getId().replace("_", "") + ".";
        return new Spellbook(getId())
                .setDisplayName(Alkatraz.getLangManager().get(key + "book_name"))
                .addCustomLoreLine(Alkatraz.getLangManager().get(key + "lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
