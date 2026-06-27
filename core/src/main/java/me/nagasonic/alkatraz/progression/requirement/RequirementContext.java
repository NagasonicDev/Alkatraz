package me.nagasonic.alkatraz.progression.requirement;

import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.entity.Player;

/**
 * Evaluation state shared by progression requirement implementations.
 */
public final class RequirementContext {

    private final Player player;
    private final MagicProfile profile;
    private final int targetCircle;

    public RequirementContext(Player player, MagicProfile profile, int targetCircle) {
        this.player = player;
        this.profile = profile;
        this.targetCircle = targetCircle;
    }

    public Player getPlayer() {
        return player;
    }

    public MagicProfile getProfile() {
        return profile;
    }

    public int getTargetCircle() {
        return targetCircle;
    }
}
