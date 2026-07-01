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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class StatsMenu extends Menu {
    private final Player target;
    private final int affinityIncrease;
    private final int resistanceIncrease;

    private static final int[] DISPLAY_SLOTS = {19, 20, 21, 23, 24, 25};
    private static final Element[] DISPLAY_ELEMENTS = {Element.FIRE, Element.WATER, Element.EARTH, Element.AIR, Element.LIGHT, Element.DARK};

    public StatsMenu(Player viewer, Player target) {
        super(viewer, target.getName() + " Stats", 54);
        this.target = target;
        this.affinityIncrease = (Integer) Configs.AFFINITY_PER_POINT.get();
        this.resistanceIncrease = (Integer) Configs.RESISTANCE_PER_POINT.get();
    }

    @Override
    protected void build() {
        MagicProfile profile = ProfileManager.getProfile(target, MagicProfile.class);

        fillBorders();

        inventory.setItem(1, createStatItem(Material.BEACON, "&eCircle Level",
                "&f" + profile.getCircleLevel()));
        inventory.setItem(2, createStatItem(Material.KNOWLEDGE_BOOK, "&bArcane Knowledge",
                "&f" + String.format("%.0f", profile.getArcaneKnowledge())));
        inventory.setItem(3, createStatItem(Material.AMETHYST_SHARD, "&dResearch Points",
                "&f" + profile.getResearchPoints()));
        inventory.setItem(4, createPlayerHead());
        inventory.setItem(5, createStatItem(Material.EXPERIENCE_BOTTLE, "&aStat Points",
                "&f" + profile.getStatPoints()));
        inventory.setItem(6, createStatItem(Material.POTION, "&bMana",
                "&f" + String.format("%.0f", profile.getMana()) + "/" + String.format("%.0f", profile.getMaxMana())));
        inventory.setItem(7, createStatItem(Material.SUGAR, "&bMana Regen",
                "&f" + profile.getManaRegeneration() + "/s"));

        for (int i = 0; i < 6; i++) {
            inventory.setItem(DISPLAY_SLOTS[i], createElementDisplay(profile, DISPLAY_ELEMENTS[i]));
        }

        for (int i = 0; i < 6; i++) {
            inventory.setItem(DISPLAY_SLOTS[i] + 9, createElementInvestItem(profile, DISPLAY_ELEMENTS[i]));
        }

        inventory.setItem(49, createResetButton(profile));
    }

    private void fillBorders() {
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, Utils.getBlank());
        }
    }

    private ItemStack createStatItem(Material material, String label, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(label));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format(value));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerHead() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName(ColorFormat.format("&6" + target.getName()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createElementDisplay(MagicProfile profile, Element element) {
        Material material = switch (element) {
            case FIRE -> Material.FIRE_CHARGE;
            case WATER -> Material.HEART_OF_THE_SEA;
            case EARTH -> Material.DIRT;
            case AIR -> Material.FEATHER;
            case LIGHT -> Material.GLOWSTONE_DUST;
            case DARK -> Material.ECHO_SHARD;
            default -> Material.BARRIER;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(element.getName()));

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format(element.getColor() + "Affinity: &f" + String.format("%.1f", profile.getAffinity(element))));
        lore.add(ColorFormat.format(element.getColor() + "Resistance: &f" + String.format("%.1f", profile.getResistance(element))));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createElementInvestItem(MagicProfile profile, Element element) {
        Material material = switch (element) {
            case FIRE -> Material.MAGMA_CREAM;
            case WATER -> Material.HEART_OF_THE_SEA;
            case EARTH -> Material.GRASS_BLOCK;
            case AIR -> Material.FEATHER;
            case LIGHT -> Material.GLOWSTONE_DUST;
            case DARK -> Material.ECHO_SHARD;
            default -> Material.BARRIER;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(element.getName()));

        List<String> lore = new ArrayList<>();
        int points = profile.getPoints(element);
        lore.add(ColorFormat.format("&eInvested Points: &6" + points));

        if (points > 0) {
            lore.add("");
            lore.add(ColorFormat.format("&eBonus:"));
            lore.add(ColorFormat.format("&7 - " + element.getColor() + "+" +
                (affinityIncrease * points) + " " + element.getName() + " Affinity"));
            lore.add(ColorFormat.format("&7 - " + element.getColor() + "+" +
                (resistanceIncrease * points) + " " + element.getName() + " Resistance"));
        }

        if (profile.getStatPoints() > 0) {
            lore.add("");
            lore.add(ColorFormat.format("&eClick to invest &61 &epoint."));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        int amount = points > 0 ? points : 1;
        item.setAmount(amount);

        setMenuData(item, "element", element.name().toLowerCase());

        return item;
    }

    private ItemStack createResetButton(MagicProfile profile) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&dReset Stats"));

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&dReset Tokens: &f" + profile.getResetTokens()));
        lore.add("");
        lore.add(ColorFormat.format("&eClick to reset all invested stats."));
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

        if (!viewer.equals(target)) {
            viewer.sendMessage(ColorFormat.format("&cYou can only modify your own stats!"));
            return true;
        }

        MagicProfile profile = ProfileManager.getProfile(target, MagicProfile.class);
        String action = getStringData(clicked, "action");

        if ("reset".equals(action)) {
            handleStatsReset(profile);
            return true;
        }

        String elementName = getStringData(clicked, "element");
        if (elementName != null && !elementName.isEmpty()) {
            handleElementInvestment(profile, elementName);
            return true;
        }

        return true;
    }

    private void handleElementInvestment(MagicProfile profile, String elementName) {
        if (profile.getStatPoints() <= 0) {
            viewer.sendMessage(ColorFormat.format("&cYou don't have any stat points available!"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Element element = Element.valueOf(elementName.toUpperCase());

        profile.setStatPoints(profile.getStatPoints() - 1);

        int currentPoints = profile.getPoints(element);
        switch (element) {
            case FIRE -> profile.setFirePoints(currentPoints + 1);
            case WATER -> profile.setWaterPoints(currentPoints + 1);
            case AIR -> profile.setAirPoints(currentPoints + 1);
            case EARTH -> profile.setEarthPoints(currentPoints + 1);
            case LIGHT -> profile.setLightPoints(currentPoints + 1);
            case DARK -> profile.setDarkPoints(currentPoints + 1);
        }

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

        refresh();
    }

    private void handleStatsReset(MagicProfile profile) {
        if (profile.getResetTokens() <= 0) {
            viewer.sendMessage(ColorFormat.format("&cYou don't have any reset tokens!"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

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

        profile.setResetTokens(profile.getResetTokens() - 1);

        profile.setFirePoints(0);
        profile.setWaterPoints(0);
        profile.setAirPoints(0);
        profile.setEarthPoints(0);
        profile.setLightPoints(0);
        profile.setDarkPoints(0);

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

        profile.setStatPoints(profile.getStatPoints() + totalPoints);

        viewer.sendMessage(ColorFormat.format("&aStats reset! Refunded " + totalPoints + " stat points."));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        refresh();
    }
}
