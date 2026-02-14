package me.nagasonic.alkatraz.spells.configuration;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SpellOption {
    private final Spell spell;
    private final String id;
    private String description;
    private final Material icon;
    private List<OptionValue<?>> optionValues;

    public SpellOption(Spell spell, String id, String description, Material icon) {
        this.spell = spell;
        this.id = id;
        this.description = description;
        this.icon = icon;
        this.optionValues = new ArrayList<>();
    }

    public Spell getSpell() {
        return spell;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public void addValue(OptionValue<?> optionValue) {
        optionValue.setParentOption(this);
        optionValues.add(optionValue);
    }

    public List<OptionValue<?>> getOptionValues() {
        return new ArrayList<>(optionValues);
    }

    /**
     * Gets only the values that currently meet their requirements for a specific player
     */
    public List<OptionValue<?>> getAvailableValues(Player player) {
        List<OptionValue<?>> available = new ArrayList<>();
        for (OptionValue<?> value : optionValues) {
            if (value.meetsRequirements(player)) {
                available.add(value);
            }
        }
        return available;
    }

    /**
     * Sets the selected value for this option for a specific player
     * Stores the selection in the player's profile
     * @return true if successful, false if requirements not met
     */
    public boolean selectValue(Player player, String valueId) {
        Optional<OptionValue<?>> value = optionValues.stream()
                .filter(v -> v.getId().equals(valueId))
                .findFirst();

        if (value.isPresent() && value.get().meetsRequirements(player)) {
            // Store the selection in player's profile
            MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);

            // Get old selection to unapply impacts
            String oldSelection = getSelectedValueId(player);
            if (oldSelection != null) {
                getValueById(oldSelection).ifPresent(oldValue ->
                        oldValue.unapplyImpacts(player));
            }

            // Store new selection
            profile.setSpellOption(spell.getId() + "." + getId(), valueId);

            // Apply new impacts
            value.get().applyImpacts(player);

            return true;
        }
        return false;
    }

    /**
     * Gets the currently selected value ID for a player from their profile
     */
    public String getSelectedValueId(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        return profile.getSpellOption(spell.getId() + "." + getId());
    }

    /**
     * Gets the currently selected value for a player
     */
    public OptionValue<?> getSelectedValue(Player player) {
        String valueId = getSelectedValueId(player);
        if (valueId == null && !optionValues.isEmpty()) {
            // Return first value as default
            return optionValues.get(0);
        }
        return getValueById(valueId).orElse(optionValues.isEmpty() ? null : optionValues.get(0));
    }

    /**
     * Gets a specific value by ID
     */
    public Optional<OptionValue<?>> getValueById(String valueId) {
        return optionValues.stream()
                .filter(v -> v.getId().equals(valueId))
                .findFirst();
    }
}
