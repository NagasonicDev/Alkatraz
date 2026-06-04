package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.SyntheticGroupOption;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paginated menu showing all spell options.
 *
 * Hidden options that share a {@code group} id are collapsed into a single
 * {@link SyntheticGroupOption}. Groups with {@code use_custom_menu: true}
 * open their configured menu (e.g. {@link me.nagasonic.alkatraz.gui.implementation.options.PooledSlotSelectionMenu}).
 */
public class SpellOptionsMenu extends PagedMenu<SpellOption> {

    private final Spell spell;

    public SpellOptionsMenu(Player viewer, Spell spell) {
        super(viewer,
                ColorFormat.format("&6" + spell.getDisplayName() + " &7- Options"),
                54,
                buildDisplayOptions(viewer, spell),
                28);
        this.spell = spell;
        this.contentSlots = buildContentSlots();
    }

    // =========================================================================
    // Display-list construction
    // =========================================================================

    /**
     * Converts the spell's raw option map into the list that the paged menu
     * actually displays.
     *
     * Rules:
     * <ol>
     *   <li>Options flagged {@code hidden: true} AND belonging to a group are
     *       collapsed into one {@link SyntheticGroupOption} per group id.</li>
     *   <li>Options flagged {@code hidden: true} with no group are dropped
     *       entirely (they are internal-only).</li>
     *   <li>All remaining options appear as-is.</li>
     * </ol>
     */
    private static List<SpellOption> buildDisplayOptions(Player viewer, Spell spell) {
        // Preserve insertion order of options as declared in the YAML.
        List<SpellOption>           result         = new ArrayList<>();
        // group-id → list of member options (all hidden)
        Map<String, List<SpellOption>> groups      = new LinkedHashMap<>();
        // group-id → position in result where the synthetic entry should land
        Map<String, Integer> groupIndex  = new LinkedHashMap<>();

        for (SpellOption option : spell.getAllOptions().values()) {
            // Pool/slot options are internal — never listed on their own.
            if (isGroupedInternalOption(option)) {
                if (!option.hasGroup()) continue;

                String gid = option.getGroup();
                if (!groups.containsKey(gid)) {
                    groupIndex.put(gid, result.size());
                    result.add(null);
                }
                groups.computeIfAbsent(gid, k -> new ArrayList<>()).add(option);
                continue;
            }

            result.add(option);
        }

        // Replace each placeholder with a fully-built SyntheticGroupOption.
        for (Map.Entry<String, List<SpellOption>> entry : groups.entrySet()) {
            String            gid     = entry.getKey();
            List<SpellOption> members = entry.getValue();
            int               idx     = groupIndex.get(gid);

            result.set(idx, buildSyntheticEntry(viewer, spell, gid, members));
        }

        return result;
    }

    private static boolean isGroupedInternalOption(SpellOption option) {
        return option.getRole() == SpellOption.OptionRole.POOL
                || option.getRole() == SpellOption.OptionRole.SLOT
                || option.isHidden();
    }

    /**
     * Builds the single synthetic entry that represents an entire group of
     * hidden options.
     *
     * <ul>
     *   <li>The display name is taken from the first group member that carries
     *       a non-blank {@code description} (used as a display-name field here)
     *       — or falls back to a title-cased version of the group id.</li>
     *   <li>The icon is taken from the first group member that declares
     *       {@code use_custom_menu: true}, then from the first member overall.</li>
     *   <li>The delegate (for click handling) is the first member with
     *       {@code use_custom_menu: true}.</li>
     *   <li>Preview lore shows the selected value of each non-hidden member
     *       that has values (useful for e.g. buff slots).</li>
     * </ul>
     */
    private static SyntheticGroupOption buildSyntheticEntry(Player viewer,
                                                            Spell spell,
                                                            String groupId,
                                                            List<SpellOption> members) {
        SpellOption delegate = null;
        Material    icon     = null;
        String      name     = null;

        for (SpellOption m : members) {
            if (m.hasCustomMenu() && delegate == null) {
                delegate = m;
                icon     = m.getIcon();
                name     = m.getDisplayName();
            }
        }

        // Fallback icon / name
        if (icon == null) icon = members.get(0).getIcon();
        if (name == null || name.isBlank()) {
            name = toTitleCase(groupId.replace('_', ' '));
        }

        // Build preview lore from options that have actual selections
        List<String> preview = buildGroupPreviewLore(viewer, spell, members);

        return new SyntheticGroupOption(spell, groupId, name, icon, delegate, preview);
    }

