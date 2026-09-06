package me.nagasonic.alkatraz.gui.implementation.options;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.SpellOptionsMenu;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.implementation.Warp;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Custom spell option menu for managing warp points. Empty slots save the
 * player's current location; filled slots show a preview and can be cleared
 * with shift-click. Slots beyond the player's current mastery cap are shown
 * locked.
 */
public class WarpPointsMenu extends Menu {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private final Spell spell;
    private final Warp warp;

    public WarpPointsMenu(Player viewer, Spell spell, SpellOption option) {
        super(viewer, ColorFormat.format(lang().get("menu.warp_points")), 27);
        this.spell = spell;
        this.warp = (Warp) spell;
    }

    @Override
    protected void build() {
        fillBorders(4, 10, 11, 12, 13, 14, 15, 16, 22);
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);

        inventory.setItem(4, ItemBuilder.of(Material.COMPASS)
                .name("&5Warp Points")
                .lore("&7Click an empty slot to save", "&7your current location.", "",
                        "&eShift-click a set point to clear it.")
                .build());

        int absolute = warp.maxSlotsAbsolute();
        for (int slot = 0; slot < absolute; slot++) {
            inventory.setItem(slotToGuiSlot(slot), buildSlotItem(slot, profile));
        }

        ItemStack back = ItemBuilder.of(Material.BARRIER)
                .name(lang().get("pooled.back_to_options"))
                .build();
        setMenuData(back, "action", "back");
        inventory.setItem(22, back);
    }

    private int slotToGuiSlot(int slot) {
        return 10 + slot;
    }

    private ItemStack buildSlotItem(int slot, MagicProfile profile) {
        int max = profile == null ? 0 : warp.maxSlots(profile);

        if (slot >= max) {
            ItemStack locked = ItemBuilder.of(Material.IRON_BARS)
                    .name("&8Locked Slot " + (slot + 1))
                    .lore("&7Requires &f" + warp.slotsMasteryRequirement(slot) + " &7Warp mastery")
                    .build();
            setMenuData(locked, "action", "locked_slot");
            return locked;
        }

        MagicProfile.WarpPoint point = profile == null ? null : profile.getWarpPoints().get(slot);
        if (point == null) {
            ItemStack empty = ItemBuilder.of(Material.LIGHT_GRAY_DYE)
                    .name("&7Slot " + (slot + 1))
                    .lore("&7Empty.", "&eClick to set current location.")
                    .build();
            setMenuData(empty, "action", "slot_click");
            setMenuData(empty, "slot", slot);
            setMenuData(empty, "filled", false);
            return empty;
        }

        ItemBuilder builder = ItemBuilder.of(Material.ENDER_PEARL)
                .name("&a&lSlot " + (point.slot() + 1))
                .lore("&7World: &f" + point.world(),
                        "&7Location: &f" + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z());
        Location dest = point.toLocation();
        if (dest != null && dest.getWorld().equals(viewer.getWorld())) {
            double distance = viewer.getLocation().distance(dest);
            builder.lore("&7Distance: &f" + (int) Math.round(distance) + "m");
        } else {
            builder.lore("&7Distance: &fN/A");
        }
        builder.lore("", "&eClick to move point here", "&eShift-click to clear");

        ItemStack item = builder.build();
        setMenuData(item, "action", "slot_click");
        setMenuData(item, "slot", slot);
        setMenuData(item, "filled", true);
        return item;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null) return true;
        String action = getStringData(clicked, "action");
        if (action == null) return true;

        switch (action) {
            case "back" -> {
                new SpellOptionsMenu(viewer, spell).open();
                return true;
            }
            case "locked_slot" -> {
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                viewer.sendMessage(ColorFormat.format("&cYou don't have enough Warp mastery for this slot!"));
                return true;
            }
            case "slot_click" -> {
                handleSlotClick(event);
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private void handleSlotClick(InventoryClickEvent event) {
        int slot = getIntData(event.getCurrentItem(), "slot");
        boolean filled = getBoolData(event.getCurrentItem(), "filled");

        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);
        if (profile == null) return;
        if (event.isShiftClick()) {
            if (!filled) {
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            profile.removeWarpPoint(slot);
            Utils.sendActionBar(viewer, lang().get("spells.warp.point_cleared"));
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            refresh();
            return;
        }

        Location loc = viewer.getLocation();
        profile.setWarpPoint(new MagicProfile.WarpPoint(
                slot, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()));
        Utils.sendActionBar(viewer, lang().get("spells.warp.point_set"));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        refresh();
    }
}