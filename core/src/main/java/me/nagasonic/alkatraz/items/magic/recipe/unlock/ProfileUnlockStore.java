package me.nagasonic.alkatraz.items.magic.recipe.unlock;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ProfileUnlockStore implements UnlockStore {
    @Override
    public Set<String> loadUnlocked(UUID playerId) {
        MagicProfile profile = ProfileManager.getProfile(playerId, MagicProfile.class);
        if (profile == null) return new HashSet<>();
        return new HashSet<>(profile.getAllUnlockedRecipes());
    }

    @Override
    public void saveUnlocked(UUID playerId, Set<String> unlockedKeys) {
        MagicProfile profile = ProfileManager.getProfile(playerId, MagicProfile.class);
        if (profile == null) return;
        for (String key : new HashSet<>(profile.getAllUnlockedRecipes())) {
            profile.setRecipeUnlocked(key, false);
        }
        for (String key : unlockedKeys) {
            profile.setRecipeUnlocked(key, true);
        }
    }

    @Override
    public void setUnlocked(UUID playerId, String recipeKey, boolean unlocked) {
        MagicProfile profile = ProfileManager.getProfile(playerId, MagicProfile.class);
        if (profile == null) return;
        profile.setRecipeUnlocked(recipeKey, unlocked);
    }
}
