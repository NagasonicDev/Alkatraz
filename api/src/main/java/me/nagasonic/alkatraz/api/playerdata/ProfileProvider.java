package me.nagasonic.alkatraz.api.playerdata;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface ProfileProvider {

    @Nullable
    MagicProfileView getProfile(OfflinePlayer player);

    @Nullable
    MagicProfileView getProfile(UUID uuid);

    void saveProfile(MagicProfileView profile);
}
