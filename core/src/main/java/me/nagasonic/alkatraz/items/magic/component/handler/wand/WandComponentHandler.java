package me.nagasonic.alkatraz.items.magic.component.handler.wand;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandler;
import me.nagasonic.alkatraz.api.magic.component.ComponentType;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.playerdata.SpellHotbarManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellCastValidator;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.Utils;
import me.nagasonic.alkatraz.util.WandUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wand click-code handling.
 *
 * <p><b>Code Mode</b> is preparation/expression: R/L/S clicks write a short code
 * which selects a spell. The buffer is deliberately short-lived so a sequence
 * started before a fight cannot fire during it.
 *
 * <p><b>Hotbar Mode</b> is reactive combat: clicking the wand opens the spell
 * hotbar and the next click selects a bound spell directly; no code accumulates.
 */
public class WandComponentHandler implements ComponentHandler {

    public static final ComponentType TYPE = new ComponentType.Builder()
        .key(MagicKeys.alkatraz("wand"))
        .description("Wand that holds spell power and casting attributes")
        .build();

    /** The pending spell code and when it last changed; both live and die together. */
    private record CastCode(String code, long updatedAt) {}

    private static final Map<UUID, CastCode> castCodes = new ConcurrentHashMap<>();
    // A prepared code is a short expression, not a persistent buffer: 1.25s of idle
    // memory. Longer windows turn old combat clicks into accidental casts.
    private static final long CAST_CODE_IDLE_MS = 1_250L;

    private static volatile boolean resetListenerRegistered;

    /** Damage aborts a prepared code; death/world change are reset in CastEventListener. */
    private static void ensureResetListener() {
        if (resetListenerRegistered) return;
        synchronized (WandComponentHandler.class) {
            if (resetListenerRegistered) return;
            Bukkit.getPluginManager().registerEvents(new CastResetListener(), Alkatraz.getInstance());
            resetListenerRegistered = true;
        }
    }

    private static final class CastResetListener implements Listener {
        @EventHandler
        public void onDamage(EntityDamageEvent e) {
            if (e.getEntity() instanceof Player p) {
                resetCastCode(p.getUniqueId());
            }
        }
    }

    /** Renders the pending code as the existing glyph line and keeps it in lang. */
    private static void showCode(Player player, String code) {
        String glyphs = code.replace("R", "\u25C6").replace("L", "\u25C8").replace("S", "\u2756");
        Utils.sendActionBar(player, Alkatraz.getLangManager().get("spells.cast.code_line", "code", glyphs));
    }

    /** Lang-backed text with an inline default until the key ships in english.lang. */
    private static String message(String key, String fallback, Object... placeholders) {
        String resolved = Alkatraz.getLangManager().get(key, placeholders);
        return key.equals(resolved) ? fallback : resolved;
    }

    public static String getCastCode(UUID playerId) {
        CastCode entry = expireIfIdle(playerId);
        return entry == null ? "" : entry.code();
    }

    public static void setCastCode(UUID playerId, String code) {
        castCodes.put(playerId, new CastCode(code, System.currentTimeMillis()));
    }

