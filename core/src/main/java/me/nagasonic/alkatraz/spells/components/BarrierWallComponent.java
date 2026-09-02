package me.nagasonic.alkatraz.spells.components;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class BarrierWallComponent implements SpellComponent {

    private final SpellProperties owner;
    private final LivingEntity caster;
    private final Location location;
    private final double collisionRadius;
    private final UUID componentId = UUID.randomUUID();
    private int lifeTicks;

    public BarrierWallComponent(SpellProperties owner, LivingEntity caster,
                                Location location, double collisionRadius, int lifeTicks) {
        this.owner = owner;
        this.caster = caster;
        this.location = location.clone();
        this.collisionRadius = collisionRadius;
        this.lifeTicks = lifeTicks;
    }

    @Override public Spell getSpell() { return null; }
    @Override public SpellProperties getProperties() { return owner; }
    @Override public LivingEntity getCaster() { return caster; }
    @Override public ItemStack getWand() { return null; }
    @Override public SpellComponentType getType() { return SpellComponentType.DEFENSE; }
    @Override public UUID getComponentID() { return componentId; }
    @Override public double getCollisionRadius() { return collisionRadius; }

    public Location getLocation() { return location; }

    public void tick() {
        if (--lifeTicks <= 0) {
            SpellComponentHandler.remove(componentId);
        }
    }
}
