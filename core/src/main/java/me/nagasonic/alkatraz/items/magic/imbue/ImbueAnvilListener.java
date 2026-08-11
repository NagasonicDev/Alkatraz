package me.nagasonic.alkatraz.items.magic.imbue;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ImbueAnvilListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack base = inventory.getItem(0);
        if (base == null || !ImbueManager.isImbued(base)) return;
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;
        if (!ImbueManager.isImbued(result)) return;
        event.setResult(ImbueManager.toWrapped(result));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot == 2) return;
        if (rawSlot == 0) {
            handleBaseSlotClick(event);
        } else if (rawSlot >= 3) {
            handlePlayerAreaClick(event);
        }
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory inventory)) return;
        for (int i = 0; i <= 2; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && ImbueManager.isImbued(item)) {
                inventory.setItem(i, ImbueManager.toWrapped(item));
            }
        }
    }

    private void handleBaseSlotClick(InventoryClickEvent event) {
        AnvilInventory inventory = (AnvilInventory) event.getInventory();
        ItemStack base = inventory.getItem(0);
        switch (event.getClick()) {
            case LEFT, RIGHT -> {
                ItemStack cursor = event.getCursor();
                if (!ImbueManager.isImbued(base) && !ImbueManager.isImbued(cursor)) return;
                if (event.getClick() == ClickType.RIGHT) {
                    if (ImbueManager.isImbued(base) && cursor != null && !cursor.getType().isAir()
                            && !ImbueManager.isImbued(cursor)) return;
                    if ((base == null || base.getType().isAir()) && cursor != null && cursor.getAmount() > 1) {
                        event.setCancelled(true);
                        ItemStack single = ImbueManager.toClean(cursor);
                        single.setAmount(1);
                        inventory.setItem(0, single);
                        cursor.setAmount(cursor.getAmount() - 1);
                        event.getView().setCursor(cursor);
                        return;
                    }
                }
                event.setCancelled(true);
                inventory.setItem(0, ImbueManager.isImbued(cursor) ? ImbueManager.toClean(cursor) : cursor);
                event.getView().setCursor(ImbueManager.isImbued(base) ? ImbueManager.toWrapped(base) : base);
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                if (!ImbueManager.isImbued(base)) return;
                event.setCancelled(true);
                ItemStack wrapped = ImbueManager.toWrapped(base);
                inventory.setItem(0, null);
                addToPlayerInventory(event, wrapped);
            }
            case NUMBER_KEY -> {
                int hotbarSlot = event.getHotbarButton();
                Inventory bottom = event.getView().getBottomInventory();
                ItemStack hotbarItem = bottom.getItem(hotbarSlot);
                if (!ImbueManager.isImbued(base) && !ImbueManager.isImbued(hotbarItem)) return;
                event.setCancelled(true);
                inventory.setItem(0, ImbueManager.isImbued(hotbarItem) ? ImbueManager.toClean(hotbarItem) : hotbarItem);
                bottom.setItem(hotbarSlot, ImbueManager.isImbued(base) ? ImbueManager.toWrapped(base) : base);
            }
            default -> { }
        }
    }

    private void handlePlayerAreaClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) return;
        ItemStack current = event.getCurrentItem();
        if (current == null || !ImbueManager.isImbued(current)) return;
        AnvilInventory inventory = (AnvilInventory) event.getInventory();
        ItemStack base = inventory.getItem(0);
        if (base != null && !base.getType().isAir()) return;

        event.setCancelled(true);
        inventory.setItem(0, ImbueManager.toClean(current));
        event.getView().getBottomInventory().setItem(bottomIndexForRawSlot(event.getRawSlot()), null);
    }

    private static int bottomIndexForRawSlot(int rawSlot) {
        return rawSlot >= 30 ? rawSlot - 30 : rawSlot - 3 + 9;
    }

    private static void addToPlayerInventory(InventoryClickEvent event, ItemStack item) {
        Inventory bottom = event.getView().getBottomInventory();
        for (int i = 0; i < bottom.getSize(); i++) {
            ItemStack existing = bottom.getItem(i);
            if (existing == null || existing.getType().isAir()) {
                bottom.setItem(i, item);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
