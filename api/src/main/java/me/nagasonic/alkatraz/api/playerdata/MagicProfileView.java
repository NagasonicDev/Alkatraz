package me.nagasonic.alkatraz.api.playerdata;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.spells.Spell;

import java.util.Collection;

public interface MagicProfileView {

    // Core stats
    int getCircleLevel();
    void setCircleLevel(int value);
    int getStatPoints();
    void setStatPoints(int value);
    int getResetTokens();
    void setResetTokens(int value);

    // Element points
    int getPoints(Element element);

    // Mana
    double getMaxMana();
    void setMaxMana(double value);
    double getMana();
    void setMana(double value);
    double getManaRegeneration();
    void setManaRegeneration(double value);
    double getExperience();
    void setExperience(double value);
    double getArcaneKnowledge();
    void setArcaneKnowledge(double value);
    int getResearchPoints();
    void setResearchPoints(int value);

    // Magic affinity/resistance
    double getMagicAffinity();
    void setMagicAffinity(double value);
    double getMagicResistance();
    void setMagicResistance(double value);

    // Element affinities
    double getAffinity(Element element);
    double getResistance(Element element);

    // Booleans
    boolean canCast();
    void setCanCast(boolean value);
    boolean isCasting();
    void setCasting(boolean value);
    boolean isStealth();
    void setStealth(boolean value);

    // Strings
    String getDisguise();
    void setDisguise(String value);
    String getCastMode();
    void setCastMode(String value);

    // Spell discovery
    boolean hasDiscoveredSpell(String spellType);
    Collection<String> getAllDiscoveredSpellTypes();

    // Research
    boolean hasCompletedResearch(String researchId);
    Collection<String> getCompletedResearchIds();
    boolean hasStartedResearch(String researchId);

    // Mastery
    int getSpellMastery(String spellId);
    void setSpellMastery(String spellId, int mastery);

    // Cooldowns
    Long getCooldown(String spellId);
    void setCooldown(String spellId, Long cooldown);
}
