package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.listener.CastEventListener;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.api.magic.trigger.event.SpellHitTriggerEvent;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for dealing spell damage and firing the hit trigger pipeline.
 * <p>
 * All spells that damage living entities should use this utility instead of
 * calling {@link LivingEntity#damage(double)} directly, so that modifiers
 * and effects listening on {@code alkatraz:on_spell_hit} are triggered.
 */
public final class SpellDamageUtil {

    private SpellDamageUtil() {}

    /**
     * Deals spell damage to a target entity and dispatches a
     * {@link SpellHitTriggerEvent} through the magic item trigger pipeline.
     *
     * @param target the entity being damaged
     * @param damage the amount of damage to deal
     * @param caster the caster of the spell
     * @param wand   the wand item stack used to cast (may be null or an old-style
     *               NBT wand â€” the method gracefully degrades)
     * @param spell  the spell being cast (may be null)
     */
    public static void damageWithSpell(
            LivingEntity target,
            double damage,
            LivingEntity caster,
            ItemStack wand,
            Spell spell
    ) {
        if (target == null || target.isDead() || caster == null) {
            return;
        }

        // Flag so CastEventListener.onAttack skips spell-caused damage
        CastEventListener.markSpellDamage(target);
        // Apply the actual damage
        target.damage(damage, caster);

        // Build parameters map with spell context
        Map<String, Object> parameters = new HashMap<>();
        if (spell != null) {
            parameters.put("spell_id", spell.getId());
            if (spell.getElement() != null) {
                parameters.put("spell_element", spell.getElement().name());
            }
        }

        // Build the trigger context
        TriggerContext context = new TriggerContext(
                caster,
                target,
                null,   // triggerType is set by the event constructor
                null,   // sourceItem â€” resolved below
                null,   // equipmentSlot â€” resolved below
                parameters
        );

        // If the wand is a new-style magic item, scope the context so the
        // pipeline can collect bindings from it directly.
        TriggerContext scoped = MagicItemStack.readInstance(wand)
                .map(instance -> context.withSource(instance, EquipmentSlot.MAIN_HAND))
                .orElse(context);

        // Fire the hit trigger event through the pipeline
        MagicItemServices.get().dispatchTrigger(new SpellHitTriggerEvent(scoped));
    }
}
