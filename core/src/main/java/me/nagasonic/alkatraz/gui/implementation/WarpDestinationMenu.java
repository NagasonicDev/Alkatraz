package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.implementation.Warp;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Destination picker opened when casting Warp. Shows every set warp point with
 * its distance from the caster; only points on the same world within range can
 * be selected. Closing the menu aborts the cast for free.
 */
public class WarpDestinationMenu extends Menu {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private final Warp warp;

    public WarpDestinationMenu(Player viewer, Spell spell) {
        super(viewer, ColorFormat.format(lang().get("menu.warp_destination")), 27);
        this.warp = (Warp) spell;
    }

    @Override
    protected void build() {
        fillBorders(4, 10, 11, 12, 13, 14, 15, 16, 22);
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);

        inventory.setItem(4, ItemBuilder.of(Material.ENDER_EYE)
                .name("&5Warp")
                .lore("&7Choose a destination to", "&7channel your teleport.", "",
                        "&7Slots: &f" + slotCount(profile),
                        "&7Max range: &f" + rangeOf(profile) + "m")
                .build());

        int slots = profile == null ? 0 : warp.maxSlots(profile);
        for (int slot = 0; slot < slots; slot++) {
            inventory.setItem(slotToGuiSlot(slot), buildPointItem(slot, profile));
        }

        ItemStack back = ItemBuilder.of(Material.BARRIER)
                .name(lang().get("pooled.back_to_options"))
                .build();
        setMenuData(back, "action", "back");
        inventory.setItem(22, back);
    }

    private String slotCount(MagicProfile profile) {
        return String.valueOf(profile == null ? 0 : warp.maxSlots(profile));
    }

    private String rangeOf(MagicProfile profile) {
        return String.valueOf((int) Math.round(warp.maxDistance(profile)));
    }

    private int slotToGuiSlot(int slot) {
        return 10 + slot;
    }

    private ItemStack buildPointItem(int slot, MagicProfile profile) {
        MagicProfile.WarpPoint point = profile == null ? null : profile.getWarpPoints().get(slot);
        ItemBuilder builder;
        boolean selectable = false;

        if (point == null) {
            builder = ItemBuilder.of(Material.GRAY_DYE);
        } else {
            Location dest = point.toLocation();
            boolean crossWorld = dest == null || !dest.getWorld().equals(viewer.getWorld());
            double distance = crossWorld ? -1 : viewer.getLocation().distance(dest);
            double maxRange = warp.maxDistance(profile);
            boolean inRange = !crossWorld && distance <= maxRange;

            if (crossWorld) {
                builder = ItemBuilder.of(Material.LIGHT_GRAY_DYE);
            } else if (inRange) {
                builder = ItemBuilder.of(Material.ENDER_PEARL);
                selectable = true;
            } else {
                builder = ItemBuilder.of(Material.REDSTONE);
            }
            decoratePoint(builder, point, crossWorld, distance, maxRange, inRange);
        }

        if (!selectable) {
            if (point == null) {
                builder.name("&7Empty Slot " + (slot + 1))
                        .lore("&7No warp point set.");
            }
            ItemStack item = builder.build();
            setMenuData(item, "action", "warp_slot");
            setMenuData(item, "slot", slot);
            setMenuData(item, "selectable", false);
            return item;
        }

        ItemStack item = builder.build();
        setMenuData(item, "action", "warp_slot");
        setMenuData(item, "slot", slot);
        setMenuData(item, "selectable", true);
        return item;
    }

    private void decoratePoint(ItemBuilder builder, MagicProfile.WarpPoint point,
                               boolean crossWorld, double distance, double maxRange, boolean inRange) {
        builder.name(inRange ? "&a&lWarp Point #" + (point.slot() + 1) : "&7Warp Point #" + (point.slot() + 1))
                .lore("&7World: &f" + point.world(), "");

        if (crossWorld) {
            builder.lore("&7Location: &f" + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z(),
                    "&cDifferent world", "&cUnavailable");
        } else if (inRange) {
            builder.lore("&7Location: &f" + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z(),
                    "&7Distance: &f" + (int) Math.round(distance) + "m",
                    "&7Max range: &f" + (int) Math.round(maxRange) + "m", "",
                    "&eClick to warp");
        } else {
            builder.lore("&7Location: &f" + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z(),
                    "&7Distance: &f" + (int) Math.round(distance) + "m",
                    "&cToo far!",
                    "&7Max range: &f" + (int) Math.round(maxRange) + "m");
        }
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null) return true;
        String action = getStringData(clicked, "action");
        if (action == null) return true;

        switch (action) {
            case "back" -> {
                close();
                return true;
            }
            case "warp_slot" -> {
                boolean selectable = getBoolData(clicked, "selectable");
                if (!selectable) {
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }
                int slot = getIntData(clicked, "slot");
                MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);
                MagicProfile.WarpPoint point = profile == null ? null : profile.getWarpPoints().get(slot);
                if (point == null) {
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }
                close();
                warp.beginChannel(viewer, point);
                return true;
            }
            default -> {
                return true;
            }
        }
    }
}