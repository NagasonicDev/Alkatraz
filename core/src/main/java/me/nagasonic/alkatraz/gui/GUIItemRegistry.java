package me.nagasonic.alkatraz.gui;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.texturepack.TexturePackManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.HashMap;
import java.util.Map;

public class GUIItemRegistry {
    private static final Map<String, ItemStack> itemCache = new HashMap<>();
    
    // Initialize the registry
    public static void init() {
        registerStandardItems();
    }
    
    // Clear and reload all items
    public static void reload() {
        itemCache.clear();
        registerStandardItems();
    }
    
    private static void registerStandardItems() {
        // Register blank/decorative items
        registerItem("blank", createDecorativeItem("blank"));
        registerItem("border", createDecorativeItem("border"));
        registerItem("header", createDecorativeItem("header"));
        
        // Register navigation buttons
        registerItem("back_button", createBackButton());
        registerItem("next_page", createPageButton("next"));
        registerItem("prev_page", createPageButton("prev"));
        registerItem("confirm_button", createConfirmButton());
        registerItem("cancel_button", createCancelButton());
        registerItem("info_button", createInfoButton());
        registerItem("close_button", createCloseButton());
        registerItem("search_button", createSearchButton());
        registerItem("sort_button", createSortButton());
        registerItem("create_button", createCreateButton());
        registerItem("duplicate_button", createDuplicateButton());
    }
    
    private static ItemStack createDecorativeItem(String type) {
        Material material = TexturePackManager.getGuiMaterial("decorative_" + type);
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // Set display name to empty for decorative items
        meta.setDisplayName(" ");
        
        // Set custom model data if available
        int cmd = TexturePackManager.getGUICMD(type + "_pane");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createBackButton() {
        Material material = TexturePackManager.getGuiMaterial("button_back");
        if (material == null) material = Material.ARROW;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(Alkatraz.getLangManager().get("common.back"));
        
        int cmd = TexturePackManager.getGUICMD("back_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createPageButton(String direction) {
        Material material = TexturePackManager.getGuiMaterial("button_" + (direction.equals("next") ? "next_page" : "prev_page"));
        if (material == null) material = Material.PAPER;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(Alkatraz.getLangManager().get(direction.equals("next") ? "common.next_page" : "common.previous_page"));
        
        int cmd = TexturePackManager.getGUICMD(direction + "_page");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createConfirmButton() {
        Material material = TexturePackManager.getGuiMaterial("button_confirm");
        if (material == null) material = Material.LIME_CONCRETE;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(Alkatraz.getLangManager().get("common.confirm"));
        
        int cmd = TexturePackManager.getGUICMD("confirm_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createCancelButton() {
        Material material = TexturePackManager.getGuiMaterial("button_cancel");
        if (material == null) material = Material.RED_CONCRETE;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(Alkatraz.getLangManager().get("common.cancel"));
        
        int cmd = TexturePackManager.getGUICMD("cancel_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createInfoButton() {
        Material material = TexturePackManager.getGuiMaterial("button_info");
        if (material == null) material = Material.KNOWLEDGE_BOOK;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(Alkatraz.getLangManager().get("common.info"));
        
        int cmd = TexturePackManager.getGUICMD("info_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createCloseButton() {
        Material material = TexturePackManager.getGuiMaterial("button_close");
        if (material == null) material = Material.BARRIER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(Alkatraz.getLangManager().get("common.close"));

        int cmd = TexturePackManager.getGUICMD("close_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createSearchButton() {
        Material material = TexturePackManager.getGuiMaterial("button_search");
        if (material == null) material = Material.COMPASS;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(Alkatraz.getLangManager().get("recipes.search"));

        int cmd = TexturePackManager.getGUICMD("search_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createSortButton() {
        Material material = TexturePackManager.getGuiMaterial("button_sort");
        if (material == null) material = Material.HOPPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(Alkatraz.getLangManager().get("recipes.sort.alphabetical"));

        int cmd = TexturePackManager.getGUICMD("sort_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createCreateButton() {
        Material material = TexturePackManager.getGuiMaterial("button_create");
        if (material == null) material = Material.EMERALD;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(Alkatraz.getLangManager().get("recipes.create.title"));

        int cmd = TexturePackManager.getGUICMD("create_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createDuplicateButton() {
        Material material = TexturePackManager.getGuiMaterial("button_duplicate");
        if (material == null) material = Material.ENDER_PEARL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(Alkatraz.getLangManager().get("recipes.detail_duplicate"));

        int cmd = TexturePackManager.getGUICMD("duplicate_button");
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        item.setItemMeta(meta);
        return item;
    }
    
    // Register a custom item
    public static void registerItem(String key, ItemStack item) {
        itemCache.put(key.toLowerCase(), item);
    }
    
    // Get an item from the registry
    public static ItemStack getItem(String key) {
        return itemCache.getOrDefault(key.toLowerCase(), new ItemStack(Material.STONE));
    }
    
    // Get all registered item keys
    public static Iterable<String> getAllKeys() {
        return itemCache.keySet();
    }
}