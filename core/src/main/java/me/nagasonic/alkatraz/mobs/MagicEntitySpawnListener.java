package me.nagasonic.alkatraz.mobs;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Optional;

/**
 * Replaces vanilla mob spawns with magic entity variants based on per-mob
 * {@code spawn_chance} values in their yml configs.
 *
 * <p>When multiple magic mobs replace the same base type, chances are treated as
 * a weighted pool (e.g. 5% mage + 3% fighter = 8% total replacement rate).
 */
public class MagicEntitySpawnListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (MagicEntities.isMagicEntity(entity)) {
            return;
        }

        Optional<MagicEntityType> replacement = MagicEntityRegistry.rollReplacement(
                event.getEntityType(),
                event.getSpawnReason()
        );
        if (replacement.isEmpty()) {
            return;
        }

        Location location = event.getLocation();
        event.setCancelled(true);

        Alkatraz.getInstance().getServer().getScheduler().runTask(Alkatraz.getInstance(), () -> {
            MagicEntities.spawn(replacement.get(), location).ifPresentOrElse(
                    spawned -> {},
                    () -> Alkatraz.logWarning("Failed to spawn magic mob '"
                            + replacement.get().getId() + "' — NMS implementation may be missing")
            );
        });
    }
}
