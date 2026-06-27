package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.playerdata.SpellHotbarManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuration menu that lets players assign discovered spells to their
 * eight wand hotbar slots.
 *
 * <p>Layout mirrors {@code PooledSlotSelectionMenu}:
 * <ul>
 *   <li>Top two rows (0-8, 9-17): border / slot headers</li>
 *   <li>Rows 3-4 (slots 19-25, 28-34): scrollable spell pool</li>
 *   <li>Row 5 nav bar: previous (46), back (49), next (52)</li>
 * </ul>
 *
 * <p>Slot headers occupy the top row (slots 0-7).  The currently-focused
 * slot is highlighted with a glow enchant.  Left-clicking a header focuses
 * it; right-clicking clears its assignment.  Clicking a spell in the pool
 * assigns it to the focused slot.
 */
public class HotbarSpellSelectionMenu extends PagedMenu<Spell> {

    // Pool content slots: rows 3-4, skipping border columns
    private static final int[] POOL_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    // Slot header positions: top row, one per hotbar slot
    private static final int[] HEADER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7};

    private int focusedSlotIndex = 0;

    public HotbarSpellSelectionMenu(Player viewer) {
        super(viewer,
                ColorFormat.format("&5Configure Spell Hotbar"),
                54,
                getDiscoveredSpells(viewer),
                14);

        this.contentSlots     = POOL_SLOTS;
        this.nextPageSlot     = 52;
        this.previousPageSlot = 46;
        this.backButtonSlot   = 49;
    }

    // -------------------------------------------------------------------------
    // Spell pool helpers
    // -------------------------------------------------------------------------

    private static List<Spell> getDiscoveredSpells(Player viewer) {
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);
        return SpellRegistry.getAllSpells().values().stream()
                .filter(s -> profile.hasDiscoveredSpell(s)
                        || viewer.hasPermission("alkatraz.allspells"))
                .sorted(Comparator.comparingInt(Spell::getLevel)
                        .thenComparing(Spell::getDisplayName))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Menu building
    // -------------------------------------------------------------------------

    @Override
    protected void addDecorations() {
        // Border: every slot not used by headers, pool, or nav buttons
        for (int i = 0; i < 54; i++) {
            if (isReserved(i)) continue;
            inventory.setItem(i, Utils.getBlank());
        }
        addSlotHeaders();
    }

    @Override
    protected void addBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta m = back.getItemMeta();
        m.setDisplayName(ColorFormat.format("&cBack to Spells"));
        back.setItemMeta(m);
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected void handleBackClick() {
        new SpellsMenu(viewer).open();
    }

    // -------------------------------------------------------------------------
    // Slot headers (top row)
    // -------------------------------------------------------------------------

    private void addSlotHeaders() {
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);

        for (int i = 0; i < SpellHotbarManager.SPELL_SLOT_COUNT; i++) {
            String spellId = profile.getHotbarSpellIds().get(i);
            Spell assigned = (spellId != null) ? SpellRegistry.getSpell(spellId) : null;
            inventory.setItem(HEADER_SLOTS[i], buildHeaderItem(i, assigned));
        }
    }

    private ItemStack buildHeaderItem(int slotIndex, Spell assigned) {
        boolean focused = (slotIndex == focusedSlotIndex);

        Material mat = (assigned != null)
                ? assigned.getGuiItem().getType()
                : Material.LIME_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();

        String prefix  = focused ? "&b▶ " : "&e";
        String content = (assigned != null) ? assigned.getDisplayName() : "&7Empty";
        m.setDisplayName(ColorFormat.format(prefix + "Slot " + (slotIndex + 1) + ": &f" + content));

        List<String> lore = new ArrayList<>();
        if (focused) {
            lore.add(ColorFormat.format("&bCurrently selected"));
            lore.add(ColorFormat.format("&7Click a spell below to assign it."));
        } else {
            lore.add(ColorFormat.format("&eLeft-click &7to select this slot."));
        }
        if (assigned != null) {
            lore.add(ColorFormat.format("&cRight-click &7to clear."));
        }
        m.setLore(lore);

        if (focused) {
            m.addEnchant(Enchantment.DURABILITY, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(m);

        setMenuData(item, "action", "focus_slot");
        setMenuData(item, "slot_num", slotIndex + 1); // 1-indexed for readability
        return item;
    }

    // -------------------------------------------------------------------------
    // Spell pool items
    // -------------------------------------------------------------------------

    @Override
    protected ItemStack createDisplayItem(Spell spell, int index) {
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);
        List<String> spellIds = profile.getHotbarSpellIds().values().stream().toList();

        boolean alreadyAssigned = spellIds.contains(spell.getId());
        boolean canAssign = !alreadyAssigned;

        ItemStack item = spell.getGuiItem().clone();
        ItemMeta m = item.getItemMeta();

        m.setDisplayName(ColorFormat.format(canAssign
                ? "&f" + spell.getDisplayName()
                : "&7" + spell.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + spell.getElement().getName()
                + "  &eCircle " + spell.getRequiredCircleLevel()));
        lore.add(ColorFormat.format("&bMana Cost: &f" + spell.getCost()));
        lore.add(ColorFormat.format("&bCast Time: &f" + spell.getCastTime() + "s"));
        lore.add(ColorFormat.format("&bCooldown:  &f" + spell.getCooldown() + "s"));
        lore.add("");

        if (alreadyAssigned) {
            lore.add(ColorFormat.format("&7Already assigned to a slot."));
            m.addEnchant(Enchantment.DURABILITY, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(ColorFormat.format("&eClick to assign to Slot " + (focusedSlotIndex + 1)));
        }

        m.setLore(lore);
        item.setItemMeta(m);

        setMenuData(item, "spell_id", spell.getId());
        setMenuData(item, "can_assign", canAssign);
        return item;
    }

    @Override
    protected void handleContentClick(Spell spell, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        boolean canAssign = getBoolData(clicked, "can_assign");

        if (!canAssign) {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            viewer.sendMessage(ColorFormat.format("&cThat spell is already assigned to a slot."));
            return;
        }

        assignSpellToFocusedSlot(spell);
    }

    // -------------------------------------------------------------------------
    // Click routing — header clicks
    // -------------------------------------------------------------------------

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if (action == null) return super.handleClick(event, clicked);

        if ("focus_slot".equals(action)) {
            int slotNum = getIntData(clicked, "slot_num"); // 1-indexed
            if (event.isRightClick()) {
                clearSlot(slotNum - 1);
            } else {
                focusedSlotIndex = slotNum - 1;
                refresh();
            }
            return true;
        }

        return super.handleClick(event, clicked);
    }

    // -------------------------------------------------------------------------
    // Slot assignment helpers
    // -------------------------------------------------------------------------

    private void assignSpellToFocusedSlot(Spell spell) {
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);
        profile.setHotbarSpell(focusedSlotIndex, spell.getId());

        viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        viewer.sendMessage(ColorFormat.format(
                "&aAssigned &f" + spell.getDisplayName()
                + " &ato Slot " + (focusedSlotIndex + 1) + "."));

        // Advance focus to the next empty slot
        advanceFocus(profile);

        // Refresh live hotbar if wand is currently held
        SpellHotbarManager.refresh(viewer);

        refresh();
    }

    private void clearSlot(int slotIndex) {
        MagicProfile profile = ProfileManager.getProfile(viewer, MagicProfile.class);
        profile.setHotbarSpell(slotIndex, null);

        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        SpellHotbarManager.refresh(viewer);
        refresh();
    }

    /** Moves focus to the next slot that has no spell assigned. */
    private void advanceFocus(MagicProfile profile) {
        for (int i = 0; i < SpellHotbarManager.SPELL_SLOT_COUNT; i++) {
            String id = profile.getHotbarSpellIds().get(i);
            if (id == null || id.isEmpty()) {
                focusedSlotIndex = i;
                return;
            }
        }
        // All slots filled — keep current focus
    }

    // -------------------------------------------------------------------------
    // Layout helper
    // -------------------------------------------------------------------------

    private boolean isReserved(int slot) {
        for (int h : HEADER_SLOTS) if (slot == h) return true;
        for (int p : POOL_SLOTS)   if (slot == p) return true;
        return slot == backButtonSlot
                || slot == previousPageSlot
                || slot == nextPageSlot;
    }
}
