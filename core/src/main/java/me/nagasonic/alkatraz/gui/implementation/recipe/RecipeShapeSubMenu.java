package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecipeShapeSubMenu extends Menu {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int BACK_SLOT = 40;

    private final RecipeEditMenu parent;

    public RecipeShapeSubMenu(Player viewer, RecipeEditMenu parent) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("recipes.edit.shape")), 45);
        this.parent = parent;
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public Set<Integer> dropZoneSlots() {
        Set<Integer> slots = new HashSet<>();
        for (int s : GRID) slots.add(s);
        return slots;
    }

    @Override
    protected void build() {
        fillAll();
        String[] shape = currentShape();
        for (int i = 0; i < GRID.length; i++) {
            int row = i / 3;
            int col = i % 3;
            char c = shape != null && row < shape.length && col < shape[row].length()
                    ? shape[row].charAt(col) : ' ';
            inventory.setItem(GRID[i], cellItem(c));
        }
        ItemStack back = ItemBuilder.of(Material.BARRIER)
                .name(lang().get("recipes.edit.back"))
                .build();
        setMenuData(back, "action", "back");
        inventory.setItem(BACK_SLOT, back);
    }

    private String[] currentShape() {
        List<String> shape = parent.session().config().getStringList("shape");
        return shape.toArray(new String[0]);
    }

    private ItemStack cellItem(char c) {
        if (c == ' ') {
            return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(lang().get("recipes.edit.drop_hint"))
                    .build();
        }
        String value = parent.session().config().getString("ingredients." + c);
        Material material = me.nagasonic.alkatraz.util.MaterialCompat.resolve(value);
        ItemStack display = material != null ? new ItemStack(material)
                : RecipeEditorSession.deserializeItem(value);
        if (display == null) display = new ItemStack(Material.STONE);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&cRight-click to clear"));
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
        int index = slotInGrid(slot);
        if (index < 0) return true;
        int row = index / 3;
        int col = index % 3;
        if (event.isRightClick()) {
            clearCell(row, col);
            return true;
        }
        return true;
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        ItemStack dragged = event.getOldCursor();
        if (dragged == null || dragged.getType() == Material.AIR) return;
        for (int slot : event.getRawSlots()) {
            int index = slotInGrid(slot);
            if (index < 0) continue;
            int row = index / 3;
            int col = index % 3;
            setCell(row, col, dragged);
            inventory.setItem(slot, cellItem(charAt(row, col)));
        }
        viewer.setItemOnCursor(null);
    }

    private void setCell(int row, int col, ItemStack stack) {
        String value = RecipeEditorSession.serializeItem(stack);
        if (value == null) {
            clearCell(row, col);
            return;
        }
        String[] shape = currentShape();
        if (shape.length == 0) shape = new String[3];
        for (int i = 0; i < 3; i++) {
            if (shape[i] == null) shape[i] = "   ";
            while (shape[i].length() < 3) shape[i] = shape[i] + " ";
        }
        char letter = letterForValue(shape, value);
        if (letter == 0) return;
        char[] rowChars = shape[row].toCharArray();
        rowChars[col] = letter;
        shape[row] = new String(rowChars);
        List<String> shapeList = new ArrayList<>();
        for (String s : shape) shapeList.add(s);
        parent.session().config().set("shape", shapeList);
        parent.session().config().set("ingredients." + letter, value);
        refresh();
    }

    private void clearCell(int row, int col) {
        String[] shape = currentShape();
        if (row >= shape.length || col >= shape[row].length()) return;
        char c = shape[row].charAt(col);
        if (c != ' ') {
            char[] rowChars = shape[row].toCharArray();
            rowChars[col] = ' ';
            shape[row] = new String(rowChars);
            List<String> shapeList = new ArrayList<>();
            for (String s : shape) shapeList.add(s);
            parent.session().config().set("shape", shapeList);
        }
        refresh();
    }

    private char charAt(int row, int col) {
        String[] shape = currentShape();
        if (row < shape.length && col < shape[row].length()) {
            return shape[row].charAt(col);
        }
        return ' ';
    }

    private char letterForValue(String[] shape, String value) {
        String joined = String.join("", shape);
        var section = parent.session().config().getConfigurationSection("ingredients");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (key.length() == 1 && value.equals(parent.session().config().getString("ingredients." + key))) {
                    return key.charAt(0);
                }
            }
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            if (joined.indexOf(c) < 0) return c;
        }
        return 0;
    }

    private int slotInGrid(int slot) {
        for (int i = 0; i < GRID.length; i++) {
            if (GRID[i] == slot) return i;
        }
        return -1;
    }

    @Override
    public void onClose() {
        parent.open();
    }
}
