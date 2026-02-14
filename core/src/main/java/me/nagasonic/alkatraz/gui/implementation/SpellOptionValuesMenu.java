package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated menu showing all values for a spell option with selection
 */
public class SpellOptionValuesMenu extends PagedMenu<OptionValue<?>> {
    private final AttackSpell spell;
    private final SpellOption option;

    public SpellOptionValuesMenu(Player viewer, AttackSpell spell, SpellOption option) {
        super(viewer,
              ColorFormat.format("&6" + option.getId() + " &7- Select Value"),
              54,
              option.getOptionValues(),
              28);
        this.spell = spell;
        this.option = option;
        
        // Use custom content slots
        this.contentSlots = getCustomContentSlots();
    }

    private int[] getCustomContentSlots() {
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
        // Add option info at top
        ItemStack optionInfo = new ItemStack(option.getIcon());
        ItemMeta meta = optionInfo.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&e" + option.getId()));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + option.getDescription()));
        lore.add("");
        lore.add(ColorFormat.format("&7Select a value below"));
        meta.setLore(lore);
        optionInfo.setItemMeta(meta);
        inventory.setItem(4, optionInfo);
        
        // Fill borders
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        
        for (int i : new int[]{0, 1, 2, 3, 5, 6, 7, 8, 9, 18, 27, 36}) {
            inventory.setItem(i, border);
        }
    }

    @Override
    protected void addBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&cBack to Options"));
        back.setItemMeta(meta);
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected ItemStack createDisplayItem(OptionValue<?> value, int index) {
        ItemStack item = new ItemStack(value.getIcon());
        ItemMeta meta = item.getItemMeta();
        
        boolean isSelected = value.equals(option.getSelectedValue(viewer));
        boolean meetsRequirements = value.meetsRequirements(viewer);
        
        // Display name with selection indicator
        if (isSelected) {
            meta.setDisplayName(ColorFormat.format("&a✓ " + value.getDisplayName()));
        } else {
            meta.setDisplayName(ColorFormat.format("&f" + value.getDisplayName()));
        }
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + value.getDescription()));
        lore.add("");
        
        // Show impacts
        if (!value.getImpacts().isEmpty()) {
            lore.add(ColorFormat.format("&eEffects:"));
            for (ValueImpact impact : value.getImpacts()) {
                lore.add(ColorFormat.format("&a  + " + impact.getDescription()));
            }
            lore.add("");
        }
        
        // Show requirements
        if (meetsRequirements) {
            if (isSelected) {
                lore.add(ColorFormat.format("&a✓ Currently Selected"));
            } else {
                lore.add(ColorFormat.format("&eClick to select"));
            }
        } else {
            lore.add(ColorFormat.format("&c&lLOCKED"));
            lore.add(ColorFormat.format("&cRequirements:"));
            for (ValueRequirement req : value.getUnmetRequirements(viewer)) {
                lore.add(ColorFormat.format("&c  • " + req.getDescription()));
            }
        }
        
        meta.setLore(lore);
        
        // Add enchant glint for locked items or selected items
        if (!meetsRequirements || isSelected) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        item.setItemMeta(meta);
        
        // Store value ID for click handling
        setMenuData(item, "value_id", value.getId());
        setMenuData(item, "meets_requirements", meetsRequirements);
        
        return item;
    }

    @Override
    protected void handleContentClick(OptionValue<?> value, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        boolean meetsRequirements = getBoolData(clicked, "meets_requirements");
        
        if (!meetsRequirements) {
            // Play error sound
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            viewer.sendMessage(ColorFormat.format("&cYou don't meet the requirements for this option!"));
            return;
        }
        
        // Select the value
        if (option.selectValue(viewer, value.getId())) {
            // Play success sound
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            viewer.sendMessage(ColorFormat.format("&aSelected: &f" + value.getDisplayName()));
            
            // Refresh the menu to update selection indicators
            refresh();
        } else {
            // Play error sound
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            viewer.sendMessage(ColorFormat.format("&cFailed to select this option!"));
        }
    }

    @Override
    protected void handleBackClick() {
        // Return to options menu
        SpellOptionsMenu optionsMenu = new SpellOptionsMenu(viewer, spell);
        optionsMenu.open();
    }
}
