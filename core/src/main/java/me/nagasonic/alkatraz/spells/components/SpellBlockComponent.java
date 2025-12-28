package me.nagasonic.alkatraz.spells.components;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SpellBlockComponent implements SpellComponent{


    // Static definition
    private final Spell spell;

    // Per-cast state
    private final SpellProperties properties;

    // Context
    private final Player caster;
    private final ItemStack wand;
    private final SpellComponentType type;
    private final UUID componentID = UUID.randomUUID();

    // Spatial + lifetime
    private final Block location;
    private final double collisionRadius;
    private int lifeTicks;

    public SpellBlockComponent(
            Spell spell,
            SpellProperties properties,
            Player caster,
            ItemStack wand,
            SpellComponentType type,
            Block location,
            double collisionRadius,
            int lifeTicks
    ) {
        this.spell = spell;
        this.properties = properties;
        this.caster = caster;
        this.wand = wand;
        this.type = type;
        this.location = location;
        this.collisionRadius = collisionRadius;
        this.lifeTicks = lifeTicks;
    }

    // ========================
    // Interface implementation
    // ========================

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
    public UUID getComponentID() {
        return componentID;
    }

    @Override
    public double getCollisionRadius() {
        return collisionRadius;
    }

    public Block getBlock() {
        return location;
    }

    public void tick() {
        if (--lifeTicks <= 0) {
            SpellComponentHandler.remove(componentID);
        }
    }
}
