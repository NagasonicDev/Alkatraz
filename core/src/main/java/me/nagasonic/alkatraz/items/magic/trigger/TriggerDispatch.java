package me.nagasonic.alkatraz.items.magic.trigger;

import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.persistence.ItemDataKeys;
import me.nagasonic.alkatraz.items.magic.persistence.ItemInstanceSerializer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * Shared helpers for dispatching internal engraving trigger events from the
 * trigger listener classes. Scopes a trigger to a magic item source so runes
 * on the right item fire.
 */
public final class TriggerDispatch {

    private TriggerDispatch() {
    }

    /** Dispatch a trigger with no parameters. */
    public static void dispatch(LivingEntity actor, String trigger) {
        dispatch(actor, trigger, Map.of());
    }

    /** Dispatch a trigger unscoped to any item, with parameters. */
    public static void dispatch(LivingEntity actor, String trigger, Map<String, Object> parameters) {
        dispatch(actor, null, null, trigger, parameters);
    }

    /** Dispatch a trigger scoped to a specific magic item instance. */
    public static void dispatch(LivingEntity actor, MagicItemInstance source, EquipmentSlot slot,
                                String trigger, Map<String, Object> parameters) {
        TriggerContext context = new TriggerContext(actor, null, null, source, slot, parameters);
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz(trigger), context));
    }

    /** Dispatch a trigger scoped to a specific magic item instance with a target. */
    public static void dispatch(LivingEntity actor, LivingEntity target, MagicItemInstance source,
                                EquipmentSlot slot, String trigger, Map<String, Object> parameters) {
        TriggerContext context = new TriggerContext(actor, target, null, source, slot, parameters);
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz(trigger), context));
    }

    /** Dispatch a trigger scoped to the magic item held in the actor's main hand. */
    public static void dispatchHeld(LivingEntity actor, String trigger, Map<String, Object> parameters) {
        if (!(actor instanceof Player player)) {
            dispatch(actor, trigger);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        TriggerContext context = new TriggerContext(actor, null, null, null, null, parameters);
        TriggerContext scoped = MagicItemStack.readInstance(held)
                .map(instance -> context.withSource(instance, EquipmentSlot.MAIN_HAND))
                .orElse(context);
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz(trigger), scoped));
    }

    /** Dispatch a trigger scoped to the magic item linked to a projectile (if any). */
    public static void dispatchProjectile(Player shooter, Projectile projectile,
                                          String trigger, Map<String, Object> parameters) {
        MagicItemInstance instance = resolveProjectileWeapon(projectile);
        TriggerContext context = instance != null
                ? new TriggerContext(shooter, null, null, instance, EquipmentSlot.MAIN_HAND, parameters)
                : new TriggerContext(shooter, null, null, null, null, parameters);
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz(trigger), context));
    }

    /** Dispatch a trigger scoped to the magic item linked to a projectile, with a target. */
    public static void dispatchProjectile(Player shooter, LivingEntity target, Projectile projectile,
                                          String trigger, Map<String, Object> parameters) {
        MagicItemInstance instance = resolveProjectileWeapon(projectile);
        TriggerContext context = instance != null
                ? new TriggerContext(shooter, target, null, instance, EquipmentSlot.MAIN_HAND, parameters)
                : new TriggerContext(shooter, target, null, null, null, parameters);
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz(trigger), context));
    }

    /** Resolves the magic weapon instance linked to a projectile at launch time. */
    public static MagicItemInstance resolveProjectileWeapon(Projectile projectile) {
        String raw = projectile.getPersistentDataContainer().get(
                ItemDataKeys.projectileWeaponInstance(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ItemInstanceSerializer.deserialize(raw);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    /**
     * Whether the player is standing in lava. The 1.19 API has no
     * {@code isInLava()}, so we check the block they occupy.
     */
    public static boolean inLava(Player player) {
        return player.getLocation().getBlock().getType() == org.bukkit.Material.LAVA;
    }

    /**
     * Whether the player is climbing a ladder/vine/scaffolding. The 1.19 API
     * has no {@code getClimbingBlock()}, so we inspect the block they occupy.
     */
    public static boolean isClimbing(Player player) {
        org.bukkit.Material at = player.getLocation().getBlock().getType();
        return at == org.bukkit.Material.LADDER || at == org.bukkit.Material.VINE
                || at == org.bukkit.Material.WEEPING_VINES || at == org.bukkit.Material.TWISTING_VINES
                || at == org.bukkit.Material.SCAFFOLDING;
    }
}
