package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.engraving.EngravingTableMenu;
import me.nagasonic.alkatraz.gui.implementation.research.ResearchGraphMenu;
import me.nagasonic.alkatraz.lang.LangManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class WandTableSelectionMenu extends Menu {

    private static LangManager lang() {
        return me.nagasonic.alkatraz.Alkatraz.getLangManager();
    }

    private static final int SLOT_RESEARCH = 11;
    private static final int SLOT_PROGRESSION = 15;
    private static final int SLOT_ENGINEERING = 13;

    public WandTableSelectionMenu(Player viewer) {
        super(viewer, lang().get("menu.arcane_table"), 27);
    }

    @Override
    protected void build() {
        fillAll();

        inventory.setItem(4, ItemBuilder.of(Material.ENCHANTING_TABLE)
                .name(lang().get("menu.arcane_table"))
                .lore(lang().get("arcane.choose_path"),
                      lang().get("arcane.research_desc"),
                      lang().get("arcane.progression_desc"),
                      lang().get("arcane.engineering_desc"))
                .build());

        inventory.setItem(SLOT_RESEARCH, ItemBuilder.of(Material.BOOKSHELF)
                .name(lang().get("arcane.research"))
                .lore(lang().get("arcane.research_lore"),
                      "",
                      lang().get("arcane.click_to_open"))
                .build());

        inventory.setItem(SLOT_PROGRESSION, ItemBuilder.of(Material.NETHER_STAR)
                .name(lang().get("arcane.progression"))
                .lore(lang().get("arcane.progression_lore"),
                      "",
                      lang().get("arcane.click_to_open"))
                .build());

        inventory.setItem(SLOT_ENGINEERING, ItemBuilder.of(Material.SMITHING_TABLE)
                .name(lang().get("arcane.engineering"))
                .lore(lang().get("arcane.engineering_lore"),
                      "",
                      lang().get("arcane.click_to_open"))
                .build());
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
        if (slot == SLOT_ENGINEERING) {
            new EngravingTableMenu(viewer).open();
            return true;
        }
        return true;
    }
}
