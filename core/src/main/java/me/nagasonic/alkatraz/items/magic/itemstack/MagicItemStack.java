package me.nagasonic.alkatraz.items.magic.itemstack;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.items.magic.config.SetBonusConfig;
import me.nagasonic.alkatraz.items.magic.lore.LoreFormatter;
import me.nagasonic.alkatraz.items.magic.persistence.ItemDataKeys;
import me.nagasonic.alkatraz.items.magic.persistence.ItemInstanceSerializer;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import me.nagasonic.alkatraz.items.magic.service.SetBonusService;
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
import java.util.UUID;
import java.lang.reflect.Method;

/**
 * Reads and writes magic item data on Bukkit {@link ItemStack}s using PDC.
 */
public final class MagicItemStack {

    private static final NamespacedKey WAND_COMPONENT = MagicKeys.alkatraz("wand");
    private static final NamespacedKey GRIMOIRE_COMPONENT = MagicKeys.alkatraz("grimoire");
    private static final NamespacedKey EQUIPMENT_COMPONENT = MagicKeys.alkatraz("equipment");
    private static final NamespacedKey SCROLL_COMPONENT = MagicKeys.alkatraz("scroll");

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    private MagicItemStack() {}

    private record StackRead(Optional<NamespacedKey> definitionKey, String rawInstance) {}

