package me.nagasonic.alkatraz.items.magic.component.handler.scroll;

import me.nagasonic.alkatraz.api.magic.component.ComponentHandler;
import me.nagasonic.alkatraz.api.magic.component.ComponentType;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ScrollComponentHandler implements ComponentHandler {

    public static final ComponentType TYPE = new ComponentType.Builder()
        .key(MagicKeys.alkatraz("scroll"))
        .description("Teaches a spell when right-clicked")
        .build();

    @Override
    public ComponentType type() {
        return TYPE;
    }

    @Override
    public void onInteract(PlayerInteractEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);

        Player player = event.getPlayer();

        Object raw = definition.staticConfig().get("spell_id");
        if (raw == null) {
            player.sendMessage("Â§cThis scroll appears to be blank.");
            return;
        }

        Spell spell = SpellRegistry.getSpell(String.valueOf(raw));
        if (spell == null) {
            player.sendMessage("Â§cThis scroll contains unknown magic.");
            return;
        }

        Spellbook spellbook = new Spellbook(spell.getId());
        spellbook.use(player, stack);
    }
}
