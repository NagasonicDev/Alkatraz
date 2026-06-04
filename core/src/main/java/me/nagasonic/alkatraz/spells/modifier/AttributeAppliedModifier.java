package me.nagasonic.alkatraz.spells.modifier;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

import java.util.UUID;

/**
 * Applies a vanilla Bukkit attribute modifier while an effect is active.
 */
public class AttributeAppliedModifier extends AppliedModifier {

    private final Attribute attribute;
    private final double amount;
    private final AttributeModifier.Operation operation;
    private final UUID modifierId;
    private final String modifierName;

    public AttributeAppliedModifier(Attribute attribute,
                                    double amount,
                                    AttributeModifier.Operation operation,
                                    String description) {
        super(description);
        this.attribute = attribute;
        this.amount = amount;
        this.operation = operation;
        this.modifierId = UUID.randomUUID();
        this.modifierName = "alkatraz_" + attribute.name().toLowerCase();
    }

    @Override
    public void apply(LivingEntity entity, Spell source) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;

        String name = modifierName + "_" + source.getId();
        instance.getModifiers().stream()
                .filter(m -> name.equals(m.getName()))
                .forEach(instance::removeModifier);

        instance.addModifier(new AttributeModifier(modifierId, name, amount, operation));
    }

    @Override
    public void remove(LivingEntity entity, Spell source) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;

        instance.getModifiers().stream()
                .filter(m -> modifierId.equals(m.getUniqueId()))
                .forEach(instance::removeModifier);
    }
}