    private static StackRead readStack(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return new StackRead(Optional.empty(), null);
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(ItemDataKeys.itemDefinition(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return new StackRead(Optional.empty(), null);
        }
        Optional<NamespacedKey> definitionKey = MagicKeys.parse(raw);
        if (definitionKey.isEmpty()) {
            return new StackRead(Optional.empty(), null);
        }
        return new StackRead(definitionKey, pdc.get(ItemDataKeys.itemInstance(), PersistentDataType.STRING));
    }

    private static boolean hasComponent(ItemStack stack, NamespacedKey componentKey) {
        if (stack == null) return false;
        Optional<NamespacedKey> definitionKey = readStack(stack).definitionKey();
        if (definitionKey.isEmpty()) return false;
        return MagicItemRegistries.ITEM_DEFINITIONS.get(definitionKey.get())
                .map(def -> def.hasComponent(componentKey))
                .orElse(false);
    }

    public static boolean isMagicItem(ItemStack stack) {
        return readDefinitionKey(stack).isPresent();
    }

    public static boolean isWandDefinition(ItemStack stack) {
        return hasComponent(stack, WAND_COMPONENT);
    }

    public static boolean isGrimoireDefinition(ItemStack stack) {
        return hasComponent(stack, GRIMOIRE_COMPONENT);
    }

    public static boolean isNonStackable(ItemDefinition definition) {
        return definition.hasComponent(EQUIPMENT_COMPONENT)
                || definition.hasComponent(WAND_COMPONENT)
                || definition.hasComponent(GRIMOIRE_COMPONENT);
    }

    public static boolean isNonStackable(ItemStack stack) {
        if (stack == null) return false;
        Optional<NamespacedKey> definitionKey = readStack(stack).definitionKey();
        if (definitionKey.isEmpty()) return false;
        return MagicItemRegistries.ITEM_DEFINITIONS.get(definitionKey.get())
                .map(MagicItemStack::isNonStackable)
                .orElse(false);
    }

    public static boolean isComponent(ItemStack stack, NamespacedKey componentKey) {
        return hasComponent(stack, componentKey);
    }

    public static Optional<NamespacedKey> readDefinitionKey(ItemStack stack) {
        return readStack(stack).definitionKey();
    }

    public static Optional<MagicItemInstance> readInstance(ItemStack stack) {
        if (stack == null) {
            return Optional.empty();
        }
        StackRead read = readStack(stack);
        if (read.definitionKey().isEmpty()) {
            return Optional.empty();
        }
        String rawInstance = read.rawInstance();
        if (rawInstance == null || rawInstance.isBlank()) {
            return Optional.of(MagicItemInstance.createDefault(read.definitionKey().get()));
        }
        try {
            return Optional.of(ItemInstanceSerializer.deserialize(rawInstance));
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().warning("Skipping corrupt magic item instance: " + e.getMessage());
            return Optional.of(MagicItemInstance.createDefault(read.definitionKey().get()));
        }
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

        applyEquipmentModel(meta, definition);

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
        if (isNonStackable(definition)) {
            meta.getPersistentDataContainer().set(ItemDataKeys.itemUuid(), PersistentDataType.STRING, UUID.randomUUID().toString());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack writeInstance(ItemStack stack, MagicItemInstance instance) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        String previous = meta.getPersistentDataContainer()
                .get(ItemDataKeys.itemInstance(), PersistentDataType.STRING);
        String serialized = write(meta, instance.definitionKey(), instance);
        if (!serialized.equals(previous)) {
            MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey()).ifPresent(def -> {
                List<String> lore = buildLore(def, instance);
                meta.setLore(lore);
                applyEquipmentModel(meta, def);
            });
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static void refreshUniqueUuid(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(ItemDataKeys.itemUuid(), PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
    }

    private static String write(ItemMeta meta, NamespacedKey definitionKey, MagicItemInstance instance) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ItemDataKeys.itemDefinition(), PersistentDataType.STRING, MagicKeys.format(definitionKey));
        String serialized = ItemInstanceSerializer.serialize(instance);
        pdc.set(ItemDataKeys.itemInstance(), PersistentDataType.STRING, serialized);
        return serialized;
    }

    public static Optional<ItemDefinition> readDefinition(ItemStack stack) {
        return readDefinitionKey(stack).flatMap(MagicItemRegistries.ITEM_DEFINITIONS::get);
    }

    /** Applies current armor visuals to an already-created plugin item when supported by the server API. */
    public static boolean ensureEquipmentModel(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        ItemDefinition definition = readDefinition(stack).orElse(null);
        if (definition == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !applyEquipmentModel(meta, definition)) return false;
        stack.setItemMeta(meta);
        return true;
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
        for (String line : LoreFormatter.runeBlock(definition)) {
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

    /**
     * Selects the resource-pack equipment layer on Paper versions which expose
     * the equippable item component. Reflection keeps core loadable on the
     * older Spigot APIs this plugin still supports; those servers retain the
     * ordinary dyed-leather appearance.
     */
    private static boolean applyEquipmentModel(ItemMeta meta, ItemDefinition definition) {
        if (!definition.hasComponent(EQUIPMENT_COMPONENT)
                || !definition.visual().material().name().startsWith("LEATHER_")) {
            return false;
        }

        String itemKey = definition.getKey().getKey();
        int separator = itemKey.indexOf('_');
        if (separator <= 0) return false;
        String setName = itemKey.substring(0, separator);
        if (!setName.equals("apprentice") && !setName.equals("scholar")
                && !setName.equals("enchanter") && !setName.equals("archmage")) {
            return false;
        }

        try {
            Class<?> itemMetaApi = Class.forName("org.bukkit.inventory.meta.ItemMeta");
            Method getEquippable = itemMetaApi.getMethod("getEquippable");
            Object equippable = getEquippable.invoke(meta);
            if (equippable == null) return false;
            Class<?> equippableApi = getEquippable.getReturnType();
            String materialName = definition.visual().material().name();
            String slotName = materialName.endsWith("HELMET") ? "HEAD"
                    : materialName.endsWith("CHESTPLATE") ? "CHEST"
                    : materialName.endsWith("LEGGINGS") ? "LEGS"
                    : materialName.endsWith("BOOTS") ? "FEET" : null;
            if (slotName == null) return false;

            Class<?> slotApi = Class.forName("org.bukkit.inventory.EquipmentSlot");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object slot = Enum.valueOf((Class<? extends Enum>) slotApi.asSubclass(Enum.class), slotName);
            equippableApi.getMethod("setSlot", slotApi).invoke(equippable, slot);

            Method setModel = equippableApi.getMethod("setModel", NamespacedKey.class);
            // Hats use the custom item model itself in the head slot. Other
            // armor pieces use the set's equipment asset for their worn layers.
            setModel.invoke(equippable, materialName.endsWith("HELMET")
                    ? null : new NamespacedKey("alkatraz", setName));
            Method setEquippable = itemMetaApi.getMethod("setEquippable", equippableApi);
            setEquippable.invoke(meta, equippable);
            return true;
        } catch (Exception ignored) {
            // The legacy Bukkit API has no equippable model component.
            return false;
        }
    }

    private static String resolveTypeLabel(ItemDefinition definition) {
        if (definition.hasComponent(WAND_COMPONENT)) return "Wand";
        if (definition.hasComponent(SCROLL_COMPONENT)) return "Scroll";
        if (definition.hasComponent(GRIMOIRE_COMPONENT)) return "Grimoire";
        if (definition.hasComponent(EQUIPMENT_COMPONENT)) return "Equipment";
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
            lore.add(lang().get("magic_item.element_header"));
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

        if (definition.hasComponent(EQUIPMENT_COMPONENT)) {
            String setName = SetBonusService.getInstance().extractSetName(definition.getKey().getKey());
            SetBonusConfig.SetBonusData setData = SetBonusService.getInstance().setBonusData(setName);
            for (String line : LoreFormatter.setBonusBlock(setName, setData)) {
                lore.add(ColorFormat.format(line));
            }
        }

        for (Engraving eng : instance.engravings()) {
            Optional<EngravingDefinition> def = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(eng.engravingKey());
            if (def.isPresent()) {
                for (String line : LoreFormatter.engravingBlock(def.get(), eng.triggerKey())) {
                    lore.add(ColorFormat.format(line));
                }
            } else {
                lore.add(ColorFormat.format("&7" + StringUtils.prettifyKey(eng.engravingKey().getKey())));
            }
        }

        return lore;
    }
}
