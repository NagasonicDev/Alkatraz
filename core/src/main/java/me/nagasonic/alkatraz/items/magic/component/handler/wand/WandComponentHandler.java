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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WandComponentHandler implements ComponentHandler {

    public static final ComponentType TYPE = new ComponentType.Builder()
        .key(MagicKeys.alkatraz("wand"))
        .description("Wand that holds spell power and casting attributes")
        .build();

    private static final Map<UUID, String> castCodes = new ConcurrentHashMap<>();

    public static String getCastCode(UUID playerId) {
        return castCodes.getOrDefault(playerId, "");
    }

    public static void setCastCode(UUID playerId, String code) {
        castCodes.put(playerId, code);
    }

    public static void resetCastCode(UUID playerId) {
        castCodes.remove(playerId);
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
            
            // Display the code to the player with proper symbols
            String message = code.replace("R", "\u25C6").replace("L", "\u25C8").replace("S", "\u2756");
            Utils.sendActionBar(player, message);
            
            // Try to cast if code is complete
            if (code.length() >= 5) {
                Spell spell = SpellRegistry.getSpellByCode(code);
                tryCast(player, stack, spell);
            }
        }
    }

    @Override
    public void onSwap(PlayerSwapHandItemsEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        Player player = event.getPlayer();
        if (SpellHotbarManager.isActive(player)) return;
        if (!WandUtils.isWand(stack)) return;

        MagicProfile data = ProfileManager.getProfile(player, MagicProfile.class);
        if (data.isCasting()) return;

        String code = getCastCode(player.getUniqueId());
        code += "S";
        setCastCode(player.getUniqueId(), code);

        String message = code.replace("R", "\u25C6").replace("L", "\u25C8").replace("S", "\u2756");
        Utils.sendActionBar(player, message);

        if (code.length() >= 5) {
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
            Alkatraz.logVeryHigh("Cast attempt: " + spell.getId() + " by " + player.getName());
            if (SpellCastValidator.canCast(player, wand, spell)) {
                Alkatraz.logVeryHigh("Cast validated, executing: " + spell.getId());
                spell.cast(player, wand);
            } else {
                Alkatraz.logHigh("Cast validation failed for " + (spell != null ? spell.getId() : "null") + " by " + player.getName());
            }
        }
        resetCastCode(player.getUniqueId());
    }
}
