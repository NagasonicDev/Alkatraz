package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Prevents summoned companions from being damaged by their own owner and from
 * harming each other. Summons are identified by the persistent NBT flags
 * {@code summoned_zombie}, {@code summoned_knight} and {@code summoned_mount};
 * their owner is the UUID stored under {@code summoner_uuid}.
 */
public final class SummonCombatGuard implements Listener {

    private static final Set<String> SUMMON_FLAGS = Set.of("summoned_zombie", "summoned_knight", "summoned_mount");

    /** True when the entity is any kind of summoned companion. */
    public static boolean isSummon(Entity entity) {
        if (entity == null) return false;
        return SUMMON_FLAGS.stream().anyMatch(flag -> NBT.getPersistentData(entity, nbt -> nbt.getBoolean(flag)));
    }

    private static String summonerUuid(Entity entity) {
        return NBT.getPersistentData(entity, nbt -> nbt.getString("summoner_uuid"));
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (shouldCancel(event.getDamager(), event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private static boolean shouldCancel(Entity rawDamager, Entity victim) {
        if (!isSummon(victim)) return false;
        Entity damager = rawDamager;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (isSummon(damager)) return true;
        if (damager instanceof Player player) {
            String ownerUuid = summonerUuid(victim);
            return ownerUuid != null && ownerUuid.equals(player.getUniqueId().toString());
        }
        return false;
    }
}