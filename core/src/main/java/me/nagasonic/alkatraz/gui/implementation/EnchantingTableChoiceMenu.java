package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.lang.LangManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class EnchantingTableChoiceMenu extends Menu {

    private static LangManager lang() {
        return me.nagasonic.alkatraz.Alkatraz.getLangManager();
    }

    private static final int SLOT_ARCANE = 11;
    private static final int SLOT_ENCHANTING = 15;

    private final Location enchantingTableLocation;

    public EnchantingTableChoiceMenu(Player viewer, Location enchantingTableLocation) {
        super(viewer, lang().get("menu.arcane_table"), 27);
        this.enchantingTableLocation = enchantingTableLocation;
    }

    @Override
    protected void build() {
        fillAll();

        inventory.setItem(4, ItemBuilder.of(Material.ENCHANTING_TABLE)
                .name(lang().get("menu.arcane_table"))
                .lore(lang().get("arcane.choose_path"))
                .build());

        inventory.setItem(SLOT_ARCANE, ItemBuilder.of(Material.ENCHANTING_TABLE)
                .name(lang().get("arcane_table_choice.arcane"))
                .lore(lang().get("arcane_table_choice.arcane_lore"),
                      "",
                      lang().get("arcane.click_to_open"))
                .build());

        inventory.setItem(SLOT_ENCHANTING, ItemBuilder.of(Material.BOOK)
                .name(lang().get("arcane_table_choice.enchanting"))
                .lore(lang().get("arcane_table_choice.enchanting_lore"),
                      "",
                      lang().get("arcane.click_to_open"))
                .build());
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        int slot = event.getSlot();
        if (slot == SLOT_ARCANE) {
            new WandTableSelectionMenu(viewer).open();
            return true;
        }
        if (slot == SLOT_ENCHANTING) {
            viewer.closeInventory();
            if (enchantingTableLocation != null) {
                viewer.openEnchanting(enchantingTableLocation, false);
            } else {
                viewer.openEnchanting(viewer.getLocation(), true);
            }
            return true;
        }
        return true;
    }
}
