package me.nagasonic.alkatraz.spells.components;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SpellEntityComponent implements SpellComponent {
    private final Spell spell;
    private final Player caster;
    private final ItemStack wand;
    private final SpellComponentType type;
    private UUID componentID;
    private final Entity entity;

    public SpellEntityComponent(Spell spell, Player caster, ItemStack wand, SpellComponentType type, Entity entity){
        this.spell = spell;
        this.caster = caster;
        this.wand = wand;
        this.type = type;
        this.entity = entity;
        UUID uuid = null;
        while (uuid == null){
            uuid = UUID.randomUUID();
            if (SpellComponentHandler.getActiveComponents().containsKey(uuid)){
                uuid = null;
            }
        }
        this.componentID = uuid;
    }

    @Override
    public Spell getSpell() {
        return spell;
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

    public Entity getEntity() {
        return entity;
    }

    public void initialize(){
        SpellComponentHandler.register(this);
    }
}
