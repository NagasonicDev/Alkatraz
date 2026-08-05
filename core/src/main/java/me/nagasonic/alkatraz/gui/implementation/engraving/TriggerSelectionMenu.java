package me.nagasonic.alkatraz.gui.implementation.engraving;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerType;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.items.magic.util.ItemTypeMapper;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TriggerSelectionMenu extends PagedMenu<TriggerType> {

    private static final int[] CONTENT_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public TriggerSelectionMenu(Player viewer) {
        super(viewer,
                ColorFormat.format(lang().get("menu.trigger_select")),
                54,
                getFilteredTriggers(viewer),
                14);
        this.contentSlots = CONTENT_SLOTS;
        this.nextPageSlot = 53;
        this.previousPageSlot = 45;
        this.backButtonSlot = 49;
    }

    private static List<TriggerType> getFilteredTriggers(Player viewer) {
        EngravingSession session = EngravingSession.get(viewer.getUniqueId());
        if (session == null) return List.of();

        Set<String> itemTypes = ItemTypeMapper.getTypes(session.targetStack().getType());

        List<TriggerType> byItemType = MagicItemRegistries.TRIGGER_TYPES.values().stream()
                .filter(t -> t.allowedItemTypes().isEmpty()
                        || t.allowedItemTypes().stream().anyMatch(itemTypes::contains))
                .collect(Collectors.toList());

        // If the selected engraving declares the triggers it supports, offer only
        // those that also pass the item-type filter above. Absent field -> fall back.
        NamespacedKey engravingKey = session.selectedEngravingKey();
        if (engravingKey != null) {
            Object rawTriggers = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(engravingKey)
                    .map(def -> def.staticConfig().get("triggers"))
                    .orElse(null);
            if (rawTriggers instanceof List<?> declared) {
                Set<NamespacedKey> declaredKeys = new HashSet<>();
                for (Object entry : declared) {
                    MagicKeys.parse(String.valueOf(entry)).ifPresent(declaredKeys::add);
                }
                return byItemType.stream()
                        .filter(t -> declaredKeys.contains(t.getKey()))
                        .collect(Collectors.toList());
            }
        }

        return byItemType;
    }

    @Override
    protected void addDecorations() {
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, createBorderPane());
            inventory.setItem(i + 45, createBorderPane());
        }
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, createBorderPane());
        }
        for (int i = 36; i < 45; i++) {
            inventory.setItem(i, createBorderPane());
        }
    }

    private ItemStack createBorderPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&7"));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    protected ItemStack createDisplayItem(TriggerType trigger, int index) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(lang().get("engraving.trigger_prefix", "trigger", trigger.getKey().getKey()));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + trigger.description()));
        lore.add("");
        lore.add(ColorFormat.format("&eClick to select this trigger"));
        meta.setLore(lore);
        item.setItemMeta(meta);

        setMenuData(item, "trigger_key", MagicKeys.format(trigger.getKey()));
        return item;
    }

    @Override
    protected void handleContentClick(TriggerType trigger, InventoryClickEvent event) {
        EngravingSession session = EngravingSession.get(viewer.getUniqueId());
        if (session == null) return;

        session.setSelectedTriggerKey(trigger.getKey());
        EngravingTableMenu.applyEngraving(viewer, session);
    }

    @Override
    protected void addBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format(lang().get("common.back")));
            back.setItemMeta(meta);
        }
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected void handleBackClick() {
        EngravingSession session = EngravingSession.get(viewer.getUniqueId());
        if (session == null) {
            close();
            return;
        }
        new EngravingTableMenu(viewer, session.targetStack(), session.targetInstance(), session.targetDefinition()).open();
    }
}