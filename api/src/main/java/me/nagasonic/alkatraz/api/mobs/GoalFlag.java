package me.nagasonic.alkatraz.api.mobs;

/**
 * Version-agnostic mirror of the vanilla {@code net.minecraft.world.entity.ai.goal.Goal$Flag}
 * enum. The four constants map 1:1 to {@code MOVE}, {@code LOOK}, {@code JUMP} and
 * {@code TARGET} in every Minecraft version from 1.19 through 26.2 (verified against
 * 1.19.1, 1.20.6, 1.21.11 and 26.1.2 spigot jars).
 */
public enum GoalFlag {
    MOVE,
    LOOK,
    JUMP,
    TARGET
}