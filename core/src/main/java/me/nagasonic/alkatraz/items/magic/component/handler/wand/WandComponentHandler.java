package me.nagasonic.alkatraz.items.magic.component.handler.wand;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandler;
import me.nagasonic.alkatraz.api.magic.component.ComponentType;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeService;
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
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import me.nagasonic.alkatraz.util.WandUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class WandComponentHandler implements ComponentHandler {

    public static final ComponentType TYPE = new ComponentType.Builder()
        .key(MagicKeys.alkatraz("wand"))
        .description("Wand that holds spell power and casting attributes")
        .build();

    @Override
    public ComponentType type() {
        return TYPE;
    }

    @Override
    public void onEquip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        syncWandAttributesToNBT(instance, stack);
    }

    @Override
    public void onUnequip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        NBT.modify(stack, nbt -> { nbt.removeKey("wand"); });
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
            String code = NBT.get(stack, nbt -> (String) nbt.getString("cast_code"));
            if (code == null) code = "";
            
            // Handle click types
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                code += "R";
            } else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                code += "L";
            }
            
            // Update the code in NBT
            String finalCode = code;
            NBT.modify(stack, nbt -> { nbt.setString("cast_code", finalCode); });
            
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

        String code = NBT.get(stack, nbt -> (String) nbt.getString("cast_code"));
        if (code == null) code = "";

        code += "S";

        String finalCode = code;
        NBT.modify(stack, nbt -> { nbt.setString("cast_code", finalCode); });

        String message = code.replace("R", "\u25C6").replace("L", "\u25C8").replace("S", "\u2756");
        Utils.sendActionBar(player, message);

        if (code.length() >= 5) {
            Spell spell = SpellRegistry.getSpellByCode(code);
            tryCast(player, stack, spell);
        }
    }

    @Override
    public void onTrigger(TriggerContext context, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        // Handle mana display updates
        Player player = context.actor() instanceof Player ? (Player) context.actor() : null;
        syncManaDisplay(player, stack);
        // Handle cast code reset when equipping a wand
        if (player != null && context.triggerType().equals(MagicKeys.alkatraz("on_equip"))) {
            NBT.modify(stack, nbt -> { nbt.setString("cast_code", ""); });
        }
    }

    private void syncManaDisplay(Player player, ItemStack stack) {
        double currentMana = getCurrentManaFromNBT(stack);
        if (player != null) {
            NBT.modify(stack, nbt -> { nbt.setDouble("mana", currentMana); });
        }
    }

    private double getCurrentManaFromNBT(ItemStack stack) {
        return NBT.get(stack, nbt -> {
            if (nbt.hasTag("mana")) {
                return nbt.getDouble("mana");
            }
            return 100.0;
        });
    }

    public static void syncWandAttributesToNBT(MagicItemInstance instance, ItemStack stack) {
        syncStatsToNBT(stack, instance, AttributeService.getInstance());
    }

    public static void syncWandAttributesToNBT(Player player, MagicItemInstance instance, ItemStack stack) {
        double spellPower = getSpellPower(player, instance);
        writeWandNBT(stack, instance.definitionKey(), spellPower);
    }

    private static void syncStatsToNBT(ItemStack stack, MagicItemInstance instance, AttributeService attributeService) {
        double spellPower = readSpellPowerFromDefinition(instance);
        writeWandNBT(stack, instance.definitionKey(), spellPower);
    }

    private static double readSpellPowerFromDefinition(MagicItemInstance instance) {
        return me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries.ITEM_DEFINITIONS
                .get(instance.definitionKey())
                .map(def -> def.attributes().getOrDefault(MagicKeys.alkatraz("spell_power"), 0.0))
                .orElse(0.0);
    }

    public static double getSpellPower(Player player, MagicItemInstance instance) {
        return AttributeService.getInstance().get(player, MagicKeys.alkatraz("spell_power"));
    }

    public static double getSpellPower(Player player) {
        return AttributeService.getInstance().get(player, MagicKeys.alkatraz("spell_power"));
    }

    private static void writeWandNBT(ItemStack stack, NamespacedKey definitionKey, double spellPower) {
        NBT.modify(stack, nbt -> {
            nbt.setBoolean("wand", true);
            nbt.setDouble("magic_power", spellPower);
            nbt.setString("definition_key", definitionKey.toString());
            nbt.setDouble("mana", 100.0);
            if (!nbt.hasTag("cast_time_multiplier")) {
                nbt.setDouble("cast_time_multiplier", 1.0);
            }
        });
        Alkatraz.logInfo("Updated wand magic_power to " + spellPower);
    }

    public static void syncManaToNBT(ItemStack stack, double mana) {
        NBT.modify(stack, nbt -> { nbt.setDouble("mana", mana); });
    }

    public static double getManaFromNBT(ItemStack stack) {
        return NBT.get(stack, nbt -> {
            if (nbt.hasTag("mana")) {
                return nbt.getDouble("mana");
            }
            return 100.0;
        });
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
        NBT.modify(wand, nbt -> { nbt.setString("cast_code", ""); });
    }
}