package me.nagasonic.alkatraz.items.magic.recipe.unlock;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.util.StringUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class NotificationManager {
    private static final String CONFIG_PATH = "recipes.unlock_notifications";

    private NotificationManager() {}

    public static void notify(Player player, String recipeKey) {
        if (player == null || recipeKey == null || recipeKey.isBlank()) return;
        YamlConfiguration config = ConfigManager.getConfig("config.yml").get();
        if (config == null) return;

        Optional<NamespacedKey> parsed = MagicKeys.parse(recipeKey);
        AlkatrazRecipe recipe = parsed.map(RecipeRegistry::get).orElse(null);
        String recipeName = resolveName(recipe, parsed.orElse(null), recipeKey);
        String message = resolveMessage(recipe, recipeName);

        if (config.getBoolean(CONFIG_PATH + ".chat", true) && !StringUtils.isEmpty(message)) {
            player.sendMessage(Utils.chat(message));
        }
        if (config.getBoolean(CONFIG_PATH + ".title", true)) {
            String title = Alkatraz.getLangManager().get("recipes.unlock_title");
            if (!StringUtils.isEmpty(title)) {
                Utils.sendTitle(player, title, "", 40, 5);
            }
        }
        if (config.getBoolean(CONFIG_PATH + ".actionbar", false) && !StringUtils.isEmpty(message)) {
            Utils.sendActionBar(player, message);
        }
        if (config.getBoolean(CONFIG_PATH + ".sound", true)) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        if (config.getBoolean(CONFIG_PATH + ".particles", true)) {
            spawnUnlockParticles(player);
        }
    }

    private static String resolveMessage(AlkatrazRecipe recipe, String recipeName) {
        if (recipe != null && !StringUtils.isEmpty(recipe.getUnlockMessage())) {
            return recipe.getUnlockMessage();
        }
        return Alkatraz.getLangManager().get("recipes.unlock_chat", "recipe", recipeName);
    }

    private static String resolveName(AlkatrazRecipe recipe, NamespacedKey key, String recipeKey) {
        if (recipe != null) {
            if (!StringUtils.isEmpty(recipe.getDisplayName())) return recipe.getDisplayName();
            if (recipe.getResult() != null && recipe.getResult().hasItemMeta()
                    && recipe.getResult().getItemMeta().hasDisplayName()) {
                return recipe.getResult().getItemMeta().getDisplayName();
            }
            if (key != null) return StringUtils.prettifyKey(key.getKey());
        }
        return recipeKey;
    }

    private static void spawnUnlockParticles(Player player) {
        Location location = player.getEyeLocation();
        player.getWorld().spawnParticle(Utils.ENCHANT, location, 30, 0.4, 0.6, 0.4, 0.1);
        player.getWorld().spawnParticle(Utils.TOTEM, location, 12, 0.3, 0.5, 0.3, 0.05);
    }
}