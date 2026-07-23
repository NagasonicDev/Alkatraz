package me.nagasonic.alkatraz.api.progression.requirement;

import org.bukkit.entity.Player;

/**
 * Context object passed to {@link ProgressionRequirement} checks,
 * providing the player and the target Circle level being attempted.
 */
public final class RequirementContext {

    private final Player player;
    private final int targetCircle;

    /**
     * Constructs a new requirement context.
     *
     * @param player the player being evaluated
     * @param targetCircle the Circle level the player is attempting to reach
     */
    public RequirementContext(Player player, int targetCircle) {
        this.player = player;
        this.targetCircle = targetCircle;
    }

    /**
     * Returns the player being evaluated.
     *
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the Circle level the player is attempting to reach.
     *
     * @return the target Circle level
     */
    public int getTargetCircle() {
        return targetCircle;
    }
}
