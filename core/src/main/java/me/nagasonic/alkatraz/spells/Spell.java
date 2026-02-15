package me.nagasonic.alkatraz.spells;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public abstract class Spell {
    protected final String type;
    protected String id;
    protected String displayName;
    protected List<String> description;
    protected Element element;
    protected String code;
    protected BarColor masteryBarColor;
    protected ItemStack guiItem;
    protected int cost;
    protected double castTime;
    protected int level;
    protected boolean enabled;
    protected int maxMastery;

    // Spell options (defined once per spell, selections stored per-player)
    protected Map<String, SpellOption> options = new HashMap<>();

    public Spell(String type) {
        this.type = type;
        setupOptions();
    }

    /**
     * Override this to define spell options (called once on spell registration)
     */
    protected void setupOptions() {
        // Subclasses can override to add their options
    }

    public abstract void loadConfiguration();

    public abstract void castAction(Player p, ItemStack wand);

    public abstract int circleAction(Player p, PlayerSpellPrepareEvent e);

    /**
     * Main spell casting method - handles validation, mana consumption, and timing
     */
    public void cast(Player p, ItemStack wand) {
        MagicProfile profile = ProfileManager.getProfile(p, MagicProfile.class);

        // Check circle level requirement
        if (profile.getCircleLevel() < getLevel()) {
            Utils.sendActionBar(p, "&cToo low Magic Circle");
            return;
        }

        // Get modified mana cost (can be affected by spell options)
        int manaCost = getModifiedManaCost(p);

        // Check mana
        if (profile.getMana() < manaCost) {
            Utils.sendActionBar(p, "&cNot Enough Mana");
            return;
        }

        // Check if player is alive
        if (p.isDead()) {
            return;
        }

        // Create and fire spell prepare event
        PlayerSpellPrepareEvent castEvent = new PlayerSpellPrepareEvent(p, this, wand);
        Bukkit.getPluginManager().callEvent(castEvent);

        if (castEvent.isCancelled()) {
            return;
        }

        // Set casting state
        profile.setCasting(true);

        // Consume mana
        profile.setMana(profile.getMana() - manaCost);

        // Add experience
        addExperience(p, Utils.getExp(getLevel()));

        // Send action bar message
        Utils.sendActionBar(p, ColorFormat.format("Casted: " + getDisplayName()));

        // Start circle animation
        int circleTaskId = circleAction(p, castEvent);

        // Calculate cast time (affected by wand and mastery)
        float baseCastTime = getFullCastTime(wand, getCastTime());
        long finalCastTime = calculateFinalCastTime(profile, baseCastTime);

        // Schedule spell execution after cast time
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
            if (!castEvent.isCancelled()) {
                profile.setCasting(false);
                Bukkit.getServer().getScheduler().cancelTask(circleTaskId);
                castAction(p, wand);
            }
        }, finalCastTime);

        // Add spell mastery
        if (profile.getSpellMastery(this) < getMaxMastery()) {
            addSpellMastery(p, this, 1);
        }
    }

    /**
     * Loads common spell configuration from YAML
     */
    public void loadCommonConfig(YamlConfiguration spellConfig) {
        this.id = spellConfig.getString("id");
        this.displayName = spellConfig.getString("display_name");
        this.description = spellConfig.getStringList("description");
        this.element = Element.valueOf(spellConfig.getString("element"));
        this.code = spellConfig.getString("code");
        this.castTime = spellConfig.getDouble("cast_time");
        this.cost = spellConfig.getInt("mana_cost");
        this.level = spellConfig.getInt("level");
        this.enabled = spellConfig.getBoolean("enabled");
        this.maxMastery = spellConfig.getInt("maximum_mastery");
        this.masteryBarColor = BarColor.valueOf(spellConfig.getString("mastery_bar_color"));
        this.guiItem = Utils.materialFromString(spellConfig.getString("gui_item"));
    }

    // ============================================
    // Spell Options Management
    // ============================================

    /**
     * Adds a spell option to this spell
     */
    public void addOption(SpellOption option) {
        options.put(option.getId(), option);
    }

    /**
     * Gets a spell option by ID
     */
    public SpellOption getOption(String optionId) {
        return options.get(optionId);
    }

    /**
     * Gets all spell options
     */
    public Map<String, SpellOption> getAllOptions() {
        return new HashMap<>(options);
    }

    // ============================================
    // Player Profile Helpers
    // ============================================

    /**
     * Checks if player has a specific spell tag from their options
     */
    protected boolean hasSpellTag(Player caster, Spell spell, String tag) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        return profile.hasSpellTag(spell, tag);
    }

    /**
     * Gets a stat value with player modifiers applied
     * Uses the SpellModifier record approach
     */
    protected double getModifiedStat(Player caster, String statName, double baseValue) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);

        if (profile.hasSpellModifier(this, statName)) {
            List<MagicProfile.SpellModifier> modifiers = profile.getSpellModifiers(this, statName);
            List<MagicProfile.SpellModifier> adds = new ArrayList<>();
            List<MagicProfile.SpellModifier> mults = new ArrayList<>();
            List<MagicProfile.SpellModifier> sets = new ArrayList<>();
            for (MagicProfile.SpellModifier modifier : modifiers) {
                String typeStr = profile.getSpellModifierType(modifier);

                if (typeStr != null) {
                    StatModifierImpact.ModifierType type = StatModifierImpact.ModifierType.valueOf(typeStr);
                    if (type == StatModifierImpact.ModifierType.ADD) adds.add(modifier);
                    if (type == StatModifierImpact.ModifierType.MULTIPLY) mults.add(modifier);
                    if (type == StatModifierImpact.ModifierType.SET) sets.add(modifier);
                }
                double val = 0;
                for (MagicProfile.SpellModifier setmod : sets){
                    val += setmod.value();
                }
                val /= sets.size();
                for (MagicProfile.SpellModifier mult : mults){
                    val *= mult.value();
                }
                for (MagicProfile.SpellModifier addmod : adds){
                    val += addmod.value();
                }
                return val;
            }
        }

        return baseValue;
    }

    /**
     * Gets mana cost with player modifiers applied
     */
    protected int getModifiedManaCost(Player caster) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);

        // Check for mana cost modifier
        if (profile.hasSpellModifier(this, "mana_cost")) {

            List<MagicProfile.SpellModifier> mods = profile.getSpellModifiers(this, "mana_cost");
            double value = 0;
            for (MagicProfile.SpellModifier mod : mods){
                value += mod.value();
            }
            return (int) Math.max(0, cost + value);
        }

        return cost;
    }

    /**
     * Adds experience to the player
     */
    protected void addExperience(Player player, double amount) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.setExperience(profile.getExperience() + amount);

        // TODO: Check for level up and handle accordingly
        // This would replace the old DataManager.addExperience logic
    }

    /**
     * Adds spell mastery to the player
     */
    protected void addSpellMastery(Player player, Spell spell, int amount) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        int currentMastery = profile.getSpellMastery(spell);
        if (currentMastery < 0) currentMastery = 0;
        profile.setSpellMastery(spell, Math.min(currentMastery + amount, spell.getMaxMastery()));
    }

    /**
     * Calculates final cast time based on mastery
     */
    private long calculateFinalCastTime(MagicProfile profile, float baseCastTime) {
        float castTimeInTicks = baseCastTime * 20;

        // If player has max mastery, reduce cast time by 25%
        if (profile.getSpellMastery(this) >= getMaxMastery()) {
            castTimeInTicks *= 0.75f; // 25% faster (was bugged before as 1.25x slower!)
        }

        return (long) castTimeInTicks;
    }

    /**
     * Calculates full cast time including wand modifier
     */
    public float getFullCastTime(ItemStack wand, double spellCastTime) {
        Double wandCastTime = NBT.get(wand, nbt -> (Double) nbt.getDouble("casting_time"));
        if (wandCastTime == null) wandCastTime = 1.0;
        return wandCastTime.floatValue() * (float) spellCastTime;
    }

    /**
     * Calculates power with affinity and resistance
     * (Replacement for old calcPower method)
     */
    public double calcPower(double base, LivingEntity target, Player caster) {
        MagicProfile casterProfile = ProfileManager.getProfile(caster, MagicProfile.class);
        double casterAffinity = casterProfile.getAffinity(getElement());

        double targetResistance;
        if (target instanceof Player t) {
            MagicProfile targetProfile = ProfileManager.getProfile(t, MagicProfile.class);
            targetResistance = targetProfile.getResistance(getElement());
        } else {
            targetResistance = Utils.getEntityResistance(getElement(), target);
        }

        return base * (1 + ((casterAffinity - targetResistance) / 100));
    }

    // ============================================
    // Getters
    // ============================================

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public Element getElement() {
        return element;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }

    public int getCost() {
        return cost;
    }

    public double getCastTime() {
        return castTime;
    }

    public int getMaxMastery() {
        return maxMastery;
    }

    public int getLevel() {
        return level;
    }

    public BarColor getMasteryBarColor() {
        return masteryBarColor;
    }

    public ItemStack getGuiItem() {
        return guiItem;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
