package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.api.dom.Permission;
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
        if (args.length == 1) {
            if (!Permission.hasPermission(p, Permission.COMMAND_SPELLS_OTHER)){
                p.sendMessage(lang().get("commands.no_permission"));
                return true;
            }
            if (Bukkit.getPlayer(args[0]) == null) {
                p.sendMessage(lang().get("commands.player_not_found", "name", args[0]));
                return true;
            }
            p.sendMessage(lang().get("commands.no_permission"));
            return true;
        } else if (args.length != 0){
            sender.sendMessage(lang().get("commands.spells_usage"));
            return true;
        }
        SpellsMenu menu = new SpellsMenu(p);
        menu.open();
        return true;
    }
}
