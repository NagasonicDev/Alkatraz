package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentService;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentStatService;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.api.magic.trigger.event.EntityKilledTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.event.EquipTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.event.SpellCastTriggerEvent;
import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.items.magic.condition.ConditionEvaluator;
import me.nagasonic.alkatraz.items.magic.effect.EffectExecutor;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import me.nagasonic.alkatraz.api.magic.equipment.EquipmentProfile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapts existing Alkatraz/Bukkit events into internal trigger events.
 */
public final class MagicItemTriggerAdapter implements Listener {

    private final Map<UUID, Map<Integer, ItemStack>> previousEquipment = new HashMap<>();

    // -----------------------------------------------------------------------
    // Existing adapters
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // New trigger adapters
    // -----------------------------------------------------------------------

    /**
     * Handles damage-cancelling engravings like Feather Fall Rune.
     * Evaluates conditions against event parameters (cause, damage)
     * and cancels the event + executes effects if all conditions match.
     * Fires at LOWEST priority so we can cancel before damage is applied.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageWithEngraving(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        EquipmentService equipment = MagicItemServices.equipment();
        var bootInstance = equipment.profile(player).instance(EquipmentSlot.FEET);
        if (bootInstance.isEmpty()) return;

        Map<String, Object> params = new HashMap<>();
        params.put("cause", event.getCause().name());
        params.put("damage", event.getDamage());

        MagicItemInstance instance = bootInstance.get();
        for (Engraving engraving : instance.engravings()) {
            EngravingDefinition def = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(engraving.engravingKey()).orElse(null);
            if (def == null) continue;

            TriggerContext ctx = new TriggerContext(player, null, null, instance, EquipmentSlot.FEET, params);
            if (!ConditionEvaluator.allMatch(def.conditions(), ctx)) continue;

            event.setCancelled(true);
            EffectExecutor.executeAll(def.effects(), ctx);
            return;
        }
    }

    /**
     * Handles damage absorption for the Barrier Rune.
     * Fires at LOWEST priority so damage can be reduced/cancelled before armor applies.
     * Absorbed damage consumes the item's durability instead of a separate HP pool.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageWithBarrier(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        EquipmentProfile profile = MagicItemServices.equipment().profile(player);
        var barrierKey = MagicKeys.alkatraz("barrier_rune");

        for (EquipmentSlot slot : List.of(
                EquipmentSlot.FEET, EquipmentSlot.LEGS,
                EquipmentSlot.CHEST, EquipmentSlot.HEAD)) {
            var optInstance = profile.instance(slot);
            if (optInstance.isEmpty()) continue;

            MagicItemInstance instance = optInstance.get();
            for (Engraving engraving : instance.engravings()) {
                if (!engraving.engravingKey().equals(barrierKey)) continue;

                EngravingDefinition def = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(
                        engraving.engravingKey()).orElse(null);
                if (def == null) continue;

                TriggerContext ctx = TriggerContext.empty(player).withSource(instance, slot);
                if (!ConditionEvaluator.allMatch(def.conditions(), ctx)) continue;

                double durabilityCost = 1.0;
                Object configRaw = def.staticConfig().get("barrier_config");
                if (configRaw instanceof ConfigurationSection barrierConfig) {
                    durabilityCost = barrierConfig.getDouble("durability_cost", 1.0);
                }
                if (durabilityCost <= 0) continue;

                double damage = event.getDamage();
                ItemStack itemStack = profile.item(slot).orElse(null);
                if (itemStack == null) continue;

                int remainingDurability = itemStack.getType().getMaxDurability() - itemStack.getDurability();
                int durabilityNeeded = (int) Math.ceil(damage * durabilityCost);
                int durabilityToUse = Math.min(durabilityNeeded, remainingDurability);
                if (durabilityToUse <= 0) continue;

                double damageAbsorbed = durabilityToUse / durabilityCost;
                double remainingDamage = damage - damageAbsorbed;

                // Sync instance data (PDC, lore) then damage the item
                MagicItemStack.writeInstance(itemStack, instance);
                itemStack.setDurability((short) (itemStack.getDurability() + durabilityToUse));

                int amp = (int) Math.ceil(damageAbsorbed / 2.0) - 1;
                if (damageAbsorbed > 0) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.ABSORPTION, 100, Math.max(0, amp), false, false, true));
                }

                if (remainingDamage <= 0) {
                    event.setCancelled(true);
                } else {
                    event.setDamage(remainingDamage);
                }
                return;
            }
        }
    }

    /**
     * Fires {@code alkatraz:on_damage_taken} when a player takes damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        LivingEntity damager = event instanceof EntityDamageByEntityEvent de
                ? (de.getDamager() instanceof LivingEntity le ? le : null)
                : null;

        Map<String, Object> params = new HashMap<>();
        params.put("damage", event.getFinalDamage());
        params.put("cause", event.getCause().name());

        TriggerContext context = new TriggerContext(
                player,
                damager,
                null, null, null,
                params
        );
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz("on_damage_taken"), context));
    }

    /**
     * Fires {@code alkatraz:on_damage_dealt} when a player damages an entity,
     * either by melee hit or by a projectile they shot.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker == null) return;

        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        Map<String, Object> params = new HashMap<>();
        params.put("damage", event.getFinalDamage());
        params.put("cause", event.getCause().name());

        TriggerContext context = new TriggerContext(attacker, victim, null, null, null, params);
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz("on_damage_dealt"), context));
    }

    /**
     * Fires {@code alkatraz:on_interact_entity} when a player right-clicks an entity.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        LivingEntity target = event.getRightClicked() instanceof LivingEntity le ? le : null;

        TriggerContext context = new TriggerContext(player, target, null, null, null, Map.of());
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz("on_interact_entity"), context));
    }

    /**
     * Fires {@code alkatraz:on_item_held}, {@code alkatraz:on_equip}, and
     * {@code alkatraz:on_unequip} when a player changes their held item slot.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();

        // --- on_item_held ---
        Map<String, Object> heldParams = new HashMap<>();
        heldParams.put("previous_slot", event.getPreviousSlot());
        heldParams.put("new_slot", event.getNewSlot());
        TriggerContext heldContext = new TriggerContext(player, null, null, null, null, heldParams);
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz("on_item_held"), heldContext));

        // --- on_unequip (for the item that was in the previous slot) ---
        ItemStack oldItem = inv.getItem(event.getPreviousSlot());
        if (oldItem != null) {
            MagicItemStack.readInstance(oldItem).ifPresent(instance -> {
                TriggerContext unequipCtx = new TriggerContext(player, null, null, null, null, Map.of());
                TriggerContext scoped = unequipCtx.withSource(instance, EquipmentSlot.MAIN_HAND);
                MagicItemServices.get().dispatchTrigger(
                        new InternalTriggerEvent(MagicKeys.alkatraz("on_unequip"), scoped));
            });
        }

        // --- on_equip (for the item that is now in the new slot) ---
        ItemStack newItem = inv.getItem(event.getNewSlot());
        if (newItem != null) {
            MagicItemStack.readInstance(newItem).ifPresent(instance -> {
                TriggerContext equipCtx = new TriggerContext(player, null, null, null, null, Map.of());
                TriggerContext scoped = equipCtx.withSource(instance, EquipmentSlot.MAIN_HAND);
                MagicItemServices.get().dispatchTrigger(new EquipTriggerEvent(scoped));
            });
        }

        EquipmentStatService.getInstance().syncEquipmentStats(player);
    }

    /**
     * Fires {@code alkatraz:on_death} when a player dies.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        TriggerContext context = new TriggerContext(player, null, null, null, null, Map.of());
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz("on_death"), context));
    }

    /**
     * Fires {@code alkatraz:on_join} when a player joins the server.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TriggerContext context = new TriggerContext(player, null, null, null, null, Map.of());
        MagicItemServices.get().dispatchTrigger(
                new InternalTriggerEvent(MagicKeys.alkatraz("on_join"), context));

        // Fire on_equip for all virtual slots
        EquipmentService service = MagicItemServices.equipment();
        for (EquipmentSlot slot : java.util.List.of(
                EquipmentSlot.RING,
                EquipmentSlot.NECKLACE,
                EquipmentSlot.BRACELET,
                EquipmentSlot.PENDANT
        )) {
            var itemStack = service.profile(player).items().get(slot);
            if (itemStack != null) {
                var instanceOpt = MagicItemStack.readInstance(itemStack);
                instanceOpt.ifPresent(instance -> {
                    TriggerContext ctx = new TriggerContext(player, null, null, null, null, Map.of());
                    TriggerContext scoped = ctx.withSource(instance, slot);
                    MagicItemServices.get().dispatchTrigger(
                            new EquipTriggerEvent(scoped));
                });
            }
        }
        me.nagasonic.alkatraz.items.magic.equipment.EquipmentStatService.getInstance().syncEquipmentStats(player);
    }

    // -----------------------------------------------------------------------
    // Equipment (armour / off-hand) change detection via inventory click
    // -----------------------------------------------------------------------

    /**
     * Records the current equipment state before any inventory click is processed,
     * so we can detect {@code on_equip} and {@code on_unequip} changes afterward.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void preInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        previousEquipment.put(player.getUniqueId(), snapshotEquipment(player));
    }

    /**
     * After a successful inventory click, compares equipment state and fires
     * {@code on_equip} / {@code on_unequip} triggers for any armour or off-hand
     * slot that changed.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void postInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Map<Integer, ItemStack> before = previousEquipment.remove(player.getUniqueId());
        if (before == null) return;

        Map<Integer, ItemStack> after = snapshotEquipment(player);
        boolean changed = false;
        for (int slot : EQUIPMENT_SLOTS) {
            ItemStack oldStack = before.get(slot);
            ItemStack newStack = after.get(slot);
            if (stacksEqual(oldStack, newStack)) continue;
            changed = true;

            // Item was removed from this slot
            if (oldStack != null && !oldStack.isSimilar(newStack)) {
                MagicItemStack.readInstance(oldStack).ifPresent(instance -> {
                    TriggerContext ctx = new TriggerContext(player, null, null, null, null, Map.of());
                    TriggerContext scoped = ctx.withSource(instance, slotToEquipmentSlot(slot));
                    MagicItemServices.get().dispatchTrigger(
                            new InternalTriggerEvent(MagicKeys.alkatraz("on_unequip"), scoped));
                });
            }

            // Item was placed into this slot
            if (newStack != null && !newStack.isSimilar(oldStack)) {
                MagicItemStack.readInstance(newStack).ifPresent(instance -> {
                    TriggerContext ctx = new TriggerContext(player, null, null, null, null, Map.of());
                    TriggerContext scoped = ctx.withSource(instance, slotToEquipmentSlot(slot));
                    MagicItemServices.get().dispatchTrigger(new EquipTriggerEvent(scoped));
                });
            }
        }
        if (changed) {
            me.nagasonic.alkatraz.items.magic.equipment.EquipmentStatService.getInstance().syncEquipmentStats(player);
        }
    }

    /**
     * Same pre/post detection for drag events (e.g. dragging an item onto an armour slot).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void preInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        previousEquipment.put(player.getUniqueId(), snapshotEquipment(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void postInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Map<Integer, ItemStack> before = previousEquipment.remove(player.getUniqueId());
        if (before == null) return;

        Map<Integer, ItemStack> after = snapshotEquipment(player);
        boolean changed = false;
        for (int slot : EQUIPMENT_SLOTS) {
            ItemStack oldStack = before.get(slot);
            ItemStack newStack = after.get(slot);
            if (stacksEqual(oldStack, newStack)) continue;
            changed = true;

            if (oldStack != null && !oldStack.isSimilar(newStack)) {
                MagicItemStack.readInstance(oldStack).ifPresent(instance -> {
                    TriggerContext ctx = new TriggerContext(player, null, null, null, null, Map.of());
                    TriggerContext scoped = ctx.withSource(instance, slotToEquipmentSlot(slot));
                    MagicItemServices.get().dispatchTrigger(
                            new InternalTriggerEvent(MagicKeys.alkatraz("on_unequip"), scoped));
                });
            }

            if (newStack != null && !newStack.isSimilar(oldStack)) {
                MagicItemStack.readInstance(newStack).ifPresent(instance -> {
                    TriggerContext ctx = new TriggerContext(player, null, null, null, null, Map.of());
                    TriggerContext scoped = ctx.withSource(instance, slotToEquipmentSlot(slot));
                    MagicItemServices.get().dispatchTrigger(new EquipTriggerEvent(scoped));
                });
            }
        }
        if (changed) {
            me.nagasonic.alkatraz.items.magic.equipment.EquipmentStatService.getInstance().syncEquipmentStats(player);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** The player inventory slot numbers for equipment (armour + off-hand). */
    private static final int[] EQUIPMENT_SLOTS = {36, 37, 38, 39, 40};

