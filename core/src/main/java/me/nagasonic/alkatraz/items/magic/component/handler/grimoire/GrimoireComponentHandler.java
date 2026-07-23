package me.nagasonic.alkatraz.items.magic.component.handler.grimoire;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandler;
import me.nagasonic.alkatraz.api.magic.component.ComponentType;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.gui.grimoire.GrimoireBookBuilder;
import me.nagasonic.alkatraz.gui.grimoire.GrimoireLecternState;
import me.nagasonic.alkatraz.gui.implementation.GrimoirePageMenu;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);

        ensurePagesInitialized(instance, definition);

        Player player = event.getPlayer();

        if (player.isSneaking()) {
            new GrimoirePageMenu(player, stack, instance, definition).open();
            return;
        }

        openLectern(player, stack, instance, definition);
    }

    public static void openLectern(Player player, ItemStack grimoireStack, MagicItemInstance instance, ItemDefinition definition) {
        ensurePagesInitialized(instance, definition);

        List<String> pages = GrimoirePageMenu.getPagesStatic(instance);
        int pageCount = pages.size();

        int startPage = 0;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i) != null) {
                startPage = i;
                break;
            }
        }

        String castToken = UUID.randomUUID().toString();

        ItemStack book = GrimoireBookBuilder.buildBook(pages, definition.getKey().toString());

        Alkatraz.logDebug("[Grimoire] openLectern called for " + player.getName() + " startPage=" + startPage + " pageCount=" + pageCount + " pages=" + pages);

        boolean opened = Alkatraz.getNms().openGrimoireLectern(
                player, book, "Grimoire",
                startPage, pageCount,
                newPage -> {
                    GrimoireLecternState state = GrimoireLecternState.get(player);
                    Alkatraz.logDebug("[Grimoire] onPageChange callback: newPage=" + newPage + " stateExists=" + (state != null));
                    if (state != null) state.setCurrentPage(newPage);
                }
        );

        if (opened) {
            Alkatraz.logDebug("[Grimoire] Lectern opened successfully, storing GrimoireLecternState for " + player.getName());
            GrimoireLecternState.put(player, new GrimoireLecternState(
                    player, grimoireStack, instance, definition, pageCount, startPage, castToken
            ));
        } else {
            Alkatraz.logDebug("[Grimoire] Failed to open lectern, falling back to GrimoirePageMenu");
            new GrimoirePageMenu(player, grimoireStack, instance, definition).open();
        }
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
