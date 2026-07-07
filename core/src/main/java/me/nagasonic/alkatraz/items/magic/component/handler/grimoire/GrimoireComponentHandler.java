package me.nagasonic.alkatraz.items.magic.component.handler.grimoire;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandler;
import me.nagasonic.alkatraz.api.magic.component.ComponentType;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.gui.implementation.GrimoirePageMenu;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GrimoireComponentHandler implements ComponentHandler {

    public static final ComponentType TYPE = new ComponentType.Builder()
        .key(MagicKeys.alkatraz("grimoire"))
        .description("A book that stores spells on its pages")
        .build();

    @Override
    public ComponentType type() {
        return TYPE;
    }

    @Override
    public void onInteract(PlayerInteractEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        Alkatraz.logInfo("Grimoire onInteract called");
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);

        ensurePagesInitialized(instance, definition);

        new GrimoirePageMenu(event.getPlayer(), stack, instance, definition).open();
    }

    public static void ensurePagesInitialized(MagicItemInstance instance, ItemDefinition definition) {
        if (instance.customData().containsKey("pages")) return;

        int pageCount = 3;
        Object raw = definition.staticConfig().get("page_count");
        if (raw instanceof Number n) {
            pageCount = n.intValue();
        }

        List<String> pages = new ArrayList<>();
        for (int i = 0; i < pageCount; i++) {
            pages.add(null);
        }
        instance.putCustomData("pages", pages);
    }
}
