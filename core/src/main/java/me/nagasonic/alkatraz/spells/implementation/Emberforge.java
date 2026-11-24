package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.FurnaceRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Circle 5 FIRE UTILITY: smelts what the caster is already carrying, with no fuel and no
 * experience. Output goes to the inventory; anything that will not fit drops at the feet.
 */
public class Emberforge extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /** Recipe lookups are per material and never change at runtime. */
    private final Map<Material, ItemStack> recipeCache = new HashMap<>();

    public Emberforge(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/emberforge_options.yml");
        Alkatraz.getInstance().save("spells/emberforge.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/emberforge.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int batch = (int) Math.round(getModifiedStat(p, "smelt_batch", option(p, "batch", 16)));
        boolean allowFood = !getOption("food").getSelectedValue(p).getValue().equals(Boolean.FALSE);

        List<ItemStack> results = new ArrayList<>();
        int smelted = 0;
        for (int slot = 0; slot < p.getInventory().getSize() && smelted < batch; slot++) {
            ItemStack input = p.getInventory().getItem(slot);
            if (input == null || input.getType().isAir()) continue;
            if (input.getType().isEdible() && !allowFood) continue;
            ItemStack output = smelted(input);
            if (output == null) continue;
            int take = Math.min(batch - smelted, input.getAmount());
            output.setAmount(output.getAmount() * take);
            results.add(output);
            smelted += take;
            if (input.getAmount() == take) {
                p.getInventory().setItem(slot, null);
            } else {
                input.setAmount(input.getAmount() - take);
            }
        }

        Location origin = p.getLocation();
        if (smelted == 0) {
            Utils.sendActionBar(p, lang().get("spells.emberforge.nothing"));
            return;
        }
        SpellVfx.nova(PALETTE.core(), p, origin, 1.4, 24);
        for (ItemStack reward : results) {
            for (ItemStack leftover : p.getInventory().addItem(reward).values()) {
                origin.getWorld().dropItemNaturally(origin, leftover);
            }
            ParticleCaster.spawn(p, origin.getWorld(), Utils.FLAME, origin.clone().add(0, 1, 0), 4);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        ParticleCaster.spawn(caster, caster.getWorld(), Utils.FLAME, caster.getLocation().add(0, 1, 0), 8);
    }

    /** The furnace result for {@code input}, or null when it is not smeltable. */
    private ItemStack smelted(ItemStack input) {
        ItemStack cached = recipeCache.get(input.getType());
        if (cached != null) return cached.clone();
        for (Recipe recipe : Bukkit.getServer().getRecipesFor(input)) {
            if (recipe instanceof FurnaceRecipe furnace
                    && furnace.getInput().getType() == input.getType()) {
                ItemStack result = furnace.getResult().clone();
                recipeCache.put(input.getType(), result);
                return result.clone();
            }
        }
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
                .setDisplayName(lang().get("spells.emberforge.book_name"))
                .addCustomLoreLine(lang().get("spells.emberforge.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
