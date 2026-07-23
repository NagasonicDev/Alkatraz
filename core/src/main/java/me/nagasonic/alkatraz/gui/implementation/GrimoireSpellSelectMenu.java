package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class GrimoireSpellSelectMenu extends PagedMenu<Spell> {

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return me.nagasonic.alkatraz.Alkatraz.getLangManager();
    }

    private static final int[] SELECT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final ItemStack grimoireStack;
    private final MagicItemInstance instance;
    private final ItemDefinition definition;
    private final int targetPageIndex;

    public GrimoireSpellSelectMenu(Player viewer, ItemStack grimoireStack, MagicItemInstance instance, ItemDefinition definition, int targetPageIndex) {
        super(viewer,
                lang().get("grimoire.spell_select_title"),
                54,
                getDiscoverableSpells(viewer),
                28);
        this.grimoireStack = grimoireStack;
        this.instance = instance;
        this.definition = definition;
        this.targetPageIndex = targetPageIndex;
        this.contentSlots = SELECT_SLOTS;
        this.nextPageSlot = 53;
        this.previousPageSlot = 45;
        this.backButtonSlot = 49;
    }

    private static List<Spell> getDiscoverableSpells(Player viewer) {
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);
        return SpellRegistry.getAllSpells().values().stream()
                .filter(s -> profile.hasDiscoveredSpell(s) || viewer.hasPermission("alkatraz.allspells"))
                .sorted(Comparator.comparingInt(Spell::getRequiredCircleLevel)
                        .thenComparing(Spell::getDisplayName))
                .collect(Collectors.toList());
    }

    @Override
    protected void addDecorations() {
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, Utils.getBlank());
        }
    }

    @Override
    protected void addBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta m = back.getItemMeta();
        m.setDisplayName(lang().get("grimoire.spell_select_back"));
        back.setItemMeta(m);
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected void handleBackClick() {
        new GrimoirePageMenu(viewer, grimoireStack, instance, definition).open();
    }

    @Override
    protected ItemStack createDisplayItem(Spell spell, int index) {
        ItemStack item = spell.getGuiItem().clone();
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ColorFormat.format("&f" + spell.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + spell.getElement().getName()
                + "  &eCircle " + spell.getRequiredCircleLevel()));
        lore.add(ColorFormat.format("&bMana Cost: &f" + spell.getCost()));
        lore.add(ColorFormat.format("&bCast Time: &f" + spell.getCastTime() + "s"));
        lore.add(ColorFormat.format("&bCooldown:  &f" + spell.getCooldown() + "s"));
        lore.add("");
        lore.add(lang().get("grimoire.spell_select_assign", "page", targetPageIndex + 1));
        meta.setLore(lore);
        item.setItemMeta(meta);

        setMenuData(item, "spell_id", spell.getId());
        return item;
    }

    @Override
    protected void handleContentClick(Spell spell, InventoryClickEvent event) {
        List<String> pages = GrimoirePageMenu.getPagesStatic(instance);
        if (targetPageIndex >= 0 && targetPageIndex < pages.size()) {
            pages.set(targetPageIndex, spell.getId());
            GrimoirePageMenu.savePages(viewer, grimoireStack, instance, pages);

            viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            viewer.sendMessage(lang().get("grimoire.spell_select_assigned",
                    "spell", spell.getDisplayName(), "page", targetPageIndex + 1));
        }

        new GrimoirePageMenu(viewer, grimoireStack, instance, definition).open();
    }
}
