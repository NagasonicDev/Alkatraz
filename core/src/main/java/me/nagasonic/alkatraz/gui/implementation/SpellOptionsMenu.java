package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated menu showing all spell options
 */
public class SpellOptionsMenu extends PagedMenu<SpellOption> {
    private final Spell spell;

    public SpellOptionsMenu(Player viewer, Spell spell) {
        super(viewer, 
              ColorFormat.format("&6" + spell.getDisplayName() + " &7- Options"),
              54,
              new ArrayList<>(spell.getAllOptions().values()),
              28);
        this.spell = spell;
        
        // Use custom content slots (leave space for decorations)
        this.contentSlots = getCustomContentSlots();
    }

    private int[] getCustomContentSlots() {
        // Use slots 10-43, excluding edges
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(i -> i).toArray();
    }

    @Override
    protected void addDecorations() {
        // Add spell info at top
        ItemStack spellInfo = spell.getGuiItem().clone();
        ItemMeta meta = spellInfo.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&6" + spell.getDisplayName()));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Configure spell options"));
        lore.add("");
        lore.add(ColorFormat.format("&eCircle: " + spell.getLevel()));
        meta.setLore(lore);
        spellInfo.setItemMeta(meta);
        inventory.setItem(4, spellInfo);

        int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int i : slots){
            inventory.setItem(i, Utils.getBlank());
        }
    }

    @Override
    protected void addBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&cBack to Spells"));
        back.setItemMeta(meta);
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected ItemStack createDisplayItem(SpellOption option, int index) {
        ItemStack item = new ItemStack(option.getIcon());
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ColorFormat.format("&e" + option.getId()));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + option.getDescription()));
        lore.add("");

        lore.add(ColorFormat.format("&aCurrent: &f" + option.getSelectedValue(viewer).getDisplayName()));
        
        lore.add("");
        lore.add(ColorFormat.format("&eClick to configure"));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        // Store option ID for click handling
        setMenuData(item, "option_id", option.getId());
        
        return item;
    }

    @Override
    protected void handleContentClick(SpellOption option, InventoryClickEvent event) {
        // Open value selector menu
        SpellOptionValuesMenu valuesMenu = new SpellOptionValuesMenu(viewer, spell, option);
        valuesMenu.open();
    }

    @Override
    protected void handleBackClick() {
        // Return to spells menu
        SpellsMenu spellsMenu = new SpellsMenu(viewer);
        spellsMenu.open();
    }
}
