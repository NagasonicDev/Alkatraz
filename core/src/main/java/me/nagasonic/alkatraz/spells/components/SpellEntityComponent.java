package me.nagasonic.alkatraz.spells.components;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SpellEntityComponent implements SpellComponent {
    private final Spell spell;
    private final SpellProperties properties;
    private final Player caster;
    private final ItemStack wand;
    private final SpellComponentType type;
    private double collisionRadius = 0.25;
    private UUID componentID;
    private final Entity entity;

    public SpellEntityComponent(Spell spell, SpellProperties properties, Player caster, ItemStack wand, SpellComponentType type, Entity entity){
        this.spell = spell;
        this.properties = properties;
        this.caster = caster;
        this.wand = wand;
        this.type = type;
        this.entity = entity;
        this.componentID = UUID.randomUUID();
    }

    @Override
    public Spell getSpell() {
        return spell;
    }

    @Override
    public SpellProperties getProperties() {
        return properties;
    }

    @Override
    public Player getCaster() {
        return caster;
    }

    @Override
    public ItemStack getWand() {
        return wand;
    }

    @Override
    public SpellComponentType getType() {
        return type;
    }

    @Override
    public double getCollisionRadius() {
        return collisionRadius;
    }

    public void setCollisionRadius(double collisionRadius) {
        this.collisionRadius = collisionRadius;
    }

    @Override
    public UUID getComponentID() {
        return componentID;
    }

    public Entity getEntity() {
        return entity;
    }
}
