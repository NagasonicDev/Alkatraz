package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.spells.domains.DomainCastSpell;
import org.bukkit.entity.Player;

/** Circle 9 LIGHT domain: cleansing radiance, allied renewal and hostile smite. */
public class CovenantOfTheFirstDawn extends DomainCastSpell {

    public CovenantOfTheFirstDawn(String type) {
        super(type);
    }

    @Override
    protected double resolveRadius(Player player) {
        return getModifiedStat(player, "covenant_radius", option(player, "radius", 24));
    }
}
