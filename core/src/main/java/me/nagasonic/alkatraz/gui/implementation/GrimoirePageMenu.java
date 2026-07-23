package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.component.handler.grimoire.GrimoireComponentHandler;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellCastValidator;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LecternInventory;

import java.util.ArrayList;
import java.util.List;

public class GrimoirePageMenu extends Menu {

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return me.nagasonic.alkatraz.Alkatraz.getLangManager();
    }

    private static final int LEFT_PAGE_SLOT = 11;
    private static final int RIGHT_PAGE_SLOT = 15;
    private static final int PREV_SPREAD_SLOT = 45;
    private static final int NEXT_SPREAD_SLOT = 53;

    private final ItemStack grimoireStack;
    private final MagicItemInstance instance;
    private final ItemDefinition definition;
    private int currentSpread;

    public GrimoirePageMenu(Player viewer, ItemStack grimoireStack, MagicItemInstance instance, ItemDefinition definition) {
        super(viewer, lang().get("menu.grimoire"), 54);
        this.grimoireStack = grimoireStack;
        this.instance = instance;
        this.definition = definition;
        this.currentSpread = 0;
        GrimoireComponentHandler.ensurePagesInitialized(instance, definition);
    }

    @Override
    protected void build() {
        ItemStack cover = ItemBuilder.of(Material.BROWN_STAINED_GLASS_PANE).rawName("").build();
        ItemStack spine = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).rawName("").build();

        fillAll();

        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, cover.clone());
            inventory.setItem(45 + i, cover.clone());
        }
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9, cover.clone());
            inventory.setItem(row * 9 + 8, cover.clone());
        }
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9 + 4, spine.clone());
        }

        List<String> pages = getPages();
        int totalPages = pages.size();
        int leftPageIndex = currentSpread * 2;
        int rightPageIndex = leftPageIndex + 1;

        if (leftPageIndex < totalPages) {
            inventory.setItem(LEFT_PAGE_SLOT, createPageItem(leftPageIndex, pages.get(leftPageIndex)));
        }
        if (rightPageIndex < totalPages) {
            inventory.setItem(RIGHT_PAGE_SLOT, createPageItem(rightPageIndex, pages.get(rightPageIndex)));
        }

        if (currentSpread > 0) {
            ItemStack prev = ItemBuilder.of(Material.ARROW)
                    .name(lang().get("grimoire.previous_pages"))
                    .build();
            setMenuData(prev, "action", "prev_spread");
            inventory.setItem(PREV_SPREAD_SLOT, prev);
        }

        if ((currentSpread + 1) * 2 < totalPages) {
            ItemStack next = ItemBuilder.of(Material.ARROW)
                    .name(lang().get("grimoire.next_pages"))
                    .build();
            setMenuData(next, "action", "next_spread");
            inventory.setItem(NEXT_SPREAD_SLOT, next);
        }
    }

    private ItemStack createPageItem(int pageIndex, String spellId) {
        int pageNumber = pageIndex + 1;

        if (spellId == null || spellId.isEmpty()) {
            ItemStack item = ItemBuilder.of(Material.BOOK)
                    .name(lang().get("grimoire.page_empty", "page", pageNumber))
                    .lore(lang().get("grimoire.page_empty_lore"))
                    .build();
            setMenuData(item, "action", "assign_spell");
            setMenuData(item, "page_index", pageIndex);
            return item;
        }

        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) {
            return ItemBuilder.of(Material.BOOK)
                    .name(lang().get("grimoire.page_unknown", "page", pageNumber))
                    .build();
        }

        ItemStack item = spell.getGuiItem().clone();
        List<String> lore = new ArrayList<>();
        lore.add(lang().get("grimoire.spell_element_circle", "element", spell.getElement().getName(), "circle", spell.getRequiredCircleLevel()));
        lore.add(lang().get("spells.mana_cost", "value", spell.getCost()));
        lore.add(lang().get("spells.cast_time", "value", spell.getCastTime()));
        lore.add(lang().get("spells.cooldown", "value", spell.getCooldown()));
        lore.add("");
        lore.add(lang().get("grimoire.page_spell_left_click"));
        lore.add(lang().get("grimoire.page_spell_right_click"));

        item = ItemBuilder.of(item)
                .rawName(lang().get("grimoire.page_title", "page", pageNumber, "spell", spell.getDisplayName()))
                .rawLore(lore)
                .build();

        setMenuData(item, "action", "cast_spell");
        setMenuData(item, "spell_id", spell.getId());
        setMenuData(item, "page_index", pageIndex);
        return item;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if (action == null) return true;

        switch (action) {
            case "prev_spread" -> {
                if (currentSpread > 0) {
                    currentSpread--;
                    refresh();
                }
                return true;
            }
            case "next_spread" -> {
                int totalPages = getPages().size();
                if ((currentSpread + 1) * 2 < totalPages) {
                    currentSpread++;
                    refresh();
                }
                return true;
            }
            case "cast_spell" -> {
                if (event.isRightClick()) {
                    int pageIndex = getIntData(clicked, "page_index");
                    clearPage(pageIndex);
                    refresh();
                    return true;
                }
                String spellId = getStringData(clicked, "spell_id");
                if (spellId != null) {
                    close();
                    Spell spell = SpellRegistry.getSpell(spellId);
                    if (spell != null && SpellCastValidator.canCast(viewer, null, spell)) {
                        spell.cast(viewer, null);
                    }
                }
                return true;
            }
            case "assign_spell" -> {
                int pageIndex = getIntData(clicked, "page_index");
                close();
                new GrimoireSpellSelectMenu(viewer, grimoireStack, instance, definition, pageIndex).open();
                return true;
            }
        }
        return true;
    }

    private void clearPage(int pageIndex) {
        List<String> pages = getPages();
        if (pageIndex >= 0 && pageIndex < pages.size()) {
            pages.set(pageIndex, null);
            savePages(viewer, grimoireStack, instance, pages);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getPages() {
        return getPagesStatic(instance);
    }

    @SuppressWarnings("unchecked")
    public static List<String> getPagesStatic(MagicItemInstance inst) {
        Object raw = inst.customData().get("pages");
        if (raw instanceof List<?> list) {
            List<String> pages = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof String s) pages.add(s);
                else pages.add(null);
            }
            return pages;
        }
        return new ArrayList<>();
    }

    public static void savePages(Player player, ItemStack stack, MagicItemInstance instance, List<String> pages) {
        instance.putCustomData("pages", new ArrayList<>(pages));
        MagicItemStack.writeInstance(stack, instance);
        player.getInventory().setItem(player.getInventory().getHeldItemSlot(), stack);
    }

}
