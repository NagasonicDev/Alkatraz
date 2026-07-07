package me.nagasonic.alkatraz.api.progression.requirement;

import org.bukkit.entity.Player;

public final class RequirementContext {

    private final Player player;
    private final int targetCircle;

    public RequirementContext(Player player, int targetCircle) {
        this.player = player;
        this.targetCircle = targetCircle;
    }

    public Player getPlayer() {
        return player;
    }

    public int getTargetCircle() {
        return targetCircle;
    }
}
