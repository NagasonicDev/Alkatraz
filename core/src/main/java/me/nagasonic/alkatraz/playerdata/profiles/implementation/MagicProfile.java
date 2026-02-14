package me.nagasonic.alkatraz.playerdata.profiles.implementation;

import me.nagasonic.alkatraz.Alkatraz;
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

        // Strings
        stringStat("disguise");

        // String sets for spell data
        stringSetStat("discoveredSpells");  // Stores spell types that player has discovered
        stringSetStat("spellOptions");      // Stores spell option selections (format: "optionId:valueId")
        stringSetStat("spellTags");         // Stores active spell tags

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
    public void setExperience(double value) { setDouble("experience", value); }

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
            // Initialize mastery if not set
            if (getSpellMastery(spell) < 0) {
                setSpellMastery(spell, 0);
            }
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

    // ============================================
    // Spell Mastery Management (replaces PlayerData.spellMasteries map)
    // ============================================

    /**
     * Gets the mastery level for a spell (matches old PlayerData.getSpellMastery())
     * Returns -1 if spell mastery not set (same as old system)
     */
    public int getSpellMastery(Spell spell) {
        String key = "mastery_" + spell.getType().toLowerCase();
        try {
            if (isInt(key)) {
                return getInt(key);
            }
        } catch (IllegalArgumentException e) {
            // Stat doesn't exist yet
        }
        return -1;
    }

    /**
     * Sets the mastery level for a spell (matches old PlayerData.setSpellMastery())
     */
    public void setSpellMastery(Spell spell, int mastery) {
        String key = "mastery_" + spell.getType().toLowerCase();
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

    /**
     * Adds a spell tag
     */
    public void addSpellTag(String tag) {
        getStringSet("spellTags").add(tag);
    }

    /**
     * Removes a spell tag
     */
    public void removeSpellTag(String tag) {
        getStringSet("spellTags").remove(tag);
    }

    /**
     * Checks if player has a spell tag
     */
    public boolean hasSpellTag(String tag) {
        return getStringSet("spellTags").contains(tag);
    }

    /**
     * Gets all active spell tags
     */
    public Collection<String> getAllSpellTags() {
        return new HashSet<>(getStringSet("spellTags"));
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
    // Non-Persistent Values
    // ============================================

    protected Map<Spell, BossBar> masteryBars = new HashMap<>();
    protected BossBar expBar;

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
}
