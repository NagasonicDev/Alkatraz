package me.nagasonic.alkatraz.gui.implementation.engraving;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EngravingSession {

    private static final Map<UUID, EngravingSession> sessions = new ConcurrentHashMap<>();

    private final ItemStack targetStack;
    private final MagicItemInstance targetInstance;
    private final ItemDefinition targetDefinition;
    private NamespacedKey selectedEngravingKey;
    private NamespacedKey selectedTriggerKey;
    private ItemStack engravingItemStack;

    public EngravingSession(ItemStack targetStack, MagicItemInstance targetInstance, ItemDefinition targetDefinition) {
        this.targetStack = targetStack;
        this.targetInstance = targetInstance;
        this.targetDefinition = targetDefinition;
    }

    public static EngravingSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public static void set(UUID playerId, EngravingSession session) {
        sessions.put(playerId, session);
    }

    public static void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    public ItemStack targetStack() {
        return targetStack;
    }

    public MagicItemInstance targetInstance() {
        return targetInstance;
    }

    public ItemDefinition targetDefinition() {
        return targetDefinition;
    }

    public NamespacedKey selectedEngravingKey() {
        return selectedEngravingKey;
    }

    public void setSelectedEngravingKey(NamespacedKey key) {
        this.selectedEngravingKey = key;
    }

    public NamespacedKey selectedTriggerKey() {
        return selectedTriggerKey;
    }

    public void setSelectedTriggerKey(NamespacedKey key) {
        this.selectedTriggerKey = key;
    }

    public ItemStack engravingItemStack() {
        return engravingItemStack;
    }

    public void setEngravingItemStack(ItemStack stack) {
        this.engravingItemStack = stack;
    }
}
