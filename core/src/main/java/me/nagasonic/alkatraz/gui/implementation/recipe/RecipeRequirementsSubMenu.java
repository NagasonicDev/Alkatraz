package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecipeRequirementsSubMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int ADD_SLOT = 38;
    private static final int BACK_SLOT = 40;

    private final RecipeEditMenu parent;

    public RecipeRequirementsSubMenu(Player viewer, RecipeEditMenu parent) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("recipes.edit.requirements")), 45);
        this.parent = parent;
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    protected void build() {
        fillAll();
        List<Map<?, ?>> requirements = parent.session().config().getMapList("requirements");
        for (int i = 0; i < SLOTS.length; i++) {
            if (i < requirements.size()) {
                Map<?, ?> req = requirements.get(i);
                Object rawType = req.get("type");
                String type = rawType != null ? String.valueOf(rawType) : "?";
                inventory.setItem(SLOTS[i], ItemBuilder.of(Material.BOOK)
                        .name(lang().get("editor.requirement_display", "num", String.valueOf(i + 1), "type", type))
                        .lore(lang().get("recipes.edit.click_edit"))
                        .lore(lang().get("recipes.edit.delete_requirement"))
                        .build());
            }
        }
        ItemStack add = ItemBuilder.of(Material.LIME_DYE)
                .name(lang().get("recipes.edit.add_requirement"))
                .build();
        setMenuData(add, "action", "add");
        inventory.setItem(ADD_SLOT, add);

        ItemStack back = ItemBuilder.of(Material.BARRIER)
                .name(lang().get("recipes.edit.back"))
                .build();
        setMenuData(back, "action", "back");
        inventory.setItem(BACK_SLOT, back);
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");
        if ("back".equals(action)) {
            parent.open();
            return true;
        }
        if ("add".equals(action)) {
            promptRequirement(-1);
            return true;
        }
        int slot = event.getRawSlot();
        int index = indexOf(slot);
        if (index < 0) return true;
        List<Map<?, ?>> requirements = parent.session().config().getMapList("requirements");
        if (event.isRightClick()) {
            if (index < requirements.size()) {
                requirements.remove(index);
                parent.session().config().set("requirements", requirements);
                refresh();
            }
        } else if (index < requirements.size()) {
            promptRequirement(index);
        }
        return true;
    }

    private void promptRequirement(int index) {
        RecipeChatHandler.prompt(viewer, lang().get("recipes.edit.requirement_prompt"), (p, input) -> {
            if (input.equalsIgnoreCase("cancel")) {
                p.sendMessage(lang().get("recipes.edit.chat_cancelled"));
                return;
            }
            Map<String, Object> req = parseRequirement(input);
            if (req == null) {
                p.sendMessage(ColorFormat.format("&cInvalid requirement format."));
                return;
            }
            List<Map<String, Object>> requirements = new ArrayList<>();
            for (Map<?, ?> m : parent.session().config().getMapList("requirements")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) m);
                requirements.add(copy);
            }
            if (index < 0) {
                requirements.add(req);
            } else if (index < requirements.size()) {
                requirements.set(index, req);
            } else {
                requirements.add(req);
            }
            parent.session().config().set("requirements", requirements);
            refresh();
        });
    }

    static Map<String, Object> parseRequirement(String value) {
        String[] parts = value.split(":", 2);
        if (parts.length < 2) return null;
        String type = parts[0].trim();
        String[] fields = parts[1].split(",");
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", type);
        for (String field : fields) {
            String[] kv = field.split("=", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim();
            String v = kv[1].trim();
            try {
                req.put(k, Integer.parseInt(v));
            } catch (NumberFormatException e1) {
                try {
                    req.put(k, Double.parseDouble(v));
                } catch (NumberFormatException e2) {
                    req.put(k, v);
                }
            }
        }
        return req;
    }

    private int indexOf(int slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) return i;
        }
        return -1;
    }

    @Override
    public void onClose() {
        parent.open();
    }
}
