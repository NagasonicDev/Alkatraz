package me.nagasonic.alkatraz.items.wands.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.items.wands.Wand;
import org.bukkit.configuration.file.YamlConfiguration;

public class WoodenWand extends Wand {

    public WoodenWand(String type) {
        super(type);
    }


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("wands/wooden_wand.yml");
        YamlConfiguration wandConfig = ConfigManager.getConfig("wands/wooden_wand.yml").get();
        loadCommonConfig(wandConfig);
    }
}
