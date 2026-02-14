package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated spell list menu
 */
public class SpellsMenu extends PagedMenu<Spell> {
    
    public SpellsMenu(Player viewer) {
        super(viewer, 
              getResourceTitle(),
              54, 
              new ArrayList<>(SpellRegistry.getAllSpells().values()), 
              28);
    }

    private static String getResourceTitle() {
        return Alkatraz.isResourcePackForced() 
            ? ColorFormat.format("&f\uF808\uF001")
            : ColorFormat.format("Spells");
    }

    @Override
    protected ItemStack createDisplayItem(Spell spell, int index) {
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);
        
        // Check if spell is discovered
        boolean discovered = profile.hasDiscoveredSpell(spell) || 
                           viewer.hasPermission("alkatraz.allspells");
        
        if (discovered) {
            return createDiscoveredSpellItem(spell, profile);
        } else {
            return createLockedSpellItem(spell);
        }
    }

    private ItemStack createDiscoveredSpellItem(Spell spell, MagicProfile profile) {
        ItemStack item = spell.getGuiItem().clone();
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ColorFormat.format(spell.getDisplayName()));
        
        List<String> lore = new ArrayList<>();
        for (String line : spell.getDescription()) {
            lore.add(ColorFormat.format(line));
        }
        
        lore.add("");
        lore.add(ColorFormat.format("&bCode: " + spell.getCode()));
        lore.add(ColorFormat.format("&bMana Cost: " + spell.getCost()));
        lore.add(ColorFormat.format("&bCast Time: " + spell.getCastTime() + "s"));
        lore.add(ColorFormat.format("&bElement: " + spell.getElement().getName()));
        lore.add(ColorFormat.format("&bMastery: " + profile.getSpellMastery(spell) + "/" + spell.getMaxMastery()));
        lore.add("");
        lore.add(ColorFormat.format("&eCircle: " + spell.getLevel()));
        
        // Add option indicator if spell has options
        if (spell instanceof AttackSpell attackSpell && !attackSpell.getAllOptions().isEmpty()) {
            lore.add("");
            lore.add(ColorFormat.format("&a✦ Has Spell Options"));
            lore.add(ColorFormat.format("&7Click to configure"));
        }
        
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        
        // Store spell type for click handling
        setMenuData(item, "spell_type", spell.getType());
        setMenuData(item, "has_options", !spell.getAllOptions().isEmpty());
        
        return item;
    }

    private ItemStack createLockedSpellItem(Spell spell) {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ColorFormat.format("&8???"));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7&oCircle: " + spell.getLevel()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }

    @Override
    protected void handleContentClick(Spell spell, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        boolean hasOptions = getBoolData(clicked, "has_options");
        
        if (hasOptions && spell instanceof AttackSpell attackSpell) {
            // Open spell options menu
            SpellOptionsMenu optionsMenu = new SpellOptionsMenu(viewer, attackSpell);
            optionsMenu.open();
        }
    }
}
