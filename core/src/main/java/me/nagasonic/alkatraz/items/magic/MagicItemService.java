package me.nagasonic.alkatraz.items.magic;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.items.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.modifier.ModifierDefinition;
import me.nagasonic.alkatraz.items.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.items.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerPipeline;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Optional;

/**
 * Primary facade for creating, reading, and dispatching magic items.
 */
public final class MagicItemService {

    private final TriggerPipeline triggerPipeline;

    public MagicItemService(TriggerPipeline triggerPipeline) {
        this.triggerPipeline = triggerPipeline;
    }

    public Optional<ItemDefinition> getDefinition(NamespacedKey key) {
        return MagicItemRegistries.ITEM_DEFINITIONS.get(key);
    }

    public Optional<ModifierDefinition> getModifier(NamespacedKey key) {
        return MagicItemRegistries.MODIFIER_DEFINITIONS.get(key);
    }

    public ItemStack createItem(NamespacedKey definitionKey) {
        ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS.require(definitionKey);
        MagicItemInstance instance = MagicItemInstance.createDefault(definitionKey);
        return MagicItemStack.create(definition, instance);
    }

    public ItemStack createItem(NamespacedKey definitionKey, MagicItemInstance instance) {
        ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS.require(definitionKey);
        return MagicItemStack.create(definition, instance);
    }

    public Optional<MagicItemInstance> readInstance(ItemStack stack) {
        return MagicItemStack.readInstance(stack);
    }

    public boolean isMagicItem(ItemStack stack) {
        return MagicItemStack.isMagicItem(stack);
    }

    public void dispatchTrigger(InternalTriggerEvent event) {
        triggerPipeline.dispatch(event);
    }

    public void reloadDefinitions() {
        MagicItemBootstrap.reloadDefinitions();
    }

    public static void saveDefaultResource(String path) {
        File file = new File(Alkatraz.getInstance().getDataFolder(), path);
        if (!file.exists()) {
            Alkatraz.getInstance().saveResource(path, false);
        }
    }

    public static void loadYamlDefinitions(String folder, DefinitionConsumer consumer) {
        File directory = new File(Alkatraz.getInstance().getDataFolder(), folder);
        if (!directory.exists()) {
            return;
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String relative = folder + "/" + file.getName();
            consumer.accept(relative, ConfigManager.getConfig(relative).get());
        }
    }

    @FunctionalInterface
    public interface DefinitionConsumer {
        void accept(String path, org.bukkit.configuration.file.YamlConfiguration config);
    }
}
