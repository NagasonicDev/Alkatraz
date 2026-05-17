package me.nagasonic.alkatraz.spells.types.properties;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public abstract class SpellProperties {

    protected final LivingEntity caster;
    protected boolean cancelled;
    protected Location castLocation;

    // Tracks which components this spell has already interacted with
    protected final Set<SpellProperties> collided = new HashSet<>();

    protected SpellProperties(LivingEntity caster, Location castLocation) {
        this.caster = caster;
        this.castLocation = castLocation;
    }

    public LivingEntity getCaster() {
        return caster;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public Set<SpellProperties> getCollided() {
        return collided;
    }
}
