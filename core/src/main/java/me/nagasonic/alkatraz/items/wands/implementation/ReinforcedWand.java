package me.nagasonic.alkatraz.items.wands.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

public class ReinforcedWand extends Wand {
    public ReinforcedWand(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("wands/reinforced_wand.yml");
        YamlConfiguration wandConfig = ConfigManager.getConfig("wands/reinforced_wand.yml").get();
        loadCommonConfig(wandConfig);
    }

    @Override
    public Recipe getRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(Alkatraz.getInstance(), "reinforced_wand"), this.getItem());
        recipe.shape(getRecipeShape().get(0), getRecipeShape().get(1), getRecipeShape().get(2));
        List<Character> chars = new ArrayList<>();
        List<ItemStack> mats = new ArrayList<>();
        for (String s : getRecipeValues()){
            String[] strs = s.split(":");
            chars.add(s.charAt(0));
            mats.add(Utils.materialFromString(strs[1]));
        }
        for (int i = 0; i < chars.size(); i++){
            recipe.setIngredient(chars.get(i), mats.get(i));
        }
        return recipe;
    }
}
