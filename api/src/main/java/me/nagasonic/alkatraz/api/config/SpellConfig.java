package me.nagasonic.alkatraz.api.config;

import java.util.HashMap;
import java.util.Map;

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getElement() { return element; }
    public void setElement(String element) { this.element = element; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getManaCost() { return manaCost; }
    public void setManaCost(int manaCost) { this.manaCost = manaCost; }
    public long getCooldown() { return cooldown; }
    public void setCooldown(long cooldown) { this.cooldown = cooldown; }
    public int getRequiredCircle() { return requiredCircle; }
    public void setRequiredCircle(int requiredCircle) { this.requiredCircle = requiredCircle; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double getCastTime() { return castTime; }
    public void setCastTime(double castTime) { this.castTime = castTime; }
    public int getMaxMastery() { return maxMastery; }
    public void setMaxMastery(int maxMastery) { this.maxMastery = maxMastery; }

    public void setCustomProperty(String key, Object value) {
        customProperties.put(key, value);
    }

    public Object getCustomProperty(String key) {
        return customProperties.get(key);
    }
}
