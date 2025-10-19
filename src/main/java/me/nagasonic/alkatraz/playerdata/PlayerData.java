package me.nagasonic.alkatraz.playerdata;

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
    private Map<Spell, Integer> spellMasteries = new HashMap<>();
    private List<Spell> discoveredSpells = new ArrayList<>();
    private Map<Spell, BossBar> masteryBars = new HashMap<>();
    private BossBar expBar = null;
    private double mana;
    private double maxMana;
    private int circle;
    private double experience;
    private double magicDamage;
    private double magicResistance;

    private double fireAffinity;
    private double fireResistance;

    private double airAffinity;
    private double airResistance;

    private double earthAffinity;
    private double earthResistance;

    private double waterAffinity;
    private double waterResistance;

    private double lightAffinity;
    private double lightResistance;

    private double darkAffinity;
    private double darkResistance;

    private boolean isCasting;

    public double getMana() {
        return mana;
    }

    public void setMana(double mana) {
        this.mana = mana;
    }

    public double getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(double maxMana) {
        this.maxMana = maxMana;
    }

    public int getCircle() {
        return circle;
    }

    public double getExperience() {
        return experience;
    }

    public void setCircle(int circle) {
        this.circle = circle;
    }

    public double getMagicDamage() {
        return magicDamage;
    }

    public void setMagicDamage(double magicDamage) {
        this.magicDamage = magicDamage;
    }

    public double getMagicResistance() {
        return magicResistance;
    }

    public void setMagicResistance(double magicResistance) {
        this.magicResistance = magicResistance;
    }

    public double getFireAffinity() {
        return fireAffinity;
    }

    public void setFireAffinity(double fireAffinity) {
        this.fireAffinity = fireAffinity;
    }

    public double getFireResistance() {
        return fireResistance;
    }

    public void setFireResistance(double fireResistance) {
        this.fireResistance = fireResistance;
    }

    public double getAirAffinity() {
        return airAffinity;
    }

    public void setAirAffinity(double airAffinity) {
        this.airAffinity = airAffinity;
    }

    public double getAirResistance() {
        return airResistance;
    }

    public void setAirResistance(double airResistance) {
        this.airResistance = airResistance;
    }

    public double getEarthAffinity() {
        return earthAffinity;
    }

    public void setEarthAffinity(double earthAffinity) {
        this.earthAffinity = earthAffinity;
    }

    public double getEarthResistance() {
        return earthResistance;
    }

    public void setEarthResistance(double earthResistance) {
        this.earthResistance = earthResistance;
    }

    public double getWaterAffinity() {
        return waterAffinity;
    }

    public void setWaterAffinity(double waterAffinity) {
        this.waterAffinity = waterAffinity;
    }

    public double getWaterResistance() {
        return waterResistance;
    }

    public void setWaterResistance(double waterResistance) {
        this.waterResistance = waterResistance;
    }

    public double getLightAffinity() {
        return lightAffinity;
    }

    public void setLightAffinity(double lightAffinity) {
        this.lightAffinity = lightAffinity;
    }

    public double getLightResistance() {
        return lightResistance;
    }

    public void setLightResistance(double lightResistance) {
        this.lightResistance = lightResistance;
    }

    public double getDarkAffinity() {
        return darkAffinity;
    }

    public void setDarkAffinity(double darkAffinity) {
        this.darkAffinity = darkAffinity;
    }

    public double getDarkResistance() {
        return darkResistance;
    }

    public void setDarkResistance(double darkResistance) {
        this.darkResistance = darkResistance;
    }

    public boolean isCasting() {
        return isCasting;
    }

    public void setCasting(boolean casting) {
        isCasting = casting;
    }

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

    public void setExperience(double experience) {
        this.experience = experience;
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
}
