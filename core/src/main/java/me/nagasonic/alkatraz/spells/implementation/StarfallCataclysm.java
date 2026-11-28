package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.spells.domains.DomainCastSpell;
import org.bukkit.entity.Player;

/** Circle 9 FIRE domain: infernal terrain, raining fireballs and a tracked firestorm. */
public class StarfallCataclysm extends DomainCastSpell {

    public StarfallCataclysm(String type) {
        super(type);
    }

    @Override
    protected double resolveRadius(Player player) {
        return getModifiedStat(player, "cataclysm_radius", option(player, "radius", 32));
    }
}
