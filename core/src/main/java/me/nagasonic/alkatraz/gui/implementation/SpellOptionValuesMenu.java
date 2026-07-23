package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated menu showing all values for a spell option with selection
 */
public class SpellOptionValuesMenu extends PagedMenu<OptionValue<?>> {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private final Spell spell;
    private final SpellOption option;

    public SpellOptionValuesMenu(Player viewer, Spell spell, SpellOption option) {
        super(viewer,
              ColorFormat.format(lang().get("menu.spell_option_values", "option", option.getId())),
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
        addStandardBorder();

        inventory.setItem(4, ItemBuilder.of(option.getIcon())
                .name("&e" + option.getId())
                .lore("&7" + option.getDescription(), "",
                      lang().get("option_values.select_value"))
                .build());
    }

    @Override
    protected void addBackButton() {
        ItemStack back = ItemBuilder.of(Material.BARRIER)
                .name(lang().get("pooled.back_to_options"))
                .build();
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected ItemStack createDisplayItem(OptionValue<?> value, int index) {
        boolean isSelected = value.equals(option.getSelectedValue(viewer));
        boolean meetsRequirements = value.meetsRequirements(viewer);

        ItemBuilder builder = ItemBuilder.of(value.getIcon())
                .name(isSelected ? "&a✓ " + value.getDisplayName() : "&f" + value.getDisplayName())
                .lore("&7" + value.getDescription(), "");

        // Show impacts
        if (!value.getImpacts().isEmpty()) {
            builder.lore(lang().get("option_values.effects_header"));
            for (ValueImpact impact : value.getImpacts()) {
                builder.lore("&a  + " + impact.getDescription());
            }
            builder.lore("");
        }

        // Show requirements
        if (meetsRequirements) {
            builder.lore(isSelected ? "&a✓ Currently Selected" : lang().get("option_values.click_select"));
        } else {
            builder.lore(lang().get("option_values.locked"), lang().get("option_values.requirements_header"));
            for (ValueRequirement req : value.getUnmetRequirements(viewer)) {
                builder.lore("&c  • " + req.getDescription());
            }
        }

        ItemStack item = builder.glint(!meetsRequirements || isSelected).build();

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
            viewer.sendMessage(ColorFormat.format(lang().get("option_values.selected") + " &f" + value.getDisplayName()));
            
            // Refresh the menu to update selection indicators
            refresh();
        } else {
            // Play error sound
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            viewer.sendMessage(ColorFormat.format(lang().get("option_values.select_failed")));
        }
    }

    @Override
    protected void handleBackClick() {
        // Return to options menu
        SpellOptionsMenu optionsMenu = new SpellOptionsMenu(viewer, spell);
        optionsMenu.open();
    }
}
