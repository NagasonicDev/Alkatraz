package me.nagasonic.alkatraz.api;

import me.nagasonic.alkatraz.api.spells.Spell;
import java.util.Set;

/**
 * Read-only view of a player's magical stats including mana, circle level,
 * elemental affinities, arcane knowledge, and spell mastery.
 */
public interface PlayerStats {

    /**
     * Returns the player's current mana pool.
     *
     * @return current mana value
     */
    double getMana();

    /**
     * Returns the player's maximum mana pool.
     *
     * @return maximum mana value
     */
    double getMaxMana();

    /**
     * Returns the player's mana regeneration rate.
     *
     * @return mana regenerated per tick
     */
    double getManaRegeneration();

    /**
     * Returns the player's magic circle level, which determines which spells they can cast.
     *
     * @return the circle level
     */
    int getCircleLevel();

    /**
     * Returns the number of unspent stat points available for allocation.
     *
     * @return available stat points
     */
    int getStatPoints();

    /**
     * Returns the number of stat points allocated to the given element.
     *
     * @param element the element to query
     * @return the number of allocated points
     */
    int getPoints(Element element);

    /**
     * Returns the player's affinity (spell power multiplier) for the given element.
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
     * Returns the player's arcane knowledge level, used for advanced spell interactions.
     *
     * @return the arcane knowledge value
     */
    double getArcaneKnowledge();

    /**
     * Returns the number of unspent research points available for spell research.
     *
     * @return available research points
     */
    int getResearchPoints();

    /**
     * Checks whether the player has discovered the given spell.
     *
     * @param spellId the unique identifier of the spell
     * @return {@code true} if the spell has been discovered
     */
    boolean hasDiscoveredSpell(String spellId);

    /**
     * Returns the set of all spell IDs the player has discovered.
     *
     * @return an unmodifiable set of discovered spell IDs
     */
    Set<String> getDiscoveredSpells();

    /**
     * Returns the mastery level for a specific spell.
     *
     * @param spellId the unique identifier of the spell
     * @return the mastery level (0 if not discovered)
     */
    int getSpellMastery(String spellId);
}
