package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.spells.domains.DomainCastSpell;
import org.bukkit.entity.Player;

/** Circle 9 EARTH domain: shifting stony ground, pillars and seismic slams. */
public class AtlasUnbound extends DomainCastSpell {

    public AtlasUnbound(String type) {
        super(type);
    }

    @Override
    protected double resolveRadius(Player player) {
        return getModifiedStat(player, "terrace_radius", option(player, "radius", 32));
    }
}
