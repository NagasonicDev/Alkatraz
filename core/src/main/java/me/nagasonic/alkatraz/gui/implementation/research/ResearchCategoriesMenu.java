package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.progression.research.definition.ResearchCategory;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ResearchCategoriesMenu extends PagedMenu<ResearchCategory> {

    public ResearchCategoriesMenu(Player viewer) {
        super(viewer, ColorFormat.format("&5Research Categories"), 54, ResearchService.getCategories(), 36);
    }

    @Override
    protected ItemStack createDisplayItem(ResearchCategory category, int index) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(category.getDisplayName()));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Open this research graph."));
        lore.add(ColorFormat.format("&eClick to browse"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        setMenuData(item, "category", category.getId());
        return item;
    }

    @Override
    protected void handleContentClick(ResearchCategory category, InventoryClickEvent event) {
        new ResearchGraphMenu(viewer, category.getId(), 0, 0).open();
    }

    @Override
    protected void addBackButton() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&fBack to Graph"));
        back.setItemMeta(meta);
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected void handleBackClick() {
        new ResearchGraphMenu(viewer).open();
    }
}
