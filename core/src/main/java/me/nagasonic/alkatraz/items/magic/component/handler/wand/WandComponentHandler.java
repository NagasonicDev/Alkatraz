package me.nagasonic.alkatraz.items.magic.component.handler.wand;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandler;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandlerRegistry;
import me.nagasonic.alkatraz.api.magic.component.ComponentType;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeService;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        String currentCode = NBT.get(stack, nbt -> (String) nbt.getString("cast_code"));
        String updatedCode = currentCode + "R";
        NBT.modify(stack, nbt -> { nbt.setString("cast_code", updatedCode); });
    }

    @Override
    public void onTrigger(TriggerContext context, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        syncManaDisplay(context.actor() instanceof Player player ? player : null, stack);
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
}