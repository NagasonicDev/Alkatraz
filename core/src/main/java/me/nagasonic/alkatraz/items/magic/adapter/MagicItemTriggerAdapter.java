package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.items.magic.trigger.event.EntityKilledTriggerEvent;
import me.nagasonic.alkatraz.items.magic.trigger.event.SpellCastTriggerEvent;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts existing Alkatraz/Bukkit events into internal trigger events.
 */
public final class MagicItemTriggerAdapter implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpellPrepare(PlayerSpellPrepareEvent event) {
        Player player = event.getCaster();
        Spell spell = event.getSpell();
        ItemStack wand = event.getWand();

        Map<String, Object> parameters = new HashMap<>();
        if (spell != null) {
            if (spell.getElement() != null) {
                parameters.put("spell_element", spell.getElement().name());
            }
            parameters.put("spell_id", spell.getId());
        }

        TriggerContext context = new TriggerContext(
                player,
                null,
                null,
                null,
                null,
                parameters
        );

        TriggerContext scoped = MagicItemStack.readInstance(wand)
                .map(instance -> context.withSource(instance, EquipmentSlot.MAIN_HAND))
                .orElse(context);

        MagicItemServices.get().dispatchTrigger(new SpellCastTriggerEvent(scoped));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        TriggerContext context = new TriggerContext(
                killer,
                event.getEntity(),
                null,
                null,
                null,
                Map.of()
        );
        MagicItemServices.get().dispatchTrigger(new EntityKilledTriggerEvent(context));
    }
}
