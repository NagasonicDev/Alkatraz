package me.nagasonic.alkatraz.gui.implementation.editor;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class EditorChatHandler implements Listener {

    private static final Set<UUID> awaitingInput = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, BiConsumer<Player, String>> callbacks = new ConcurrentHashMap<>();
    private static EditorChatHandler instance;

    public static void prompt(Player player, String message, ItemDetailMenu menu) {
        prompt(player, message, menu, () -> {
            ItemDetailMenu detail = new ItemDetailMenu(player, menu.definition, menu.defKey);
            detail.markNeedsSave();
            detail.open();
        });
    }

    public static void prompt(Player player, String message, ItemDetailMenu menu, Runnable onComplete) {
        if (instance == null) {
            instance = new EditorChatHandler();
            Bukkit.getPluginManager().registerEvents(instance, Alkatraz.getInstance());
        }
        awaitingInput.add(player.getUniqueId());
        EditorSession session = EditorSession.get(player.getUniqueId());
        if (session != null) {
            callbacks.put(player.getUniqueId(), (p, msg) -> {
                awaitingInput.remove(p.getUniqueId());
                if (msg.equalsIgnoreCase("cancel")) {
                    p.sendMessage(ColorFormat.format("&cCancelled."));
                } else {
                    String action = session.pendingChatAction();
                    if (action != null) {
                        applyEdit(p, session, action, msg);
                    }
                }
                session.clearPendingChatAction();
                menu.markNeedsSave();
                Bukkit.getScheduler().runTask(Alkatraz.getInstance(), onComplete);
            });
        }
        player.sendMessage(ColorFormat.format("&7[Editor] " + message));
        player.sendMessage(ColorFormat.format("&7Type your input in chat, or type &ccancel &7to abort."));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingInput.contains(player.getUniqueId())) return;
        event.setCancelled(true);
        BiConsumer<Player, String> callback = callbacks.remove(player.getUniqueId());
        if (callback != null) {
            callback.accept(player, event.getMessage());
        }
    }

    private static void applyEdit(Player player, EditorSession session, String action, String value) {
        value = value.trim();
        if (action.equals("edit_display_name")) {
            session.config().set("display_name", value);
            player.sendMessage(ColorFormat.format("&aSet display name to: &f" + value));
            return;
        }
        if (action.equals("edit_material")) {
            try {
                org.bukkit.Material mat = org.bukkit.Material.valueOf(value.toUpperCase());
                session.config().set("material", mat.name());
                player.sendMessage(ColorFormat.format("&aSet material to: &f" + mat.name()));
            } catch (IllegalArgumentException e) {
                player.sendMessage(ColorFormat.format("&cInvalid material: " + value));
            }
            return;
        }
        if (action.equals("edit_dye_color")) {
            if (value.equalsIgnoreCase("none")) {
                session.config().set("dye_color", null);
                player.sendMessage(ColorFormat.format("&aRemoved dye color."));
            } else {
                session.config().set("dye_color", value.toUpperCase());
                player.sendMessage(ColorFormat.format("&aSet dye color to: &f#" + value.toUpperCase()));
            }
            return;
        }
        if (action.equals("edit_cmd")) {
            try {
                int cmd = Integer.parseInt(value);
                if (cmd <= 0) {
                    session.config().set("custom_model_data", null);
                    player.sendMessage(ColorFormat.format("&aRemoved custom model data."));
                } else {
                    session.config().set("custom_model_data", cmd);
                    player.sendMessage(ColorFormat.format("&aSet custom model data to: &f" + cmd));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ColorFormat.format("&cInvalid number: " + value));
            }
            return;
        }
        if (action.equals("edit_max_engravings")) {
            try {
                int eng = Integer.parseInt(value);
                session.config().set("max_engravings", Math.max(0, eng));
                player.sendMessage(ColorFormat.format("&aSet max engravings to: &f" + Math.max(0, eng)));
            } catch (NumberFormatException e) {
                player.sendMessage(ColorFormat.format("&cInvalid number: " + value));
            }
            return;
        }
        if (action.equals("edit_spell_id")) {
            if (value.equalsIgnoreCase("none")) {
                session.config().set("spell_id", null);
                player.sendMessage(ColorFormat.format("&aRemoved spell ID."));
            } else {
                session.config().set("spell_id", value.toLowerCase());
                player.sendMessage(ColorFormat.format("&aSet spell ID to: &f" + value.toLowerCase()));
            }
            return;
        }
        if (action.equals("add_lore_line")) {
            List<String> lore = new ArrayList<>(session.config().getStringList("lore"));
            lore.add(value);
            session.config().set("lore", lore);
            player.sendMessage(ColorFormat.format("&aAdded lore line: &f" + value));
            return;
        }
        if (action.startsWith("edit_lore_line_")) {
            int index = Integer.parseInt(action.substring("edit_lore_line_".length()));
            List<String> lore = new ArrayList<>(session.config().getStringList("lore"));
            if (index >= 0 && index < lore.size()) {
                lore.set(index, value);
                session.config().set("lore", lore);
                player.sendMessage(ColorFormat.format("&aUpdated lore line " + (index + 1) + ": &f" + value));
            }
            return;
        }
        if (action.startsWith("add_attr:")) {
            String section = action.substring("add_attr:".length());
            String[] parts = value.split(":", 2);
            if (parts.length == 2) {
                String attrKey = parts[0].trim();
                String attrVal = parts[1].trim();
                try {
                    double val = Double.parseDouble(attrVal);
                    session.config().set(section + "." + attrKey, val);
                    player.sendMessage(ColorFormat.format("&aAdded attribute: &f" + attrKey + " = " + val));
                } catch (NumberFormatException e) {
                    player.sendMessage(ColorFormat.format("&cInvalid number: " + attrVal));
                }
            } else {
                player.sendMessage(ColorFormat.format("&cInvalid format. Use key:value"));
            }
            return;
        }
        if (action.startsWith("edit_attr:")) {
            String rest = action.substring("edit_attr:".length());
            int colonIdx = rest.lastIndexOf(':');
            if (colonIdx > 0) {
                String section = rest.substring(0, colonIdx);
                String attrKey = rest.substring(colonIdx + 1);
                try {
                    double val = Double.parseDouble(value);
                    session.config().set(section + "." + attrKey, val);
                    player.sendMessage(ColorFormat.format("&aSet &f" + attrKey + " &ato: &f" + val));
                } catch (NumberFormatException e) {
                    player.sendMessage(ColorFormat.format("&cInvalid number: " + value));
                }
            }
            return;
        }
        if (action.startsWith("recipe_ingredient:")) {
            char c = action.charAt("recipe_ingredient:".length());
            try {
                org.bukkit.Material mat = org.bukkit.Material.valueOf(value.toUpperCase());
                session.config().set("recipe.ingredients." + c, mat.name());
                player.sendMessage(ColorFormat.format("&aSet ingredient '" + c + "' to: &f" + mat.name()));
            } catch (IllegalArgumentException e) {
                player.sendMessage(ColorFormat.format("&cInvalid material: " + value));
            }
            return;
        }
        if (action.equals("add_requirement")) {
            String[] parts = value.split(":", 2);
            if (parts.length >= 2) {
                String type = parts[0].trim();
                String[] fields = parts[1].split(",");
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("type", type);
                for (String field : fields) {
                    String[] kv = field.split("=", 2);
                    if (kv.length == 2) {
                        String k = kv[0].trim();
                        String v = kv[1].trim();
                        try {
                            req.put(k, Integer.parseInt(v));
                        } catch (NumberFormatException e1) {
                            try {
                                req.put(k, Double.parseDouble(v));
                            } catch (NumberFormatException e2) {
                                req.put(k, v);
                            }
                        }
                    }
                }
                List<Map<String, Object>> reqs = new ArrayList<>();
                List<?> raw = session.config().getList("requirements");
                if (raw != null) {
                    for (Object obj : raw) {
                        if (obj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) obj;
                            reqs.add(m);
                        }
                    }
                }
                reqs.add(req);
                session.config().set("requirements", reqs);
                player.sendMessage(ColorFormat.format("&aAdded requirement: " + type));
            } else {
                player.sendMessage(ColorFormat.format("&cInvalid format. Use type:field=val,field=val"));
            }
            return;
        }
        if (action.startsWith("edit_req:")) {
            int idx = Integer.parseInt(action.substring("edit_req:".length()));
            String[] parts = value.split(":", 2);
            if (parts.length >= 2) {
                String type = parts[0].trim();
                String[] fields = parts[1].split(",");
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("type", type);
                for (String field : fields) {
                    String[] kv = field.split("=", 2);
                    if (kv.length == 2) {
                        String k = kv[0].trim();
                        String v = kv[1].trim();
                        try {
                            req.put(k, Integer.parseInt(v));
                        } catch (NumberFormatException e1) {
                            try {
                                req.put(k, Double.parseDouble(v));
                            } catch (NumberFormatException e2) {
                                req.put(k, v);
                            }
                        }
                    }
                }
                List<Map<String, Object>> reqs = new ArrayList<>();
                List<?> raw = session.config().getList("requirements");
                if (raw != null) {
                    for (Object obj : raw) {
                        if (obj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) obj;
                            reqs.add(m);
                        }
                    }
                }
                if (idx >= 0 && idx < reqs.size()) {
                    reqs.set(idx, req);
                    session.config().set("requirements", reqs);
                    player.sendMessage(ColorFormat.format("&aUpdated requirement " + (idx + 1) + ": " + type));
                }
            } else {
                player.sendMessage(ColorFormat.format("&cInvalid format. Use type:field=val,field=val"));
            }
            return;
        }

        player.sendMessage(ColorFormat.format("&cUnknown action: " + action));
    }
}
