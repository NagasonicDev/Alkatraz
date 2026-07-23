package me.nagasonic.alkatraz.gui.implementation.engraving;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.WandTableSelectionMenu;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

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

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public EngravingTableMenu(Player viewer) {
        super(viewer, ColorFormat.format(lang().get("menu.engraving_table")), 45);
        this.selecting = true;
        this.maxEngravings = 1;
    }

    public EngravingTableMenu(Player viewer, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        super(viewer, ColorFormat.format(lang().get("menu.engraving_table")), 45);
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
        fillAll();

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
        List<String> lore = new ArrayList<>();
        for (String line : lang().get("engraving.select_item_lore").split("\\n")) {
            lore.add(ColorFormat.format(line));
        }
        return ItemBuilder.of(Material.ENCHANTING_TABLE)
                .name(lang().get("engraving.select_item"))
                .rawLore(lore)
                .build();
    }

    private ItemStack createItemDisplay() {
        ItemStack display = targetStack.clone();
        List<String> lore = display.hasItemMeta() && display.getItemMeta().hasLore()
                ? display.getItemMeta().getLore()
                : new ArrayList<>();
        lore.add("");
        lore.add(ColorFormat.format(lang().get("engraving.engravings_header",
                "current", targetInstance.engravings().size(), "max", maxEngravings)));
        return ItemBuilder.of(display).rawLore(lore).build();
    }

    private ItemStack createEngravingDisplay(Engraving engraving, int index) {
        String engName = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(engraving.engravingKey())
                .map(def -> StringUtils.prettifyKey(def.getKey().getKey())).orElse("Unknown");
        String trigName = MagicItemRegistries.TRIGGER_TYPES.get(engraving.triggerKey())
                .map(t -> StringUtils.prettifyKey(t.getKey().getKey())).orElse("Unknown");

        ItemStack item = ItemBuilder.of(Material.ENCHANTED_BOOK)
                .name("&6" + engName)
                .lore("&7Trigger: &f" + trigName,
                      "",
                      lang().get("engraving.unequip_click"))
                .build();
        setMenuData(item, "engraving_index", index);
        setMenuData(item, "action", "remove");
        return item;
    }

    private ItemStack createEmptyEngravingSlot() {
        return ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name(lang().get("engraving.empty_slot"))
                .lore(lang().get("engraving.empty_slot_lore"))
                .build();
    }

    private ItemStack createLockedSlotIcon() {
        return ItemBuilder.of(Material.RED_STAINED_GLASS_PANE)
                .name(lang().get("engraving.locked_slot"))
                .lore(lang().get("engraving.locked_slot_lore"))
                .build();
    }

    private ItemStack createBackButton() {
        ItemStack item = ItemBuilder.of(Material.BARRIER)
                .name(lang().get("common.back"))
                .lore("&7Return to Arcane Table")
                .build();
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
            viewer.sendMessage(ColorFormat.format(lang().get("engraving.invalid_item")));
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
}
