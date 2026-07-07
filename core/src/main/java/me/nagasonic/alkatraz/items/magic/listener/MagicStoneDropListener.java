package me.nagasonic.alkatraz.items.magic.listener;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.mobs.MagicEntities;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class MagicStoneDropListener implements Listener {

    private static final double BASE_CHANCE = 0.75;
    private static final double LOOTING_BONUS = 0.10;

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!MagicEntities.isMagicEntity(entity)) return;

        // Exclude summoned mobs (e.g. from SummonZombies spell)
        Boolean summoned = NBT.get(entity, nbt -> (Boolean) nbt.getBoolean("summoned_zombie"));
        if (Boolean.TRUE.equals(summoned)) return;

        Enchantment looting = Enchantment.getByKey(NamespacedKey.minecraft("looting"));
        int lootingLevel = event.getEntity().getKiller() != null && looting != null
                ? event.getEntity().getKiller().getInventory().getItemInMainHand().getEnchantmentLevel(looting)
                : 0;

        double chance = BASE_CHANCE + lootingLevel * LOOTING_BONUS;
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            ItemStack stone = MagicItemServices.get().createItem(MagicKeys.alkatraz("magic_stone"));
            if (stone != null) {
                int count = lootingLevel > 0 ? ThreadLocalRandom.current().nextInt(1, lootingLevel + 2) : 1;
                stone.setAmount(count);
                event.getDrops().add(stone);
            }
        }
    }
}
