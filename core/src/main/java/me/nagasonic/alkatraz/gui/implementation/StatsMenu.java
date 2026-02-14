package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.ItemUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Optimized Stats GUI using the new Menu system and MagicProfile
 */
public class StatsMenu extends Menu {
    private final Player target;
    private final int affinityIncrease;
    private final int resistanceIncrease;

    public StatsMenu(Player viewer, Player target) {
        super(viewer, target.getName() + " Stats", 36);
        this.target = target;
        this.affinityIncrease = (Integer) Configs.AFFINITY_PER_POINT.get();
        this.resistanceIncrease = (Integer) Configs.RESISTANCE_PER_POINT.get();
    }

    @Override
    protected void build() {
        MagicProfile profile = ProfileManager.getProfile(target, MagicProfile.class);

        // Add decorative borders (top and bottom)
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, Utils.getBlank());
        }
        for (int i = 27; i < 36; i++) {
            inventory.setItem(i, Utils.getBlank());
        }

        // Top row - Element stats displays (read-only)
        inventory.setItem(1, createElementStatsDisplay(profile, Element.LIGHT));
        inventory.setItem(2, createElementStatsDisplay(profile, Element.EARTH));
        inventory.setItem(3, createElementStatsDisplay(profile, Element.WATER));
        inventory.setItem(4, createPlayerStatsDisplay(profile));
        inventory.setItem(5, createElementStatsDisplay(profile, Element.FIRE));
        inventory.setItem(6, createElementStatsDisplay(profile, Element.AIR));
        inventory.setItem(7, createElementStatsDisplay(profile, Element.DARK));

        // Middle rows - Element investment items (clickable)
        inventory.setItem(12, createElementInvestmentItem(profile, Element.FIRE, Material.MAGMA_CREAM));
        inventory.setItem(13, createElementInvestmentItem(profile, Element.WATER, Material.HEART_OF_THE_SEA));
        inventory.setItem(14, createElementInvestmentItem(profile, Element.EARTH, Material.GRASS_BLOCK));
        inventory.setItem(21, createElementInvestmentItem(profile, Element.AIR, Material.PLAYER_HEAD, 
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWIyNGQ1NzhkYWYxZTg2MjRiNjJjZDY0Nzg2NDUyMmEyNmJmY2RjMDJiYWMxMTAyZjljMWQ5ZDgyZDdiMjVkMiJ9fX0"));
        inventory.setItem(22, createElementInvestmentItem(profile, Element.LIGHT, Material.PLAYER_HEAD,
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTEzMzIzZjIwZTY0MjFlZjFjMWRjNGU2ZjcwYTdhOGEzODRlMWZjYTUyMjA5ZDY2ZTU1YTliNjg1MmYzMmExZCJ9fX0"));
        inventory.setItem(23, createElementInvestmentItem(profile, Element.DARK, Material.PLAYER_HEAD,
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUzNzgyNjdiNzJhMzM2MThjOGM5ZDhmZjRiZTJkNDUyYTI2NTA5YTk5NjRiMDgwYjE5ZDdjMzA4ZWM3OTYwNSJ9fX0"));

        // Reset button
        inventory.setItem(31, createResetButton(profile));
    }

    /**
     * Creates a display item showing element affinity/resistance (top row)
     */
    private ItemStack createElementStatsDisplay(MagicProfile profile, Element element) {
        ItemStack item;
        
        switch (element) {
            case FIRE -> item = new ItemStack(Material.FIRE_CHARGE);
            case WATER -> item = new ItemStack(Material.HEART_OF_THE_SEA);
            case EARTH -> item = new ItemStack(Material.DIRT);
            case AIR -> item = ItemUtils.headFromBase64(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWIyNGQ1NzhkYWYxZTg2MjRiNjJjZDY0Nzg2NDUyMmEyNmJmY2RjMDJiYWMxMTAyZjljMWQ5ZDgyZDdiMjVkMiJ9fX0");
            case LIGHT -> item = ItemUtils.headFromBase64(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTEzMzIzZjIwZTY0MjFlZjFjMWRjNGU2ZjcwYTdhOGEzODRlMWZjYTUyMjA5ZDY2ZTU1YTliNjg1MmYzMmExZCJ9fX0");
            case DARK -> item = ItemUtils.headFromBase64(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUzNzgyNjdiNzJhMzM2MThjOGM5ZDhmZjRiZTJkNDUyYTI2NTA5YTk5NjRiMDgwYjE5ZDdjMzA4ZWM3OTYwNSJ9fX0");
            default -> item = new ItemStack(Material.BARRIER);
        }

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(element.getName()));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format(element.getColor() + "Affinity: " + profile.getAffinity(element)));
        lore.add(ColorFormat.format(element.getColor() + "Resistance: " + profile.getResistance(element)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }

    /**
     * Creates the player stats display item (center top)
     */
    private ItemStack createPlayerStatsDisplay(MagicProfile profile) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&6" + target.getName()));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&eCircle Level: &f" + profile.getCircleLevel()));
        lore.add(ColorFormat.format("&bMana: &f" + String.format("%.0f", profile.getMana()) + 
                                   "/" + String.format("%.0f", profile.getMaxMana())));
        lore.add(ColorFormat.format("&bMana Regen: &f" + profile.getManaRegeneration() + "/s"));
        lore.add("");
        lore.add(ColorFormat.format("&aAvailable Points: &f" + profile.getStatPoints()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }

    /**
     * Creates an element investment item (clickable to invest points)
     */
    private ItemStack createElementInvestmentItem(MagicProfile profile, Element element, Material material) {
        return createElementInvestmentItem(profile, element, material, null);
    }

    private ItemStack createElementInvestmentItem(MagicProfile profile, Element element, 
                                                  Material material, String base64) {
        ItemStack item;
        if (base64 != null && material == Material.PLAYER_HEAD) {
            item = ItemUtils.headFromBase64(base64);
        } else {
            item = new ItemStack(material);
        }

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(element.getName()));
        
        List<String> lore = new ArrayList<>();
        int points = profile.getPoints(element);
        lore.add(ColorFormat.format("&eInvested Points: &6" + points));
        lore.add("");
        
        if (points > 0) {
            lore.add(ColorFormat.format("&eBonus:"));
            lore.add(ColorFormat.format("&7 - " + element.getColor() + "+" + 
                (affinityIncrease * points) + " " + element.getName() + " Affinity"));
            lore.add(ColorFormat.format("&7 - " + element.getColor() + "+" + 
                (resistanceIncrease * points) + " " + element.getName() + " Resistance"));
            lore.add("");
        }
        
        lore.add(ColorFormat.format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        // Set stack size to show invested points visually
        int amount = points > 0 ? points : 1;
        item.setAmount(amount);
        
        // Store element name in NBT
        setMenuData(item, "element", element.name().toLowerCase());
        
        return item;
    }

    /**
     * Creates the reset stats button
     */
    private ItemStack createResetButton(MagicProfile profile) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&dReset Stats"));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&dReset Tokens: &f" + profile.getResetTokens()));
        lore.add("");
        lore.add(ColorFormat.format("&eClick to reset stats."));
        lore.add(ColorFormat.format("&c&lTHIS IS NOT UNDOABLE"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        setMenuData(item, "action", "reset");
        
        return item;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return true;
        }

        // Only allow the target player to modify their own stats
        if (!viewer.equals(target)) {
            viewer.sendMessage(ColorFormat.format("&cYou can only modify your own stats!"));
            return true;
        }

        MagicProfile profile = ProfileManager.getProfile(target, MagicProfile.class);
        String action = getStringData(clicked, "action");
        
        // Handle reset button
        if ("reset".equals(action)) {
            handleStatsReset(profile);
            return true;
        }

        // Handle element investment
        String elementName = getStringData(clicked, "element");
        if (elementName != null && !elementName.isEmpty()) {
            handleElementInvestment(profile, elementName);
            return true;
        }

        return true;
    }

    /**
     * Handles investing a point into an element
     */
    private void handleElementInvestment(MagicProfile profile, String elementName) {
        if (profile.getStatPoints() <= 0) {
            viewer.sendMessage(ColorFormat.format("&cYou don't have any stat points available!"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Element element = Element.valueOf(elementName.toUpperCase());
        
        // Deduct stat point
        profile.setStatPoints(profile.getStatPoints() - 1);
        
        // Increase element points
        int currentPoints = profile.getPoints(element);
        switch (element) {
            case FIRE -> profile.setFirePoints(currentPoints + 1);
            case WATER -> profile.setWaterPoints(currentPoints + 1);
            case AIR -> profile.setAirPoints(currentPoints + 1);
            case EARTH -> profile.setEarthPoints(currentPoints + 1);
            case LIGHT -> profile.setLightPoints(currentPoints + 1);
            case DARK -> profile.setDarkPoints(currentPoints + 1);
        }
        
        // Increase affinity and resistance
        double currentAffinity = profile.getAffinity(element);
        double currentResistance = profile.getResistance(element);
        
        switch (element) {
            case FIRE -> {
                profile.setFireAffinity(currentAffinity + affinityIncrease);
                profile.setFireResistance(currentResistance + resistanceIncrease);
            }
            case WATER -> {
                profile.setWaterAffinity(currentAffinity + affinityIncrease);
                profile.setWaterResistance(currentResistance + resistanceIncrease);
            }
            case AIR -> {
                profile.setAirAffinity(currentAffinity + affinityIncrease);
                profile.setAirResistance(currentResistance + resistanceIncrease);
            }
            case EARTH -> {
                profile.setEarthAffinity(currentAffinity + affinityIncrease);
                profile.setEarthResistance(currentResistance + resistanceIncrease);
            }
            case LIGHT -> {
                profile.setLightAffinity(currentAffinity + affinityIncrease);
                profile.setLightResistance(currentResistance + resistanceIncrease);
            }
            case DARK -> {
                profile.setDarkAffinity(currentAffinity + affinityIncrease);
                profile.setDarkResistance(currentResistance + resistanceIncrease);
            }
        }

        viewer.sendMessage(ColorFormat.format("&aInvested 1 point into " + element.getName() + "!"));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        
        // Refresh the menu to show updated values
        refresh();
    }

    /**
     * Handles resetting all invested stats
     */
    private void handleStatsReset(MagicProfile profile) {
        if (profile.getResetTokens() <= 0) {
            viewer.sendMessage(ColorFormat.format("&cYou don't have any reset tokens!"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Calculate total invested points
        int totalPoints = 0;
        for (Element element : Element.values()) {
            if (element != Element.NONE) {
                totalPoints += profile.getPoints(element);
            }
        }

        if (totalPoints <= 0) {
            viewer.sendMessage(ColorFormat.format("&cYou have no stats to reset!"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Use a reset token
        profile.setResetTokens(profile.getResetTokens() - 1);
        
        // Reset all element points to 0
        profile.setFirePoints(0);
        profile.setWaterPoints(0);
        profile.setAirPoints(0);
        profile.setEarthPoints(0);
        profile.setLightPoints(0);
        profile.setDarkPoints(0);
        
        // Reset all affinities and resistances to base values
        profile.setFireAffinity(0);
        profile.setFireResistance(0);
        profile.setWaterAffinity(0);
        profile.setWaterResistance(0);
        profile.setAirAffinity(0);
        profile.setAirResistance(0);
        profile.setEarthAffinity(0);
        profile.setEarthResistance(0);
        profile.setLightAffinity(0);
        profile.setLightResistance(0);
        profile.setDarkAffinity(0);
        profile.setDarkResistance(0);
        
        // Refund stat points
        profile.setStatPoints(profile.getStatPoints() + totalPoints);

        viewer.sendMessage(ColorFormat.format("&aStats reset! Refunded " + totalPoints + " stat points."));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        
        // Refresh the menu
        refresh();
    }
}
