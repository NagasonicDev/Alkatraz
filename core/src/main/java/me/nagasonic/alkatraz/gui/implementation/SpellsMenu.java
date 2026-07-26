package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.texturepack.TexturePackManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SpellsMenu extends PagedMenu<Spell> {

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return me.nagasonic.alkatraz.Alkatraz.getLangManager();
    }

    private static final int CONFIGURE_HOTBAR_SLOT = 49;

    public SpellsMenu(Player viewer) {
        super(viewer,
                getResourceTitle(),
                54,
                getSortedSpells(),
                28);
        this.contentSlots = getInnerContentSlots();
    }

    private static List<Spell> getSortedSpells() {
        return SpellRegistry.getAllSpellsByIdFull().values().stream()
                .sorted(Comparator.comparingInt(Spell::getLevel)
                        .thenComparing(Spell::getDisplayName))
                .collect(Collectors.toList());
    }

    private static String getResourceTitle() {
        String code = Alkatraz.getTexturePackManager().getMenuTitleCode("spells");
        if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
            return lang().get("menu.spells");
        }
        return code;
    }

    private static int[] getInnerContentSlots() {
        int[] slots = new int[28];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[idx++] = row * 9 + col;
            }
        }
        return slots;
    }

    @Override
    protected void addDecorations() {
        fillAll();
        inventory.setItem(CONFIGURE_HOTBAR_SLOT, createConfigureHotbarItem());
    }

    private ItemStack createConfigureHotbarItem() {
        ItemStack item = ItemBuilder.of(Material.COMPARATOR)
                .name(lang().get("spells.configure_hotbar"))
                .lore(lang().get("spells.configure_hotbar_lore"),
                      "&7These appear when you hold a wand.")
                .build();
        setMenuData(item, "action", "open_hotbar_config");
        return item;
    }

    @Override
    protected ItemStack createDisplayItem(Spell spell, int index) {
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);

        boolean discovered = profile.hasDiscoveredSpell(spell)
                || viewer.hasPermission("alkatraz.allspells");

        return discovered
                ? createDiscoveredSpellItem(spell, profile)
                : createLockedSpellItem(spell);
    }

    private ItemStack createDiscoveredSpellItem(Spell spell, MagicProfile profile) {
        List<String> lore = new ArrayList<>();
        for (String line : spell.getDescription()) {
            lore.add(ColorFormat.format(line));
        }
        lore.add("");
        lore.add(lang().get("spells.code", "value", spell.getCode()));
        lore.add(lang().get("spells.mana_cost", "value", String.valueOf(spell.getCost())));
        lore.add(lang().get("spells.cooldown", "value", spell.getCooldown() + "s"));
        lore.add(lang().get("spells.cast_time", "value", spell.getCastTime() + "s"));
        lore.add(lang().get("spells.element", "value", spell.getElement().getName()));
        lore.add(lang().get("spells.mastery", "value", profile.getSpellMastery(spell) + "/" + spell.getMaxMastery()));
        lore.add("");
        lore.add(lang().get("spells.circle", "value", String.valueOf(spell.getRequiredCircleLevel())));

        if (!spell.getAllOptions().isEmpty()) {
            lore.add("");
            lore.add(lang().get("spells.has_options"));
            lore.add(lang().get("spells.has_options_lore"));
        }

        ItemStack item = ItemBuilder.of(spell.getGuiItem().clone())
                .rawName(ColorFormat.format(spell.getDisplayName()))
                .rawLore(lore)
                .hideAttributes()
                .customModelData(spell.getGuiCustomModelData())
                .glint(false)
                .build();

        setMenuData(item, "spell_type", spell.getType());
        setMenuData(item, "has_options", !spell.getAllOptions().isEmpty());

        return item;
    }

    private ItemStack createLockedSpellItem(Spell spell) {
        return ItemBuilder.of(Material.GRAY_DYE)
                .name(lang().get("spells.locked_name"))
                .rawLore(List.of(lang().get("spells.locked_lore", "circle", String.valueOf(spell.getRequiredCircleLevel()))))
                .build();
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if ("open_hotbar_config".equals(action)) {
            new HotbarSpellSelectionMenu(viewer).open();
            return true;
        }

        return super.handleClick(event, clicked);
    }

    @Override
    protected void handleContentClick(Spell spell, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        boolean hasOptions = getBoolData(clicked, "has_options");

        if (hasOptions) {
            SpellOptionsMenu optionsMenu = new SpellOptionsMenu(viewer, spell);
            optionsMenu.open();
        }
    }
}
