package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.dom.Permission;
import me.nagasonic.alkatraz.gui.implementation.recipe.RecipeCategoryMenu;
import me.nagasonic.alkatraz.gui.implementation.recipe.RecipesPermissions;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import me.nagasonic.alkatraz.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecipesCommand implements CommandExecutor, TabCompleter {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            openBook(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "unlock" -> handleSetUnlocked(sender, args, true, Permission.COMMAND_RECIPE_UNLOCK, "recipes.commands.unlock");
            case "lock" -> handleSetUnlocked(sender, args, false, Permission.COMMAND_RECIPE_LOCK, "recipes.commands.lock");
            case "give" -> handleSetUnlocked(sender, args, true, Permission.COMMAND_RECIPE_GIVE, "recipes.commands.give");
            case "check" -> handleCheck(sender, args);
            case "reload" -> handleReload(sender, args);
            default -> openBook(sender);
        }
        return true;
    }

    private void openBook(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang().get("commands.player_only"));
            return;
        }
        if (!RecipesPermissions.canView(p)) {
            p.sendMessage(lang().get("commands.recipes_permission"));
            return;
        }
        new RecipeCategoryMenu(p).open();
    }

    private void handleSetUnlocked(CommandSender sender, String[] args, boolean unlock, Permission perm, String successKey) {
        if (!Permission.hasPermission(sender, perm)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(lang().get("recipes.commands.usage"));
            return;
        }
        NamespacedKey key = resolveRecipe(sender, args[1]);
        if (key == null) return;
        Player target = resolveOptionalPlayer(sender, args, 2);
        if (target == null) return;

        if (unlock) {
            if (UnlockManager.isUnlocked(target, key.toString())) {
                sender.sendMessage(lang().get("recipes.already_unlocked", "id", key.getKey()));
                return;
            }
            UnlockManager.unlock(target, key.toString());
        } else {
            UnlockManager.lock(target, key.toString());
        }
        sender.sendMessage(lang().get(successKey, "id", key.getKey(), "player", target.getName()));
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_RECIPE_CHECK)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length != 3) {
            sender.sendMessage(lang().get("recipes.commands.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(lang().get("commands.player_not_found", "name", args[1]));
            return;
        }
        NamespacedKey key = resolveRecipe(sender, args[2]);
        if (key == null) return;

        boolean unlocked = UnlockManager.isUnlocked(target, key.toString());
        String status = lang().get(unlocked ? "recipes.unlocked" : "recipes.locked");
        sender.sendMessage(lang().get("recipes.commands.check", "id", key.getKey(), "player", target.getName(), "status", status));
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_RECIPE_RELOAD)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        RecipeRegistry.reload();
        int count = RecipeRegistry.getAll().size();
        sender.sendMessage(lang().get("recipes.commands.reload", "count", String.valueOf(count)));
    }

    private NamespacedKey resolveRecipe(CommandSender sender, String id) {
        NamespacedKey key = MagicKeys.parse(id).orElse(null);
        if (key == null || RecipeRegistry.get(key) == null) {
            sender.sendMessage(lang().get("recipes.not_found", "id", id));
            return null;
        }
        return key;
    }

    private Player resolveOptionalPlayer(CommandSender sender, String[] args, int argIndex) {
        if (args.length > argIndex) {
            Player p = Bukkit.getPlayer(args[argIndex]);
            if (p == null) {
                sender.sendMessage(lang().get("commands.player_not_found", "name", args[argIndex]));
            }
            return p;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang().get("commands.console_require_player"));
            return null;
        }
        return p;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return buildSubcommandList(sender);
        }
        String sub = args[0].toLowerCase();
        return switch (args.length) {
            case 2 -> switch (sub) {
                case "unlock" -> Permission.hasPermission(sender, Permission.COMMAND_RECIPE_UNLOCK) ? recipeIds() : List.of();
                case "lock" -> Permission.hasPermission(sender, Permission.COMMAND_RECIPE_LOCK) ? recipeIds() : List.of();
                case "give" -> Permission.hasPermission(sender, Permission.COMMAND_RECIPE_GIVE) ? recipeIds() : List.of();
                case "check" -> Permission.hasPermission(sender, Permission.COMMAND_RECIPE_CHECK) ? playerNames() : List.of();
                default -> List.of();
            };
            case 3 -> switch (sub) {
                case "unlock" -> Permission.hasPermission(sender, Permission.COMMAND_RECIPE_UNLOCK) ? playerNames() : List.of();
                case "lock" -> Permission.hasPermission(sender, Permission.COMMAND_RECIPE_LOCK) ? playerNames() : List.of();
                case "give" -> Permission.hasPermission(sender, Permission.COMMAND_RECIPE_GIVE) ? playerNames() : List.of();
                case "check" -> Permission.hasPermission(sender, Permission.COMMAND_RECIPE_CHECK) ? recipeIds() : List.of();
                default -> List.of();
            };
            default -> List.of();
        };
    }

    private List<String> buildSubcommandList(CommandSender sender) {
        var list = new ArrayList<String>();
        if (Permission.hasPermission(sender, Permission.COMMAND_RECIPE_UNLOCK)) list.add("unlock");
        if (Permission.hasPermission(sender, Permission.COMMAND_RECIPE_LOCK)) list.add("lock");
        if (Permission.hasPermission(sender, Permission.COMMAND_RECIPE_GIVE)) list.add("give");
        if (Permission.hasPermission(sender, Permission.COMMAND_RECIPE_CHECK)) list.add("check");
        if (Permission.hasPermission(sender, Permission.COMMAND_RECIPE_RELOAD)) list.add("reload");
        return list;
    }

    private List<String> recipeIds() {
        return RecipeRegistry.getAllKeys().stream().map(NamespacedKey::getKey).collect(Collectors.toList());
    }

    private List<String> playerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }
}