    /** Aborts a prepared code with a notice; callers that already reported the outcome use clearCastCode. */
    public static void resetCastCode(UUID playerId) {
        if (castCodes.remove(playerId) == null) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            Utils.sendActionBar(player, message("cast.aborted", "Cast aborted."));
        }
    }

    /** Drops a code without a second notice when the caller already sent the outcome. */
    private static void clearCastCode(UUID playerId) {
        castCodes.remove(playerId);
    }

    private static CastCode expireIfIdle(UUID playerId) {
        CastCode entry = castCodes.get(playerId);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.updatedAt() > CAST_CODE_IDLE_MS) {
            castCodes.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                Utils.sendActionBar(player, message("cast.buffer_cleared", "Spell buffer cleared."));
            }
            return null;
        }
        return entry;
    }

    @Override
    public ComponentType type() {
        return TYPE;
    }

    @Override
    public void onEquip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        cleanLegacyWandNbt(stack);
    }

    @Override
    public void onUnequip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        cleanLegacyWandNbt(stack);
    }

    private static void cleanLegacyWandNbt(ItemStack stack) {
        NBT.modify(stack, nbt -> {
            nbt.removeKey("wand");
            nbt.removeKey("cast_code");
            nbt.removeKey("circle_limit");
            nbt.removeKey("magic_power");
            nbt.removeKey("casting_time");
            nbt.removeKey("mana");
            nbt.removeKey("cast_time_multiplier");
            nbt.removeKey("definition_key");
            nbt.removeKey("fire_damage");
            nbt.removeKey("air_damage");
            nbt.removeKey("earth_damage");
            nbt.removeKey("water_damage");
            nbt.removeKey("light_damage");
            nbt.removeKey("dark_damage");
        });
    }

    @Override
    public void onInteract(PlayerInteractEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        Player player = event.getPlayer();
        if (SpellHotbarManager.isActive(player)) return;
        
        // Skip if not a wand (should never happen)
        if (!WandUtils.isWand(stack)) return;
        ensureResetListener();

        // Enchanting table clicks are handled by EnchantingTableListener
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
            event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.ENCHANTING_TABLE) {
            return;
        }
        
        // Cancel right-click events for wands
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
        }
        
        MagicProfile data = ProfileManager.getProfile(player, MagicProfile.class);
        if (data == null) return;

        // Sneak + right-click is the voluntary abort: stop an in-flight cast or
        // discard a prepared code without requiring the player to take damage.
        if (player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (data.isCasting() && data.getCastingSpell() != null) {
                data.getCastingSpell().cancelCastByPlayer(player);
            } else {
                resetCastCode(player.getUniqueId());
            }
            return;
        }

        if (data.getCastMode().equals("hotbar") && !data.isCasting()) {
            SpellHotbarManager.enter(player, stack);
            return;
        }
        
        if (!data.isCasting()) {
            String code = getCastCode(player.getUniqueId());
            
            // Handle click types
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                code += "R";
            } else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                code += "L";
            }
            
            // Store the code in memory
            setCastCode(player.getUniqueId(), code);

            showCode(player, code);

            // Try to cast if code is complete
            if (code.length() > 5) {
                code = code.substring(code.length() - 5);
            }
            if (code.length() == 5) {
                Spell spell = SpellRegistry.getSpellByCode(code);
                tryCast(player, stack, spell);
            }
        } else {
            Utils.sendActionBar(player, Alkatraz.getLangManager().get("spells.cast.busy"));
        }
    }

    @Override
    public void onSwap(PlayerSwapHandItemsEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        Player player = event.getPlayer();
        if (SpellHotbarManager.isActive(player)) return;
        if (!WandUtils.isWand(stack)) return;

        MagicProfile data = ProfileManager.getProfile(player, MagicProfile.class);
        if (data == null) return;
        if (data.isCasting()) {
            Utils.sendActionBar(player, Alkatraz.getLangManager().get("spells.cast.busy"));
            return;
        }

        // The swap event is cancelled outside hotbar mode, so a wand held in
        // the offhand can never actually swap: its S click starts a fresh code
        // instead of extending a buffer typed with the main-hand wand.
        String code;
        if (!WandUtils.isWand(player.getInventory().getItemInMainHand())
                && WandUtils.isWand(player.getInventory().getItemInOffHand())) {
            CastCode pending = expireIfIdle(player.getUniqueId());
            if (pending != null && !pending.code().isEmpty()) {
                Utils.sendActionBar(player, message("cast.buffer_cleared", "Spell buffer cleared."));
            }
            code = "S";
        } else {
            code = getCastCode(player.getUniqueId()) + "S";
        }
        setCastCode(player.getUniqueId(), code);

        showCode(player, code);

        if (code.length() > 5) {
            code = code.substring(code.length() - 5);
        }
        if (code.length() == 5) {
            Spell spell = SpellRegistry.getSpellByCode(code);
            tryCast(player, stack, spell);
        }
    }

    @Override
    public void onTrigger(TriggerContext context, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        // Handle cast code reset when equipping a wand
        Player player = context.actor() instanceof Player ? (Player) context.actor() : null;
        if (player != null && context.triggerType().equals(MagicKeys.alkatraz("on_equip"))) {
            resetCastCode(player.getUniqueId());
        }
    }

    // ============================
    // CAST HELPERS
    // ============================
    
    private void tryCast(Player player, ItemStack wand, Spell spell) {
        if (spell != null) {
            String name = spell.getDisplayName() == null ? spell.getId() : spell.getDisplayName();
            String resolvedCode = spell.getCode() == null ? "" : spell.getCode();
            String fallback = "Spell: " + name + (resolvedCode.isEmpty() ? "" : " (" + resolvedCode + ")");
            // Chat, not the action bar: Spell.cast overwrites the bar with its
            // "casting" line in the same tick, which would hide the preview.
            player.sendMessage(message("cast.preview", fallback,
                    "code", resolvedCode, "name", name, "default", fallback));
            Alkatraz.logVeryHigh("Cast attempt: " + spell.getId() + " by " + player.getName());
            if (SpellCastValidator.canCast(player, wand, spell)) {
                Alkatraz.logVeryHigh("Cast validated, executing: " + spell.getId());
                spell.cast(player, wand);
            } else {
                Alkatraz.logHigh("Cast validation failed for " + (spell != null ? spell.getId() : "null") + " by " + player.getName());
            }
        } else {
            Utils.sendActionBar(player, Alkatraz.getLangManager().get("spells.cast.unknown_code")
                    + " " + message("cast.buffer_cleared", "Spell buffer cleared."));
        }
        clearCastCode(player.getUniqueId());
    }
}
