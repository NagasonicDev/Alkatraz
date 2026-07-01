package me.nagasonic.alkatraz.playerdata.profiles.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.playerdata.SpellHotbarManager;
import me.nagasonic.alkatraz.playerdata.profiles.Profile;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.StatUtils;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;

import java.util.*;

public class MagicProfile extends Profile {

    private transient List<SpellModifier> spellModifiers;
    private transient Map<SpellModifier, String> spellModifierTypes;

    {
        // Core stats
        intStat("circleLevel", 0);
        intStat("statPoints", 0);
        intStat("resetTokens", 0);

        // Element points
        intStat("firePoints", 0);
        intStat("waterPoints", 0);
        intStat("airPoints", 0);
        intStat("earthPoints", 0);
        intStat("lightPoints", 0);
        intStat("darkPoints", 0);

        // Mana stats
        doubleStat("maxMana", 100);
        doubleStat("mana", 100);
        doubleStat("manaRegeneration", 1);
        doubleStat("experience", 0);
        doubleStat("arcaneKnowledge", 0);
        intStat("researchPoints", 0);

        // Magic affinity/resistance
        doubleStat("magicAffinity", 0);
        doubleStat("magicResistance", 0);

        // Element affinity
        doubleStat("fireAffinity", 0);
        doubleStat("airAffinity", 0);
        doubleStat("waterAffinity", 0);
        doubleStat("earthAffinity", 0);
        doubleStat("lightAffinity", 0);
        doubleStat("darkAffinity", 0);

        // Element resistance
        doubleStat("fireResistance", 0);
        doubleStat("airResistance", 0);
        doubleStat("waterResistance", 0);
        doubleStat("earthResistance", 0);
        doubleStat("lightResistance", 0);
        doubleStat("darkResistance", 0);

        // Booleans
        boolStat("casting", false);
        boolStat("stealth", false);
        boolStat("canCast", true);
        boolStat("tutorialSeen", false);

        // Strings
        stringStat("disguise");
        stringStat("castMode", "code");

        // String sets for spell data
        stringSetStat("discoveredSpells");  // Stores spell types that player has discovered
        stringSetStat("spellOptions");      // Stores spell option selections (format: "optionId:valueId")
        stringSetStat("spellTags");         // Stores active spell tags
        stringSetStat("researchProgress");  // Adapter storage for configurable research requirements
        stringSetStat("researchStarted");
        stringSetStat("researchCompleted");
        stringSetStat("researchObjectiveProgress");
        stringSetStat("researchRewardsApplied");

        // Note: Spell masteries are stored as dynamic int stats (see getSpellMastery/setSpellMastery)
    }

    public MagicProfile(UUID owner) {
        super(owner);
        // Initialize record-based approach
        this.spellModifiers = new ArrayList<>();
        this.spellModifierTypes = new HashMap<>();
        addManaPerSecond();
        // Map-based approach initialized inline above
    }

    // ============================================
    // Core Stats Getters/Setters
    // ============================================

    public int getCircleLevel() { return getInt("circleLevel"); }
    public void setCircleLevel(int value) { setInt("circleLevel", value); }

    public int getStatPoints() { return getInt("statPoints"); }
    public void setStatPoints(int value) { setInt("statPoints", value); }

    public int getResetTokens() { return getInt("resetTokens"); }
    public void setResetTokens(int value) { setInt("resetTokens", value); }

    // ============================================
    // Element Points Getters/Setters
    // ============================================

    public int getFirePoints() { return getInt("firePoints"); }
    public void setFirePoints(int value) { setInt("firePoints", value); }

    public int getWaterPoints() { return getInt("waterPoints"); }
    public void setWaterPoints(int value) { setInt("waterPoints", value); }

    public int getAirPoints() { return getInt("airPoints"); }
    public void setAirPoints(int value) { setInt("airPoints", value); }

    public int getEarthPoints() { return getInt("earthPoints"); }
    public void setEarthPoints(int value) { setInt("earthPoints", value); }

