package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.research.ResearchGraphMenu;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class WandTableSelectionMenu extends Menu {

    private static final int SLOT_RESEARCH = 11;
    private static final int SLOT_PROGRESSION = 15;

    public WandTableSelectionMenu(Player viewer) {
        super(viewer, ColorFormat.format("&5Arcane Table"), 27);
    }

    @Override
    protected void build() {
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, createPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inventory.setItem(13, createInfoItem());

        inventory.setItem(SLOT_RESEARCH, createButton(
                Material.BOOKSHELF,
                "&dResearch Library",
                "&7Browse research trees, unlock new",
                "&7spells and abilities through study.",
                "",
                "&eClick to open"
        ));

        inventory.setItem(SLOT_PROGRESSION, createButton(
                Material.NETHER_STAR,
                "&dProgression",
                "&7View your circle progression and",
                "&7advance to the next circle when",
                "&7requirements are met.",
                "",
                "&eClick to open"
        ));
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        int slot = event.getSlot();
        if (slot == SLOT_RESEARCH) {
            new ResearchGraphMenu(viewer).open();
            return true;
        }
        if (slot == SLOT_PROGRESSION) {
            new ProgressionMenu(viewer).open();
            return true;
        }
        return true;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&dArcane Table"));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Choose your path:"));
        lore.add(ColorFormat.format("&7  Research &8- &7Unlock new knowledge"));
        lore.add(ColorFormat.format("&7  Progression &8- &7Advance your circle"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(name));
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(ColorFormat.format(line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(name));
        item.setItemMeta(meta);
        return item;
    }
}
