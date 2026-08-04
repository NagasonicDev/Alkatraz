package me.nagasonic.alkatraz.items.magic.listener;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.config.SpellbookConfig;
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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!MagicEntities.isMagicEntity(entity)) return;

        Boolean summoned = NBT.getPersistentData(entity, nbt -> nbt.getBoolean("summoned_zombie"));
        if (Boolean.TRUE.equals(summoned)) return;

        Enchantment looting = Enchantment.getByKey(NamespacedKey.minecraft("looting"));
        int lootingLevel = event.getEntity().getKiller() != null && looting != null
                ? event.getEntity().getKiller().getInventory().getItemInMainHand().getEnchantmentLevel(looting)
                : 0;

        double baseChance = SpellbookConfig.getMagicStoneBaseChance();
        double lootingBonus = SpellbookConfig.getMagicStoneLootingBonus();
        double chance = baseChance + lootingLevel * lootingBonus;
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