    public int getLightPoints() { return getInt("lightPoints"); }
    public void setLightPoints(int value) { setInt("lightPoints", value); }

    public int getDarkPoints() { return getInt("darkPoints"); }
    public void setDarkPoints(int value) { setInt("darkPoints", value); }

    /**
     * Gets points invested in a specific element (matches old PlayerData.getPoints())
     */
    public int getPoints(Element element) {
        if (element == null || element == Element.NONE) return 0;

        return switch (element) {
            case FIRE -> getFirePoints();
            case WATER -> getWaterPoints();
            case AIR -> getAirPoints();
            case EARTH -> getEarthPoints();
            case LIGHT -> getLightPoints();
            case DARK -> getDarkPoints();
            default -> 0;
        };
    }

    // ============================================
    // Mana Stats Getters/Setters
    // ============================================

    public double getMaxMana() { return getDouble("maxMana"); }
    public void setMaxMana(double value) { setDouble("maxMana", value); }

    public double getMana() { return getDouble("mana"); }
    public void setMana(double value) { setDouble("mana", value); }

    public double getManaRegeneration() { return getDouble("manaRegeneration"); }
    public void setManaRegeneration(double value) { setDouble("manaRegeneration", value); }

    public double getExperience() { return getDouble("experience"); }
    public void setExperience(double value) {
        setDouble("experience", value);
        setArcaneKnowledge(value);
    }

    public double getArcaneKnowledge() {
        double arcaneKnowledge = getDouble("arcaneKnowledge");
        double legacyExperience = isDouble("experience") ? getDouble("experience") : 0;
        return Math.max(arcaneKnowledge, legacyExperience);
    }
    public void setArcaneKnowledge(double value) {
        setDouble("arcaneKnowledge", value);
        if (isDouble("experience")) {
            setDouble("experience", value);
        }
    }

    public int getResearchPoints() { return getInt("researchPoints"); }
    public void setResearchPoints(int value) { setInt("researchPoints", value); }
    public void addResearchPoints(int amount) { setResearchPoints(Math.max(0, getResearchPoints() + amount)); }

    // ============================================
    // Magic Affinity/Resistance Getters/Setters
    // ============================================

    public double getMagicAffinity() { return getDouble("magicAffinity"); }
    public void setMagicAffinity(double value) { setDouble("magicAffinity", value); }

    public double getMagicResistance() { return getDouble("magicResistance"); }
    public void setMagicResistance(double value) { setDouble("magicResistance", value); }

    // ============================================
    // Element Affinity Getters/Setters
    // ============================================

    public double getFireAffinity() { return getDouble("fireAffinity"); }
    public void setFireAffinity(double value) { setDouble("fireAffinity", value); }

    public double getAirAffinity() { return getDouble("airAffinity"); }
    public void setAirAffinity(double value) { setDouble("airAffinity", value); }

    public double getWaterAffinity() { return getDouble("waterAffinity"); }
    public void setWaterAffinity(double value) { setDouble("waterAffinity", value); }

    public double getEarthAffinity() { return getDouble("earthAffinity"); }
    public void setEarthAffinity(double value) { setDouble("earthAffinity", value); }

    public double getLightAffinity() { return getDouble("lightAffinity"); }
    public void setLightAffinity(double value) { setDouble("lightAffinity", value); }

    public double getDarkAffinity() { return getDouble("darkAffinity"); }
    public void setDarkAffinity(double value) { setDouble("darkAffinity", value); }

    // ============================================
    // Element Resistance Getters/Setters
    // ============================================

    public double getFireResistance() { return getDouble("fireResistance"); }
    public void setFireResistance(double value) { setDouble("fireResistance", value); }

    public double getAirResistance() { return getDouble("airResistance"); }
    public void setAirResistance(double value) { setDouble("airResistance", value); }

    public double getWaterResistance() { return getDouble("waterResistance"); }
    public void setWaterResistance(double value) { setDouble("waterResistance", value); }

    public double getEarthResistance() { return getDouble("earthResistance"); }
    public void setEarthResistance(double value) { setDouble("earthResistance", value); }

