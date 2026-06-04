package me.nagasonic.alkatraz.gui.implementation.options;

import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.gui.implementation.SpellOptionsMenu;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generic menu for spell option groups that use a shared value pool and
 * multiple slot options ({@link SpellOption.OptionRole#POOL} /
 * {@link SpellOption.OptionRole#SLOT}).
 *
 * <p>Slot header count and layout come from {@code menu_slots} on the pool
 * option (see {@link PooledSlotMenuLayout}).
 */
public class PooledSlotSelectionMenu extends PagedMenu<OptionValue<?>> {

    private static final int[] POOL_CONTENT_SLOTS = {
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final Spell spell;
    private final List<SpellOption> slotOptions;
    private final int[] slotHeaderPositions;

    private int focusedSlotIndex = 0;

    public PooledSlotSelectionMenu(Player viewer, Spell spell, SpellOption groupEntry) {
        super(viewer,
                ColorFormat.format("&5" + resolveTitle(groupEntry, spell)),
                54,
                resolvePoolValues(spell, groupEntry),
                14);
        this.spell = spell;

        String groupId = resolveGroupId(groupEntry);
        SpellOption pool = findPoolOption(spell, groupId);
        List<SpellOption> allSlots = findSlotOptions(spell, groupId);

        int displayCount = resolveDisplaySlotCount(pool, allSlots);
        this.slotOptions = allSlots.stream().limit(displayCount).toList();
        this.slotHeaderPositions = PooledSlotMenuLayout.computeHeaderSlots(displayCount);

        this.contentSlots = POOL_CONTENT_SLOTS;
        this.nextPageSlot = 52;
        this.previousPageSlot = 46;
        this.backButtonSlot = 49;
    }

    // =========================================================================
    // Group resolution
    // =========================================================================

    private static String resolveGroupId(SpellOption groupEntry) {
        if (groupEntry.hasGroup()) return groupEntry.getGroup();
        return groupEntry.getId();
    }

    private static String resolveTitle(SpellOption groupEntry, Spell spell) {
        SpellOption pool = findPoolOption(spell, resolveGroupId(groupEntry));
        if (pool != null) return pool.getDisplayName();
        return groupEntry.getDisplayName();
    }

    private static List<OptionValue<?>> resolvePoolValues(Spell spell, SpellOption groupEntry) {
        SpellOption pool = findPoolOption(spell, resolveGroupId(groupEntry));
        return pool != null ? pool.getOptionValues() : List.of();
    }

    private static SpellOption findPoolOption(Spell spell, String groupId) {
        return spell.getAllOptions().values().stream()
                .filter(o -> groupId.equals(o.getGroup()))
                .filter(o -> o.getRole() == SpellOption.OptionRole.POOL)
                .findFirst()
                .orElse(null);
    }

    private static List<SpellOption> findSlotOptions(Spell spell, String groupId) {
        return spell.getAllOptions().values().stream()
                .filter(o -> groupId.equals(o.getGroup()))
                .filter(o -> o.getRole() == SpellOption.OptionRole.SLOT)
                .sorted(Comparator.comparingInt(SpellOption::getSlotIndex))
                .toList();
    }

    private static int resolveDisplaySlotCount(SpellOption pool, List<SpellOption> slotOptions) {
        int configured = pool != null ? pool.getMenuSlots() : 0;
        int count = configured > 0 ? configured : slotOptions.size();
        count = Math.min(count, slotOptions.size());
        return PooledSlotMenuLayout.clampSlotCount(count);
    }

    // =========================================================================
    // Menu layout
    // =========================================================================

    @Override
    protected void addDecorations() {
        Set<Integer> reserved = new HashSet<>();
        for (int slot : slotHeaderPositions) {
            reserved.add(slot);
        }
        for (int slot : POOL_CONTENT_SLOTS) {
            reserved.add(slot);
        }
        reserved.add(backButtonSlot);
        reserved.add(previousPageSlot);
        reserved.add(nextPageSlot);

        for (int borderSlot : PooledSlotMenuLayout.borderSlotsExcluding(reserved)) {
            inventory.setItem(borderSlot, Utils.getBlank());
        }

        addSlotHeaders();
    }

    @Override
    protected void addBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta m = back.getItemMeta();
        m.setDisplayName(ColorFormat.format("&cBack to Options"));
        back.setItemMeta(m);
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    private void addSlotHeaders() {
        for (int i = 0; i < slotOptions.size() && i < slotHeaderPositions.length; i++) {
            SpellOption slotOption = slotOptions.get(i);
            int invSlot = slotHeaderPositions[i];

            if (!slotOption.meetsRequirements(viewer)) {
                inventory.setItem(invSlot, buildLockedSlotItem(slotOption));
                continue;
            }

            String selectedId = slotOption.getSelectedValueId(viewer);
            OptionValue<?> selected = slotOption.getValueById(selectedId).orElse(null);
            inventory.setItem(invSlot, buildSlotItem(i + 1, selected, focusedSlotIndex == i, slotOption));
        }
    }

    @Override
    protected ItemStack createDisplayItem(OptionValue<?> value, int index) {
        return buildPoolValueItem(value);
    }

    @Override
    protected void handleContentClick(OptionValue<?> value, InventoryClickEvent event) {
        if ("none".equals(value.getId())) return;

        ItemStack clicked = event.getCurrentItem();
        if (!getBoolData(clicked, "can_select")) {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            viewer.sendMessage(ColorFormat.format("&cYou don't meet the requirements for this option."));
            return;
        }

        String valueId = value.getId();
        if (isAssignedToAnySlot(valueId)) {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            viewer.sendMessage(ColorFormat.format("&cThat option is already assigned to a slot."));
            return;
        }

        assignToFocusedSlot(valueId);
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if (action == null) return true;

        if ("focus_slot".equals(action)) {
            int slotNum = getIntData(clicked, "slot_num");
            if (event.isRightClick()) {
                clearSlot(slotNum - 1);
            } else {
                focusedSlotIndex = slotNum - 1;
                refresh();
            }
            return true;
        }

        if ("locked_slot".equals(action)) {
            viewer.sendMessage(ColorFormat.format("&cThis slot is not yet unlocked."));
            return true;
        }

        return super.handleClick(event, clicked);
    }

    @Override
    protected void handleBackClick() {
        new SpellOptionsMenu(viewer, spell).open();
    }

    // =========================================================================
    // Item builders
    // =========================================================================

    private ItemStack buildLockedSlotItem(SpellOption slotOption) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(ColorFormat.format("&8" + slotOption.getDisplayName() + " &7(Locked)"));
        List<String> lore = new ArrayList<>();
        if (!slotOption.getUnmetRequirements(viewer).isEmpty()) {
            lore.add(ColorFormat.format("&c&lRequirements:"));
            slotOption.getUnmetRequirements(viewer).forEach(req ->
                    lore.add(ColorFormat.format("&c  • " + req.getDescription())));
        } else {
            lore.add(ColorFormat.format("&7Unlock by meeting the"));
            lore.add(ColorFormat.format("&7requirements for this slot."));
        }
        m.setLore(lore);
        item.setItemMeta(m);
        setMenuData(item, "action", "locked_slot");
        return item;
    }

    private ItemStack buildSlotItem(int slotNum, OptionValue<?> assigned,
                                    boolean focused, SpellOption slotOption) {
        Material mat = assigned != null && !assigned.getId().equals("none")
                ? assigned.getIcon()
                : Material.LIME_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();

        String prefix = focused ? "&b▶ " : "&e";
        String label = assigned != null && !assigned.getId().equals("none")
                ? assigned.getDisplayName()
                : "&7Empty";

        m.setDisplayName(ColorFormat.format(prefix + slotOption.getDisplayName() + ": &f" + label));

        List<String> lore = new ArrayList<>();
        if (focused) {
            lore.add(ColorFormat.format("&bSelected for assignment"));
            lore.add(ColorFormat.format("&7Click a value below to assign it."));
        } else {
            lore.add(ColorFormat.format("&eLeft-click &7to select this slot."));
        }
        if (assigned != null && !assigned.getId().equals("none")) {
            lore.add(ColorFormat.format("&cRight-click &7to clear."));
        }

        if (focused) {
            m.addEnchant(Enchantment.DURABILITY, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        m.setLore(lore);
        item.setItemMeta(m);
        setMenuData(item, "action", "focus_slot");
        setMenuData(item, "slot_num", slotNum);
        return item;
    }

    private ItemStack buildPoolValueItem(OptionValue<?> value) {
        ItemStack item = new ItemStack(value.getIcon());
        ItemMeta m = item.getItemMeta();

        boolean focusedSlotUsable = focusedSlotIndex >= 0
                && focusedSlotIndex < slotOptions.size()
                && slotOptions.get(focusedSlotIndex).meetsRequirements(viewer);
        boolean canSelect = focusedSlotUsable && value.meetsRequirements(viewer);
        boolean alreadyAssigned = isAssignedToAnySlot(value.getId());

        m.setDisplayName(ColorFormat.format(canSelect ? "&f" + value.getDisplayName()
                : "&7" + value.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&8" + value.getDescription()));
        lore.add("");

        if (!value.getImpacts().isEmpty()) {
            lore.add(ColorFormat.format("&eEffects:"));
            value.getImpacts().forEach(impact ->
                    lore.add(ColorFormat.format("&a  + " + impact.getDescription())));
            lore.add("");
        }

        if (!focusedSlotUsable && focusedSlotIndex < slotOptions.size()) {
            lore.add(ColorFormat.format("&c&lSLOT LOCKED"));
            slotOptions.get(focusedSlotIndex).getUnmetRequirements(viewer).forEach(req ->
                    lore.add(ColorFormat.format("&c  • " + req.getDescription())));
            lore.add("");
        }

        if (!canSelect && !"none".equals(value.getId())) {
            lore.add(ColorFormat.format("&c&lLOCKED"));
            value.getUnmetRequirements(viewer).forEach(req ->
                    lore.add(ColorFormat.format("&c  • " + req.getDescription())));
        } else if (alreadyAssigned) {
            lore.add(ColorFormat.format("&7Already in a slot."));
        } else if (!"none".equals(value.getId())) {
            if (focusedSlotIndex < slotOptions.size()) {
                lore.add(ColorFormat.format("&eClick to assign to "
                        + slotOptions.get(focusedSlotIndex).getDisplayName()));
            } else {
                lore.add(ColorFormat.format("&eClick to assign to slot " + (focusedSlotIndex + 1)));
            }
        }

        if (!canSelect || alreadyAssigned) {
            m.addEnchant(Enchantment.DURABILITY, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        m.setLore(lore);
        item.setItemMeta(m);
        setMenuData(item, "can_select", canSelect && !alreadyAssigned && !"none".equals(value.getId()));
        return item;
    }

    // =========================================================================
    // Slot assignment
    // =========================================================================

    private void assignToFocusedSlot(String valueId) {
        if (focusedSlotIndex < 0 || focusedSlotIndex >= slotOptions.size()) return;

        SpellOption target = slotOptions.get(focusedSlotIndex);
        if (target.selectValue(viewer, valueId)) {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            viewer.sendMessage(ColorFormat.format("&aAssigned to " + target.getDisplayName() + "."));
            advanceFocus();
            refresh();
        } else {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            viewer.sendMessage(ColorFormat.format("&cFailed to assign option."));
        }
    }

    private void clearSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotOptions.size()) return;
        slotOptions.get(slotIndex).selectValue(viewer, "none");
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        refresh();
    }

    private void advanceFocus() {
        for (int i = 0; i < slotOptions.size(); i++) {
            String sel = slotOptions.get(i).getSelectedValueId(viewer);
            if ("none".equals(sel) || sel == null) {
                focusedSlotIndex = i;
                return;
            }
        }
    }

    private boolean isAssignedToAnySlot(String valueId) {
        for (SpellOption opt : slotOptions) {
            if (valueId.equals(opt.getSelectedValueId(viewer))) return true;
        }
        return false;
    }
}
