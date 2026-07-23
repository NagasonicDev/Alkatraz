package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentStorage;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class EquipmentMenu extends Menu {

    private static final int RING_SLOT = 20;
    private static final int NECKLACE_SLOT = 13;
    private static final int BRACELET_SLOT = 24;
    private static final int PENDANT_SLOT = 31;

    private static final Map<UUID, EquipmentSlot> pendingEquip = new ConcurrentHashMap<>();

    private static final Map<Integer, EquipmentSlot> SLOT_MAP = Map.of(
            RING_SLOT, EquipmentSlot.RING,
            NECKLACE_SLOT, EquipmentSlot.NECKLACE,
            BRACELET_SLOT, EquipmentSlot.BRACELET,
            PENDANT_SLOT, EquipmentSlot.PENDANT
    );

    public EquipmentMenu(Player viewer) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("menu.equipment")), 45);
    }

    @Override
    protected void build() {
        fillAll();

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

    private ItemStack createFilledSlot(ItemStack equipped, EquipmentSlot equipSlot) {
        List<String> existingLore = equipped.hasItemMeta() && equipped.getItemMeta().hasLore()
                ? equipped.getItemMeta().getLore()
                : new ArrayList<>();
        existingLore.add("");
        existingLore.add(ColorFormat.format(Alkatraz.getLangManager().get("equipment.click_unequip")));

        ItemStack display = ItemBuilder.of(equipped.clone())
                .rawLore(existingLore)
                .build();

        setMenuData(display, "equip_slot", equipSlot.getKey().getKey());
        setMenuData(display, "filled", true);
        return display;
    }

    private ItemStack createEmptySlot(EquipmentSlot equipSlot) {
        String slotName = lang().get("equipment.slot_" + equipSlot.getKey().getKey());
        ItemStack item = ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                .name(lang().get("equipment.empty_slot", "slot", slotName))
                .lore(lang().get("equipment.empty_slot_lore"))
                .build();
        setMenuData(item, "equip_slot", equipSlot.getKey().getKey());
        setMenuData(item, "filled", false);
        return item;
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
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
                viewer.sendMessage(ColorFormat.format(lang().get("equipment.equip_wrong_slot")));
                pendingEquip.remove(viewer.getUniqueId());
                return true;
            }
            EquipmentSlot itemSlot = getSlotForItem(clicked);
            if (itemSlot == null || !itemSlot.equals(pending)) {
                viewer.sendMessage(ColorFormat.format(lang().get("equipment.equip_wrong_slot")));
                pendingEquip.remove(viewer.getUniqueId());
                return true;
            }
            handleEquipFromInventory(pending, clicked);
            pendingEquip.remove(viewer.getUniqueId());
            return true;
        }

        // Auto-equip: if the clicked item is a valid equipment item, find the correct slot
        if (isEquipmentItem(clicked)) {
            EquipmentSlot targetSlot = getSlotForItem(clicked);
            if (targetSlot != null && SLOT_MAP.containsValue(targetSlot)) {
                Optional<ItemStack> existing = EquipmentStorage.getItem(viewer, targetSlot);
                if (existing.isEmpty() || existing.get().getType() == Material.AIR) {
                    handleEquipFromInventory(targetSlot, clicked);
                    return true;
                }
                viewer.sendMessage(lang().get("equipment.slot_occupied"));
                return true;
            }
            viewer.sendMessage(lang().get("equipment.cannot_equip"));
            return true;
        }

        return false;
    }

    private void handlePendingEquip(EquipmentSlot slot) {
        pendingEquip.put(viewer.getUniqueId(), slot);
        String slotName = lang().get("equipment.slot_" + slot.getKey().getKey());
        viewer.sendMessage(ColorFormat.format(lang().get("equipment.equip_instructions", "slot", slotName)));
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleEquipFromInventory(EquipmentSlot slot, ItemStack item) {
        if (item.getType() == Material.AIR) {
            pendingEquip.remove(viewer.getUniqueId());
            viewer.sendMessage(lang().get("equipment.cancelled"));
            return;
        }

        // Fire on_equip trigger event before setting item
        fireOnEquipEvent(viewer, slot, item);

        EquipmentStorage.setItem(viewer, slot, item);
        item.setAmount(item.getAmount() - 1);

        String slotName = lang().get("equipment.slot_" + slot.getKey().getKey());
        viewer.sendMessage(lang().get("equipment.equipped", "slot", slotName));
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
            viewer.sendMessage(lang().get("equipment.inventory_full_dropped"));
        }

        EquipmentStorage.removeItem(viewer, slot);

        String slotName = lang().get("equipment.slot_" + slot.getKey().getKey());
        viewer.sendMessage(lang().get("equipment.unequipped", "slot", slotName));
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

    // Cleanup handled by MenuListener.onInventoryClose + clearPending()

    private static boolean isEquipmentItem(ItemStack item) {
        return MagicItemStack.readDefinition(item)
                .map(def -> def.hasComponent(MagicKeys.alkatraz("equipment")))
                .orElse(false);
    }

    private static EquipmentSlot getSlotForItem(ItemStack item) {
        return MagicItemStack.readDefinition(item)
                .map(def -> {
                    String key = def.getKey().getKey();
                    if (key.endsWith("_ring")) return EquipmentSlot.RING;
                    if (key.endsWith("_necklace")) return EquipmentSlot.NECKLACE;
                    if (key.endsWith("_bracelet")) return EquipmentSlot.BRACELET;
                    if (key.endsWith("_pendant")) return EquipmentSlot.PENDANT;
                    return null;
                })
                .orElse(null);
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
