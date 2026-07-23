package me.nagasonic.alkatraz.api.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable configuration POJO representing the settings for a single spell.
 * <p>
 * Instances of this class hold all configurable properties of a spell, including its identity,
 * display metadata, gameplay costs, timing, and arbitrary custom properties.
 * </p>
 */
public class SpellConfig {
    private String id;
    private String displayName;
    private String description;
    private String element;
    private int level;
    private int manaCost;
    private long cooldown;
    private int requiredCircle;
    private boolean enabled;
    private double castTime;
    private int maxMastery;
    private final Map<String, Object> customProperties;

    /**
     * Constructs a new {@code SpellConfig} with sensible defaults.
     * <p>
     * Defaults: level 1, mana cost 10, cooldown 1000ms, required circle 1,
     * enabled, cast time 1.0s, max mastery 10, and an empty custom properties map.
     * </p>
     */
    public SpellConfig() {
        this.level = 1;
        this.manaCost = 10;
        this.cooldown = 1000;
        this.requiredCircle = 1;
        this.enabled = true;
        this.castTime = 1.0;
        this.maxMastery = 10;
        this.customProperties = new HashMap<>();
    }

    /**
     * Returns the unique identifier of this spell.
     *
     * @return the spell id
     */
    public String getId() { return id; }

    /**
     * Sets the unique identifier of this spell.
     *
     * @param id the spell id to set
     */
    public void setId(String id) { this.id = id; }

    /**
     * Returns the display name of this spell.
     *
     * @return the display name
     */
    public String getDisplayName() { return displayName; }

    /**
     * Sets the display name of this spell.
     *
     * @param displayName the display name to set
     */
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /**
     * Returns the description of this spell.
     *
     * @return the description
     */
    public String getDescription() { return description; }

    /**
     * Sets the description of this spell.
     *
     * @param description the description to set
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * Returns the elemental type of this spell.
     *
     * @return the element
     */
    public String getElement() { return element; }

    /**
     * Sets the elemental type of this spell.
     *
     * @param element the element to set
     */
    public void setElement(String element) { this.element = element; }

    /**
     * Returns the level of this spell.
     *
     * @return the level
     */
    public int getLevel() { return level; }

    /**
     * Sets the level of this spell.
     *
     * @param level the level to set
     */
    public void setLevel(int level) { this.level = level; }

    /**
     * Returns the mana cost of this spell.
     *
     * @return the mana cost
     */
    public int getManaCost() { return manaCost; }

    /**
     * Sets the mana cost of this spell.
     *
     * @param manaCost the mana cost to set
     */
    public void setManaCost(int manaCost) { this.manaCost = manaCost; }

    /**
     * Returns the cooldown of this spell in milliseconds.
     *
     * @return the cooldown in milliseconds
     */
    public long getCooldown() { return cooldown; }

    /**
     * Sets the cooldown of this spell in milliseconds.
     *
     * @param cooldown the cooldown in milliseconds to set
     */
    public void setCooldown(long cooldown) { this.cooldown = cooldown; }

    /**
     * Returns the required magic circle rank to cast this spell.
     *
     * @return the required circle
     */
    public int getRequiredCircle() { return requiredCircle; }

    /**
     * Sets the required magic circle rank to cast this spell.
     *
     * @param requiredCircle the required circle to set
     */
    public void setRequiredCircle(int requiredCircle) { this.requiredCircle = requiredCircle; }

    /**
     * Returns whether this spell is currently enabled.
     *
     * @return {@code true} if the spell is enabled, {@code false} otherwise
     */
    public boolean isEnabled() { return enabled; }

    /**
     * Sets whether this spell is enabled.
     *
     * @param enabled {@code true} to enable the spell, {@code false} to disable it
     */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * Returns the cast time of this spell in seconds.
     *
     * @return the cast time in seconds
     */
    public double getCastTime() { return castTime; }

    /**
     * Sets the cast time of this spell in seconds.
     *
     * @param castTime the cast time in seconds to set
     */
    public void setCastTime(double castTime) { this.castTime = castTime; }

    /**
     * Returns the maximum mastery level for this spell.
     *
     * @return the max mastery level
     */
    public int getMaxMastery() { return maxMastery; }

    /**
     * Sets the maximum mastery level for this spell.
     *
     * @param maxMastery the max mastery level to set
     */
    public void setMaxMastery(int maxMastery) { this.maxMastery = maxMastery; }

    /**
     * Stores a custom key-value property on this spell configuration.
     *
     * @param key   the property key
     * @param value the property value
     */
    public void setCustomProperty(String key, Object value) {
        customProperties.put(key, value);
    }

    /**
     * Retrieves a custom property value by key.
     *
     * @param key the property key
     * @return the property value, or {@code null} if no property is stored for the given key
     */
    public Object getCustomProperty(String key) {
        return customProperties.get(key);
    }
}
