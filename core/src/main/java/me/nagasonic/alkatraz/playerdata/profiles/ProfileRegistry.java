package me.nagasonic.alkatraz.playerdata.profiles;

import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Registry for all profile types in the plugin
 * Handles registration and creation of blank profiles
 */
public class ProfileRegistry {
    private static final Map<Class<? extends Profile>, ProfileFactory> registeredProfiles = new HashMap<>();

    public static void registerProfiles() {
        registerProfile(MagicProfile.class);
    }

    /**
     * Registers a profile type with a factory for creating blank instances
     * @param profileClass The profile class
     * @param factory Factory to create blank profile instances
     */
    public static <T extends Profile> void registerProfile(Class<T> profileClass, ProfileFactory factory) {
        registeredProfiles.put(profileClass, factory);
    }

    /**
     * Registers a profile type using its constructor
     * Profile class must have a constructor that accepts UUID
     * @param profileClass The profile class
     */
    public static <T extends Profile> void registerProfile(Class<T> profileClass) {
        registeredProfiles.put(profileClass, uuid -> {
            try {
                Constructor<? extends Profile> constructor = profileClass.getConstructor(UUID.class);
                return constructor.newInstance(uuid);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create blank profile for " + profileClass.getName(), e);
            }
        });
    }

    /**
     * Creates a blank profile instance for a player
     * @param uuid The player UUID
     * @param profileClass The profile class
     * @return A new blank profile instance
     */
    @SuppressWarnings("unchecked")
    public static <T extends Profile> T createBlankProfile(UUID uuid, Class<T> profileClass) {
        ProfileFactory factory = registeredProfiles.get(profileClass);
        if (factory == null) {
            throw new IllegalArgumentException("Profile type not registered: " + profileClass.getName());
        }
        return (T) factory.create(uuid);
    }

    /**
     * Checks if a profile type is registered
     * @param profileClass The profile class
     * @return true if registered
     */
    public static boolean isRegistered(Class<? extends Profile> profileClass) {
        return registeredProfiles.containsKey(profileClass);
    }

    /**
     * Gets all registered profile types
     * @return Set of registered profile classes
     */
    public static Set<Class<? extends Profile>> getRegisteredProfiles() {
        return new HashSet<>(registeredProfiles.keySet());
    }

    /**
     * Unregisters a profile type
     * @param profileClass The profile class to unregister
     * @return true if the profile was registered and removed
     */
    public static boolean unregisterProfile(Class<? extends Profile> profileClass) {
        return registeredProfiles.remove(profileClass) != null;
    }

    /**
     * Clears all registered profiles
     */
    public static void clear() {
        registeredProfiles.clear();
    }

    /**
     * Functional interface for creating blank profile instances
     */
    @FunctionalInterface
    public interface ProfileFactory {
        Profile create(UUID uuid);
    }
}
