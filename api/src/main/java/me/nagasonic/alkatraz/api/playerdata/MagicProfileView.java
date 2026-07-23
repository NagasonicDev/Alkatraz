package me.nagasonic.alkatraz.api.playerdata;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.spells.Spell;

import java.util.Collection;

/**
 * Interface providing read and write access to a player's magic profile,
 * including circle level, mana, element affinities, spell data, and research progress.
 */
public interface MagicProfileView {

    /**
     * Returns the player's current Circle level.
     *
     * @return the Circle level as an integer (1-9)
     */
    int getCircleLevel();

    /**
     * Sets the player's Circle level.
     *
     * @param value the new Circle level
     */
    void setCircleLevel(int value);

    /**
     * Returns the number of unspent stat points the player has.
     *
     * @return the stat point count
     */
    int getStatPoints();

    /**
     * Sets the number of unspent stat points.
     *
     * @param value the new stat point count
     */
    void setStatPoints(int value);

    /**
     * Returns the number of reset tokens the player possesses.
     *
     * @return the reset token count
     */
    int getResetTokens();

    /**
     * Sets the number of reset tokens.
     *
     * @param value the new reset token count
     */
    void setResetTokens(int value);

    /**
     * Returns the number of stat points allocated to the given element.
     *
     * @param element the element to query
     * @return the allocated point count
     */
    int getPoints(Element element);

    /**
     * Returns the player's maximum mana pool.
     *
     * @return the max mana value
     */
    double getMaxMana();

    /**
     * Sets the player's maximum mana pool.
     *
     * @param value the new max mana value
     */
    void setMaxMana(double value);

    /**
     * Returns the player's current mana.
     *
     * @return the current mana value
     */
    double getMana();

    /**
     * Sets the player's current mana.
     *
     * @param value the new current mana value
     */
    void setMana(double value);

    /**
     * Returns the player's mana regeneration rate.
     *
     * @return the mana regen value
     */
    double getManaRegeneration();

    /**
     * Sets the player's mana regeneration rate.
     *
     * @param value the new mana regen value
     */
    void setManaRegeneration(double value);

    /**
     * Returns the player's experience points.
     *
     * @return the experience value
     */
    double getExperience();

    /**
     * Sets the player's experience points.
     *
     * @param value the new experience value
     */
    void setExperience(double value);

    /**
     * Returns the player's arcane knowledge level.
     *
     * @return the arcane knowledge value
     */
    double getArcaneKnowledge();

    /**
     * Sets the player's arcane knowledge level.
     *
     * @param value the new arcane knowledge value
     */
    void setArcaneKnowledge(double value);

    /**
     * Returns the number of research points the player has.
     *
     * @return the research point count
     */
    int getResearchPoints();

    /**
     * Sets the number of research points.
     *
     * @param value the new research point count
     */
    void setResearchPoints(int value);

    /**
     * Returns the player's magic affinity (spell power multiplier).
     *
     * @return the magic affinity value
     */
    double getMagicAffinity();

    /**
     * Sets the player's magic affinity.
     *
     * @param value the new magic affinity value
     */
    void setMagicAffinity(double value);

    /**
     * Returns the player's magic resistance (spell damage reduction).
     *
     * @return the magic resistance value
     */
    double getMagicResistance();

    /**
     * Sets the player's magic resistance.
     *
     * @param value the new magic resistance value
     */
    void setMagicResistance(double value);

    /**
     * Returns the player's affinity for the given element.
     *
     * @param element the element to query
     * @return the affinity value
     */
    double getAffinity(Element element);

    /**
     * Returns the player's resistance to the given element.
     *
     * @param element the element to query
     * @return the resistance value
     */
    double getResistance(Element element);

    /**
     * Returns whether the player is currently able to cast spells.
     *
     * @return {@code true} if casting is permitted
     */
    boolean canCast();

    /**
     * Sets whether the player is currently able to cast spells.
     *
     * @param value {@code true} to allow casting
     */
    void setCanCast(boolean value);

    /**
     * Returns whether the player is in the middle of casting a spell.
     *
     * @return {@code true} if casting
     */
    boolean isCasting();

    /**
     * Sets whether the player is currently casting a spell.
     *
     * @param value {@code true} if casting
     */
    void setCasting(boolean value);

    /**
     * Returns whether the player is in stealth mode.
     *
     * @return {@code true} if stealthed
     */
    boolean isStealth();

    /**
     * Sets the player's stealth state.
     *
     * @param value {@code true} to enable stealth
     */
    void setStealth(boolean value);

    /**
     * Returns the name of the player's current disguise, or {@code null} if none.
     *
     * @return the disguise identifier
     */
    String getDisguise();

    /**
     * Sets the player's current disguise.
     *
     * @param value the disguise identifier, or {@code null} to clear
     */
    void setDisguise(String value);

    /**
     * Returns the player's current cast mode identifier.
     *
     * @return the cast mode string
     */
    String getCastMode();

    /**
     * Sets the player's current cast mode.
     *
     * @param value the cast mode identifier
     */
    void setCastMode(String value);

    /**
     * Returns whether the player has discovered the given spell type.
     *
     * @param spellType the spell type identifier
     * @return {@code true} if discovered
     */
    boolean hasDiscoveredSpell(String spellType);

    /**
     * Returns all spell type identifiers the player has discovered.
     *
     * @return collection of discovered spell type IDs
     */
    Collection<String> getAllDiscoveredSpellTypes();

    /**
     * Returns whether the player has completed the given research.
     *
     * @param researchId the research identifier
     * @return {@code true} if completed
     */
    boolean hasCompletedResearch(String researchId);

    /**
     * Returns all research IDs the player has completed.
     *
     * @return collection of completed research IDs
     */
    Collection<String> getCompletedResearchIds();

    /**
     * Returns whether the player has started the given research.
     *
     * @param researchId the research identifier
     * @return {@code true} if started
     */
    boolean hasStartedResearch(String researchId);

    /**
     * Returns the mastery level for the given spell.
     *
     * @param spellId the spell identifier
     * @return the mastery level
     */
    int getSpellMastery(String spellId);

    /**
     * Sets the mastery level for the given spell.
     *
     * @param spellId the spell identifier
     * @param mastery the new mastery level
     */
    void setSpellMastery(String spellId, int mastery);

    /**
     * Returns the cooldown timestamp for the given spell, or {@code null} if none.
     *
     * @param spellId the spell identifier
     * @return the cooldown end timestamp in milliseconds
     */
    Long getCooldown(String spellId);

    /**
     * Sets the cooldown timestamp for the given spell.
     *
     * @param spellId the spell identifier
     * @param cooldown the cooldown end timestamp in milliseconds, or {@code null} to clear
     */
    void setCooldown(String spellId, Long cooldown);
}
