package me.nagasonic.alkatraz.gui;

import de.tr7zw.nbtapi.NBT;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all menu GUIs
 */
public abstract class Menu {
    protected Player viewer;
    protected String title;
    protected int size;
    protected Inventory inventory;
    
    // Track menu instances for event handling
    private static final Map<UUID, Menu> activeMenus = new HashMap<>();

    public Menu(Player viewer, String title, int size) {
        this.viewer = viewer;
        this.title = title;
        this.size = size;
        this.inventory = Bukkit.createInventory(null, size, title);
    }

    /**
     * Builds the menu contents
     */
    protected abstract void build();

    /**
     * Handles clicks on items in this menu
     * @return true if the event should be canceled
     */
    protected abstract boolean handleClick(InventoryClickEvent event, ItemStack clicked);

    /**
     * Opens this menu for the viewer
     */
    public void open() {
        build();
        viewer.openInventory(inventory);
        activeMenus.put(viewer.getUniqueId(), this);
    }

    /**
     * Refreshes the menu contents without closing
     */
    public void refresh() {
        inventory.clear();
        build();
    }

    /**
     * Closes this menu
     */
    public void close() {
        viewer.closeInventory();
        activeMenus.remove(viewer.getUniqueId());
    }

    /**
     * Gets the active menu for a player
     */
    public static Menu getActiveMenu(Player player) {
        return activeMenus.get(player.getUniqueId());
    }

    /**
     * Removes a player's active menu (called on inventory close)
     */
    public static void removeActiveMenu(Player player) {
        activeMenus.remove(player.getUniqueId());
    }

    /**
     * Checks if an inventory matches this menu's title
     */
    public boolean matches(String inventoryTitle) {
        return inventoryTitle.equals(title);
    }

    // Helper methods for NBT data storage in items
    protected void setMenuData(ItemStack item, String key, Object value) {
        NBT.modify(item, nbt -> {
            if (value instanceof String) nbt.setString(key, (String) value);
            else if (value instanceof Integer) nbt.setInteger(key, (Integer) value);
            else if (value instanceof Boolean) nbt.setBoolean(key, (Boolean) value);
            else if (value instanceof Double) nbt.setDouble(key, (Double) value);
        });
    }

    protected String getStringData(ItemStack item, String key) {
        return NBT.get(item, nbt -> (String) nbt.getString(key));
    }

    protected int getIntData(ItemStack item, String key) {
        return NBT.get(item, nbt -> (Integer) nbt.getInteger(key));
    }

    protected boolean getBoolData(ItemStack item, String key) {
        return NBT.get(item, nbt -> (Boolean) nbt.getBoolean(key));
    }

    // Getters
    public Player getViewer() {
        return viewer;
    }

    public String getTitle() {
        return title;
    }

    public Inventory getInventory() {
        return inventory;
    }
}