    public double getLightResistance() { return getDouble("lightResistance"); }
    public void setLightResistance(double value) { setDouble("lightResistance", value); }

    public double getDarkResistance() { return getDouble("darkResistance"); }
    public void setDarkResistance(double value) { setDouble("darkResistance", value); }

    // ============================================
    // String Stat Getters/Setters
    // ============================================

    public String getDisguise() { return getString("disguise"); }
    public void setDisguise(String value) { setString("disguise", value); }

    public String getCastMode() { return getString("castMode"); }
    public void setCastMode(String value) {
        if (value.equals("code") || value.equals("hotbar")) {
            setString("castMode", value);
        }
    }

    /**
     * Gets affinity for a specific element (matches old PlayerData.getAffinity())
     */
    public double getAffinity(Element element) {
        if (element == null || element == Element.NONE) return getMagicAffinity();

        return switch (element) {
            case FIRE -> getFireAffinity();
            case WATER -> getWaterAffinity();
            case AIR -> getAirAffinity();
            case EARTH -> getEarthAffinity();
            case LIGHT -> getLightAffinity();
            case DARK -> getDarkAffinity();
            default -> getMagicAffinity();
        };
    }

    /**
     * Gets resistance for a specific element (matches old PlayerData.getResistance())
     */
    public double getResistance(Element element) {
        if (element == null || element == Element.NONE) return getMagicResistance();

        return switch (element) {
            case FIRE -> getFireResistance();
            case WATER -> getWaterResistance();
            case AIR -> getAirResistance();
            case EARTH -> getEarthResistance();
            case LIGHT -> getLightResistance();
            case DARK -> getDarkResistance();
            default -> getMagicResistance();
        };
    }

    // ============================================
    // Boolean Stats Getters/Setters
    // ============================================

    public boolean canCast() { return getBool("canCast"); }
    public void setCanCast(boolean value) { setBool("canCast", value); }

    public boolean isCasting() { return getBool("casting"); }
    public void setCasting(boolean value) { setBool("casting", value); }

    public boolean isStealth() { return getBool("stealth"); }
    public void setStealth(boolean value) { setBool("stealth", value); }

    // ============================================
    // Spell Discovery Management (replaces PlayerData.discoveredSpells)
    // ============================================

    /**
     * Checks if player has discovered a spell (matches old PlayerData.hasDiscovered())
     */
    public boolean hasDiscoveredSpell(Spell spell) {
        return getStringSet("discoveredSpells").contains(spell.getType().toLowerCase());
    }

    /**
     * Marks a spell as discovered (matches old PlayerData.setDiscovered())
     */
    public void setDiscoveredSpell(Spell spell, boolean discovered) {
        if (discovered) {
            getStringSet("discoveredSpells").add(spell.getType().toLowerCase());
        } else {
            getStringSet("discoveredSpells").remove(spell.getType().toLowerCase());
        }
    }

    /**
     * Gets all discovered spell types
     */
    public Collection<String> getAllDiscoveredSpellTypes() {
        return new HashSet<>(getStringSet("discoveredSpells"));
    }

    public boolean hasResearch(String researchId) {
        return hasCompletedResearch(researchId);
    }

    public void setResearch(String researchId, boolean completed) {
        setResearchCompleted(researchId, completed);
    }

    public boolean hasStartedResearch(String researchId) {
        return researchId != null && getStringSet("researchStarted").contains(researchId.toLowerCase());
    }

    public boolean hasCompletedResearch(String researchId) {
        if (researchId == null) return false;
        String key = researchId.toLowerCase();
        return getStringSet("researchCompleted").contains(key) || getStringSet("researchProgress").contains(key);
    }

    public Collection<String> getCompletedResearchIds() {
        Set<String> completed = new HashSet<>();
        completed.addAll(getStringSet("researchCompleted"));
        completed.addAll(getStringSet("researchProgress"));
        return completed;
    }

