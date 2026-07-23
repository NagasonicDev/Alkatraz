package me.nagasonic.alkatraz.hooks;

import me.nagasonic.alkatraz.Alkatraz;

public abstract class PluginHook {
    protected final String pluginName;
    protected final boolean present;

    public PluginHook(String pluginName) {
        this.present = Alkatraz.getInstance().getServer().getPluginManager().getPlugin(pluginName) != null;
        this.pluginName = pluginName;
    }

    public String getPluginName() {
        return pluginName;
    }

    public boolean isPresent() {
        return present;
    }

    public abstract void ifPresent();
}
