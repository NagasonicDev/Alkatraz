package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.spells.domains.DomainCastSpell;
import org.bukkit.entity.Player;

/** Circle 9 WATER domain: abyssal flood that drags, slows and extinguishes hostiles. */
public class LeviathansTestament extends DomainCastSpell {

    public LeviathansTestament(String type) {
        super(type);
    }

    @Override
    protected double resolveRadius(Player player) {
        return getModifiedStat(player, "tide_length", option(player, "length", 60)) / 2.0;
    }
}
