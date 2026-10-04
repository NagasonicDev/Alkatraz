package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentProfile;
import me.nagasonic.alkatraz.api.magic.equipment.VirtualSlotResolver;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves equipped magic items for a player across vanilla and virtual slots.
 */
public final class EquipmentService {

    private static final EquipmentSlot[] VANILLA_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final Map<EquipmentSlot, VirtualSlotResolver> virtualResolvers = new LinkedHashMap<>();

    /**
     * Per-player profile cache. Resolving a profile deserializes every equipped magic
     * item, which is far too costly to redo on every trigger dispatch. Entries are
     * dropped by {@link #invalidate(UUID)} from every hook that can change a player's
     * equipment (see {@link EquipmentCacheListener}).
     */
    private static final Map<UUID, EquipmentProfile> PROFILE_CACHE = new ConcurrentHashMap<>();

    /**
     * Cheap global guard for the periodic reconcile sweep. Fingerprinting a player costs up
     * to ten {@code getItemMeta()} clones, which is pure waste on a server where nobody wears
     * a magic item. Latched to {@code true} the moment a profile containing at least one
     * {@link MagicItemInstance} is built, and recomputed on invalidation so it can fall back
     * to {@code false} once the last magic item is gone.
     */
    private static volatile boolean anyMagicEquipped;

    public static boolean isAnyMagicEquipped() {
        return anyMagicEquipped;
    }

    static void markMagicEquipped() {
        anyMagicEquipped = true;
    }

    private static void recomputeAnyMagicEquipped() {
        for (EquipmentProfile profile : PROFILE_CACHE.values()) {
            if (!profile.instances().isEmpty()) {
                anyMagicEquipped = true;
                return;
            }
        }
        anyMagicEquipped = false;
    }

    public void registerVirtualSlot(EquipmentSlot slot, VirtualSlotResolver resolver) {
        virtualResolvers.put(slot, resolver);
    }

    /** Drops the cached profile for a player so the next {@link #profile(Player)} rebuilds it. */
    public static void invalidate(UUID uuid) {
        if (uuid != null) {
            if (PROFILE_CACHE.remove(uuid) != null) {
                recomputeAnyMagicEquipped();
            }
        }
    }

    /** Drops every cached profile. */
    public static void invalidateAll() {
        PROFILE_CACHE.clear();
        recomputeAnyMagicEquipped();
    }

    public EquipmentProfile profile(Player player) {
        UUID uuid = player.getUniqueId();
        EquipmentProfile cached = PROFILE_CACHE.get(uuid);
        if (cached != null) {
            if (!cached.instances().isEmpty()) {
                anyMagicEquipped = true;
            }
            return cached;
        }
        EquipmentProfile built = buildProfile(player);
        EquipmentProfile existing = PROFILE_CACHE.putIfAbsent(uuid, built);
        EquipmentProfile effective = existing != null ? existing : built;
        if (!effective.instances().isEmpty()) {
            anyMagicEquipped = true;
        }
        return effective;
    }

    private EquipmentProfile buildProfile(Player player) {
        Map<EquipmentSlot, ItemStack> items = new LinkedHashMap<>();
        Map<EquipmentSlot, MagicItemInstance> instances = new LinkedHashMap<>();

        PlayerInventory inventory = player.getInventory();
        putIfMagic(inventory.getItemInMainHand(), EquipmentSlot.MAIN_HAND, items, instances);
        putIfMagic(inventory.getItemInOffHand(), EquipmentSlot.OFF_HAND, items, instances);
        for (EquipmentSlot slot : VANILLA_ORDER) {
            putIfMagic(inventory.getItem(slot.vanillaSlot()), slot, items, instances);
        }
        for (Map.Entry<EquipmentSlot, VirtualSlotResolver> entry : virtualResolvers.entrySet()) {
            putIfMagic(entry.getValue().resolve(player), entry.getKey(), items, instances);
        }

        return new EquipmentProfile(items, instances);
    }

    private static void putIfMagic(
            ItemStack stack,
            EquipmentSlot slot,
            Map<EquipmentSlot, ItemStack> items,
            Map<EquipmentSlot, MagicItemInstance> instances
    ) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        items.put(slot, stack);
        try {
            MagicItemStack.readInstance(stack).ifPresent(instance ->
                instances.put(slot, instance)
            );
        } catch (Exception e) {
            Alkatraz.logWarning("Skipping corrupt magic item in slot " + slot.getKey().getKey() + ": " + e.getMessage());
        }
    }
}
