package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fires gathering-related engraving triggers.
 */
public final class GatheringTriggerListener implements Listener {

    private static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
    );

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        Map<String, Object> params = new HashMap<>();
        params.put("block_type", block.getType().name());
        params.put("block_exp", event.getExpToDrop());
        TriggerDispatch.dispatchHeld(player, "on_break_block", params);

        if (ORES.contains(block.getType())) {
            Map<String, Object> oreParams = new HashMap<>();
            oreParams.put("block_type", block.getType().name());
            TriggerDispatch.dispatchHeld(player, "on_mine_ore", oreParams);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("block_type", event.getBlock().getType().name());
        TriggerDispatch.dispatchHeld(player, "on_place_block", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        TriggerDispatch.dispatchHeld(player, "on_fish", Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("entity_type", event.getEntity().getType().name());
        TriggerDispatch.dispatchHeld(player, "on_shear", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFillBucket(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("block_type", event.getBlock().getType().name());
        TriggerDispatch.dispatchHeld(player, "on_fill_bucket", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEmptyBucket(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("block_type", event.getBlock().getType().name());
        TriggerDispatch.dispatchHeld(player, "on_empty_bucket", params);
    }
}
