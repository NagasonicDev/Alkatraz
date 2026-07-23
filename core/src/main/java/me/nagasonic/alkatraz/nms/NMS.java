package me.nagasonic.alkatraz.nms;

import me.nagasonic.alkatraz.mobs.MagicEntityType;
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
     * Uses a per-viewer scoreboard team for colour and a raw entity metadata
     * packet to set the glowing flag on only the viewer's client.
     */
    default void setGlowing(Entity entity, Player viewer, ChatColor color) {
        Scoreboard scoreboard = viewer.getScoreboard();
        String teamName = "ge-" + viewer.getUniqueId() + "-" + entity.getUniqueId();
        Team team = scoreboard.getTeam(teamName);
        if (team != null) team.unregister();
        team = scoreboard.registerNewTeam(teamName);
        team.setColor(color);
        team.addEntry(entity.getUniqueId().toString());
        sendGlowingPacket(entity, viewer, true);
    }

    /**
     * Removes the per-player glow effect from an entity for a specific viewer.
     */
    default void unsetGlowing(Entity entity, Player viewer) {
        String teamName = "ge-" + viewer.getUniqueId() + "-" + entity.getUniqueId();
        Team team = viewer.getScoreboard().getTeam(teamName);
        if (team != null) team.unregister();
        sendGlowingPacket(entity, viewer, false);
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
