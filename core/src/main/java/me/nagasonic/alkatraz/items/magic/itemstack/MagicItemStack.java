package me.nagasonic.alkatraz.items.magic.itemstack;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerType;
import me.nagasonic.alkatraz.items.magic.persistence.ItemDataKeys;
import me.nagasonic.alkatraz.items.magic.persistence.ItemInstanceSerializer;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes magic item data on Bukkit {@link ItemStack}s using PDC.
 */
public final class MagicItemStack {

    private MagicItemStack() {}

    public static boolean isMagicItem(ItemStack stack) {
        return readDefinitionKey(stack).isPresent();
    }

    public static boolean isWandDefinition(ItemStack stack) {
        if (stack == null || !isMagicItem(stack)) return false;
        return readDefinition(stack).map(def -> def.hasComponent(MagicKeys.alkatraz("wand"))).orElse(false);
    }

    public static boolean isGrimoireDefinition(ItemStack stack) {
        if (stack == null || !isMagicItem(stack)) return false;
        return readDefinition(stack).map(def -> def.hasComponent(MagicKeys.alkatraz("grimoire"))).orElse(false);
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
        meta.setLore(buildLore(definition, instance));

        if (definition.visual().customModelData() > 0) {
            meta.setCustomModelData(definition.visual().customModelData());
        }
        meta.setUnbreakable(definition.visual().unbreakable());
        if (definition.visual().hideAttributes()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        if (definition.visual().dyeColor() != null && meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(definition.visual().dyeColor());
        }

        for (Map.Entry<Attribute, Double> entry : definition.vanillaAttributes().entrySet()) {
            String modName = definition.getKey().getKey() + "_" + entry.getKey().name().toLowerCase();
            AttributeModifier mod = new AttributeModifier(
                    modName,
                    entry.getValue(),
                    AttributeModifier.Operation.ADD_NUMBER
            );
            meta.addAttributeModifier(entry.getKey(), mod);
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
        readDefinition(stack).ifPresent(def -> {
            List<String> lore = buildLore(def, instance);
            meta.setLore(lore);
        });
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

    public static boolean isEngravingItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(ItemDataKeys.engraving(), PersistentDataType.STRING);
    }

    public static Optional<NamespacedKey> readEngravingKey(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemDataKeys.engraving(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return Optional.empty();
        return MagicKeys.parse(raw);
    }

    public static ItemStack createEngravingItem(EngravingDefinition definition) {
        ItemStack stack = new ItemStack(definition.visual().material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.setDisplayName(ColorFormat.format(definition.visual().displayName()));
        List<String> lore = new ArrayList<>();
        for (String line : definition.visual().lore()) {
            lore.add(ColorFormat.format(line));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(
                ItemDataKeys.engraving(),
                PersistentDataType.STRING,
                MagicKeys.format(definition.getKey())
        );
        if (definition.visual().customModelData() > 0) {
            meta.setCustomModelData(definition.visual().customModelData());
        }
        if (definition.visual().unbreakable()) {
            meta.setUnbreakable(true);
        }
        if (definition.visual().hideAttributes()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        if (definition.visual().dyeColor() != null && meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(definition.visual().dyeColor());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static String prettifyKey(String key) {
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private static String resolveTypeLabel(ItemDefinition definition) {
        if (definition.hasComponent(MagicKeys.alkatraz("wand"))) return "Wand";
        if (definition.hasComponent(MagicKeys.alkatraz("scroll"))) return "Scroll";
        if (definition.hasComponent(MagicKeys.alkatraz("grimoire"))) return "Grimoire";
        if (definition.hasComponent(MagicKeys.alkatraz("equipment"))) return "Equipment";
        return null;
    }

    private static List<String> buildLore(ItemDefinition definition, MagicItemInstance instance) {
        List<String> lore = new ArrayList<>();

        String typeLabel = resolveTypeLabel(definition);
        if (typeLabel != null) {
            lore.add(ColorFormat.format("&8[" + typeLabel + "&8]"));
        }

        for (String line : definition.visual().lore()) {
            lore.add(ColorFormat.format(line));
        }

        Map<NamespacedKey, Double> attributes = definition.attributes();
        List<String> affinityLines = new ArrayList<>();
        List<String> statLines = new ArrayList<>();
        boolean hasAffinity = false;
        boolean hasStat = false;

        for (Map.Entry<NamespacedKey, Double> entry : attributes.entrySet()) {
            double value = entry.getValue();
            if (value == 0) continue;
            String keyStr = entry.getKey().getKey();
            String displayName = MagicItemRegistries.ATTRIBUTE_TYPES.get(entry.getKey())
                    .map(AttributeType::displayName)
                    .orElse(keyStr);
            String line;
            if (value > 0) {
                line = "&7" + displayName + ": &a+" + value;
            } else {
                line = "&7" + displayName + ": &c" + value;
            }
            if (keyStr.endsWith("_affinity")) {
                affinityLines.add(line);
                hasAffinity = true;
            } else {
                statLines.add(line);
                hasStat = true;
            }
        }

        if (hasAffinity) {
            lore.add("");
            lore.add(ColorFormat.format("&7&m---&r &bElement Affinities &7&m---"));
            for (String line : affinityLines) {
                lore.add(ColorFormat.format(line));
            }
        }

        if (hasStat) {
            lore.add("");
            lore.add(ColorFormat.format("&7&m---&r &6Attributes &7&m---"));
            for (String line : statLines) {
                lore.add(ColorFormat.format(line));
            }
        }

        for (Engraving eng : instance.engravings()) {
            String engName = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(eng.engravingKey())
                    .map(def -> prettifyKey(def.getKey().getKey()))
                    .orElse("?");
            String trigName = MagicItemRegistries.TRIGGER_TYPES.get(eng.triggerKey())
                    .map(t -> prettifyKey(t.getKey().getKey()))
                    .orElse("?");
            lore.add(ColorFormat.format("&7" + engName + " &8(" + trigName + ")"));
        }

        return lore;
    }
}
