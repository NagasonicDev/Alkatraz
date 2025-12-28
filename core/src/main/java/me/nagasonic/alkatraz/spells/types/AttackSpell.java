package me.nagasonic.alkatraz.spells.types;

import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class AttackSpell extends Spell {
    protected double basePower;
    protected List<UUID> collided = new ArrayList<>();

    public List<UUID> getCollidedIDs() {
        return collided;
    }

    public AttackSpell(String type) {
        super(type);
    }

    @Override
    public void loadCommonConfig(YamlConfiguration spellConfig) {
        super.loadCommonConfig(spellConfig);
        this.basePower = spellConfig.getDouble("power");
    }

    /**
     * Raw spell power before modifiers
     */
    public double getBasePower() {
        return basePower;
    }

    public double getPower(Player caster, LivingEntity target, double power) {

        PlayerData data = DataManager.getPlayerData(caster);

        double casterAffinity = data.getAffinity(getElement());
        double targetResistance;

        if (target instanceof Player t) {
            PlayerData tdata = DataManager.getPlayerData(t);
            targetResistance = tdata.getResistance(getElement());
        } else {
            targetResistance = Utils.getEntityResistance(getElement(), target);
        }

        return power * (1 + ((casterAffinity - targetResistance) / 100));
    }

    /**
     * Overload for non-entity targets (barriers, constructs, etc)
     */
    public double getPower(Player caster, double power) {
        PlayerData data = DataManager.getPlayerData(caster);
        return power * (data.getAffinity(getElement()) / 100);
    }

    /**
     * Called when this spell hits a barrier
     */
    public abstract void onHitBarrier(BarrierSpell barrier, Location location, Player caster);

    /**
     * Called when this spell is fully countered
     */
    public abstract void onCountered(Location location);

}
