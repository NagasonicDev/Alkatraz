package me.nagasonic.alkatraz.spells.configuration;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class SpellOption {

    /**
     * How this option participates in the spell-options UI.
     */
    public enum OptionRole {
        /** Standard option shown (or hidden without a group) in SpellOptionsMenu. */
        NORMAL,
        /** Master value list for a pooled slot group (e.g. buff_pool). */
        POOL,
        /** A selectable slot that picks from the pool's values. */
        SLOT
    }

    private final Spell spell;
    private final String id;
    private final int defIndex;
    private String description;
    private String displayName;
    private final Material icon;
    private List<OptionValue<?>> optionValues;
    private OptionRole role = OptionRole.NORMAL;
    private int slotIndex = 0;
    /**
     * How many slot headers the pooled selection menu displays (pool options only).
     * {@code 0} means use every {@link OptionRole#SLOT} option in the group.
     */
    private int menuSlots = 0;
    private final List<ValueRequirement> requirements = new ArrayList<>();
    private final List<ValueImpact> impacts = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Custom-menu support (driven entirely from config — no spell subclass needed)
    // -------------------------------------------------------------------------

    /**
     * When true this option opens a bespoke Menu class instead of the
     * standard SpellOptionValuesMenu.  Set via {@code use_custom_menu: true}
     * in the options YAML.
     */
    private boolean useCustomMenu = false;

    /**
     * Fully-qualified class name of the Menu to open.
     * The class must expose a constructor: {@code (Player, Spell, SpellOption)}.
     * Set via {@code custom_menu: "com.example.MyMenu"} in the options YAML.
     */
    private String customMenuClass = null;

    // -------------------------------------------------------------------------
    // Grouping / visibility support
    // -------------------------------------------------------------------------

    /**
     * When true this option is hidden from the main SpellOptionsMenu list.
     * Hidden options can still be shown indirectly through a group's synthetic
     * entry.  Set via {@code hidden: true} in the options YAML.
     */
    private boolean hidden = false;

    /**
     * Non-null when this option belongs to a named group.  All options sharing
     * the same group id are collapsed into a single synthetic entry in
     * SpellOptionsMenu.  The first option in the group that declares
     * {@code use_custom_menu: true} drives the click behaviour of that entry.
     * Set via {@code group: "my_group"} in the options YAML.
     */
    private String group = null;

    // =========================================================================
    // Constructor
    // =========================================================================

    public SpellOption(Spell spell, String id, String description, Material icon, int defIndex) {
        this.spell = spell;
        this.id = id;
        this.defIndex = defIndex;
        this.description = description;
        this.displayName = description;
        this.icon = icon;
        this.optionValues = new ArrayList<>();
    }

    // =========================================================================
    // Custom-menu API
    // =========================================================================

    public boolean hasCustomMenu() {
        return useCustomMenu;
    }

    public void setUseCustomMenu(boolean useCustomMenu) {
        this.useCustomMenu = useCustomMenu;
    }

    public String getCustomMenuClass() {
        return customMenuClass;
    }

    public void setCustomMenuClass(String customMenuClass) {
        this.customMenuClass = customMenuClass;
    }

    /**
     * Reflectively constructs and opens the custom menu for this option.
     *
     * The target class must have a constructor with the signature:
     * {@code MyMenu(Player viewer, Spell spell, SpellOption option)}
     *
     * @param viewer The player opening the menu.
     * @return true if the menu was opened successfully, false otherwise.
     */
    public boolean openCustomMenu(Player viewer) {
        if (!useCustomMenu || customMenuClass == null) return false;

        try {
            @SuppressWarnings("unchecked")
            Class<? extends Menu> clazz =
                    (Class<? extends Menu>) Class.forName(customMenuClass);

            Constructor<? extends Menu> ctor =
                    clazz.getConstructor(Player.class, Spell.class, SpellOption.class);

            ctor.newInstance(viewer, spell, this).open();
            return true;

        } catch (ClassNotFoundException e) {
            Alkatraz.getInstance().getLogger().log(Level.WARNING,
                    "[SpellOption] Custom menu class not found: " + customMenuClass, e);
        } catch (NoSuchMethodException e) {
            Alkatraz.getInstance().getLogger().log(Level.WARNING,
                    "[SpellOption] Custom menu class '" + customMenuClass
                            + "' must have a constructor (Player, Spell, SpellOption).", e);
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().log(Level.WARNING,
                    "[SpellOption] Failed to open custom menu '" + customMenuClass + "'.", e);
        }
        return false;
    }

    // =========================================================================
    // Grouping / visibility API
    // =========================================================================

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public boolean hasGroup() {
        return group != null && !group.isEmpty();
    }

    // =========================================================================
    // Core accessors
    // =========================================================================

    public Spell getSpell() {
        return spell;
    }

    public String getId() {
        return id;
    }

    public int getDefIndex() {
        return defIndex;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Human-readable label for menus. Falls back to {@link #getDescription()}.
     */
    public String getDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName : description;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public OptionRole getRole() {
        return role;
    }

    public void setRole(OptionRole role) {
        this.role = role != null ? role : OptionRole.NORMAL;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public int getMenuSlots() {
        return menuSlots;
    }

    public void setMenuSlots(int menuSlots) {
        this.menuSlots = Math.max(0, menuSlots);
    }

    public Material getIcon() {
        return icon;
    }

    public void addRequirement(ValueRequirement requirement) {
        requirements.add(requirement);
    }

    public void addImpact(ValueImpact impact) {
        impacts.add(impact);
    }

    public List<ValueRequirement> getRequirements() {
        return new ArrayList<>(requirements);
    }

    public List<ValueImpact> getImpacts() {
        return new ArrayList<>(impacts);
    }

    /**
     * Whether the player may use this option at all (e.g. unlocked buff slot).
     */
    public boolean meetsRequirements(Player player) {
        for (ValueRequirement req : requirements) {
            if (!req.isMet(player)) {
                return false;
            }
        }
        return true;
    }

    public List<ValueRequirement> getUnmetRequirements(Player player) {
        List<ValueRequirement> unmet = new ArrayList<>();
        for (ValueRequirement req : requirements) {
            if (!req.isMet(player)) {
                unmet.add(req);
            }
        }
        return unmet;
    }

    /**
     * Pool and slot options only persist selections — gameplay effects apply at cast time.
     */
    private boolean appliesSelectionImpacts() {
        return role != OptionRole.POOL && role != OptionRole.SLOT;
    }

    public void addValue(OptionValue<?> optionValue) {
        optionValue.setParentOption(this);
        optionValues.add(optionValue);
    }

    public List<OptionValue<?>> getOptionValues() {
        return new ArrayList<>(optionValues);
    }

    /**
     * Gets only the values that currently meet their requirements for a
     * specific player.
     */
    public List<OptionValue<?>> getAvailableValues(Player player) {
        if (!meetsRequirements(player)) {
            return optionValues.stream()
                    .filter(v -> "none".equals(v.getId()))
                    .toList();
        }

        List<OptionValue<?>> available = new ArrayList<>();
        for (OptionValue<?> value : optionValues) {
            if (value.meetsRequirements(player)) {
                available.add(value);
            }
        }
        return available;
    }

    /**
     * Sets the selected value for this option for a specific player.
     * Stores the selection in the player's profile.
     *
     * @return true if successful, false if requirements not met.
     */
    public boolean selectValue(Player player, String valueId) {
        Optional<OptionValue<?>> value = optionValues.stream()
                .filter(v -> v.getId().equals(valueId))
                .findFirst();

        if (!meetsRequirements(player)) {
            return false;
        }

        if (value.isPresent() && value.get().meetsRequirements(player)) {
            MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);

            // Unapply the old selection's impacts
            String oldSelection = getSelectedValueId(player);
            if (oldSelection != null && appliesSelectionImpacts()) {
                getValueById(oldSelection).ifPresent(oldValue ->
                        oldValue.unapplyImpacts(player));
            }

            // Persist the new selection
            profile.setSpellOption(spell.getId() + "." + getId(), valueId);

            // Apply the new selection's impacts (skipped for pool/slot bookkeeping options)
            if (appliesSelectionImpacts()) {
                value.get().applyImpacts(player);
            }

            return true;
        }
        return false;
    }

    /**
     * Gets the currently selected value ID for a player from their profile.
     */
    public String getSelectedValueId(Player player) {
        String key = spell.getId() + "." + getId();
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile.getSpellOption(key) == null) {
            profile.setSpellOption(key, getOptionValues().get(getDefIndex()).getId());
        }
        return profile.getSpellOption(key);
    }

    /**
     * Gets the currently selected value for a player.
     */
    public OptionValue<?> getSelectedValue(Player player) {
        String valueId = getSelectedValueId(player);
        if (valueId == null && !optionValues.isEmpty()) {
            return optionValues.get(defIndex);
        }
        return getValueById(valueId)
                .orElse(optionValues.isEmpty() ? null : optionValues.get(defIndex));
    }

    /**
     * Gets a specific value by ID.
     */
    public Optional<OptionValue<?>> getValueById(String valueId) {
        return optionValues.stream()
                .filter(v -> v.getId().equals(valueId))
                .findFirst();
    }
}
