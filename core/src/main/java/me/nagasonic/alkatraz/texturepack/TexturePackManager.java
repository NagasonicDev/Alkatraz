package me.nagasonic.alkatraz.texturepack;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Material;

import java.util.Map;
import java.util.HashMap;

public class TexturePackManager {
    private static final String CONFIG_NAME = "texturepack.yml";
    private static YamlConfiguration config;
    private static final Map<String, Integer> itemCMDCache = new HashMap<>();
    private static final Map<String, String> asciiCodeCache = new HashMap<>();
    private static final Map<String, Material> guiMaterialCache = new HashMap<>();
    private static final Map<String, Integer> connectorCMDCache = new HashMap<>();
    
    // Load the texture pack configuration
    public static void load() {
        // Make sure the config file exists
        Alkatraz.getInstance().saveConfig(CONFIG_NAME);
        config = ConfigManager.getConfig(CONFIG_NAME).get();
        reloadCaches();
    }
    
    // Reload all caches from config
    public static void reload() {
        reloadCaches();
    }
    
    private static void reloadCaches() {
        // Clear caches
        itemCMDCache.clear();
        asciiCodeCache.clear();
        guiMaterialCache.clear();
        
        // Load resource pack settings
        loadResourcePackSettings();
        
        // Load ASCII codes
        loadAsciiCodes();
        
        // Load custom model data for items
        loadItemCMDs();
        
        // Load GUI materials
        loadGuiMaterials();

        // Load connector CMDs
        loadConnectorCMDs();
    }
    
    private static void loadResourcePackSettings() {
        // Resource pack settings will be handled by the main plugin
    }
    
    private static void loadAsciiCodes() {
        // Load menu title codes
        asciiCodeCache.put("menu_title_spells", config.getString("ascii_codes.menu_titles.spells"));
        asciiCodeCache.put("menu_title_equipment", config.getString("ascii_codes.menu_titles.equipment"));
        asciiCodeCache.put("menu_title_research", config.getString("ascii_codes.menu_titles.research"));
        asciiCodeCache.put("menu_title_recipes", config.getString("ascii_codes.menu_titles.recipes"));
        asciiCodeCache.put("menu_title_stats", config.getString("ascii_codes.menu_titles.stats"));
        asciiCodeCache.put("menu_title_grimoire", config.getString("ascii_codes.menu_titles.grimoire"));
        
        // Load icon codes
        asciiCodeCache.put("icon_back", config.getString("ascii_codes.icons.back"));
        asciiCodeCache.put("icon_confirm", config.getString("ascii_codes.icons.confirm"));
        asciiCodeCache.put("icon_cancel", config.getString("ascii_codes.icons.cancel"));
        asciiCodeCache.put("icon_info", config.getString("ascii_codes.icons.info"));
    }
    
    private static void loadItemCMDs() {
        // Load GUI CMDs
        itemCMDCache.put("gui_next_page", config.getInt("custom_model_data.gui.next_page"));
        itemCMDCache.put("gui_prev_page", config.getInt("custom_model_data.gui.prev_page"));
        itemCMDCache.put("gui_back_button", config.getInt("custom_model_data.gui.back_button"));
        itemCMDCache.put("gui_blank_pane", config.getInt("custom_model_data.gui.blank_pane"));
        itemCMDCache.put("gui_border_pane", config.getInt("custom_model_data.gui.border_pane"));
        itemCMDCache.put("gui_header_pane", config.getInt("custom_model_data.gui.header_pane"));

        // Load default item CMDs
        if (config.contains("custom_model_data.items")) {
            for (String key : config.getConfigurationSection("custom_model_data.items").getKeys(false)) {
                itemCMDCache.put("item_" + key, config.getInt("custom_model_data.items." + key));
            }
        }
    }
    
    private static void loadGuiMaterials() {
        // Load decorative materials
        if (config.contains("gui_items.decorative")) {
            for (String key : config.getConfigurationSection("gui_items.decorative").getKeys(false)) {
                guiMaterialCache.put("decorative_" + key, Material.matchMaterial(config.getString("gui_items.decorative." + key)));
            }
        }

        // Load button materials
        if (config.contains("gui_items.buttons")) {
            for (String key : config.getConfigurationSection("gui_items.buttons").getKeys(false)) {
                guiMaterialCache.put("button_" + key, Material.matchMaterial(config.getString("gui_items.buttons." + key)));
            }
        }
    }

    private static void loadConnectorCMDs() {
        ConfigurationSection section = config.getConfigurationSection("connector_textures");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            connectorCMDCache.put(key, section.getInt(key));
        }
    }

    // Get methods for ASCII codes
    public static String getMenuTitleCode(String menuType) {
        return asciiCodeCache.getOrDefault("menu_title_" + menuType.toLowerCase(), "");
    }
    
    public static String getIconCode(String iconType) {
        return asciiCodeCache.getOrDefault("icon_" + iconType.toLowerCase(), "");
    }
    
    // Get methods for custom model data
    public static int getItemCMD(String itemKey) {
        return itemCMDCache.getOrDefault("item_" + itemKey.toLowerCase(), 0);
    }
    
    public static int getGUICMD(String guiElement) {
        return itemCMDCache.getOrDefault("gui_" + guiElement.toLowerCase(), 0);
    }
    
    // Get methods for GUI materials
    public static Material getGuiMaterial(String materialType) {
        Material material = guiMaterialCache.get(materialType.toLowerCase());
        return material != null ? material : Material.STONE; // Fallback
    }

    public static int getConnectorCMD(String pieceKey) {
        return connectorCMDCache.getOrDefault(pieceKey, 0);
    }

    // Get the raw config for admin interface
    public static YamlConfiguration getConfig() {
        return config;
    }
    
    // Check if resource pack is enabled
    public static boolean isResourcePackEnabled() {
        return config.getBoolean("resource_pack.enabled", false);
    }
}