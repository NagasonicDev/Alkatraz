package me.nagasonic.alkatraz.items.magic.recipe.unlock;

public interface UnlockStore {
    java.util.Set<String> loadUnlocked(java.util.UUID playerId);
    void saveUnlocked(java.util.UUID playerId, java.util.Set<String> unlockedKeys);
    void setUnlocked(java.util.UUID playerId, String recipeKey, boolean unlocked);
}
