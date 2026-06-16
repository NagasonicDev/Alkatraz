package me.nagasonic.alkatraz.items.magic;

import me.nagasonic.alkatraz.items.magic.attribute.AttributeService;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentService;

/**
 * Global holder for initialized magic item services.
 */
public final class MagicItemServices {

    private static MagicItemService items;
    private static AttributeService attributes;
    private static EquipmentService equipment;

    private MagicItemServices() {}

    public static void initialize(MagicItemService itemService, AttributeService attributeService, EquipmentService equipmentService) {
        items = itemService;
        attributes = attributeService;
        equipment = equipmentService;
    }

    public static MagicItemService get() {
        if (items == null) {
            throw new IllegalStateException("Magic item services have not been initialized");
        }
        return items;
    }

    public static AttributeService attributes() {
        if (attributes == null) {
            throw new IllegalStateException("Attribute service has not been initialized");
        }
        return attributes;
    }

    public static EquipmentService equipment() {
        if (equipment == null) {
            throw new IllegalStateException("Equipment service has not been initialized");
        }
        return equipment;
    }
}
