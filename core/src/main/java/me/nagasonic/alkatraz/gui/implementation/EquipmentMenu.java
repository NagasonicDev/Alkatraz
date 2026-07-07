package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentStorage;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class EquipmentMenu extends Menu {

    private static final int RING_1_SLOT = 20;
    private static final int RING_2_SLOT = 22;
    private static final int ARTIFACT_SLOT = 24;
    private static final int ROBE_SLOT = 31;

    private static final Map<UUID, EquipmentSlot> pendingEquip = new HashMap<>();

    private static final Map<Integer, EquipmentSlot> SLOT_MAP = Map.of(
            RING_1_SLOT, EquipmentSlot.RING_1,
            RING_2_SLOT, EquipmentSlot.RING_2,
            ARTIFACT_SLOT, EquipmentSlot.ARTIFACT,
            ROBE_SLOT, EquipmentSlot.ROBE
    );

    public EquipmentMenu(Player viewer) {
        super(viewer, ColorFormat.format("&8Equipment"), 45);
    }

    @Override
    protected void build() {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, createBackgroundPane());
        }

        pendingEquip.remove(viewer.getUniqueId());

        for (Map.Entry<Integer, EquipmentSlot> entry : SLOT_MAP.entrySet()) {
            int slot = entry.getKey();
            EquipmentSlot equipSlot = entry.getValue();
            Optional<ItemStack> item = EquipmentStorage.getItem(viewer, equipSlot);

            if (item.isPresent() && item.get().getType() != Material.AIR) {
                inventory.setItem(slot, createFilledSlot(item.get(), equipSlot));
            } else {
                inventory.setItem(slot, createEmptySlot(equipSlot));
            }
        }
    }

    private ItemStack createBackgroundPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&7"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFilledSlot(ItemStack equipped, EquipmentSlot equipSlot) {
        ItemStack display = equipped.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();
            lore.add("");
            lore.add(ColorFormat.format("&eClick to &cunequip"));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        setMenuData(display, "equip_slot", equipSlot.getKey().getKey());
        setMenuData(display, "filled", true);
        return display;
    }

    private ItemStack createEmptySlot(EquipmentSlot equipSlot) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String slotName = formatSlotName(equipSlot);
            meta.setDisplayName(ColorFormat.format("&7" + slotName + " &8(Empty)"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7Click then click an item in your"));
            lore.add(ColorFormat.format("&7inventory to equip it."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        setMenuData(item, "equip_slot", equipSlot.getKey().getKey());
        setMenuData(item, "filled", false);
        return item;
    }

    private String formatSlotName(EquipmentSlot slot) {
        return switch (slot.getKey().getKey()) {
            case "ring_1" -> "Ring Slot 1";
            case "ring_2" -> "Ring Slot 2";
            case "artifact" -> "Artifact Slot";
            case "robe" -> "Robe Slot";
            default -> slot.getKey().getKey();
        };
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        int rawSlot = event.getRawSlot();

        // Click in the menu top area
        if (rawSlot < size) {
            if (clicked == null || clicked.getType() == Material.AIR) {
                return true;
            }
            String equipSlotStr = getStringData(clicked, "equip_slot");
            if (equipSlotStr == null) return true;

            EquipmentSlot equipSlot = findSlotByKey(equipSlotStr);
            if (equipSlot == null) return true;

            boolean filled = getBoolData(clicked, "filled");

            if (filled) {
                handleUnequip(equipSlot);
            } else {
                handlePendingEquip(equipSlot);
            }
            return true;
        }

        // Click in the player's own inventory (bottom)
        if (clicked == null || clicked.getType() == Material.AIR) {
            // Clear any pending equip so it doesn't get stuck
            pendingEquip.remove(viewer.getUniqueId());
            return false;
        }

        EquipmentSlot pending = pendingEquip.get(viewer.getUniqueId());
        if (pending != null) {
            if (!isEquipmentItem(clicked)) {
                viewer.sendMessage(ColorFormat.format("&cThat item cannot be equipped in this slot."));
                pendingEquip.remove(viewer.getUniqueId());
                return true;
            }
            handleEquipFromInventory(pending, clicked);
            pendingEquip.remove(viewer.getUniqueId());
            return true;
        }

        // Auto-equip: if the clicked item is a valid equipment item, find first free slot
        if (isEquipmentItem(clicked)) {
            for (EquipmentSlot equipSlot : SLOT_MAP.values()) {
                Optional<ItemStack> existing = EquipmentStorage.getItem(viewer, equipSlot);
                if (existing.isEmpty() || existing.get().getType() == Material.AIR) {
                    handleEquipFromInventory(equipSlot, clicked);
                    return true;
                }
            }
            viewer.sendMessage(ColorFormat.format("&cAll equipment slots are full!"));
            return true;
        }

        return false;
    }

    private void handlePendingEquip(EquipmentSlot slot) {
        pendingEquip.put(viewer.getUniqueId(), slot);
        String slotName = formatSlotName(slot);
        viewer.sendMessage(ColorFormat.format("&eClick an item in your inventory to equip it in the " + slotName + "."));
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleEquipFromInventory(EquipmentSlot slot, ItemStack item) {
        if (item.getType() == Material.AIR) {
            pendingEquip.remove(viewer.getUniqueId());
            viewer.sendMessage(ColorFormat.format("&cEquipment cancelled."));
            return;
        }

        // Fire on_equip trigger event before setting item
        fireOnEquipEvent(viewer, slot, item);

        EquipmentStorage.setItem(viewer, slot, item);
        item.setAmount(item.getAmount() - 1);

        String slotName = formatSlotName(slot);
        viewer.sendMessage(ColorFormat.format("&aEquipped item in the " + slotName + "."));
        viewer.playSound(viewer.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.0f);

        refresh();
    }

    private void handleUnequip(EquipmentSlot slot) {
        Optional<ItemStack> equipped = EquipmentStorage.getItem(viewer, slot);
        if (equipped.isEmpty()) {
            refresh();
            return;
        }

        ItemStack item = equipped.get();

        // Fire on_unequip trigger event before removing item
        fireOnUnequipEvent(viewer, slot, item);

        // Try to add to inventory, drop if full
        HashMap<Integer, ItemStack> remaining = viewer.getInventory().addItem(item);
        if (!remaining.isEmpty()) {
            viewer.getWorld().dropItemNaturally(viewer.getLocation(), remaining.get(0));
            viewer.sendMessage(ColorFormat.format("&eInventory full! Item dropped on the ground."));
        }

        EquipmentStorage.removeItem(viewer, slot);

        String slotName = formatSlotName(slot);
        viewer.sendMessage(ColorFormat.format("&cUnequipped item from the " + slotName + "."));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

        refresh();
    }

    private EquipmentSlot findSlotByKey(String key) {
        for (EquipmentSlot slot : SLOT_MAP.values()) {
            if (slot.getKey().getKey().equals(key)) return slot;
        }
        return null;
    }

    public static void clearPending(Player player) {
        pendingEquip.remove(player.getUniqueId());
    }

    private static boolean isEquipmentItem(ItemStack item) {
        return MagicItemStack.readDefinition(item)
                .map(def -> def.hasComponent(MagicKeys.alkatraz("equipment")))
                .orElse(false);
    }

    private void fireOnEquipEvent(Player player, EquipmentSlot slot, ItemStack item) {
        try {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
            if (me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.isMagicItem(item)) {
                me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance instance = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readInstance(item).orElse(null);
                if (instance != null) {
                    me.nagasonic.alkatraz.api.magic.definition.ItemDefinition definition = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readDefinition(item).orElse(null);
                    if (definition != null) {
                        me.nagasonic.alkatraz.api.magic.trigger.TriggerContext ctx = new me.nagasonic.alkatraz.api.magic.trigger.TriggerContext(player, null, null, null, null, java.util.Map.of());
                        me.nagasonic.alkatraz.api.magic.trigger.TriggerContext scoped = ctx.withSource(instance, slot);
                        me.nagasonic.alkatraz.items.magic.MagicItemServices.get().dispatchTrigger(
                                new me.nagasonic.alkatraz.api.magic.trigger.event.EquipTriggerEvent(scoped));
                    }
                }
            }
        } catch (Exception e) {
            me.nagasonic.alkatraz.Alkatraz.logWarning("Error firing on_equip event: " + e.getMessage());
        }
    }

    private void fireOnUnequipEvent(Player player, EquipmentSlot slot, ItemStack item) {
        try {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
            if (me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.isMagicItem(item)) {
                me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance instance = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readInstance(item).orElse(null);
                if (instance != null) {
                    me.nagasonic.alkatraz.api.magic.definition.ItemDefinition definition = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readDefinition(item).orElse(null);
                    if (definition != null) {
                        me.nagasonic.alkatraz.api.magic.trigger.TriggerContext ctx = new me.nagasonic.alkatraz.api.magic.trigger.TriggerContext(player, null, null, null, null, java.util.Map.of());
                        me.nagasonic.alkatraz.api.magic.trigger.TriggerContext scoped = ctx.withSource(instance, slot);
                        me.nagasonic.alkatraz.items.magic.MagicItemServices.get().dispatchTrigger(
                                new me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent(
                                        me.nagasonic.alkatraz.api.magic.registry.MagicKeys.alkatraz("on_unequip"), scoped));
                    }
                }
            }
        } catch (Exception e) {
            me.nagasonic.alkatraz.Alkatraz.logWarning("Error firing on_unequip event: " + e.getMessage());
        }
    }
}
