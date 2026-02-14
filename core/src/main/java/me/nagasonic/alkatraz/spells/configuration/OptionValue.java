package me.nagasonic.alkatraz.spells.configuration;

import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class OptionValue<T> {
    private SpellOption parentOption;
    private String id;
    private String displayName;
    private String description;
    private Material icon;
    private T value;

    private List<ValueRequirement> requirements;
    private List<ValueImpact> impacts;

    public OptionValue(String id, String displayName, String description, Material icon, T value) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.value = value;
        this.requirements = new ArrayList<>();
        this.impacts = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public T getValue() {
        return value;
    }

    public void setParentOption(SpellOption option) {
        this.parentOption = option;
    }

    public SpellOption getParentOption() {
        return parentOption;
    }

    /**
     * Adds a requirement that must be met for this value to be selectable
     */
    public OptionValue<T> addRequirement(ValueRequirement requirement) {
        this.requirements.add(requirement);
        return this;
    }

    /**
     * Adds an impact that this value has on the spell for a player
     */
    public OptionValue<T> addImpact(ValueImpact impact) {
        this.impacts.add(impact);
        return this;
    }

    /**
     * Checks if all requirements are currently met for a specific player
     */
    public boolean meetsRequirements(Player player) {
        for (ValueRequirement req : requirements) {
            if (!req.isMet(player)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the list of unmet requirements for a specific player
     */
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
     * Applies all impacts for a specific player
     */
    public void applyImpacts(Player player) {
        for (ValueImpact impact : impacts) {
            impact.apply(player);
        }
    }

    /**
     * Removes all impacts for a specific player
     */
    public void unapplyImpacts(Player player) {
        for (ValueImpact impact : impacts) {
            impact.unapply(player);
        }
    }

    public List<ValueRequirement> getRequirements() {
        return new ArrayList<>(requirements);
    }

    public List<ValueImpact> getImpacts() {
        return new ArrayList<>(impacts);
    }
}
