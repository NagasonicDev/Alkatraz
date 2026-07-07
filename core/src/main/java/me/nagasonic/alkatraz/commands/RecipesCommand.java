package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.gui.implementation.RecipeBookMenu;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RecipesCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ColorFormat.format("&cOnly players can use this command."));
            return true;
        }
        if (!p.hasPermission("alkatraz.recipebook")) {
            p.sendMessage(ColorFormat.format("&cYou don't have permission to view the recipe book."));
            return true;
        }
        new RecipeBookMenu(p).open();
        return true;
    }
}
