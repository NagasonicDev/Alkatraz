package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.gui.implementation.RecipeBookMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RecipesCommand implements CommandExecutor {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang().get("commands.player_only"));
            return true;
        }
        if (!p.hasPermission("alkatraz.recipebook")) {
            p.sendMessage(lang().get("commands.recipes_permission"));
            return true;
        }
        new RecipeBookMenu(p).open();
        return true;
    }
}
