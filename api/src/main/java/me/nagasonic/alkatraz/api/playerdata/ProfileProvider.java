package me.nagasonic.alkatraz.api.playerdata;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Interface for loading and saving player {@link MagicProfileView} profiles.
 */
public interface ProfileProvider {

    /**
     * Loads the magic profile for the given player.
     *
     * @param player the offline player to load
     * @return the player's {@link MagicProfileView}, or {@code null} if unavailable
     */
    @Nullable
    MagicProfileView getProfile(OfflinePlayer player);

    /**
     * Loads the magic profile for the given UUID.
     *
     * @param uuid the player's UUID
     * @return the player's {@link MagicProfileView}, or {@code null} if unavailable
     */
    @Nullable
    MagicProfileView getProfile(UUID uuid);

    /**
     * Persists the given magic profile to storage.
     *
     * @param profile the profile to save
     */
    void saveProfile(MagicProfileView profile);
}
