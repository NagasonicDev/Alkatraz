package me.nagasonic.alkatraz.gui;

import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for paginated menus
 */
public abstract class PagedMenu<T> extends Menu {
    protected int currentPage = 1;
    protected int totalPages;
    protected List<T> allItems;
    protected int itemsPerPage;
    
    // Slot configuration
    protected int[] contentSlots;
    protected int nextPageSlot = 53;
    protected int previousPageSlot = 45;
    protected int backButtonSlot = 49;

    public PagedMenu(Player viewer, String title, int size, List<T> items, int itemsPerPage) {
        super(viewer, title, size);
        this.allItems = items;
        this.itemsPerPage = itemsPerPage;
        this.totalPages = (int) Math.ceil((double) items.size() / itemsPerPage);
        this.contentSlots = getDefaultContentSlots();
    }

    /**
     * Default content slots for a 54-slot inventory (6 rows)
     * Returns slots 9-44 (middle 4 rows)
     */
    protected int[] getDefaultContentSlots() {
        int[] slots = new int[36];
        for (int i = 0; i < 36; i++) {
            slots[i] = i + 9;
        }
        return slots;
    }

    @Override
    protected void build() {
        // Clear inventory
        inventory.clear();
        
        // Add decorative items (borders, etc.)
        addDecorations();
        
        // Add content items for current page
        addPageContent();
        
        // Add navigation buttons
        addNavigationButtons();
        
        // Add back button if applicable
        addBackButton();
    }

    /**
     * Override to add decorative items (borders, info items, etc.)
     */
    protected void addDecorations() {
        // Default: no decorations
    }

    /**
     * Adds items for the current page
     */
    protected void addPageContent() {
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allItems.size());
        
        int slotIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (slotIndex >= contentSlots.length) break;
            
            T item = allItems.get(i);
            ItemStack displayItem = createDisplayItem(item, i);
            inventory.setItem(contentSlots[slotIndex], displayItem);
            slotIndex++;
        }
    }

    /**
     * Creates the display item for a content item
     * Override this to customize how items are displayed
     */
    protected abstract ItemStack createDisplayItem(T item, int index);

    /**
     * Handles clicks on content items
     * Override this to handle item clicks
     */
    protected abstract void handleContentClick(T item, InventoryClickEvent event);

    /**
     * Adds next/previous page buttons
     */
    protected void addNavigationButtons() {
        // Next page button
        if (currentPage < totalPages) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta meta = nextPage.getItemMeta();
            meta.setDisplayName(ColorFormat.format("&fNext Page"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&ePage " + (currentPage + 1)));
            meta.setLore(lore);
            if (meta.hasCustomModelData()) {
                meta.setCustomModelData(32112);
            }
            nextPage.setItemMeta(meta);
            setMenuData(nextPage, "action", "next_page");
            inventory.setItem(nextPageSlot, nextPage);
        }
        
        // Previous page button
        if (currentPage > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta meta = prevPage.getItemMeta();
            meta.setDisplayName(ColorFormat.format("&fPrevious Page"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&ePage " + (currentPage - 1)));
            meta.setLore(lore);
            if (meta.hasCustomModelData()) {
                meta.setCustomModelData(32111);
            }
            prevPage.setItemMeta(meta);
            setMenuData(prevPage, "action", "previous_page");
            inventory.setItem(previousPageSlot, prevPage);
        }
    }

    /**
     * Override to add a back button
     */
    protected void addBackButton() {
        // Default: no back button
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return true;
        }
        
        String action = getStringData(clicked, "action");
        
        if ("next_page".equals(action)) {
            if (currentPage < totalPages) {
                currentPage++;
                refresh();
            }
            return true;
        }
        
        if ("previous_page".equals(action)) {
            if (currentPage > 1) {
                currentPage--;
                refresh();
            }
            return true;
        }
        
        if ("back".equals(action)) {
            handleBackClick();
            return true;
        }
        
        // Check if click is on a content slot
        int slot = event.getSlot();
        for (int contentSlot : contentSlots) {
            if (slot == contentSlot) {
                int pageIndex = (currentPage - 1) * itemsPerPage;
                int itemIndex = pageIndex + getIndexInContentSlots(slot);
                
                if (itemIndex < allItems.size()) {
                    handleContentClick(allItems.get(itemIndex), event);
                }
                return true;
            }
        }
        
        return true;
    }

    /**
     * Handles back button click
     */
    protected void handleBackClick() {
        close();
    }

    /**
     * Gets the index of a slot in the contentSlots array
     */
    private int getIndexInContentSlots(int slot) {
        for (int i = 0; i < contentSlots.length; i++) {
            if (contentSlots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Goes to a specific page
     */
    public void goToPage(int page) {
        if (page >= 1 && page <= totalPages) {
            currentPage = page;
            refresh();
        }
    }

    // Getters
    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public List<T> getAllItems() {
        return allItems;
    }
}
