package me.nagasonic.alkatraz.spells.spellbooks;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles spellbook interactions
 */
public class SpellbookListener implements Listener {

    private static final Set<UUID> IN_USE = new HashSet<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;

        // Check if it's a spellbook
        String spellbookType = NBT.get(item, nbt -> (String) nbt.getString("spellbook_type"));
        if (spellbookType == null) return;

        // Cancel the event to prevent other interactions
        event.setCancelled(true);

        // Block if already using a spellbook
        UUID uuid = player.getUniqueId();
        if (!IN_USE.add(uuid)) return;

        Runnable release = () -> IN_USE.remove(uuid);

        // Handle based on type
        if ("random".equals(spellbookType)) {
            RandomSpellbook.use(player, item, release);
        } else if ("regular".equals(spellbookType)) {
            String spellId = Spellbook.getSpellId(item);
            if (spellId != null) {
                Spellbook spellbook = new Spellbook(spellId);
                spellbook.use(player, item, release);
            } else {
                release.run();
            }
        } else {
            release.run();
        }
    }
}
