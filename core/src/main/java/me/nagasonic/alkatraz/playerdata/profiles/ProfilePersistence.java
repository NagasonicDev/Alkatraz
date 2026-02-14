package me.nagasonic.alkatraz.playerdata.profiles;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Handles saving and loading profiles to/from YAML files
 */
public class ProfilePersistence {
    
    private static final String PLAYERDATA_FOLDER = "playerdata";

    /**
     * Gets the folder path for a player's data
     * @param uuid Player UUID
     * @return File object for player's folder
     */
    private static File getPlayerFolder(UUID uuid) {
        File folder = new File(Bukkit.getPluginsFolder(), "Alkatraz/" + PLAYERDATA_FOLDER + "/" + uuid);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    /**
     * Gets the file for a specific profile type
     * @param uuid Player UUID
     * @param profileClass Profile class
     * @return File for the profile
     */
    private static File getProfileFile(UUID uuid, Class<? extends Profile> profileClass) {
        String fileName = getProfileFileName(profileClass);
        return new File(getPlayerFolder(uuid), fileName);
    }

    /**
     * Gets the file name for a profile type
     * @param profileClass Profile class
     * @return File name (e.g., "magic.yml")
     */
    private static String getProfileFileName(Class<? extends Profile> profileClass) {
        String className = profileClass.getSimpleName().toLowerCase();
        if (className.endsWith("profile")) {
            className = className.substring(0, className.length() - 7);
        }
        return className + ".yml";
    }

    /**
     * Saves a profile to disk
     * @param profile The profile to save
     */
    public static void saveProfile(Profile profile) {
        saveProfile(profile.getOwner(), profile);
    }

    /**
     * Saves a profile to disk
     * @param uuid Player UUID
     * @param profile The profile to save
     */
    public static void saveProfile(UUID uuid, Profile profile) {
        File file = getProfileFile(uuid, profile.getClass());
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Save all integer stats
        for (String stat : profile.getInts()) {
            config.set("stats.ints." + stat, profile.getInt(stat));
        }

        // Save all double stats
        for (String stat : profile.getDoubles()) {
            config.set("stats.doubles." + stat, profile.getDouble(stat));
        }

        // Save all float stats
        for (String stat : profile.getFloats()) {
            config.set("stats.floats." + stat, profile.getFloat(stat));
        }

        // Save all boolean stats
        for (String stat : profile.getBools()) {
            config.set("stats.bools." + stat, profile.getBool(stat));
        }

        for (String stat : profile.getStrings()) {
            config.set("stats.strings." + stat, profile.getString(stat));
        }

        // Save all string set stats
        for (String stat : profile.getStringSets()) {
            config.set("stats.stringSets." + stat, new ArrayList<>(profile.getStringSet(stat)));
        }

        try {
            config.save(file);
        } catch (IOException e) {
            Alkatraz.getInstance().getLogger().severe("Failed to save profile " + profile.getClass().getSimpleName() + " for player " + uuid);
            e.printStackTrace();
        }
    }

    /**
     * Loads a profile from disk
     * @param uuid Player UUID
     * @param profileClass Profile class type
     * @return Loaded profile, or a blank profile if file doesn't exist
     */
    public static <T extends Profile> T loadProfile(UUID uuid, Class<T> profileClass) {
        File file = getProfileFile(uuid, profileClass);
        T profile = ProfileRegistry.createBlankProfile(uuid, profileClass);

        if (!file.exists()) {
            // Return blank profile with defaults if file doesn't exist
            return profile;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Load all integer stats
        if (config.contains("stats.ints")) {
            for (String stat : profile.getInts()) {
                if (config.contains("stats.ints." + stat)) {
                    profile.setInt(stat, config.getInt("stats.ints." + stat));
                }
            }
        }

        // Load all double stats
        if (config.contains("stats.doubles")) {
            for (String stat : profile.getDoubles()) {
                if (config.contains("stats.doubles." + stat)) {
                    profile.setDouble(stat, config.getDouble("stats.doubles." + stat));
                }
            }
        }

        // Load all float stats
        if (config.contains("stats.floats")) {
            for (String stat : profile.getFloats()) {
                if (config.contains("stats.floats." + stat)) {
                    profile.setFloat(stat, (float) config.getDouble("stats.floats." + stat));
                }
            }
        }

        // Load all boolean stats
        if (config.contains("stats.bools")) {
            for (String stat : profile.getBools()) {
                if (config.contains("stats.bools." + stat)) {
                    profile.setBool(stat, config.getBoolean("stats.bools." + stat));
                }
            }
        }

        // Load all string set stats
        if (config.contains("stats.strings")) {
            for (String stat : profile.getStringSets()) {
                if (config.contains("stats.strings." + stat)) {
                    List<String> strings = config.getStringList("stats.strings." + stat);
                    profile.setStringSet(stat, new HashSet<>(strings));
                }
            }
        }

        return profile;
    }

    /**
     * Loads a profile from disk
     * @param player The player
     * @param profileClass Profile class type
     * @return Loaded profile
     */
    public static <T extends Profile> T loadProfile(OfflinePlayer player, Class<T> profileClass) {
        return loadProfile(player.getUniqueId(), profileClass);
    }

    /**
     * Saves all profiles for a player
     * @param uuid Player UUID
     * @param profiles Collection of profiles to save
     */
    public static void saveAllProfiles(UUID uuid, Collection<Profile> profiles) {
        for (Profile profile : profiles) {
            saveProfile(uuid, profile);
        }
    }

    /**
     * Loads all registered profile types for a player
     * @param uuid Player UUID
     * @return Map of loaded profiles
     */
    public static Map<Class<? extends Profile>, Profile> loadAllProfiles(UUID uuid) {
        Map<Class<? extends Profile>, Profile> profiles = new HashMap<>();
        for (Class<? extends Profile> profileClass : ProfileRegistry.getRegisteredProfiles()) {
            Profile profile = loadProfile(uuid, profileClass);
            profiles.put(profileClass, profile);
        }
        return profiles;
    }

    /**
     * Deletes all profile files for a player
     * @param uuid Player UUID
     * @return true if all files were deleted successfully
     */
    public static boolean deleteAllProfiles(UUID uuid) {
        File playerFolder = getPlayerFolder(uuid);
        if (!playerFolder.exists()) return true;

        boolean success = true;
        for (File file : playerFolder.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".yml")) {
                success &= file.delete();
            }
        }
        
        // Try to delete the folder if it's empty
        if (playerFolder.listFiles().length == 0) {
            playerFolder.delete();
        }
        
        return success;
    }

    /**
     * Deletes a specific profile file for a player
     * @param uuid Player UUID
     * @param profileClass Profile class type
     * @return true if file was deleted successfully
     */
    public static boolean deleteProfile(UUID uuid, Class<? extends Profile> profileClass) {
        File file = getProfileFile(uuid, profileClass);
        return !file.exists() || file.delete();
    }

    /**
     * Checks if a profile file exists for a player
     * @param uuid Player UUID
     * @param profileClass Profile class type
     * @return true if profile file exists
     */
    public static boolean hasProfile(UUID uuid, Class<? extends Profile> profileClass) {
        return getProfileFile(uuid, profileClass).exists();
    }

    /**
     * Checks if a player has any profile files
     * @param uuid Player UUID
     * @return true if player folder exists and contains yml files
     */
    public static boolean hasAnyProfiles(UUID uuid) {
        File folder = getPlayerFolder(uuid);
        if (!folder.exists()) return false;
        
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        return files != null && files.length > 0;
    }
}
