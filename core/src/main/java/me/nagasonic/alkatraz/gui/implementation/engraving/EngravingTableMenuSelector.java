package me.nagasonic.alkatraz.gui.implementation.engraving;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class EngravingTableMenuSelector extends Menu {

    private static final int ENGRVING_TABLE_SLOT = 22;

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public EngravingTableMenuSelector(Player viewer) {
        super(viewer, ColorFormat.format(lang().get("menu.engraving_selector")), 27);
    }

    @Override
    protected void build() {
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, createBackgroundPane());
        }

        inventory.setItem(ENGRVING_TABLE_SLOT, createInfoItem());
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        int slot = event.getSlot();
        if (slot == ENGRVING_TABLE_SLOT) return true;

        int playerSlot = event.getRawSlot();

        if (playerSlot >= 9 && playerSlot < viewer.getInventory().getSize()) {
            ItemStack item = viewer.getInventory().getItem(playerSlot);
            if (item != null && item.getType() != Material.AIR && MagicItemStack.isMagicItem(item)) {
                handleItemSelection(item);
                viewer.closeInventory();
                return true;
            }
        }

        return true;
    }

    private void handleItemSelection(ItemStack magicItem) {
        var def = MagicItemStack.readDefinition(magicItem);
        var inst = MagicItemStack.readInstance(magicItem);
        if (def.isEmpty() || inst.isEmpty()) {
            viewer.sendMessage(ColorFormat.format(lang().get("engraving.invalid_item")));
            return;
        }

        new EngravingTableMenu(viewer, magicItem, inst.get(), def.get()).open();
    }

    private ItemStack createBackgroundPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&7"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format(lang().get("engraving.select_item")));
            List<String> lore = new ArrayList<>();
            for (String line : lang().get("engraving.select_item_lore").split("\\n")) {
                lore.add(ColorFormat.format(line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}