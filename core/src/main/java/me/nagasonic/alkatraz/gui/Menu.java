package me.nagasonic.alkatraz.gui;

import de.tr7zw.changeme.nbtapi.NBT;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.texturepack.TexturePackManager;

import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Map<UUID, Menu> activeMenus = new ConcurrentHashMap<>();

    public Menu(Player viewer, String title, int size) {
        this.viewer = viewer;
        this.title = title;
        this.size = size;
        this.inventory = Bukkit.createInventory(null, size, title);
    }

    /**
     * Resource-pack title glyph when the pack is enabled, otherwise {@code fallback}.
     * Call from constructors before {@code super(...)} via a static helper, or pass
     * the result into {@code super}.
     */
    protected static String packTitle(String menuType, String fallback) {
        return TexturePackManager.getMenuTitle(menuType, fallback);
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
        playOpenSound();
    }

    /**
     * Refreshes the menu contents without closing
     */
    public void refresh() {
        if (!clearsInventoryInBuild()) inventory.clear();
        build();
    }

    /**
     * Whether {@link #build()} clears the inventory itself. When true, {@link #refresh()}
     * skips its own clear so the inventory is not wiped twice per rebuild.
     */
    protected boolean clearsInventoryInBuild() {
        return false;
    }

    /**
     * Replaces a single slot without rebuilding the whole menu.
     */
    protected void refreshSlot(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    /**
     * Whether {@link #handleClick} needs to see clicks in the player's own inventory.
     * When false, {@code MenuListener} cancels those clicks without invoking the handler.
     */
    protected boolean usesBottomInventory() {
        return false;
    }

    /**
     * Closes this menu
     */
    public void close() {
        viewer.closeInventory();
        activeMenus.remove(viewer.getUniqueId());
    }

    protected void fillAll() {
        ItemStack blank = TexturePackManager.createBlankPane();
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, blank);
        }
    }

    protected void fillBorders(int... excludeSlots) {
        Set<Integer> excluded = new HashSet<>();
        for (int s : excludeSlots) excluded.add(s);
        ItemStack blank = TexturePackManager.createBlankPane();
        for (int i = 0; i < size; i++) {
            if (!excluded.contains(i)) {
                inventory.setItem(i, blank);
            }
        }
    }

    protected void playOpenSound() {
        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
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
     * Drops every tracked menu instance (plugin disable)
     */
    public static void clearActiveMenus() {
        activeMenus.clear();
    }

    public Set<Integer> dropZoneSlots() {
        return Set.of();
    }

    public void onClose() {
    }

    public void onDrag(InventoryDragEvent event) {
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
        Integer result = NBT.get(item, nbt -> (Integer) nbt.getInteger(key));
        return result != null ? result : 0;
    }

    protected boolean getBoolData(ItemStack item, String key) {
        Boolean result = NBT.get(item, nbt -> (Boolean) nbt.getBoolean(key));
        return result != null && result;
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
