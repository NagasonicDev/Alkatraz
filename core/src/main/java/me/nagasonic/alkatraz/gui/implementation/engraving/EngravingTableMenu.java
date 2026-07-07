package me.nagasonic.alkatraz.gui.implementation.engraving;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.WandTableSelectionMenu;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EngravingTableMenu extends Menu {

    private static final int[] ENGRAVING_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int TARGET_SLOT = 31;
    private static final int BACK_SLOT = 33;

    private ItemStack targetStack;
    private MagicItemInstance targetInstance;
    private ItemDefinition targetDefinition;
    private int maxEngravings;
    private boolean selecting;

    public EngravingTableMenu(Player viewer) {
        super(viewer, ColorFormat.format("&8Engraving Table"), 45);
        this.selecting = true;
        this.maxEngravings = 1;
    }

    public EngravingTableMenu(Player viewer, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        super(viewer, ColorFormat.format("&8Engraving Table"), 45);
        this.targetStack = stack;
        this.targetInstance = instance;
        this.targetDefinition = definition;
        Object raw = definition.staticConfig().get("max_engravings");
        this.maxEngravings = raw != null ? Integer.parseInt(raw.toString()) : 1;
        this.selecting = false;
        EngravingSession.set(viewer.getUniqueId(), new EngravingSession(stack, instance, definition));
    }

    @Override
    protected void build() {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, createBackgroundPane());
        }

        if (selecting) {
            buildSelectMode();
            return;
        }

        inventory.setItem(TARGET_SLOT, createItemDisplay());

        List<Engraving> engravings = targetInstance.engravings();
        for (int i = 0; i < ENGRAVING_SLOTS.length; i++) {
            if (i < maxEngravings) {
                if (i < engravings.size()) {
                    inventory.setItem(ENGRAVING_SLOTS[i], createEngravingDisplay(engravings.get(i), i));
                } else {
                    inventory.setItem(ENGRAVING_SLOTS[i], createEmptyEngravingSlot());
                }
            } else {
                inventory.setItem(ENGRAVING_SLOTS[i], createLockedSlotIcon());
            }
        }

        inventory.setItem(BACK_SLOT, createBackButton());
    }

    private void buildSelectMode() {
        inventory.setItem(TARGET_SLOT, createSelectPrompt());
    }

    private ItemStack createSelectPrompt() {
        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&dSelect an Item"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7Click any magic item in your"));
            lore.add(ColorFormat.format("&7inventory below to engrave it."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
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

    private ItemStack createItemDisplay() {
        ItemStack display = targetStack.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();
            lore.add("");
            lore.add(ColorFormat.format("&7Engravings: &f" + targetInstance.engravings().size() + "&7/&f" + maxEngravings));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack createEngravingDisplay(Engraving engraving, int index) {
        String engName = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(engraving.engravingKey())
                .map(def -> prettifyKey(def.getKey().getKey())).orElse("Unknown");
        String trigName = MagicItemRegistries.TRIGGER_TYPES.get(engraving.triggerKey())
                .map(t -> prettifyKey(t.getKey().getKey())).orElse("Unknown");

        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ColorFormat.format("&6" + engName));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Trigger: &f" + trigName));
        lore.add("");
        lore.add(ColorFormat.format("&eClick to unequip this engraving"));
        meta.setLore(lore);
        item.setItemMeta(meta);

        setMenuData(item, "engraving_index", index);
        setMenuData(item, "action", "remove");
        return item;
    }

    private ItemStack createEmptyEngravingSlot() {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&7Empty Slot"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7Click a rune in your inventory"));
            lore.add(ColorFormat.format("&7to equip it here."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createLockedSlotIcon() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&8Locked Slot"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7Upgrade this item to unlock"));
            lore.add(ColorFormat.format("&7additional engraving slots."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&cBack"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7Return to Arcane Table"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", "back");
        return item;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (selecting) {
            return handleSelectModeClick(event, clicked);
        }

        int rawSlot = event.getRawSlot();

        if (rawSlot >= 0 && rawSlot < size) {
            if (rawSlot == TARGET_SLOT) {
                return true;
            }

            for (int i = 0; i < Math.min(ENGRAVING_SLOTS.length, maxEngravings); i++) {
                if (rawSlot == ENGRAVING_SLOTS[i]) {
                    handleEngravingSlotClick(i);
                    return true;
                }
            }

            if (rawSlot == BACK_SLOT) {
                handleBack();
                return true;
            }

            return true;
        }

        if (clicked == null || clicked.getType() == Material.AIR) return true;

        if (MagicItemStack.isEngravingItem(clicked)) {
            handleRuneClick(event, clicked);
            return true;
        }

        return true;
    }

    private boolean handleSelectModeClick(InventoryClickEvent event, ItemStack clicked) {
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < size) {
            return true;
        }

        if (clicked == null || clicked.getType() == Material.AIR || !MagicItemStack.isMagicItem(clicked)) {
            return true;
        }

        Optional<ItemDefinition> def = MagicItemStack.readDefinition(clicked);
        Optional<MagicItemInstance> inst = MagicItemStack.readInstance(clicked);
        if (def.isEmpty() || inst.isEmpty()) {
            viewer.sendMessage(ColorFormat.format("&cThis is not a valid magic item."));
            return true;
        }

        this.targetStack = clicked;
        this.targetInstance = inst.get();
        this.targetDefinition = def.get();
        Object raw = def.get().staticConfig().get("max_engravings");
        this.maxEngravings = raw != null ? Integer.parseInt(raw.toString()) : 1;
        this.selecting = false;
        EngravingSession.set(viewer.getUniqueId(), new EngravingSession(clicked, inst.get(), def.get()));

        viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        refresh();
        return true;
    }

    private void handleRuneClick(InventoryClickEvent event, ItemStack runeStack) {
        if (targetInstance.engravings().size() >= maxEngravings) {
            viewer.sendMessage(ColorFormat.format("&cEngraving slots are full! Unequip one first."));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Optional<NamespacedKey> engravingKey = MagicItemStack.readEngravingKey(runeStack);
        if (engravingKey.isEmpty()) {
            viewer.sendMessage(ColorFormat.format("&cInvalid engraving item."));
            return;
        }

        if (MagicItemRegistries.ENGRAVING_DEFINITIONS.get(engravingKey.get()).isEmpty()) {
            viewer.sendMessage(ColorFormat.format("&cUnknown engraving type."));
            return;
        }

        EngravingSession session = EngravingSession.get(viewer.getUniqueId());
        if (session == null) return;

        session.setSelectedEngravingKey(engravingKey.get());
        session.setEngravingItemStack(runeStack);

        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        new TriggerSelectionMenu(viewer).open();
    }

    private void handleEngravingSlotClick(int index) {
        List<Engraving> engravings = targetInstance.engravings();
        if (index < 0 || index >= engravings.size()) return;

        Engraving removed = engravings.get(index);

        Optional<EngravingDefinition> def = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(removed.engravingKey());
        if (def.isPresent()) {
            ItemStack refundItem = MagicItemStack.createEngravingItem(def.get());
            viewer.getInventory().addItem(refundItem)
                    .values().forEach(leftover ->
                            viewer.getWorld().dropItemNaturally(viewer.getLocation(), leftover));
        }

        List<Engraving> updated = new ArrayList<>(engravings);
        updated.remove(index);
        targetInstance.setEngravings(updated);
        MagicItemStack.writeInstance(targetStack, targetInstance);

        viewer.sendMessage(ColorFormat.format("&cUnequipped: &f" + removed.engravingKey().getKey()));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        refresh();
    }

    private void handleBack() {
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        close();
        new WandTableSelectionMenu(viewer).open();
    }

    private static String prettifyKey(String key) {
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }
}