    public void setResearchStarted(String researchId, boolean started) {
        if (researchId == null || researchId.isBlank()) return;
        String key = researchId.toLowerCase();
        if (started && !hasCompletedResearch(key)) {
            getStringSet("researchStarted").add(key);
        } else {
            getStringSet("researchStarted").remove(key);
        }
    }

    public void setResearchCompleted(String researchId, boolean completed) {
        if (researchId == null || researchId.isBlank()) return;
        String key = researchId.toLowerCase();
        if (completed) {
            getStringSet("researchCompleted").add(key);
            getStringSet("researchProgress").add(key);
            getStringSet("researchStarted").remove(key);
        } else {
            getStringSet("researchCompleted").remove(key);
            getStringSet("researchProgress").remove(key);
        }
    }

    public int getResearchObjectiveProgress(String researchId, String objectiveId) {
        String prefix = researchProgressPrefix(researchId, objectiveId);
        for (String entry : getStringSet("researchObjectiveProgress")) {
            if (entry.startsWith(prefix)) {
                try {
                    return Integer.parseInt(entry.substring(prefix.length()));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    public void setResearchObjectiveProgress(String researchId, String objectiveId, int progress) {
        if (researchId == null || objectiveId == null) return;
        String prefix = researchProgressPrefix(researchId, objectiveId);
        getStringSet("researchObjectiveProgress").removeIf(entry -> entry.startsWith(prefix));
        getStringSet("researchObjectiveProgress").add(prefix + Math.max(0, progress));
    }

    public int addResearchObjectiveProgress(String researchId, String objectiveId, int amount, int max) {
        int next = Math.min(max, getResearchObjectiveProgress(researchId, objectiveId) + Math.max(0, amount));
        setResearchObjectiveProgress(researchId, objectiveId, next);
        return next;
    }

    public boolean hasAppliedResearchRewards(String researchId) {
        return researchId != null && getStringSet("researchRewardsApplied").contains(researchId.toLowerCase());
    }

    public void setResearchRewardsApplied(String researchId, boolean applied) {
        if (researchId == null || researchId.isBlank()) return;
        String key = researchId.toLowerCase();
        if (applied) {
            getStringSet("researchRewardsApplied").add(key);
        } else {
            getStringSet("researchRewardsApplied").remove(key);
        }
    }

    public boolean addMagicStat(String stat, double amount, String operation) {
        if (stat == null || stat.isBlank()) return false;
        String mode = operation == null ? "add" : operation.toLowerCase();
        if (isInt(stat)) {
            int value = getInt(stat);
            setInt(stat, (int) Math.round(applyRewardOperation(value, amount, mode)));
            return true;
        }
        if (isDouble(stat)) {
            setDouble(stat, applyRewardOperation(getDouble(stat), amount, mode));
            return true;
        }
        if (isFloat(stat)) {
            setFloat(stat, (float) applyRewardOperation(getFloat(stat), amount, mode));
            return true;
        }
        if (isLong(stat)) {
            setLong(stat, Math.round(applyRewardOperation(getLong(stat), amount, mode)));
            return true;
        }
        return false;
    }

    private String researchProgressPrefix(String researchId, String objectiveId) {
        return researchId.toLowerCase() + ":" + objectiveId.toLowerCase() + "=";
    }

    private double applyRewardOperation(double current, double amount, String operation) {
        return switch (operation) {
            case "set" -> amount;
            case "multiply" -> current * amount;
            default -> current + amount;
        };
    }

    // ============================================
    // Spell Cooldown Management
    // ============================================

    public Long getCooldown(Spell spell) {
        String id = spell.getId() + "_cooldown";
        if (longs.containsKey(id)) { return getLong(id); }
        return null;
    }

    public void setCooldown(Spell spell, Long cooldown) {
        String id = spell.getId() + "_cooldown";
        if (!longs.containsKey(id)) {
            longStat(id, cooldown);
        }else{
            setLong(id, cooldown);
        }
    }


    // ============================================
    // Spell Mastery Management
    // ============================================

    /**
     * Gets the mastery level for a spell (matches old PlayerData.getSpellMastery())
     * Returns 0 if spell mastery not set.
     */
    public int getSpellMastery(Spell spell) {
        String key = "mastery_" + spell.getId();
        try {
            if (isInt(key)) {
                return getInt(key);
            }
        } catch (IllegalArgumentException e) {
            // Stat doesn't exist yet
        }
        return 0;
    }

    /**
     * Sets the mastery level for a spell (matches old PlayerData.setSpellMastery())
     */
    public void setSpellMastery(Spell spell, int mastery) {
        String key = "mastery_" + spell.getId();
        // Dynamically create the stat if it doesn't exist
        if (!isInt(key)) {
            intStat(key, mastery);
        } else {
            setInt(key, mastery);
        }
    }

    // ============================================
    // Spell Option Management (persisted)
    // ============================================

    /**
     * Sets the selected value for a spell option
     */
    public void setSpellOption(String optionId, String valueId) {
        getStringSet("spellOptions").removeIf(s -> s.startsWith(optionId + ":"));
        getStringSet("spellOptions").add(optionId + ":" + valueId);
    }

    /**
     * Gets the selected value ID for a spell option
     */
    public String getSpellOption(String optionId) {
        return getStringSet("spellOptions").stream()
                .filter(s -> s.startsWith(optionId + ":"))
                .map(s -> s.substring(optionId.length() + 1))
                .findFirst()
                .orElse(null);
    }

    /**
     * Sets a spell modifier using the SpellModifier record
     */
    public void setSpellModifier(SpellModifier mod) {
        setSpellModifier(mod.spell(), mod.id(), mod);
    }

    /**
     * Sets a spell modifier for a specific spell and id
     */
    public void setSpellModifier(Spell spell, String id, SpellModifier modifier) {
        spellModifiers.removeIf(s -> s.id().equals(id) && s.spell().equals(spell));
        spellModifiers.add(modifier);
    }

    /**
     * Sets the type of a spell modifier
     */
    public void setSpellModifierType(SpellModifier mod, String type) {
        spellModifierTypes.put(mod, type);
    }

    /**
     * Gets a spell modifier by spell and id
     */
    public List<SpellModifier> getSpellModifiers(Spell spell, String id) {
        List<SpellModifier> mods = new ArrayList<>();
        for (SpellModifier mod : spellModifiers) {
            if (Objects.equals(mod.getCombinedId(), spell.getId() + "_" + id)) {
                mods.add(mod);
            }
        }
        return mods;
    }

    /**
     * Gets the type string for a spell modifier
     */
    public String getSpellModifierType(SpellModifier mod) {
        return spellModifierTypes.get(mod);
    }

    /**
     * Removes a spell modifier
     */
    public void removeSpellModifier(SpellModifier modifier) {
        spellModifiers.remove(modifier);
        spellModifierTypes.remove(modifier);
    }

    /**
     * Checks if a spell modifier exists
     */
    public boolean hasSpellModifier(Spell spell, String id) {
        return getSpellModifiers(spell, id) != null;
    }

    /**
     * Gets all spell modifiers
     */
    public List<SpellModifier> getAllSpellModifiers() {
        return new ArrayList<>(spellModifiers);
    }

    /**
     * Returns a sum of all values of spell modifiers
     */
    public double sumSpellModifiers(Spell spell, String id) {
        double sum = 0;
        for (SpellModifier mod : getSpellModifiers(spell, id)) {
            sum += mod.value();
        }
        return sum;
    }

    /**
     * Clears all spell modifiers (useful for recalculation on login)
     */
    public void clearAllSpellModifiers() {
        // Clear both approaches
        spellModifiers.clear();
        spellModifierTypes.clear();
    }

    // ============================================
    // Spell Tag Management (persisted)
    // ============================================

    private String spellTagsKey(Spell spell) {
        return spell.getId() + ".tags";
    }

    private void ensureSpellTagsSet(Spell spell) {
        String key = spellTagsKey(spell);
        if (!isStringSet(key)) {
            stringSetStat(key);
        }
    }

    /**
     * Adds a spell tag
     */
    public void addSpellTag(Spell spell, String tag) {
        ensureSpellTagsSet(spell);
        getStringSet(spellTagsKey(spell)).add(tag);
    }

    /**
     * Removes a spell tag
     */
    public void removeSpellTag(Spell spell, String tag) {
        if (!isStringSet(spellTagsKey(spell))) return;
        getStringSet(spellTagsKey(spell)).remove(tag);
    }

    /**
     * Checks if player has a spell tag
     */
    public boolean hasSpellTag(Spell spell, String tag) {
        if (!isStringSet(spellTagsKey(spell))) return false;
        return getStringSet(spellTagsKey(spell)).contains(tag);
    }

    /**
     * Gets all active spell tags
     */
    public Collection<String> getAllSpellTags(Spell spell) {
        if (!isStringSet(spellTagsKey(spell))) return Set.of();
        return new HashSet<>(getStringSet(spellTagsKey(spell)));
    }

    @Override
    public Profile getBlankProfile(UUID owner) {
        return new MagicProfile(owner);
    }

    // ============================================
    // SpellModifier Record
    // ============================================

    /**
     * Record for storing spell modifier data
     */
    public record SpellModifier(Spell spell, String id, double value) {

        /**
         * Gets a combined ID for this modifier (spell_id + modifier_id)
         */
        public String getCombinedId() {
            return spell.getId() + "_" + id;
        }
    }

    public void addManaPerSecond(){
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (getMana() < getMaxMana()){
                StatUtils.addMana(Bukkit.getPlayer(getOwner()), getManaRegeneration());
            }
        }, 0L, 20L);
    }

    // ============================================
    // Spell Hotbar stuff
    // ============================================

    private Map<Integer, String> hotbarSpellIds = new HashMap<>();

    public Map<Integer, String> getHotbarSpellIds() {
        return hotbarSpellIds;
    }

    public void setHotbarSpell(int slotIndex, String spellId) {
        if (slotIndex < 0 || slotIndex >= SpellHotbarManager.SPELL_SLOT_COUNT) return;
        hotbarSpellIds.put(slotIndex, spellId);
    }

    public void removeHotbarSpell(String spellId) {
        hotbarSpellIds.entrySet().removeIf(entry -> spellId.equals(entry.getValue()));
    }


    // ============================================
    // Non-Persistent Values
    // ============================================

    protected Map<Spell, BossBar> masteryBars = new HashMap<>();
    protected BossBar expBar = null;
    protected BossBar arcaneKnowledgeBar = null;
    protected int arcaneKnowledgeBarTaskId = -1;
    protected Map<Spell, Integer> masteryBarTaskIds = new HashMap<>();

    public Map<Spell, BossBar> getMasteryBars() {
        return masteryBars;
    }

    public void setMasteryBars(Map<Spell, BossBar> masteryBars) {
        this.masteryBars = masteryBars;
    }

    public BossBar getExpBar() {
        return expBar;
    }

    public void setExpBar(BossBar expBar) {
        this.expBar = expBar;
    }

    public BossBar getArcaneKnowledgeBar() {
        return arcaneKnowledgeBar != null ? arcaneKnowledgeBar : expBar;
    }

    public void setArcaneKnowledgeBar(BossBar arcaneKnowledgeBar) {
        this.arcaneKnowledgeBar = arcaneKnowledgeBar;
        this.expBar = arcaneKnowledgeBar;
    }

    public int getArcaneKnowledgeBarTaskId() {
        return arcaneKnowledgeBarTaskId;
    }

    public void setArcaneKnowledgeBarTaskId(int taskId) {
        this.arcaneKnowledgeBarTaskId = taskId;
    }

    public Map<Spell, Integer> getMasteryBarTaskIds() {
        return masteryBarTaskIds;
    }

    public void setMasteryBarTaskIds(Map<Spell, Integer> masteryBarTaskIds) {
        this.masteryBarTaskIds = masteryBarTaskIds;
    }
}
