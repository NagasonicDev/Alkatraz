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
import java.util.List;

public class RecipePermissionsSubMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int ADD_SLOT = 38;
    private static final int BACK_SLOT = 40;

    private final RecipeEditMenu parent;

    public RecipePermissionsSubMenu(Player viewer, RecipeEditMenu parent) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("recipes.edit.permissions")), 45);
        this.parent = parent;
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    protected void build() {
        fillAll();
        List<String> permissions = parent.session().config().getStringList("permissions");
        for (int i = 0; i < SLOTS.length; i++) {
            if (i < permissions.size()) {
                inventory.setItem(SLOTS[i], ItemBuilder.of(Material.PAPER)
                        .name("&f" + permissions.get(i))
                        .lore(lang().get("recipes.edit.click_edit"))
                        .lore(lang().get("recipes.edit.delete_requirement"))
                        .build());
            }
        }
        ItemStack add = ItemBuilder.of(Material.LIME_DYE)
                .name(lang().get("recipes.edit.add_permission"))
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
            RecipeChatHandler.prompt(viewer, lang().get("recipes.edit.permission_prompt"), (p, value) -> {
                if (value.equalsIgnoreCase("cancel")) {
                    p.sendMessage(lang().get("recipes.edit.chat_cancelled"));
                    return;
                }
                List<String> permissions = new ArrayList<>(parent.session().config().getStringList("permissions"));
                permissions.add(value.trim());
                parent.session().config().set("permissions", permissions);
                refresh();
            });
            return true;
        }
        int index = indexOf(event.getRawSlot());
        if (index < 0) return true;
        if (event.isRightClick()) {
            List<String> permissions = new ArrayList<>(parent.session().config().getStringList("permissions"));
            if (index < permissions.size()) {
                permissions.remove(index);
                parent.session().config().set("permissions", permissions);
                refresh();
            }
        }
        return true;
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
