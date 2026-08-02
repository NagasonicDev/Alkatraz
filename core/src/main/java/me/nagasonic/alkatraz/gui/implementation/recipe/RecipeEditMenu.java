package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.RecipeDetailMenu;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecipeEditMenu extends Menu {

    private static final int RESULT_DROP_SLOT = 22;
    private static final int RESULT_LABEL_SLOT = 13;

    private static final int SAVE_SLOT = 49;
    private static final int DISCARD_SLOT = 53;
    private static final int BACK_SLOT = 45;

    private static final int FIELD_SLOT_DISPLAY_NAME = 10;
    private static final int FIELD_SLOT_AMOUNT = 11;
    private static final int FIELD_SLOT_OVERRIDE = 12;
    private static final int FIELD_SLOT_HIDDEN = 14;
    private static final int FIELD_SLOT_UNLOCK_MSG = 15;
    private static final int FIELD_SLOT_REQUIREMENTS = 16;
    private static final int FIELD_SLOT_PERMISSIONS = 19;
    private static final int FIELD_SLOT_SPECIAL_A = 20;
    private static final int FIELD_SLOT_SPECIAL_B = 21;
    private static final int FIELD_SLOT_SPECIAL_C = 23;

    private final RecipeEditorSession session;
    private final AlkatrazRecipe recipe;
    private final RecipeType type;
    private final Set<Integer> stationDropSlots = new HashSet<>();
    private boolean suppressCleanup;

    public RecipeEditMenu(Player viewer, AlkatrazRecipe recipe) {
        super(viewer, ColorFormat.format(
                Alkatraz.getLangManager().get("menu.recipe_edit")), 54);
        this.recipe = recipe;
        this.type = recipe.getType();
        this.session = RecipeEditorSession.start(viewer, recipe.getKey());
        computeStationDropSlots();
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private void computeStationDropSlots() {
        switch (type) {
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE, STONECUTTER -> stationDropSlots.add(FIELD_SLOT_SPECIAL_A);
            case BREWING -> {
                stationDropSlots.add(FIELD_SLOT_SPECIAL_A);
                stationDropSlots.add(FIELD_SLOT_SPECIAL_B);
            }
            case SMITHING, ANVIL -> {
                stationDropSlots.add(FIELD_SLOT_SPECIAL_A);
                stationDropSlots.add(FIELD_SLOT_SPECIAL_B);
            }
            default -> {}
        }
    }

    @Override
    public Set<Integer> dropZoneSlots() {
        Set<Integer> slots = new HashSet<>(stationDropSlots);
        slots.add(RESULT_DROP_SLOT);
        return slots;
    }

    @Override
    protected void build() {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, blankPane());
        }

        inventory.setItem(FIELD_SLOT_DISPLAY_NAME, createTextField(
                Material.NAME_TAG, RecipeField.DISPLAY_NAME, currentString(RecipeField.DISPLAY_NAME)));
        inventory.setItem(FIELD_SLOT_AMOUNT, createTextField(
                Material.REPEATER, RecipeField.RESULT_AMOUNT, String.valueOf(config().getInt("result.amount", 1))));
        inventory.setItem(FIELD_SLOT_OVERRIDE, createBoolField(
                Material.ENDER_EYE, RecipeField.OVERRIDE_VANILLA, config().getBoolean("override_vanilla", false)));
        inventory.setItem(FIELD_SLOT_HIDDEN, createBoolField(
                Material.LEATHER, RecipeField.HIDDEN_WHEN_LOCKED, config().getBoolean("hidden_when_locked", false)));
        inventory.setItem(FIELD_SLOT_UNLOCK_MSG, createTextField(
                Material.PAPER, RecipeField.UNLOCK_MESSAGE, currentString(RecipeField.UNLOCK_MESSAGE)));
        inventory.setItem(FIELD_SLOT_REQUIREMENTS, createListField(
                Material.BOOK, RecipeField.REQUIREMENTS, config().getList("requirements") != null
                        ? config().getList("requirements").size() : 0));
        inventory.setItem(FIELD_SLOT_PERMISSIONS, createListField(
                Material.IRON_INGOT, RecipeField.PERMISSIONS, config().getStringList("permissions").size()));

        switch (type) {
            case SHAPED -> inventory.setItem(FIELD_SLOT_SPECIAL_A, createSubField(
                    Material.CRAFTING_TABLE, RecipeField.SHAPE, "recipes.edit.shape_grid_lore"));
            case SHAPELESS -> inventory.setItem(FIELD_SLOT_SPECIAL_A, createSubField(
                    Material.HOPPER, RecipeField.SHAPELESS_INGREDIENTS, "recipes.edit.ingredients_list_lore"));
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> {
                inventory.setItem(FIELD_SLOT_SPECIAL_A, createDropField(
                        Material.FURNACE, RecipeField.INPUT, dropDisplay(FIELD_SLOT_SPECIAL_A, RecipeField.INPUT)));
                inventory.setItem(FIELD_SLOT_SPECIAL_B, createTextField(
                        Material.EXPERIENCE_BOTTLE, RecipeField.EXPERIENCE,
                        String.valueOf(config().getDouble("experience", 0))));
                inventory.setItem(FIELD_SLOT_SPECIAL_C, createTextField(
                        Material.CLOCK, RecipeField.COOKING_TIME,
                        String.valueOf(config().getInt("cooking_time", 0))));
            }
            case BREWING -> {
                inventory.setItem(FIELD_SLOT_SPECIAL_A, createDropField(
                        Material.BREWING_STAND, RecipeField.INPUT, dropDisplay(FIELD_SLOT_SPECIAL_A, RecipeField.INPUT)));
                inventory.setItem(FIELD_SLOT_SPECIAL_B, createDropField(
                        Material.BLAZE_POWDER, RecipeField.ADDITION, dropDisplay(FIELD_SLOT_SPECIAL_B, RecipeField.ADDITION)));
            }
            case SMITHING, ANVIL -> {
                inventory.setItem(FIELD_SLOT_SPECIAL_A, createDropField(
                        Material.SMITHING_TABLE, RecipeField.BASE, dropDisplay(FIELD_SLOT_SPECIAL_A, RecipeField.BASE)));
                inventory.setItem(FIELD_SLOT_SPECIAL_B, createDropField(
                        Material.NETHERITE_INGOT, RecipeField.ADDITION, dropDisplay(FIELD_SLOT_SPECIAL_B, RecipeField.ADDITION)));
            }
            case STONECUTTER -> inventory.setItem(FIELD_SLOT_SPECIAL_A, createDropField(
                    Material.STONECUTTER, RecipeField.INPUT, dropDisplay(FIELD_SLOT_SPECIAL_A, RecipeField.INPUT)));
            default -> {}
        }

        inventory.setItem(RESULT_LABEL_SLOT, ItemBuilder.of(Material.ITEM_FRAME)
                .name(lang().get("recipes.edit.result"))
                .lore(lang().get("recipes.edit.drop_hint"))
                .build());
        ItemStack resultDisplay = dropDisplay(RESULT_DROP_SLOT, RecipeField.RESULT);
        inventory.setItem(RESULT_DROP_SLOT, resultDisplay != null
                ? resultDisplay
                : ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(lang().get("recipes.edit.result"))
                        .lore(lang().get("recipes.edit.drop_hint"))
                        .build());

        ItemStack save = Alkatraz.getGuiItemRegistry().getItem("confirm_button").clone();
        ItemMeta saveMeta = save.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(lang().get("recipes.edit.save"));
            saveMeta.setLore(List.of(ColorFormat.format(lang().get("recipes.edit.save_unsaved"))));
            save.setItemMeta(saveMeta);
        }
        setMenuData(save, "action", "save");
        inventory.setItem(SAVE_SLOT, save);

        ItemStack discard = Alkatraz.getGuiItemRegistry().getItem("cancel_button").clone();
        ItemMeta discardMeta = discard.getItemMeta();
        if (discardMeta != null) {
            discardMeta.setDisplayName(lang().get("recipes.edit.discard"));
            discard.setItemMeta(discardMeta);
        }
        setMenuData(discard, "action", "discard");
        inventory.setItem(DISCARD_SLOT, discard);

        ItemStack back = Alkatraz.getGuiItemRegistry().getItem("back_button").clone();
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(lang().get("recipes.edit.back"));
            back.setItemMeta(backMeta);
        }
        setMenuData(back, "action", "back");
        inventory.setItem(BACK_SLOT, back);
    }

    private ItemStack blankPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&7"));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createTextField(Material material, RecipeField field, String currentValue) {
        return ItemBuilder.of(material)
                .name(lang().get(field.langKey()))
                .lore(lang().get("recipes.edit.current", "value", currentValue))
                .lore(lang().get("recipes.edit.click_edit"))
                .build();
    }

    private ItemStack createBoolField(Material material, RecipeField field, boolean value) {
        Material icon = value ? Material.LIME_DYE : Material.RED_DYE;
        return ItemBuilder.of(icon)
                .name(lang().get(field.langKey()))
                .lore(lang().get("recipes.edit.current", "value", String.valueOf(value)))
                .lore(lang().get("recipes.edit.click_toggle"))
                .glint(value)
                .build();
    }

    private ItemStack createListField(Material material, RecipeField field, int count) {
        return ItemBuilder.of(material)
                .name(lang().get(field.langKey()))
                .lore(lang().get("recipes.edit.current", "value", String.valueOf(count)))
                .lore(lang().get("recipes.edit.click_edit"))
                .build();
    }

    private ItemStack createSubField(Material material, RecipeField field, String loreKey) {
        return ItemBuilder.of(material)
                .name(lang().get(field.langKey()))
                .lore(lang().get(loreKey))
                .build();
    }

    private ItemStack createDropField(Material material, RecipeField field, ItemStack display) {
        return display != null ? display
                : ItemBuilder.of(material)
                        .name(lang().get(field.langKey()))
                        .lore(lang().get("recipes.edit.drop_hint"))
                        .build();
    }

    private String currentString(RecipeField field) {
        String value = config().getString(field.configPath(type));
        return value != null ? value : lang().get("recipes.edit.current_none");
    }

    private ItemStack dropDisplay(int slot, RecipeField field) {
        ItemStack staged = session.stagedItem(slot);
        if (staged != null) return staged.clone();
        return RecipeEditorSession.deserializeItem(config().getString(configPathFor(field)));
    }

    private YamlConfiguration config() {
        return session.config();
    }

    RecipeEditorSession session() { return session; }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        int slot = event.getRawSlot();
        if (dropZoneSlots().contains(slot)) {
            handleDropZoneClick(slot, event);
            return true;
        }
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");
        if (action != null) {
            switch (action) {
                case "save" -> { handleSave(); return true; }
                case "discard" -> { handleDiscard(); return true; }
                case "back" -> { handleBack(); return true; }
                default -> {}
            }
        }
        switch (slot) {
            case FIELD_SLOT_DISPLAY_NAME -> promptField(RecipeField.DISPLAY_NAME, "Enter a display name (or \"none\" to clear):");
            case FIELD_SLOT_AMOUNT -> promptField(RecipeField.RESULT_AMOUNT, "Enter a result amount (positive integer):");
            case FIELD_SLOT_OVERRIDE -> toggleField(RecipeField.OVERRIDE_VANILLA);
            case FIELD_SLOT_HIDDEN -> toggleField(RecipeField.HIDDEN_WHEN_LOCKED);
            case FIELD_SLOT_UNLOCK_MSG -> promptField(RecipeField.UNLOCK_MESSAGE, "Enter an unlock message (or \"none\" to clear):");
            case FIELD_SLOT_REQUIREMENTS -> new RecipeRequirementsSubMenu(viewer, this).open();
            case FIELD_SLOT_PERMISSIONS -> new RecipePermissionsSubMenu(viewer, this).open();
            case FIELD_SLOT_SPECIAL_A -> {
                if (type == RecipeType.SHAPED) new RecipeShapeSubMenu(viewer, this).open();
                else if (type == RecipeType.SHAPELESS) new RecipeShapelessIngredientsSubMenu(viewer, this).open();
            }
            default -> {}
        }
        return true;
    }

    private void handleDropZoneClick(int slot, InventoryClickEvent event) {
        RecipeField field = fieldForSlot(slot);
        if (field == null) return;
        InventoryAction action = event.getAction();
        if (action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_ALL) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                ItemStack copy = cursor.clone();
                copy.setAmount(1);
                session.setDropItem(slot, copy, configPathFor(field));
                ItemStack remaining = cursor.clone();
                remaining.setAmount(action == InventoryAction.PLACE_ONE
                        ? Math.max(0, cursor.getAmount() - 1) : 0);
                viewer.setItemOnCursor(remaining.getAmount() > 0 ? remaining : null);
                refresh();
            }
        } else if (action == InventoryAction.COLLECT_TO_CURSOR) {
            session.clearDropItem(slot, configPathFor(field));
            refresh();
        }
    }

    private RecipeField fieldForSlot(int slot) {
        if (slot == RESULT_DROP_SLOT) return RecipeField.RESULT;
        switch (type) {
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE, STONECUTTER -> {
                return slot == FIELD_SLOT_SPECIAL_A ? RecipeField.INPUT : null;
            }
            case BREWING -> {
                if (slot == FIELD_SLOT_SPECIAL_A) return RecipeField.INPUT;
                if (slot == FIELD_SLOT_SPECIAL_B) return RecipeField.ADDITION;
                return null;
            }
            case SMITHING, ANVIL -> {
                if (slot == FIELD_SLOT_SPECIAL_A) return RecipeField.BASE;
                if (slot == FIELD_SLOT_SPECIAL_B) return RecipeField.ADDITION;
                return null;
            }
            default -> { return null; }
        }
    }

    private String configPathFor(RecipeField field) {
        if (field == RecipeField.ADDITION && type == RecipeType.BREWING) {
            return "brewing.ingredient";
        }
        return field.configPath(type);
    }

    private void promptField(RecipeField field, String message) {
        RecipeChatHandler.prompt(viewer, message, (p, value) -> {
            if (value.equalsIgnoreCase("cancel")) {
                p.sendMessage(lang().get("recipes.edit.chat_cancelled"));
                return;
            }
            String path = configPathFor(field);
            switch (field.widget()) {
                case STRING -> {
                    if (value.equalsIgnoreCase("none")) {
                        config().set(path, null);
                    } else {
                        config().set(path, value);
                    }
                }
                case INT -> {
                    try {
                        int parsed = Integer.parseInt(value.trim());
                        if (parsed < 0) {
                            p.sendMessage(ColorFormat.format("&cValue must be 0 or greater."));
                            return;
                        }
                        config().set(path, parsed);
                    } catch (NumberFormatException e) {
                        p.sendMessage(ColorFormat.format("&cInvalid number: " + value));
                        return;
                    }
                }
                case DOUBLE -> {
                    try {
                        double parsed = Double.parseDouble(value.trim());
                        if (parsed < 0) {
                            p.sendMessage(ColorFormat.format("&cValue must be 0 or greater."));
                            return;
                        }
                        config().set(path, parsed);
                    } catch (NumberFormatException e) {
                        p.sendMessage(ColorFormat.format("&cInvalid number: " + value));
                        return;
                    }
                }
                default -> { return; }
            }
            p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            refresh();
        });
    }

    private void toggleField(RecipeField field) {
        boolean current = config().getBoolean(field.configPath(type), false);
        config().set(field.configPath(type), !current);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        refresh();
    }

    private void handleSave() {
        String error = session.validate();
        if (error != null) {
            viewer.sendMessage(lang().get(error));
            return;
        }
        session.save();
        RecipeRegistry.reload();
        UnlockManager.invalidateAll();
        RecipeEditorSession.remove(viewer.getUniqueId());
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        new RecipeDetailMenu(viewer, RecipeRegistry.get(recipe.getKey())).open();
    }

    private void handleDiscard() {
        session.returnDropItems(viewer);
        RecipeEditorSession.remove(viewer.getUniqueId());
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        new RecipeDetailMenu(viewer, recipe).open();
    }

    private void handleBack() {
        suppressCleanup = true;
        new RecipeDetailMenu(viewer, recipe).open();
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        ItemStack dragged = event.getOldCursor();
        if (dragged == null || dragged.getType() == Material.AIR) return;
        int affected = 0;
        for (int slot : event.getRawSlots()) {
            if (dropZoneSlots().contains(slot)) affected++;
        }
        if (affected == 0) return;
        int consumed = 0;
        for (int slot : event.getRawSlots()) {
            if (!dropZoneSlots().contains(slot)) continue;
            RecipeField field = fieldForSlot(slot);
            if (field == null) continue;
            ItemStack placed = dragged.clone();
            placed.setAmount(1);
            session.setDropItem(slot, placed, configPathFor(field));
            inventory.setItem(slot, placed);
            consumed++;
        }
        ItemStack cursor = dragged.clone();
        cursor.setAmount(Math.max(0, cursor.getAmount() - consumed));
        viewer.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
    }

    @Override
    public void onClose() {
        if (suppressCleanup) {
            suppressCleanup = false;
            return;
        }
        RecipeEditorSession sessionNow = RecipeEditorSession.get(viewer.getUniqueId());
        if (sessionNow != null) {
            sessionNow.returnDropItems(viewer);
            RecipeEditorSession.remove(viewer.getUniqueId());
        }
    }
}
