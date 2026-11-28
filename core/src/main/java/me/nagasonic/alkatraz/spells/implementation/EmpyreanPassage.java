package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.spells.domains.DomainCastSpell;
import org.bukkit.entity.Player;

/** Circle 9 AIR domain: celestial expanse of updrafts, lifts and slow fall. */
public class EmpyreanPassage extends DomainCastSpell {

    public EmpyreanPassage(String type) {
        super(type);
    }

    @Override
    protected double resolveRadius(Player player) {
        return getModifiedStat(player, "passage_length", option(player, "length", 96)) / 3.7;
    }
}
