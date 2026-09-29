package me.nagasonic.alkatraz.nms;

import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.ai.AiApplier;
import me.nagasonic.alkatraz.util.Skin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface NMS extends Listener {
    void setInvisible(org.bukkit.entity.Entity e, boolean invis);
    void setTransparent(org.bukkit.entity.Entity e, Player target, boolean trans);
    void fakeArmor(HumanEntity e, Player target, org.bukkit.inventory.ItemStack helmet, org.bukkit.inventory.ItemStack chest, org.bukkit.inventory.ItemStack legs, org.bukkit.inventory.ItemStack boots);
    void fakeExp(Player player, float progress, int level, int totalExp);
    void changeSkin(Player player, List<Player> viewers, Skin skin);
    void changeSkinElse(Player player, List<Player> viewers, Skin skin);
    void registerMagicEntities();
    Optional<Entity> spawnMagicEntity(String key, Location location);

    default Optional<Entity> spawnMagicEntity(MagicEntityType type, Location location) {
        return spawnMagicEntity(type.getId(), location);
    }

    /**
     * Applies a declarative goal brain to a living mob, replacing ALL current
     * goals. Vanilla mobs are supported. Core-driven: delegates to
     * {@link AiApplier#applyGoalBrain(LivingEntity, GoalBrain)}.
     *
     * @param entity the mob to re-wire (must be a {@link org.bukkit.entity.Mob})
     * @param brain  the brain to apply; an empty brain makes the mob passive
     */
    default void applyBrain(org.bukkit.entity.LivingEntity entity, me.nagasonic.alkatraz.api.mobs.GoalBrain brain) {
        AiApplier.applyGoalBrain(entity, brain);
    }

    // -----------------------------------------------------------------------
    // Core-driven AI primitives (implemented once per module, called by core)
    // -----------------------------------------------------------------------

    /**
     * Unwraps a Bukkit living entity into its native
     * {@code net.minecraft.world.entity.Mob} handle, returned as Object so core
     * stays NMS-free.
     */
    Object unwrapMob(org.bukkit.entity.LivingEntity entity);

    /** Removes every goal and target-goal from the native mob's selectors. */
    void wipeGoals(Object nativeMob);

    /** Adds a built native goal to the regular ({@code target=false}) or target selector. */
    void addGoal(Object nativeMob, Object nativeGoal, int priority, boolean target);

    /** Wraps an api {@code Goal} into a native goal using the module's own bridge + context. */
    Object bridgeCustomGoal(Object nativeMob, me.nagasonic.alkatraz.api.mobs.Goal apiGoal);
    default void onEnable(){
        // default: do nothing
    }

    /**
     * Opens a fake lectern with a written book for the player.
     *
     * @param player      the player to open the lectern for
     * @param writtenBook the written book item to display
     * @param title       the title shown in the lectern UI
     * @param startPage   the initial page index (0-based)
     * @param totalPages  total number of pages in the book
     * @param onPageChange callback invoked with the new page index when the player navigates
     * @return true if the lectern was opened, false if unsupported (caller should use fallback)
     */
    default boolean openGrimoireLectern(Player player, ItemStack writtenBook, String title,
                                         int startPage, int totalPages, Consumer<Integer> onPageChange) {
        return false;
    }

    // -----------------------------------------------------------------------
    // Fake lectern block entity (per-player visual)
    // -----------------------------------------------------------------------

    /**
     * Spawns a fake lectern block with a book in front of the player,
     * visible only to that player via client-side packets.
     *
     * @param player the player to show the fake lectern to
     */
    default void spawnGrimoireLectern(Player player) {}

    /**
     * Removes the fake lectern block that was previously spawned for the player.
     *
     * @param player the player whose fake lectern should be removed
     */
    default void removeGrimoireLectern(Player player) {}

    // -----------------------------------------------------------------------
    // Per-player coloured glowing
    // -----------------------------------------------------------------------

    /**
     * Makes an entity appear with a coloured glow outline for a specific player.
     * Uses one scoreboard team per (viewer, colour) pair - reused across every
     * entity glowing in that colour for that viewer - and a raw entity metadata
     * packet to set the glowing flag on only the viewer's client. Registering or
     * unregistering a scoreboard team is expensive and re-broadcasts to every
     * viewer, so the call is idempotent: it only touches the team when something
     * actually changed. Keying on the colour keeps distinct outline colours for
     * the same viewer while still bounding the team count to the palette in use.
     */
    default void setGlowing(Entity entity, Player viewer, ChatColor color) {
        Scoreboard scoreboard = viewer.getScoreboard();
        String teamName = glowTeamName(viewer, color);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        if (color != null && team.getColor() != color) {
            team.setColor(color);
        }
        String entry = entity.getUniqueId().toString();
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
        sendGlowingPacket(entity, viewer, true);
    }

    /**
     * Removes the per-player glow effect from an entity for a specific viewer.
     */
    default void unsetGlowing(Entity entity, Player viewer) {
        Scoreboard scoreboard = viewer.getScoreboard();
        String entry = entity.getUniqueId().toString();
        for (ChatColor color : ChatColor.values()) {
            String teamName = glowTeamName(viewer, color);
            Team team = scoreboard.getTeam(teamName);
            if (team == null) continue;
            if (team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
        sendGlowingPacket(entity, viewer, false);
    }

    /**
     * Team name for a viewer's glow teams. The colour is folded into the name so
     * each outline colour gets its own team (a team carries a single colour),
     * which keeps the per-viewer team count bounded by the palette in use rather
     * than by the number of glowing entities.
     */
    private static String glowTeamName(Player viewer, ChatColor color) {
        return "ge-" + viewer.getUniqueId() + (color == null ? "" : "-" + color.name());
    }

    /**
     * Drops the viewer's glow team entirely, clearing every glowing entry that
     * was registered through {@link #setGlowing(Entity, Player, ChatColor)}.
     * Safe to call when the viewer has no team.
     */
    default void clearGlowing(Player viewer) {
        Scoreboard scoreboard = viewer.getScoreboard();
        for (ChatColor color : ChatColor.values()) {
            Team team = scoreboard.getTeam(glowTeamName(viewer, color));
            if (team != null) {
                team.unregister();
            }
        }
    }

    /**
     * Sends a {@code ClientboundSetEntityDataPacket} to the viewer to toggle
     * the glowing flag on the given entity. The default implementation uses
     * the global Bukkit API; individual NMS modules may override this with
     * a per-player packet for stealthier glow.
     */
    default void sendGlowingPacket(Entity entity, Player viewer, boolean glowing) {
        entity.setGlowing(glowing);
    }
}
