package me.nagasonic.alkatraz.items.magic.recipe.unlock;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.StringUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;

public final class NotificationManager {
    private static final String CONFIG_PATH = "recipes.unlock_notifications";

    /**
     * Notification toggles, re-read at most once per second instead of on every
     * notification. Reading the YAML config handle per notification is pure overhead.
     */
    private static volatile NotificationToggles cachedToggles;
    private static volatile long togglesReadAt;

    private record NotificationToggles(boolean chat, boolean title, boolean actionbar,
                                       boolean sound, boolean particles) {}

    private NotificationManager() {}

    private static NotificationToggles toggles() {
        long now = System.currentTimeMillis();
        NotificationToggles cached = cachedToggles;
        if (cached != null && now - togglesReadAt < 1000L) {
            return cached;
        }
        YamlConfiguration config = ConfigManager.getConfig("config.yml").get();
        if (config == null) {
            return cached;
        }
        NotificationToggles fresh = new NotificationToggles(
                config.getBoolean(CONFIG_PATH + ".chat", true),
                config.getBoolean(CONFIG_PATH + ".title", true),
                config.getBoolean(CONFIG_PATH + ".actionbar", false),
                config.getBoolean(CONFIG_PATH + ".sound", true),
                config.getBoolean(CONFIG_PATH + ".particles", true));
        cachedToggles = fresh;
        togglesReadAt = now;
        return fresh;
    }

    public static void notify(Player player, String recipeKey) {
        if (player == null || recipeKey == null || recipeKey.isBlank()) return;
        NotificationToggles flags = toggles();
        if (flags == null) return;

        Optional<NamespacedKey> parsed = MagicKeys.parse(recipeKey);
        AlkatrazRecipe recipe = parsed.map(RecipeRegistry::get).orElse(null);
        String recipeName = resolveName(recipe, parsed.orElse(null), recipeKey);
        String message = resolveMessage(recipe, recipeName);

        if (flags.chat() && !StringUtils.isEmpty(message)) {
            player.sendMessage(Utils.chat(message));
        }
        if (flags.title()) {
            String title = Alkatraz.getLangManager().get("recipes.unlock_title");
            if (!StringUtils.isEmpty(title)) {
                Utils.sendTitle(player, title, "", 40, 5);
            }
        }
        if (flags.actionbar() && !StringUtils.isEmpty(message)) {
            Utils.sendActionBar(player, message);
        }
        if (flags.sound()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        if (flags.particles()) {
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
            ItemStack result = recipe.getResult();
            if (result != null && result.hasItemMeta()) {
                ItemMeta meta = result.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    return meta.getDisplayName();
                }
            }
            if (key != null) return StringUtils.prettifyKey(key.getKey());
        }
        return recipeKey;
    }

    private static void spawnUnlockParticles(Player player) {
        Location location = player.getEyeLocation();
        ParticleCaster.spawn(player, player.getWorld(), Utils.ENCHANT, location, 30, 0.4, 0.6, 0.4, 0.1);
        ParticleCaster.spawn(player, player.getWorld(), Utils.TOTEM, location, 12, 0.3, 0.5, 0.3, 0.05);
    }
}