package me.nagasonic.alkatraz.items.magic.itemstack;

import me.nagasonic.alkatraz.items.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.persistence.ItemDataKeys;
import me.nagasonic.alkatraz.items.magic.persistence.ItemInstanceSerializer;
import me.nagasonic.alkatraz.items.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes magic item data on Bukkit {@link ItemStack}s using PDC.
 */
public final class MagicItemStack {

    private MagicItemStack() {}

    public static boolean isMagicItem(ItemStack stack) {
        return readDefinitionKey(stack).isPresent();
    }

    public static Optional<NamespacedKey> readDefinitionKey(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(ItemDataKeys.itemDefinition(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return MagicKeys.parse(raw);
    }

    public static Optional<MagicItemInstance> readInstance(ItemStack stack) {
        Optional<NamespacedKey> definitionKey = readDefinitionKey(stack);
        if (definitionKey.isEmpty() || stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }

        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String rawInstance = pdc.get(ItemDataKeys.itemInstance(), PersistentDataType.STRING);
        if (rawInstance == null || rawInstance.isBlank()) {
            return Optional.of(MagicItemInstance.createDefault(definitionKey.get()));
        }
        return Optional.of(ItemInstanceSerializer.deserialize(rawInstance));
    }

    public static ItemStack create(ItemDefinition definition, MagicItemInstance instance) {
        ItemStack stack = new ItemStack(definition.visual().material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.setDisplayName(ColorFormat.format(definition.visual().displayName()));
        List<String> lore = new ArrayList<>();
        for (String line : definition.visual().lore()) {
            lore.add(ColorFormat.format(line));
        }
        meta.setLore(lore);

        if (definition.visual().customModelData() > 0) {
            meta.setCustomModelData(definition.visual().customModelData());
        }
        meta.setUnbreakable(definition.visual().unbreakable());
        if (definition.visual().hideAttributes()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        write(meta, definition.getKey(), instance);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack writeInstance(ItemStack stack, MagicItemInstance instance) {
        if (stack == null || !stack.hasItemMeta()) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        write(meta, instance.definitionKey(), instance);
        stack.setItemMeta(meta);
        return stack;
    }

    private static void write(ItemMeta meta, NamespacedKey definitionKey, MagicItemInstance instance) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ItemDataKeys.itemDefinition(), PersistentDataType.STRING, MagicKeys.format(definitionKey));
        pdc.set(ItemDataKeys.itemInstance(), PersistentDataType.STRING, ItemInstanceSerializer.serialize(instance));
    }

    public static Optional<ItemDefinition> readDefinition(ItemStack stack) {
        return readDefinitionKey(stack).flatMap(MagicItemRegistries.ITEM_DEFINITIONS::get);
    }
}
