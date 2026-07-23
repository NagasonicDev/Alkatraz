package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.dom.Permission;
import me.nagasonic.alkatraz.gui.implementation.SpellsMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SpellsCommand implements CommandExecutor {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)){
            sender.sendMessage(lang().get("commands.player_only"));
            return true;
        }
        Player p = (Player) sender;
        Player target;
        if (args.length == 1) {
            if (Permission.hasPermission(p, Permission.COMMAND_SPELLS_OTHER)){
                target = Bukkit.getPlayer(args[0]);
            }else{
                p.sendMessage(lang().get("commands.no_permission"));
                return true;
            }
        }else if (args.length == 0){
            target = (Player) sender;
        }else{
            sender.sendMessage(lang().get("commands.spells_usage"));
            return true;
        }
        SpellsMenu menu = new SpellsMenu(p);
        menu.open();
        return true;
    }
}
