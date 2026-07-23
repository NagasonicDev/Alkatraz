package me.nagasonic.alkatraz.gui.implementation.editor;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ItemEditorMenu extends PagedMenu<Object> {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private boolean showingRunes = false;

    public ItemEditorMenu(Player viewer) {
        super(viewer, lang().get("menu.item_editor"), 54, getSortedItems(), 28);
        this.contentSlots = CONTENT_SLOTS;
        this.nextPageSlot = 53;
        this.previousPageSlot = 45;
        this.backButtonSlot = 49;
    }

    private static List<Object> getSortedItems() {
        List<ItemDefinition> items = new ArrayList<>(MagicItemRegistries.ITEM_DEFINITIONS.values());
        items.sort(Comparator.comparing(a -> a.getKey().getKey()));
        return new ArrayList<>(items);
    }

    private static List<Object> getSortedRunes() {
        List<EngravingDefinition> runes = new ArrayList<>(MagicItemRegistries.ENGRAVING_DEFINITIONS.values());
        runes.sort(Comparator.comparing(a -> a.getKey().getKey()));
        return new ArrayList<>(runes);
    }

    @Override
    protected void addDecorations() {
        fillAll();
        inventory.setItem(4, createHeaderItem());
    }

    private ItemStack createHeaderItem() {
        if (showingRunes) {
            ItemStack item = ItemBuilder.of(Material.COMMAND_BLOCK)
                    .name(lang().get("editor.rune_editor_title"))
                    .lore(lang().get("editor.switch_to_items"))
                    .build();
            setMenuData(item, "action", "toggle_mode");
            return item;
        }
        ItemStack item = ItemBuilder.of(Material.COMMAND_BLOCK)
                .name(lang().get("editor.item_editor_title"))
                .lore("&7Click an item to edit its config and recipe.",
                      lang().get("editor.switch_to_runes"))
                .build();
        setMenuData(item, "action", "toggle_mode");
        return item;
    }

    @Override
    protected ItemStack createDisplayItem(Object item, int index) {
        if (item instanceof EngravingDefinition rune) {
            ItemStack display = MagicItemStack.createEngravingItem(rune);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add(ColorFormat.format(""));
                lore.add(ColorFormat.format("&8" + MagicKeys.format(rune.getKey())));
                if (showingRunes) {
                    lore.add(ColorFormat.format("&7Click to view rune details"));
                }
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            return display;
        }

        if (item instanceof ItemDefinition def) {
            MagicItemInstance instance = MagicItemInstance.createDefault(def.getKey());
            ItemStack display = MagicItemStack.create(def, instance);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add(ColorFormat.format(""));
                lore.add(ColorFormat.format("&8" + MagicKeys.format(def.getKey())));
                lore.add(lang().get("editor.click_edit_item"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            return display;
        }

        return new ItemStack(Material.BARRIER);
    }

    @Override
    protected void handleContentClick(Object item, InventoryClickEvent event) {
        if (item instanceof EngravingDefinition) {
            viewer.sendMessage(lang().get("editor.not_supported"));
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            return;
        }

        if (item instanceof ItemDefinition def) {
            String key = def.getKey().getKey();
            new EditorSession(viewer, key);
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            new ItemDetailMenu(viewer, def, key).open();
        }
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");

        if ("toggle_mode".equals(action)) {
            showingRunes = !showingRunes;
            if (showingRunes) {
                this.allItems = getSortedRunes();
            } else {
                this.allItems = getSortedItems();
            }
            this.totalPages = (int) Math.ceil((double) allItems.size() / itemsPerPage);
            this.currentPage = 1;
            refresh();
            return true;
        }

        return super.handleClick(event, clicked);
    }

    @Override
    protected void addBackButton() {
        ItemStack back = ItemBuilder.of(Material.BARRIER)
                .name("&cClose")
                .build();
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected void handleBackClick() {
        EditorSession.remove(viewer.getUniqueId());
        close();
    }
}