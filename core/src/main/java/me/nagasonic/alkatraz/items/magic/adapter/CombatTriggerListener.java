package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.trigger.RangedKillTracker;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import org.bukkit.Material;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fires combat-related engraving triggers.
 */
public final class CombatTriggerListener implements Listener {

    private final RangedKillTracker<MagicItemInstance> rangedKills = new RangedKillTracker<>(10_000L);

    /**
     * Fires {@code alkatraz:on_melee_hit} when a player lands a direct melee hit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (event.getDamager() instanceof Projectile) return;

        Map<String, Object> params = new HashMap<>();
        params.put("damage", event.getFinalDamage());
        params.put("cause", event.getCause().name());
        TriggerDispatch.dispatchHeld(player, "on_melee_hit", params);
    }

    /**
     * Fires {@code alkatraz:on_critical_hit} when a player lands a melee hit
     * while falling (a critical hit approximation).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCriticalHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (event.getDamager() instanceof Projectile) return;
        if (player.isOnGround() || player.isInWater() || TriggerDispatch.inLava(player)
                || player.isFlying() || player.isGliding()) return;

        Map<String, Object> params = new HashMap<>();
        params.put("damage", event.getFinalDamage());
        params.put("cause", event.getCause().name());
        TriggerDispatch.dispatch(player, victim, null, null, "on_critical_hit", params);
    }

    /**
     * Fires {@code alkatraz:on_player_attacked} when a player is attacked by
     * another player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttacked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        Map<String, Object> params = new HashMap<>();
        params.put("attacker", attacker.getName());
        params.put("damage", event.getFinalDamage());
        TriggerDispatch.dispatch(player, "on_player_attacked", params);
    }

    /**
     * Fires {@code alkatraz:on_blocked} when a player's shield takes damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlocked(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SHIELD) return;
        Player player = event.getPlayer();

        Map<String, Object> params = new HashMap<>();
        params.put("damage", event.getDamage());
        TriggerDispatch.dispatch(player, "on_blocked", params);
    }

    /**
     * Fires {@code alkatraz:on_projectile_launch} when a player launches an
     * arrow or trident, and {@code alkatraz:on_throw} when they throw a
     * throwable.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        Map<String, Object> params = new HashMap<>();
        params.put("projectile_type", event.getEntity().getType().name());

        if (event.getEntity() instanceof Egg || event.getEntity() instanceof Snowball
                || event.getEntity() instanceof ThrownPotion || event.getEntity() instanceof EnderPearl) {
            TriggerDispatch.dispatch(player, "on_throw", params);
        } else {
            TriggerDispatch.dispatch(player, "on_projectile_launch", params);
        }
    }

    /**
     * Fires {@code alkatraz:on_bow_release} when a player releases a bow or
     * crossbow shot.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowRelease(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null) return;

        Map<String, Object> params = new HashMap<>();
        params.put("bow_type", bow.getType().name());
        TriggerDispatch.dispatchHeld(player, "on_bow_release", params);
    }

    /**
     * Fires {@code alkatraz:on_bow_pull} when a player right-clicks while
     * holding a bow or crossbow.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowPull(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Material type = item.getType();
        if (type != Material.BOW && type != Material.CROSSBOW) return;
        Player player = event.getPlayer();

        Map<String, Object> params = new HashMap<>();
        params.put("hand", event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND ? "OFF_HAND" : "MAIN_HAND");
        TriggerDispatch.dispatchHeld(player, "on_bow_pull", params);
    }

    /**
     * Fires {@code alkatraz:on_projectile_hit_block} when a projectile shot by
     * a player hits a block.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHitBlock(ProjectileHitEvent event) {
        if (event.getHitBlock() == null) return;
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        Map<String, Object> params = new HashMap<>();
        params.put("block_type", event.getHitBlock().getType().name());
        params.put("projectile_type", event.getEntity().getType().name());
        TriggerDispatch.dispatchProjectile(player, event.getEntity(), "on_projectile_hit_block", params);
    }

    /**
     * Records a ranged hit so the weapon that shot it can be resolved when the
     * victim dies.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRangedDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) return;
        if (!(projectile.getShooter() instanceof Player shooter)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        MagicItemInstance weapon = TriggerDispatch.resolveProjectileWeapon(projectile);
        rangedKills.record(victim.getUniqueId(), shooter.getUniqueId(), weapon);
    }

    /**
     * Fires {@code alkatraz:on_ranged_kill} when an entity dies shortly after
     * being hit by a player's projectile.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRangedKill(EntityDeathEvent event) {
        UUID victim = event.getEntity().getUniqueId();
        var entry = rangedKills.resolve(victim, System.currentTimeMillis());
        if (entry.isEmpty()) return;

        Player shooter = org.bukkit.Bukkit.getPlayer(entry.get().shooter());
        if (shooter == null || !shooter.isOnline()) return;

        Map<String, Object> params = new HashMap<>();
        params.put("projectile_type", event.getEntity().getType().name());
        TriggerDispatch.dispatch(shooter, entry.get().weapon(), null, "on_ranged_kill", params);
    }

    /**
     * Fires {@code alkatraz:on_targeted} when a mob targets a player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargeted(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        Map<String, Object> params = new HashMap<>();
        params.put("entity_type", event.getEntity().getType().name());
        params.put("reason", event.getReason() != null ? event.getReason().name() : "UNKNOWN");
        TriggerDispatch.dispatch(player, entity, null, null, "on_targeted", params);
    }

    /**
     * Fires {@code alkatraz:on_arm_swing} when a player swings their arm.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();

        Map<String, Object> params = new HashMap<>();
        params.put("hand", "MAIN_HAND");
        TriggerDispatch.dispatchHeld(player, "on_arm_swing", params);
    }
}
