package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecipeShapelessIngredientsSubMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int BACK_SLOT = 40;

    private final RecipeEditMenu parent;

    public RecipeShapelessIngredientsSubMenu(Player viewer, RecipeEditMenu parent) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("recipes.edit.ingredients")), 45);
        this.parent = parent;
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public Set<Integer> dropZoneSlots() {
        Set<Integer> slots = new HashSet<>();
        for (int s : SLOTS) slots.add(s);
        return slots;
    }

    @Override
    protected void build() {
        fillAll();
        List<String> ingredients = parent.session().config().getStringList("ingredients");
        for (int i = 0; i < SLOTS.length; i++) {
            if (i < ingredients.size()) {
                inventory.setItem(SLOTS[i], ingredientItem(ingredients.get(i)));
            } else {
                inventory.setItem(SLOTS[i], ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(lang().get("recipes.edit.drop_hint"))
                        .build());
            }
        }
        ItemStack back = ItemBuilder.of(Material.BARRIER)
                .name(lang().get("recipes.edit.back"))
                .build();
        setMenuData(back, "action", "back");
        inventory.setItem(BACK_SLOT, back);
    }

    private ItemStack ingredientItem(String value) {
        ItemStack display = RecipeEditorSession.deserializeItem(value);
        if (display == null) display = new ItemStack(Material.STONE);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7" + value));
            lore.add(ColorFormat.format("&cRight-click to remove"));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");
        if ("back".equals(action)) {
            parent.open();
            return true;
        }
        int slot = event.getRawSlot();
        int index = indexOf(slot);
        if (index < 0) return true;
        if (event.isRightClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            List<String> ingredients = parent.session().config().getStringList("ingredients");
            if (index < ingredients.size()) {
                ingredients.remove(index);
                parent.session().config().set("ingredients", ingredients);
                refresh();
            }
            return true;
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            String value = RecipeEditorSession.serializeItem(cursor);
            if (value != null) {
                List<String> ingredients = parent.session().config().getStringList("ingredients");
                if (index < ingredients.size()) {
                    ingredients.set(index, value);
                } else {
                    ingredients.add(value);
                }
                parent.session().config().set("ingredients", ingredients);
                placeOne(cursor);
                refresh();
            }
        }
        return true;
    }

    private void placeOne(ItemStack cursor) {
        ItemStack remaining = cursor.clone();
        remaining.setAmount(cursor.getAmount() - 1);
        viewer.setItemOnCursor(remaining.getAmount() > 0 ? remaining : null);
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        ItemStack dragged = event.getOldCursor();
        if (dragged == null || dragged.getType() == Material.AIR) return;
        String value = RecipeEditorSession.serializeItem(dragged);
        if (value == null) return;
        int affected = 0;
        for (int slot : event.getRawSlots()) {
            if (indexOf(slot) >= 0) affected++;
        }
        if (affected == 0) return;
        int maxAffected = Math.min(affected, dragged.getAmount());
        List<String> ingredients = parent.session().config().getStringList("ingredients");
        int consumed = 0;
        for (int slot : event.getRawSlots()) {
            if (consumed >= maxAffected) break;
            int index = indexOf(slot);
            if (index < 0) continue;
            if (index < ingredients.size()) {
                ingredients.set(index, value);
            } else {
                ingredients.add(value);
            }
            consumed++;
        }
        parent.session().config().set("ingredients", ingredients);
        ItemStack cursor = dragged.clone();
        cursor.setAmount(Math.max(0, cursor.getAmount() - consumed));
        viewer.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
        refresh();
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
