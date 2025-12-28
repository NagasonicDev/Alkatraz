package me.nagasonic.alkatraz.spells.types;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
public abstract class BarrierSpell extends Spell {

    protected double maxHitpoints;

    public BarrierSpell(String type) {
        super(type);
    }

    @Override
    public void loadCommonConfig(YamlConfiguration spellConfig) {
        super.loadCommonConfig(spellConfig);
        this.maxHitpoints = spellConfig.getDouble("max_hitpoints");
    }

    public double getMaxHitpoints() {
        return maxHitpoints;
    }

    /**
     * Called when the barrier takes a hit
     */
    public abstract void onHit(double damage, AttackSpell source);

    /**
     * Called when barrier breaks
     */
    public abstract void onBarrierBreak(Location center);
}
