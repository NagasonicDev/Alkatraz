package me.nagasonic.alkatraz.events;

import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player completes a research node.
 *
 * <p>This event is public so other plugins can listen for research completions,
 * e.g. to run rewards or broadcast progress. It is not cancellable.</p>
 */
public class ResearchCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final ResearchNode node;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public ResearchCompletedEvent(Player player, ResearchNode node) {
        this.player = player;
        this.node = node;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the player who completed the research node.
     *
     * @return the completing player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the completed research node.
     *
     * @return the completed node
     */
    public ResearchNode getNode() {
        return node;
    }
}
