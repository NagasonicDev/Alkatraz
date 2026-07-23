package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.Alkatraz;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public class AlkatrazPlaceholder extends PlaceholderExpansion {
    private final Map<String, Placeholder> handlers = new LinkedHashMap<>();

    @Override
    public String getIdentifier() {
        return "alkatraz";
    }

    @Override
    public String getAuthor() {
        return "Nagasonic";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    public void registerHandler(Placeholder handler) {
        handlers.put(handler.name(), handler);
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null || identifier == null) return "";

        int sep = identifier.indexOf('_');
        if (sep <= 0) return "";

        String handlerName = identifier.substring(0, sep);
        String params = identifier.substring(sep + 1);

        Placeholder handler = handlers.get(handlerName);
        if (handler == null) return "";

        try {
            return handler.onPlaceholderRequest(player, params);
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().warning("Placeholder handler threw: " + handler.name() + " params=" + params);
            return "";
        }
    }
}
