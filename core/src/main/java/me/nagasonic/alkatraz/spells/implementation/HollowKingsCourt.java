package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.spells.domains.DomainCastSpell;
import org.bukkit.entity.Player;

/** Circle 9 DARK domain: umbral pressure that drains, withers and blinds hostiles. */
public class HollowKingsCourt extends DomainCastSpell {

    public HollowKingsCourt(String type) {
        super(type);
    }

    @Override
    protected double resolveRadius(Player player) {
        return getModifiedStat(player, "court_radius", option(player, "radius", 18));
    }
}
