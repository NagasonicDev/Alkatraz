package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.SpellHotbarManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

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

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

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
                ColorFormat.format(lang().get("menu.hotbar_config")),
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
        ItemStack blank = Alkatraz.getGuiItemRegistry().getItem("blank");
        for (int i = 0; i < 54; i++) {
            if (isReserved(i)) continue;
            inventory.setItem(i, blank.clone());
        }
        addSlotHeaders();
    }

    @Override
    protected void addBackButton() {
        ItemStack back = ItemBuilder.of(Material.BARRIER)
                .name(lang().get("hotbar.back_to_spells"))
                .build();
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

        String prefix  = focused ? "&b▶ " : "&e";
        String content = (assigned != null) ? assigned.getDisplayName()
                : lang().get("hotbar.slot_empty", "slot", String.valueOf(slotIndex + 1));

        ItemBuilder builder = ItemBuilder.of(mat)
                .name(prefix + lang().get("hotbar.slot_header", "slot", String.valueOf(slotIndex + 1)) + ": &f" + content)
                .glint(focused);

        if (focused) {
            builder.lore(lang().get("hotbar.currently_selected"), lang().get("hotbar.click_assign_lore"));
        } else {
            builder.lore(lang().get("hotbar.left_click_select"));
        }
        if (assigned != null) {
            builder.lore(lang().get("hotbar.right_click_clear"));
        }

        ItemStack item = builder.build();
        setMenuData(item, "action", "focus_slot");
        setMenuData(item, "slot_num", slotIndex + 1);
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

        ItemBuilder builder = ItemBuilder.of(spell.getGuiItem().clone())
                .name(canAssign ? "&f" + spell.getDisplayName() : "&7" + spell.getDisplayName())
                .lore("&7" + spell.getElement().getName() + "  &eCircle " + spell.getRequiredCircleLevel(),
                      "&bMana Cost: &f" + spell.getCost(),
                      "&bCast Time: &f" + spell.getCastTime() + "s",
                      "&bCooldown:  &f" + spell.getCooldown() + "s",
                      "");

        if (alreadyAssigned) {
            builder.lore(lang().get("hotbar.already_assigned")).glint(true);
        } else {
            builder.lore(lang().get("hotbar.click_assign", "slot", String.valueOf(focusedSlotIndex + 1)));
        }

        ItemStack item = builder.build();
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
