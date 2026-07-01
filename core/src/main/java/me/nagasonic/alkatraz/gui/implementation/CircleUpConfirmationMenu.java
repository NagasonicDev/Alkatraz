package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.ProgressionService;
import me.nagasonic.alkatraz.progression.CircleUpAnimation;
import me.nagasonic.alkatraz.progression.circle.CircleDefinition;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CircleUpConfirmationMenu extends Menu {

    private final int targetCircle;

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;

    public CircleUpConfirmationMenu(Player viewer, int targetCircle) {
        super(viewer, ColorFormat.format("&5&lConfirm Circle Up"), 27);
        this.targetCircle = targetCircle;
    }

    @Override
    protected void build() {
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, createPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inventory.setItem(13, createInfoItem());

        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ColorFormat.format("&a&lConfirm"));
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(ColorFormat.format("&7Advance to the " + StringUtils.toOrdinal(targetCircle) + " circle."));
        confirmMeta.setLore(confirmLore);
        confirm.setItemMeta(confirmMeta);
        setMenuData(confirm, "action", "confirm");
        inventory.setItem(SLOT_CONFIRM, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ColorFormat.format("&c&lCancel"));
        cancel.setItemMeta(cancelMeta);
        setMenuData(cancel, "action", "cancel");
        inventory.setItem(SLOT_CANCEL, cancel);
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if ("confirm".equals(action)) {
            if (!ProgressionService.canAdvance(viewer, targetCircle)) {
                viewer.sendMessage(ColorFormat.format("&cYou no longer meet the requirements for this circle."));
                new ProgressionMenu(viewer).open();
                return true;
            }
            close();
            CircleUpAnimation.play(viewer, () -> {
                ProgressionService.advance(viewer);
                new ProgressionMenu(viewer).open();
            });
            return true;
        }
        if ("cancel".equals(action)) {
            new ProgressionMenu(viewer).open();
            return true;
        }
        return true;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&d&lCircle Up"));

        MagicProfile profile = ProfileManager.getProfile(viewer.getUniqueId(), MagicProfile.class);
        CircleDefinition def = ProgressionService.getCircleDefinition(targetCircle);

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7You are about to advance to the"));
        lore.add(ColorFormat.format("&d" + StringUtils.toOrdinal(targetCircle) + " &7circle."));
        lore.add(ColorFormat.format(""));
        lore.add(ColorFormat.format("&e&lRewards:"));
        if (def != null) {
            if (def.getStatPoints() > 0)
                lore.add(ColorFormat.format("&a  +" + def.getStatPoints() + " Stat Points"));
            lore.add(ColorFormat.format("&a  +" + (int) def.getMaxMana() + " Max Mana"));
            lore.add(ColorFormat.format("&a  +" + def.getManaRegeneration() + " Mana Regen/s"));
        }
        lore.add(ColorFormat.format(""));
        lore.add(ColorFormat.format("&7Are you sure you want to proceed?"));

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
