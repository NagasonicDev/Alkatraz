package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class Config {
    private final String name;
    private File file;
    private YamlConfiguration config;
    private final Alkatraz plugin = Alkatraz.getInstance();

    public Config(String name) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid config name: " + name);
        }
        this.name = name;
        save();
    }

    public static boolean isValidName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("..")
                && !name.contains("\\")
                && !name.startsWith("/")
                && !name.contains(":");
    }

    public Config save() {
        if ((this.config == null) || (this.file == null) || !file.exists())
            return this;
        try {
            ConfigurationSection section = config.getConfigurationSection("");
            if (section != null && !section.getKeys(true).isEmpty())
                config.save(this.file);
        } catch (IOException ex) {
            Alkatraz.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Failed to save config", ex);
        }
        return this;
    }

    public YamlConfiguration get() {
        if (this.config == null)
            reload();

        return this.config;
    }

    public Config saveDefaultConfig() {
        file = new File(plugin.getDataFolder(), this.name);

        plugin.saveResource(this.name, false);

        return this;
    }

    public Config reload() {
        if (file == null)
            this.file = new File(plugin.getDataFolder(), this.name);

        this.config = YamlConfiguration.loadConfiguration(file);

        return this;
    }

    public Config copyDefaults(boolean force) {
        get().options().copyDefaults(force);
        return this;
    }

    public Config set(String key, Object value) {
        get().set(key, value);
        return this;
    }

    public Object get(String key) {
        return get().get(key);
    }
}
