package me.nagasonic.alkatraz.api.spells;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.events.SpellPrepareEvent;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public abstract class Spell {
    protected final String type;
    protected String id;
    protected String displayName;
    protected String code;
    protected Element element;
    protected int cost;
    protected int requiredCircle;
    protected boolean enabled;
    protected List<String> description;
    protected BarColor masteryBarColor;
    protected ItemStack guiItem;
    protected long cooldown;
    protected double castTime;
    protected int level;
    protected int maxMastery;

    public Spell(String type) {
        this.type = type;
    }

    // ── Abstract methods that external plugins must implement ──────────

    public abstract void loadConfiguration();
    public abstract void castAction(Player caster, ItemStack wand);
    public abstract void mobCastAction(Mob caster, ItemStack wand);
    public abstract int circleAction(LivingEntity caster, SpellPrepareEvent event);
    public abstract ItemStack getSpellBook();

    // ── Concrete methods available to subclasses ──────────────────────

    protected void setupOptions() {
    }

    public boolean canMobCast(Mob mob) {
        return true;
    }

    protected void cancelCast(Player caster) {
        Set<UUID> cancelled = getCancelledPlayers();
        if (cancelled != null) {
            cancelled.add(caster.getUniqueId());
        }
    }

    protected Set<UUID> getCancelledPlayers() {
        return null;
    }

    // ── Getters for inherited fields ──────────────────────────────────

    public String getType() { return type; }
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Element getElement() { return element; }
    public int getCost() { return cost; }
    public int getRequiredCircle() { return requiredCircle; }
    public boolean isEnabled() { return enabled; }
    public String getCode() { return code; }
    public List<String> getDescription() { return description; }
    public long getCooldown() { return cooldown; }
    public double getCastTime() { return castTime; }
    public int getMaxMastery() { return maxMastery; }
    public int getLevel() { return level; }
    public int getRequiredCircleLevel() { return getRequiredCircle(); }
    public BarColor getMasteryBarColor() { return masteryBarColor; }
    public ItemStack getGuiItem() { return guiItem; }
}
