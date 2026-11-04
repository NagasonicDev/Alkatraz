package me.nagasonic.alkatraz.texturepack;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.MaterialCompat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TexturePackManager {
    private static final String CONFIG_NAME = "texturepack.yml";
    private static YamlConfiguration config;
    private static final Map<String, Integer> itemCMDCache = new ConcurrentHashMap<>();
    private static final Map<String, String> asciiCodeCache = new ConcurrentHashMap<>();
    private static final Map<String, Material> guiMaterialCache = new ConcurrentHashMap<>();
    private static final Map<String, Integer> connectorCMDCache = new ConcurrentHashMap<>();
    private static final Map<String, Integer> researchNodeCMDCache = new ConcurrentHashMap<>();
    private static final Map<String, Integer> researchPanArrowCMDCache = new ConcurrentHashMap<>();
    
    // Load the texture pack configuration
    public static void load() {
        // Alkatraz.onLoad already ran saveConfig("texturepack.yml"), so the file exists.
        reload();
    }
    
    // Reload all caches from config
    public static void reload() {
        // Config.reload() swaps in a brand new YamlConfiguration, so the static reference has to
        // be re-fetched or every cache below would be rebuilt from the boot-time object.
        config = ConfigManager.reloadConfig(CONFIG_NAME).get();
        reloadCaches();
    }
    
    private static void reloadCaches() {
        // Clear caches
        itemCMDCache.clear();
        asciiCodeCache.clear();
        guiMaterialCache.clear();
        connectorCMDCache.clear();
        researchNodeCMDCache.clear();
        researchPanArrowCMDCache.clear();
        
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
        loadResearchNodeCMDs();
        loadResearchPanArrowCMDs();
    }
    
    private static String resourcePackUrl = "";
    private static String resourcePackHash = "";
    private static String resourcePackPrompt = "";
    private static String resourcePackMessage = "";

    private static void loadResourcePackSettings() {
        resourcePackUrl = config.getString("resource_pack.url", "");
        resourcePackHash = config.getString("resource_pack.hash", "");
        resourcePackPrompt = config.getString("resource_pack.prompt", "");
        resourcePackMessage = config.getString("resource_pack.message", "");
    }

    public static String getResourcePackUrl() { return resourcePackUrl; }
    public static String getResourcePackHash() { return resourcePackHash; }
    public static String getResourcePackPrompt() { return resourcePackPrompt; }
    public static String getResourcePackMessage() { return resourcePackMessage; }
    
    private static void loadAsciiCodes() {
        // Jar defaults first so a stale on-disk texturepack.yml still gets new menu glyphs.
        loadMenuTitles(ConfigManager.getDefault(CONFIG_NAME));
        loadMenuTitles(config);
    }

    private static void loadMenuTitles(YamlConfiguration source) {
        if (source == null) return;
        ConfigurationSection menuTitles = source.getConfigurationSection("ascii_codes.menu_titles");
        if (menuTitles == null) return;
        for (String key : menuTitles.getKeys(false)) {
            String value = menuTitles.getString(key);
            if (value != null && !value.isEmpty()) {
                asciiCodeCache.put("menu_title_" + key.toLowerCase(), value);
            }
        }
    }
    
    private static void loadItemCMDs() {
        loadGuiCmds(ConfigManager.getDefault(CONFIG_NAME));
        loadGuiCmds(config);
        loadItemCmds(ConfigManager.getDefault(CONFIG_NAME));
        loadItemCmds(config);
    }

    private static void loadGuiCmds(YamlConfiguration source) {
        if (source == null) return;
        ConfigurationSection guiSection = source.getConfigurationSection("custom_model_data.gui");
        if (guiSection == null) return;
        for (String key : guiSection.getKeys(false)) {
            if (!key.startsWith("spell_")) {
                itemCMDCache.put("gui_" + key.toLowerCase(), guiSection.getInt(key));
            }
        }
    }

    private static void loadItemCmds(YamlConfiguration source) {
        if (source == null) return;
        ConfigurationSection items = source.getConfigurationSection("custom_model_data.items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            itemCMDCache.put("item_" + key.toLowerCase(), items.getInt(key));
        }
    }
    
    private static void loadGuiMaterials() {
        loadGuiMaterials(ConfigManager.getDefault(CONFIG_NAME));
        loadGuiMaterials(config);
    }

    private static void loadGuiMaterials(YamlConfiguration source) {
        if (source == null) return;
        ConfigurationSection decorative = source.getConfigurationSection("gui_items.decorative");
        if (decorative != null) {
            for (String key : decorative.getKeys(false)) {
                guiMaterialCache.put("decorative_" + key.toLowerCase(), MaterialCompat.resolve(decorative.getString(key)));
            }
        }
        ConfigurationSection buttons = source.getConfigurationSection("gui_items.buttons");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                guiMaterialCache.put("button_" + key.toLowerCase(), MaterialCompat.resolve(buttons.getString(key)));
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

    private static void loadResearchNodeCMDs() {
        YamlConfiguration defaults = ConfigManager.getDefault(CONFIG_NAME);
        ConfigurationSection defaultSection = defaults != null
                ? defaults.getConfigurationSection("research_node_textures") : null;
        if (defaultSection != null) {
            for (String key : defaultSection.getKeys(false)) {
                researchNodeCMDCache.put(key.toLowerCase(), defaultSection.getInt(key));
            }
        }
        ConfigurationSection section = config.getConfigurationSection("research_node_textures");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                researchNodeCMDCache.put(key.toLowerCase(), section.getInt(key));
            }
        }
    }

    private static void loadResearchPanArrowCMDs() {
        YamlConfiguration defaults = ConfigManager.getDefault(CONFIG_NAME);
        ConfigurationSection defaultSection = defaults != null
                ? defaults.getConfigurationSection("research_pan_arrows") : null;
        if (defaultSection != null) {
            for (String key : defaultSection.getKeys(false)) {
                researchPanArrowCMDCache.put(key.toLowerCase(), defaultSection.getInt(key));
            }
        }
        ConfigurationSection section = config.getConfigurationSection("research_pan_arrows");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                researchPanArrowCMDCache.put(key.toLowerCase(), section.getInt(key));
            }
        }
    }

    /**
     * Title for a menu: the resource-pack glyph when one is configured and the pack is
     * enabled or forced, otherwise the plain language title.
     *
     * <p>Menus call this in their constructor, before {@code Bukkit.createInventory}, because
     * {@code Menu.matches} compares the open inventory title by exact string equality.
     */
    public static String getMenuTitle(String menuType, String fallback) {
        if (!isResourcePackEnabled() && !Alkatraz.isResourcePackForced()) return fallback;
        String code = getMenuTitleCode(menuType);
        return code == null || code.isEmpty() ? fallback : ColorFormat.format("&f" + code);
    }

    // Get methods for ASCII codes
    public static String getMenuTitleCode(String menuType) {
        return asciiCodeCache.getOrDefault("menu_title_" + menuType.toLowerCase(), "");
    }
    
    // Get methods for custom model data
    public static int getItemCMD(String itemKey) {
        return itemCMDCache.getOrDefault("item_" + itemKey.toLowerCase(), 0);
    }
    
    public static int getGUICMD(String guiElement) {
        return itemCMDCache.getOrDefault("gui_" + guiElement.toLowerCase(), 0);
    }

    /**
     * Transparent filler pane (CMD {@code blank_pane} / 4004). Title-font menu art
     * draws behind items; an unmodelled glass pane would hide it.
     */
    public static ItemStack createBlankPane() {
        return createGuiControl("blank_pane", getGuiMaterial("decorative_blank"), " ");
    }

    /**
     * Pack-backed GUI control: the configured material plus {@code custom_model_data.gui}
     * ({@code back_button} 4003, {@code confirm_button} 4007, {@code cancel_button} 4008, …).
     */
    public static ItemStack createGuiControl(String guiElement, Material fallback, String displayName) {
        Material material = controlMaterial(guiElement, fallback);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null) meta.setDisplayName(displayName);
            applyGuiModel(meta, guiElement);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createBackButton(String displayName) {
        return createGuiControl("back_button", Material.BARRIER, displayName);
    }

    public static ItemStack createConfirmButton(String displayName) {
        return createGuiControl("confirm_button", Material.LIME_CONCRETE, displayName);
    }

    public static ItemStack createCancelButton(String displayName) {
        return createGuiControl("cancel_button", Material.RED_CONCRETE, displayName);
    }

    private static Material controlMaterial(String guiElement, Material fallback) {
        String materialKey = switch (guiElement == null ? "" : guiElement.toLowerCase()) {
            case "blank_pane" -> "decorative_blank";
            case "border_pane" -> "decorative_border";
            case "header_pane" -> "decorative_header";
            case "back_button" -> "button_back";
            case "confirm_button" -> "button_confirm";
            case "cancel_button" -> "button_cancel";
            case "next_page" -> "button_next_page";
            case "prev_page" -> "button_prev_page";
            case "info_button" -> "button_info";
            case "close_button" -> "button_close";
            default -> null;
        };
        Material fromConfig = materialKey != null ? guiMaterialCache.get(materialKey) : null;
        if (fromConfig != null) return fromConfig;
        return fallback != null ? fallback : Material.GRAY_STAINED_GLASS_PANE;
    }

    /** Applies the configured custom model data for a GUI control, if any. */
    public static void applyGuiModel(ItemStack item, String guiElement) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !applyGuiModel(meta, guiElement)) return;
        item.setItemMeta(meta);
    }

    /** @return true if {@code meta} was given a custom model data value */
    public static boolean applyGuiModel(ItemMeta meta, String guiElement) {
        int cmd = getGUICMD(guiElement);
        if (cmd <= 0 || meta == null) return false;
        meta.setCustomModelData(cmd);
        return true;
    }
    
    // Get methods for GUI materials
    public static Material getGuiMaterial(String materialType) {
        Material material = guiMaterialCache.get(materialType.toLowerCase());
        return material != null ? material : Material.STONE; // Fallback
    }

    public static int getConnectorCMD(String pieceKey) {
        return connectorCMDCache.getOrDefault(pieceKey, 0);
    }

    public static int getResearchNodeCMD(String nodeId) {
        return researchNodeCMDCache.getOrDefault(nodeId.toLowerCase(), 0);
    }

    public static int getResearchPanArrowCMD(String direction) {
        return researchPanArrowCMDCache.getOrDefault(direction.toLowerCase(), 0);
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
