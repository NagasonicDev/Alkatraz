package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class RecipeCreateMenu extends Menu {

    private static final int RESULT_SLOT = 22;
    private static final int TYPE_SLOT = 20;
    private static final int KEY_SLOT = 21;
    private static final int CONTINUE_SLOT = 24;
    private static final int BACK_SLOT = 40;

    private RecipeType type = RecipeType.SHAPED;
    private ItemStack result;
    private String recipeKey;

    private final AlkatrazRecipe source; // null = create; non-null = duplicate

    public RecipeCreateMenu(Player viewer) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("menu.recipe_create")), 45);
        this.source = null;
    }

    public RecipeCreateMenu(Player viewer, AlkatrazRecipe source) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("menu.recipe_create")), 45);
        this.source = source;
        this.type = source.getType();
        this.result = source.getResult().clone();
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public Set<Integer> dropZoneSlots() {
        return Set.of(RESULT_SLOT);
    }

    @Override
    protected void build() {
        fillAll();

        inventory.setItem(TYPE_SLOT, ItemBuilder.of(Material.CRAFTING_TABLE)
                .name(lang().get("recipes.create.type", "type", type.name().toLowerCase()))
                .lore(lang().get("recipes.click_details"))
                .build());

        ItemStack keyItem = recipeKey != null
                ? ItemBuilder.of(Material.NAME_TAG).name("&e" + recipeKey).build()
                : ItemBuilder.of(Material.NAME_TAG)
                        .name(lang().get("recipes.create.key"))
                        .lore(lang().get("recipes.create.key_prompt").split("\n"))
                        .build();
        setMenuData(keyItem, "action", "key");
        inventory.setItem(KEY_SLOT, keyItem);

        if (result != null) {
            inventory.setItem(RESULT_SLOT, result.clone());
        } else {
            inventory.setItem(RESULT_SLOT, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(lang().get("recipes.create.result"))
                    .lore(lang().get("recipes.create.result_hint"))
                    .build());
        }

        ItemStack cont = Alkatraz.getGuiItemRegistry().getItem("confirm_button").clone();
        ItemMeta contMeta = cont.getItemMeta();
        if (contMeta != null) {
            contMeta.setDisplayName(lang().get("recipes.create.continue"));
            cont.setItemMeta(contMeta);
        }
        setMenuData(cont, "action", "continue");
        inventory.setItem(CONTINUE_SLOT, cont);

        ItemStack back = Alkatraz.getGuiItemRegistry().getItem("back_button").clone();
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(lang().get("recipes.back_to_categories"));
            back.setItemMeta(backMeta);
        }
        setMenuData(back, "action", "back");
        inventory.setItem(BACK_SLOT, back);
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (event.getRawSlot() == RESULT_SLOT) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_ALL) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    result = cursor.clone();
                    result.setAmount(1);
                    viewer.setItemOnCursor(null);
                    refresh();
                }
            } else if (action == InventoryAction.COLLECT_TO_CURSOR) {
                result = null;
                refresh();
            }
            return true;
        }
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");
        if (action != null) {
            switch (action) {
                case "key" -> {
                    RecipeChatHandler.prompt(viewer, lang().get("recipes.create.key_prompt"), (p, value) -> {
                        if (value.equalsIgnoreCase("cancel")) {
                            p.sendMessage(lang().get("recipes.edit.chat_cancelled"));
                            return;
                        }
                        String key = value.trim().toLowerCase();
                        if (!key.matches("[a-z0-9_]+")) {
                            p.sendMessage(lang().get("recipes.validation.key_invalid"));
                            return;
                        }
                        NamespacedKey parsed = MagicKeys.parse(key).orElse(null);
                        if (parsed == null || RecipeRegistry.get(parsed) != null) {
                            p.sendMessage(lang().get("recipes.validation.key_exists"));
                            return;
                        }
                        recipeKey = key;
                        refresh();
                    });
                    return true;
                }
                case "continue" -> {
                    handleContinue();
                    return true;
                }
                case "back" -> {
                    returnResult();
                    new RecipeCategoryMenu(viewer).open();
                    return true;
                }
                default -> {}
            }
        }
        if (event.getRawSlot() == TYPE_SLOT && source == null) {
            cycleType();
            return true;
        }
        return true;
    }

    private void cycleType() {
        RecipeType[] values = RecipeType.values();
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == type) {
                index = i;
                break;
            }
        }
        type = values[(index + 1) % values.length];
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        refresh();
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        ItemStack dragged = event.getOldCursor();
        if (dragged == null || dragged.getType() == Material.AIR) return;
        result = dragged.clone();
        result.setAmount(1);
        inventory.setItem(RESULT_SLOT, result.clone());
        viewer.setItemOnCursor(null);
    }

    @Override
    public void onClose() {
        returnResult();
    }

    private void returnResult() {
        if (result != null && source == null) {
            viewer.getInventory().addItem(result.clone())
                    .values().forEach(leftover ->
                            viewer.getWorld().dropItemNaturally(viewer.getLocation(), leftover));
            result = null;
        }
    }

    private void handleContinue() {
        if (result == null) {
            viewer.sendMessage(ColorFormat.format("&cDrag an item into the result slot first."));
            return;
        }
        if (recipeKey == null) {
            viewer.sendMessage(lang().get("recipes.create.key_prompt"));
            return;
        }
        NamespacedKey key = MagicKeys.alkatraz(recipeKey);
        if (RecipeRegistry.get(key) != null) {
            viewer.sendMessage(lang().get("recipes.validation.key_exists"));
            return;
        }
        File file = new File(Alkatraz.getInstance().getDataFolder(), "magic/recipes/" + recipeKey + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("definition", MagicKeys.format(key));
        config.set("type", type.name().toLowerCase());
        config.set("result.item", RecipeEditorSession.serializeItem(result));
        config.set("result.amount", result.getAmount());
        if (source != null) {
            File sourceFile = RecipeRegistry.fileOf(source.getKey());
            if (sourceFile != null && sourceFile.exists()) {
                YamlConfiguration sourceConfig = YamlConfiguration.loadConfiguration(sourceFile);
                config.set("requirements", sourceConfig.get("requirements"));
                config.set("permissions", sourceConfig.get("permissions"));
                config.set("experience", sourceConfig.get("experience"));
                config.set("cooking_time", sourceConfig.get("cooking_time"));
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            Alkatraz.logSevere("Failed to create recipe " + recipeKey + ": " + e.getMessage());
            viewer.sendMessage(ColorFormat.format("&cFailed to create the recipe file."));
            return;
        }
        ItemStack savedResult = result;
        if (source == null) {
            result = null;
        }
        RecipeRegistry.reload();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        viewer.sendMessage(lang().get(source != null ? "recipes.duplicated" : "recipes.created", "key", key.toString()));
        AlkatrazRecipe created = RecipeRegistry.get(key);
        if (created != null) {
            new RecipeEditMenu(viewer, created).open();
        } else {
            new RecipeEditMenu(viewer, AlkatrazRecipe.builder()
                    .key(key)
                    .type(type)
                    .result(savedResult)
                    .resultAmount(savedResult.getAmount())
                    .build()).open();
        }
    }
}
