package me.nagasonic.alkatraz.api;

import me.nagasonic.alkatraz.api.spells.Spell;
import java.util.Set;

public interface PlayerStats {

    double getMana();
    double getMaxMana();
    double getManaRegeneration();

    int getCircleLevel();
    int getStatPoints();

    int getPoints(Element element);
    double getAffinity(Element element);
    double getResistance(Element element);

    double getArcaneKnowledge();
    int getResearchPoints();

    boolean hasDiscoveredSpell(String spellId);
    Set<String> getDiscoveredSpells();

    int getSpellMastery(String spellId);
}
