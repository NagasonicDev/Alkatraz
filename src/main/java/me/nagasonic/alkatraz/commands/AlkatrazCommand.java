package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.items.wands.WandRegistry;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class AlkatrazCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0){
            sender.sendMessage(format("&cPlease add an argument. e.g /alkatraz reload"));
            return true;
        }
        if (args[0].equals("discoverspell")){
            if (sender.hasPermission("alkatraz.command.discoverspell")){
                if (args.length < 2 || args.length > 3){
                    sender.sendMessage(format("&cThere are too many/few arguments: /alkatraz discoverspell <spell> <player>"));
                    return true;
                }
                Spell spell = SpellRegistry.getSpellFromName(args[1]);
                if (spell == null){
                    sender.sendMessage(format("&cThere is no spell named " + args[1]));
                    return true;
                }
                Player p = args.length == 3 ? Bukkit.getPlayer(args[2]) : (Player) sender;
                if (p == null){
                    sender.sendMessage(format("&cCouldn't find a player named " + args[2] + ". Make sure they are online."));
                    return true;
                }
                PlayerData data = DataManager.getPlayerData(p);
                data.setDiscovered(spell, true);
            }else{
                sender.sendMessage(format("&cYou do not have permission to use this command."));
                return true;
            }
        }else if (args[0].equals("undiscoverspell")){
            if (sender.hasPermission("alkatraz.command.undiscoverspell")){
                if (args.length < 2 || args.length > 3){
                    sender.sendMessage(format("&cThere are too many/few arguments: /alkatraz discoverspell <spell> <player>"));
                    return true;
                }
                Spell spell = SpellRegistry.getSpellFromName(args[1]);
                if (spell == null){
                    sender.sendMessage(format("&cThere is no spell named " + args[1]));
                    return true;
                }
                Player p = args.length == 3 ? Bukkit.getPlayer(args[2]) : (Player) sender;
                if (p == null){
                    sender.sendMessage(format("&cCouldn't find a player named " + args[2] + ". Make sure they are online."));
                    return true;
                }
                PlayerData data = DataManager.getPlayerData(p);
                data.setDiscovered(spell, false);
            }else{
                sender.sendMessage(format("&cYou do not have permission to use this command."));
                return true;
            }
        } else if (args[0].equals("give")) {
            if (sender.hasPermission("alkatraz.command.give")){
                if (args.length < 2 || args.length > 3){
                    sender.sendMessage(format("&cThere are too many/few arguments: /alkatraz give <item> <player>"));
                    return true;
                }
                Wand wand = WandRegistry.getWand(args[1].toUpperCase());
                Player p = args.length == 3 ? Bukkit.getPlayer(args[2]) : (Player) sender;
                if (p == null){
                    sender.sendMessage(format("&cCouldn't find a player named " + args[2] + ". Make sure they are online."));
                    return true;
                }
                p.getInventory().addItem(wand.getItem());
                sender.sendMessage(format("&aGave " + wand.getName() + " to " + p.getName()));
            }else{
                sender.sendMessage(format("&cYou do not have permission to use this command."));
                return true;
            }

        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1){
            List<String> list = new ArrayList<>();
            if (sender.hasPermission("discoverspell")){ list.add("discoverspell"); }
            if (sender.hasPermission("undiscoverspell")){ list.add("undiscoverspell"); }
            if (sender.hasPermission("give")){ list.add("give"); }
            return list;
        }else if (args.length == 2){
            if (args[0].equals("discoverspell") && sender.hasPermission("alkatraz.command.discoverspell")){
                List<String> list = new ArrayList<>();
                for (Spell spell : SpellRegistry.getAllSpells().values()){
                    list.add(format(spell.getDisplayName().toLowerCase().replace(" ", "_")));
                }
                return list;
            } else if (args[0].equals("undiscoverspell") && sender.hasPermission("alkatraz.command.undiscoverspell")) {
                List<String> list = new ArrayList<>();
                for (Spell spell : SpellRegistry.getAllSpells().values()){
                    list.add(format(spell.getDisplayName().toLowerCase().replace(" ", "_")));
                }
                return list;
            } else if (args[0].equals("give") && sender.hasPermission("alkatraz.command.give")) {
                List<String> list = new ArrayList<>();
                for (Wand wand : WandRegistry.getAllWands().values()){
                    list.add(format(wand.getName().toLowerCase().replace(" ", "_")));
                }
                return list;
            }
        } else if (args.length == 3) {
            if (args[0].equals("discoverspell") && sender.hasPermission("alkatraz.command.discoverspell")){
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()){
                    list.add(p.getName());
                }
                return list;
            } else if (args[0].equals("undiscoverspell") && sender.hasPermission("alkatraz.command.undiscoverspell")) {
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()){
                    list.add(p.getName());
                }
                return list;
            } else if (args[0].equals("give") && sender.hasPermission("alkatraz.command.give")) {
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()){
                    list.add(p.getName());
                }
                return list;
            }
        }
        return List.of();
    }
}
