package me.nagasonic.alkatraz.progression.research;

import me.nagasonic.alkatraz.events.PlayerCastEvent;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ResearchObjectiveListener implements Listener {

    private static final long SPELL_CONTEXT_MILLIS = 8000L;
    private final Map<UUID, RecentSpell> recentSpells = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ResearchService.applyCompletedRewards(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpellCast(PlayerCastEvent event) {
        Player player = event.getCaster();
        Spell spell = event.getSpell();
        if (spell == null) return;

        recentSpells.put(player.getUniqueId(), new RecentSpell(spell, System.currentTimeMillis()));

        Map<String, Object> context = baseContext(player);
        addSpellContext(context, spell);
        ResearchService.recordObjectiveEvent(player, "spell_cast", context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        Map<String, Object> context = baseContext(killer);
        addEntityContext(context, victim, "entity");
        context.put("distance", killer.getLocation().distance(victim.getLocation()));
        ResearchService.recordObjectiveEvent(killer, "kill", context);

        RecentSpell recent = recentSpell(killer);
        if (recent != null) {
            addSpellContext(context, recent.spell());
            ResearchService.recordObjectiveEvent(killer, "kill_with_spell", context);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Map<String, Object> context = baseContext(event.getPlayer());
        context.put("block", block.getType().name());
        context.put("material", block.getType().name());
        context.put("biome", block.getBiome().name());
        ResearchService.recordObjectiveEvent(event.getPlayer(), "block_break", context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getRecipe().getResult();
        Map<String, Object> context = baseContext(player);
        addItemContext(context, item);
        ResearchService.recordObjectiveEvent(player, "item_craft", context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Map<String, Object> context = baseContext(player);
        addItemContext(context, event.getItem().getItemStack());
        ResearchService.recordObjectiveEvent(player, "item_pickup", context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsumeItem(PlayerItemConsumeEvent event) {
        Map<String, Object> context = baseContext(event.getPlayer());
        addItemContext(context, event.getItem());
        ResearchService.recordObjectiveEvent(event.getPlayer(), "item_consume", context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Map<String, Object> context = baseContext(event.getEnchanter());
        addItemContext(context, event.getItem());
        context.put("level_cost", event.getExpLevelCost());
        ResearchService.recordObjectiveEvent(event.getEnchanter(), "item_enchant", context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Map<String, Object> context = baseContext(event.getPlayer());
        context.put("state", event.getState().name());
        Entity caught = event.getCaught();
        if (caught != null) addEntityContext(context, caught, "caught");
        ResearchService.recordObjectiveEvent(event.getPlayer(), "fish", context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevelChange(PlayerLevelChangeEvent event) {
        Map<String, Object> context = baseContext(event.getPlayer());
        context.put("from_level", event.getOldLevel());
        context.put("level", event.getNewLevel());
        ResearchService.recordObjectiveEvent(event.getPlayer(), "player_level", context);
    }

    private Map<String, Object> baseContext(Player player) {
        Map<String, Object> context = new HashMap<>();
        context.put("world", player.getWorld().getName());
        context.put("biome", player.getLocation().getBlock().getBiome().name());
        context.put("level", player.getLevel());
        return context;
    }

    private void addSpellContext(Map<String, Object> context, Spell spell) {
        context.put("spell", spell.getId());
        context.put("spell_id", spell.getId());
        context.put("spell_type", spell.getType());
        if (spell.getElement() != null) {
            context.put("element", spell.getElement().name());
            context.put("spell_element", spell.getElement().name());
        }
    }

    private void addEntityContext(Map<String, Object> context, Entity entity, String prefix) {
        context.put(prefix, entity.getType().name());
        context.put(prefix + "_type", entity.getType().name());
    }

    private void addItemContext(Map<String, Object> context, ItemStack item) {
        if (item == null) return;
        context.put("item", item.getType().name());
        context.put("material", item.getType().name());
        context.put("amount", item.getAmount());
    }

    private RecentSpell recentSpell(Player player) {
        RecentSpell recent = recentSpells.get(player.getUniqueId());
        if (recent == null) return null;
        if (System.currentTimeMillis() - recent.timestamp() > SPELL_CONTEXT_MILLIS) {
            recentSpells.remove(player.getUniqueId());
            return null;
        }
        return recent;
    }

    private record RecentSpell(Spell spell, long timestamp) {}
}