    /**
     * Produces lore lines summarising the current state of a group's member
     * options.  Only members that have at least one value are included.
     */
    private static List<String> buildGroupPreviewLore(Player viewer,
                                                      Spell spell,
                                                      List<SpellOption> members) {
        List<String> lore = new ArrayList<>();
        for (SpellOption m : members) {
            if (m.getRole() == SpellOption.OptionRole.POOL) continue;
            if (m.getRole() != SpellOption.OptionRole.SLOT) continue;
            if (m.getOptionValues().isEmpty()) continue;
            String selId = m.getSelectedValueId(viewer);
            m.getValueById(selId).ifPresentOrElse(
                    v  -> lore.add(ColorFormat.format("&7" + m.getDisplayName()
                            + ": &f" + v.getDisplayName())),
                    ()  -> lore.add(ColorFormat.format("&7" + m.getDisplayName()
                            + ": &8Empty"))
            );
        }
        return lore;
    }

    // =========================================================================
    // PagedMenu implementation
    // =========================================================================

    private int[] buildContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(i -> i).toArray();
    }

    @Override
    protected void addDecorations() {
        ItemStack spellInfo = spell.getGuiItem().clone();
        ItemMeta meta = spellInfo.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&6" + spell.getDisplayName()));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Configure spell options"));
        lore.add("");
        lore.add(ColorFormat.format("&eCircle: " + spell.getLevel()));
        meta.setLore(lore);
        spellInfo.setItemMeta(meta);
        inventory.setItem(4, spellInfo);

        int[] borders = {0,1,2,3,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53};
        for (int s : borders) inventory.setItem(s, Utils.getBlank());
    }

    @Override
    protected void addBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&cBack to Spells"));
        back.setItemMeta(meta);
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected ItemStack createDisplayItem(SpellOption option, int index) {
        // Synthetic group entries get a specialised item
        if (option instanceof SyntheticGroupOption synthetic) {
            return buildSyntheticItem(synthetic);
        }

        // Normal option
        ItemStack item = new ItemStack(option.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&e" + option.getId()));

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + option.getDescription()));
        lore.add("");

        // Show currently selected value (only valid for options with values)
        if (!option.getOptionValues().isEmpty()) {
            lore.add(ColorFormat.format("&aCurrent: &f"
                    + option.getSelectedValue(viewer).getDisplayName()));
            lore.add("");
        }

        if (option.hasCustomMenu()) {
            lore.add(ColorFormat.format("&eClick to configure"));
        } else {
            lore.add(ColorFormat.format("&eClick to configure"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        setMenuData(item, "option_id", option.getId());
        return item;
    }

    /**
     * Builds the display item for a {@link SyntheticGroupOption}.
     */
    private ItemStack buildSyntheticItem(SyntheticGroupOption synthetic) {
        ItemStack item = new ItemStack(synthetic.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&b" + synthetic.getSyntheticDisplayName()));

        List<String> lore = new ArrayList<>(synthetic.getPreviewLore());
        if (!lore.isEmpty()) lore.add("");
        lore.add(ColorFormat.format("&eClick to configure"));

        meta.setLore(lore);
        item.setItemMeta(meta);
        setMenuData(item, "option_id", synthetic.getId());
        return item;
    }

    @Override
    protected void handleContentClick(SpellOption option, InventoryClickEvent event) {
        // Custom-menu option (including synthetic group entries) — delegate to
        // the option's openCustomMenu() which uses reflection internally.
        if (option.hasCustomMenu()) {
            option.openCustomMenu(viewer);
            return;
        }

        // Standard option — open the value-selection menu.
        new SpellOptionValuesMenu(viewer, spell, option).open();
    }

    @Override
    protected void handleBackClick() {
        new SpellsMenu(viewer).open();
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private static String toTitleCase(String input) {
        if (input == null || input.isBlank()) return input;
        String[] words = input.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }
}
