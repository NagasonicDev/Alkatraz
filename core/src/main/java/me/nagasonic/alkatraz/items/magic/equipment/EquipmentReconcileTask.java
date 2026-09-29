package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.items.magic.persistence.ItemDataKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically fingerprints each online player's equipped items (vanilla armor,
 * hands, and virtual slots) and re-syncs equipment stats only when the
 * fingerprint changes. Catches external armor changes that bypass inventory
 * events (commands, other plugins). Baseline is recorded on first run; only
 * subsequent changes trigger a sync.
 * <p>
 * Because it observes every slot the equipment profile is derived from, an
 * observed change also drops the cached {@link EquipmentService} profile, which
 * bounds how long that cache can be stale after an out-of-band change.
 */
public final class EquipmentReconcileTask implements Runnable, Listener {

    private static final long INTERVAL = 20L;

    private static final EquipmentSlot[] VIRTUAL_SLOTS = {
            EquipmentSlot.RING, EquipmentSlot.NECKLACE, EquipmentSlot.BRACELET, EquipmentSlot.PENDANT
    };

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final Map<UUID, Long> fingerprints = new ConcurrentHashMap<>();

    private EquipmentReconcileTask() {}

    public static void start() {
        Alkatraz plugin = Alkatraz.getInstance();
        EquipmentReconcileTask task = new EquipmentReconcileTask();
        plugin.getServer().getScheduler().runTaskTimer(plugin, task, INTERVAL, INTERVAL);
        plugin.getServer().getPluginManager().registerEvents(task, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        fingerprints.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long fingerprint = fingerprint(player);
            Long previous = fingerprints.putIfAbsent(uuid, fingerprint);
            if (previous != null && previous.longValue() != fingerprint) {
                fingerprints.put(uuid, fingerprint);
                EquipmentService.invalidate(uuid);
                EquipmentStatService.getInstance().syncEquipmentStats(player);
            }
        }
    }

    private static long fingerprint(Player player) {
        long hash = 1L;
        hash = 31L * hash + stackHash(player.getInventory().getItemInMainHand());
        hash = 31L * hash + stackHash(player.getInventory().getItemInOffHand());
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            hash = 31L * hash + stackHash(player.getInventory().getItem(slot.vanillaSlot()));
        }
        for (EquipmentSlot slot : VIRTUAL_SLOTS) {
            hash = 31L * hash + stackHash(EquipmentStorage.getItemOrNull(player, slot));
        }
        return hash;
    }

    /**
     * Cheap, stable identity hash for a stack. {@code ItemStack#hashCode} builds a
     * full meta copy on Paper whenever the stack has item meta, so instead we mix the
     * material, the amount, and the magic instance payload, which uniquely identifies
     * any magic item and still catches a vanilla durability or name change because
     * those rewrite the meta-bearing stack's magic payload or material.
     */
    private static long stackHash(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return 0L;
        }
        long hash = 31L * stack.getType().hashCode() + stack.getAmount();
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                String instance = pdc.get(ItemDataKeys.itemInstance(), PersistentDataType.STRING);
                if (instance != null) {
                    hash = 31L * hash + instance.hashCode();
                }
                String definition = pdc.get(ItemDataKeys.itemDefinition(), PersistentDataType.STRING);
                if (definition != null) {
                    hash = 31L * hash + definition.hashCode();
                }
            }
        }
        return hash;
    }
}
