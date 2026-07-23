package me.nagasonic.alkatraz.gui.implementation.editor;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class RequirementsSubMenu extends Menu {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    private static final int[] REQ_SLOTS = {10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int ADD_SLOT = 38;
    private static final int BACK_SLOT = 42;

    private final ItemDetailMenu parent;
    private final EditorSession session;
    private final List<Map<String, Object>> requirements;
    private int offset;

    public RequirementsSubMenu(Player viewer, ItemDetailMenu parent) {
        super(viewer, ColorFormat.format("&8Edit Requirements"), 45);
        this.parent = parent;
        this.session = EditorSession.get(viewer.getUniqueId());
        this.requirements = loadRequirements();
        this.offset = 0;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadRequirements() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<?> raw = session.config().getList("requirements");
        if (raw != null) {
            for (Object obj : raw) {
                if (obj instanceof Map) {
                    result.add((Map<String, Object>) obj);
                }
            }
        }
        return result;
    }

    @Override
    protected void build() {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, createBackgroundPane());
        }

        int end = Math.min(offset + REQ_SLOTS.length, requirements.size());
        for (int i = offset; i < end; i++) {
            int slotIndex = i - offset;
            if (slotIndex < REQ_SLOTS.length) {
                inventory.setItem(REQ_SLOTS[slotIndex], createReqItem(i));
            }
        }

        if (offset > 0) {
            inventory.setItem(36, createNavItem(Material.ARROW, "&ePrevious", "prev"));
        }
        if (end < requirements.size()) {
            inventory.setItem(44, createNavItem(Material.ARROW, "&eNext", "next"));
        }

        inventory.setItem(ADD_SLOT, createAddButton());
        inventory.setItem(BACK_SLOT, createBackButton());
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

    private ItemStack createReqItem(int index) {
        Map<String, Object> req = requirements.get(index);
        String type = (String) req.getOrDefault("type", "unknown");

        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang().get("editor.requirement_display", "num", String.valueOf(index + 1), "type", type));
            List<String> lore = new ArrayList<>();
        for (Map.Entry<String, Object> e : req.entrySet()) {
                lore.add(ColorFormat.format("&7" + e.getKey() + ": &f" + e.getValue()));
            }
            lore.add("");
            lore.add(ColorFormat.format("&eLeft-click to edit"));
            lore.add(ColorFormat.format("&cRight-click to delete"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        setMenuData(item, "req_index", index);
        return item;
    }

    private ItemStack createAddButton() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang().get("editor.add_requirement"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7Click to add a new requirement"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", "add");
        return item;
    }

    private ItemStack createNavItem(Material mat, String name, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format(name));
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", action);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang().get("common.back"));
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", "back");
        return item;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            saveAndReturn();
            return true;
        }

        if (slot == ADD_SLOT) {
            handleAdd();
            return true;
        }

        if (slot == 36 && offset > 0) {
            offset = Math.max(0, offset - REQ_SLOTS.length);
            refresh();
            return true;
        }
        if (slot == 44 && offset + REQ_SLOTS.length < requirements.size()) {
            offset += REQ_SLOTS.length;
            refresh();
            return true;
        }

        for (int i = 0; i < REQ_SLOTS.length; i++) {
            if (slot == REQ_SLOTS[i]) {
                int idx = offset + i;
                if (idx < requirements.size()) {
                    if (event.isRightClick()) {
                        handleDelete(idx);
                    } else {
                        handleEdit(idx);
                    }
                }
                return true;
            }
        }

        return true;
    }

    private void handleEdit(int index) {
        Map<String, Object> req = requirements.get(index);
        viewer.closeInventory();
        parent.markNeedsSave();
        session.setPendingChatAction("edit_req:" + index);
        StringBuilder current = new StringBuilder(req.getOrDefault("type", "unknown") + ":");
        for (Map.Entry<String, Object> e : req.entrySet()) {
            if (!e.getKey().equals("type")) {
                if (current.length() > 0) current.append(",");
                current.append(e.getKey()).append("=").append(e.getValue());
            }
        }
        EditorChatHandler.prompt(viewer,
                "Edit requirement. Current: " + current + "\n" +
                        "Enter new value in format: type:field1=val1,field2=val2",
                parent, () -> new RequirementsSubMenu(viewer, parent).open());
    }

    private void handleAdd() {
        viewer.closeInventory();
        parent.markNeedsSave();
        session.setPendingChatAction("add_requirement");
        EditorChatHandler.prompt(viewer,
                "Enter requirement in format: type:field1=val1,field2=val2\n" +
                        "e.g. permission:permission=alkatraz.craft.archmage\n" +
                        "e.g. number_stat:stat=circleLevel,minimum=5",
                parent, () -> new RequirementsSubMenu(viewer, parent).open());
    }

    private void handleDelete(int index) {
        requirements.remove(index);
        saveRequirements();
        parent.markNeedsSave();
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        refresh();
    }

    private void saveRequirements() {
        List<Map<String, Object>> cleanList = new ArrayList<>();
        for (Map<String, Object> req : requirements) {
            Map<String, Object> clean = new LinkedHashMap<>(req);
            cleanList.add(clean);
        }
        session.config().set("requirements", cleanList);
    }

    private void saveAndReturn() {
        saveRequirements();
        parent.markNeedsSave();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        new ItemDetailMenu(viewer, parent.definition, parent.defKey).open();
    }
}