    /**
     * Takes a snapshot of the current equipment items for the given player.
     */
    private static Map<Integer, ItemStack> snapshotEquipment(Player player) {
        Map<Integer, ItemStack> map = new HashMap<>();
        PlayerInventory inv = player.getInventory();
        for (int slot : EQUIPMENT_SLOTS) {
            map.put(slot, inv.getItem(slot));
        }
        return map;
    }

    /**
     * Maps a player inventory slot number to the magic item system's {@link EquipmentSlot}.
     */
    private static EquipmentSlot slotToEquipmentSlot(int slot) {
        return switch (slot) {
            case 36 -> EquipmentSlot.vanilla(org.bukkit.inventory.EquipmentSlot.FEET);
            case 37 -> EquipmentSlot.vanilla(org.bukkit.inventory.EquipmentSlot.LEGS);
            case 38 -> EquipmentSlot.vanilla(org.bukkit.inventory.EquipmentSlot.CHEST);
            case 39 -> EquipmentSlot.vanilla(org.bukkit.inventory.EquipmentSlot.HEAD);
            case 40 -> EquipmentSlot.vanilla(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
            default -> EquipmentSlot.MAIN_HAND;
        };
    }

    /**
     * Returns {@code true} if two {@link ItemStack references} are logically
     * identical (both null, or ItemStack#isSimilar).
     */
    private static boolean stacksEqual(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.isSimilar(b);
    }
}
