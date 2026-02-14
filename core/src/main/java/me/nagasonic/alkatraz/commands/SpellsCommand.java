package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.dom.Permission;
import me.nagasonic.alkatraz.gui.implementation.SpellsMenu;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SpellsCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)){
            sender.sendMessage(ColorFormat.format("&cOnly Players can run this command."));
            return true;
        }
        Player p = (Player) sender;
        Player target;
        if (args.length == 1) {
            if (Permission.hasPermission(p, Permission.COMMAND_SPELLS_OTHER)){
                target = Bukkit.getPlayer(args[0]);
            }else{
                p.sendMessage(ColorFormat.format("&cYou do not have permission to see another player's spells."));
                return true;
            }
        }else if (args.length == 0){
            target = (Player) sender;
        }else{
            sender.sendMessage(ColorFormat.format("&cUsage: /spells [<player>]"));
            return true;
        }
        SpellsMenu menu = new SpellsMenu(p);
        menu.open();
        return true;
    }
}
