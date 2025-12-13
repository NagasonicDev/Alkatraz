package me.nagasonic.alkatraz.playerdata;

import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.boss.BossBar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerData {
    // Max Mana Formula = Math.round(100 + (1500 / 9) * circle)
    // Spell XP Gain Formula: XPgain(C) = Math.round(2 * 1.9^(circle - 1))
    // r = (800 / 150)^1/8
    // Target Casts between circles: casts(C) = Math.round(150 * r^(circle - 1))
    // Circle XP Gap (XP from circle - 1 to circle) XP(C) = casts(c) * XPgain(C)
    private Map<String, Double> doubleStats = new HashMap<>();
    private Map<String, Integer> intStats = new HashMap<>();
    private Map<String, Boolean> boolStats = new HashMap<>();

    public Double getDouble(String key){
        return doubleStats.get(key);
    }

    public void setDouble(String key, Double value){
        doubleStats.put(key, value);
    }

    public Integer getInt(String key){
        return intStats.get(key);
    }

    public void setInt(String key, Integer value){
        intStats.put(key, value);
    }

    public Boolean getBoolean(String key){
        return boolStats.get(key);
    }

    public void setBoolean(String key, Boolean value){
        boolStats.put(key, value);
    }

    private Map<Spell, Integer> spellMasteries = new HashMap<>();
    private List<Spell> discoveredSpells = new ArrayList<>();
    private Map<Spell, BossBar> masteryBars = new HashMap<>();
    private BossBar expBar = null;

    public int getSpellMastery(Spell spell){
        return spellMasteries.getOrDefault(spell, -1);
    }

    public void setSpellMastery(Spell spell, int mastery){
        if (spellMasteries.containsKey(spell)){
            spellMasteries.replace(spell, mastery);
        }else{
            spellMasteries.put(spell, mastery);
        }
    }

    public Map<Spell, Integer> getAllSpellMasteries() {
        return spellMasteries;
    }

    public boolean hasDiscovered(Spell spell){
        return discoveredSpells.contains(spell);
    }

    public void setDiscovered(Spell spell, boolean discovered){
        if (discovered){
            if (!discoveredSpells.contains(spell)){
                discoveredSpells.add(spell);
                if (getSpellMastery(spell) < 0){
                    setSpellMastery(spell, 0);
                }
            }
        }else{
            discoveredSpells.remove(spell);
        }
    }

    public List<Spell> getAllDiscoveredSpells() {
        return discoveredSpells;
    }

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

    public double getAffinity(Element element){
        return switch (element){
            case WATER -> getDouble("water_affinity");
            case AIR -> getDouble("air_affinity");
            case DARK -> getDouble("dark_affinity");
            case FIRE -> getDouble("fire_affinity");
            case EARTH -> getDouble("earth_affinity");
            case LIGHT -> getDouble("light_affinity");
            case NONE -> getDouble("magic_affinity");
        };
    }

    public double getResistance(Element element){
        return switch (element){
            case WATER -> getDouble("water_resistance");
            case AIR -> getDouble("air_resistance");
            case DARK -> getDouble("dark_resistance");
            case FIRE -> getDouble("fire_resistance");
            case EARTH -> getDouble("earth_resistance");
            case LIGHT -> getDouble("light_resistance");
            case NONE -> getDouble("magic_resistance");
        };
    }

    public int getPoints(Element element){
        return getInt(element.getName().toLowerCase() + "_points");
    }
}
