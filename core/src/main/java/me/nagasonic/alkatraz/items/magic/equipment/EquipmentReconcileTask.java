package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically fingerprints each online player's equipped items (vanilla armor,
 * hands, and virtual slots) and re-syncs equipment stats only when the
 * fingerprint changes. Catches external armor changes that bypass inventory
 * events (commands, other plugins). Baseline is recorded on first run; only
 * subsequent changes trigger a sync.
 */
public final class EquipmentReconcileTask implements Runnable {

    private static final long INTERVAL = 20L;

    private static final EquipmentSlot[] VIRTUAL_SLOTS = {
            EquipmentSlot.RING, EquipmentSlot.NECKLACE, EquipmentSlot.BRACELET, EquipmentSlot.PENDANT
    };

    private final Map<UUID, Long> fingerprints = new ConcurrentHashMap<>();

    private EquipmentReconcileTask() {}

    public static void start() {
        Alkatraz plugin = Alkatraz.getInstance();
        plugin.getServer().getScheduler().runTaskTimer(plugin, new EquipmentReconcileTask(), INTERVAL, INTERVAL);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long fingerprint = fingerprint(player);
            Long previous = fingerprints.putIfAbsent(uuid, fingerprint);
            if (previous != null) {
                if (previous.longValue() != fingerprint) {
                    fingerprints.put(uuid, fingerprint);
                    EquipmentStatService.getInstance().syncEquipmentStats(player);
                }
            }
        }
        fingerprints.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private static long fingerprint(Player player) {
        long hash = 1L;
        hash = 31L * hash + stackHash(player.getInventory().getItemInMainHand());
        hash = 31L * hash + stackHash(player.getInventory().getItemInOffHand());
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            hash = 31L * hash + stackHash(player.getInventory().getItem(slot.vanillaSlot()));
        }
        for (EquipmentSlot slot : VIRTUAL_SLOTS) {
            hash = 31L * hash + stackHash(EquipmentStorage.getItem(player, slot).orElse(null));
        }
        return hash;
    }

    private static long stackHash(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return 0L;
        }
        return stack.hashCode();
    }
}
