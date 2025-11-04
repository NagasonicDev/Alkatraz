package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.dom.Permission;
import me.nagasonic.alkatraz.gui.StatsGUI;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.items.wands.WandRegistry;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class AlkatrazCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0){
            sender.sendMessage(format("&cPlease add an argument. e.g /alkatraz reload"));
            return true;
        }
        if (args[0].equals("discoverspell")){
            if (Permission.hasPermission(sender, Permission.COMMAND_DISCOVER)){
                if (args.length < 2 || args.length > 3){
                    sender.sendMessage(format("&cThere are too many/few arguments: /alkatraz discoverspell <spell> <player>"));
                    return true;
                }
                Spell spell = SpellRegistry.getSpellFromName(args[1]);
                if (spell == null){
                    sender.sendMessage(format("&cThere is no spell named " + args[1]));
                    return true;
                }
                OfflinePlayer p = args.length == 3 ? Bukkit.getOfflinePlayer(args[2]) : (OfflinePlayer) sender;
                if (p == null){
                    sender.sendMessage(format("&cCouldn't find a player named " + args[2] + ". Make sure they are online."));
                    return true;
                }
                PlayerData data = p.isOnline() ? DataManager.getPlayerData(p) : DataManager.getConfigData(p);
                data.setDiscovered(spell, true);
                if (!p.isOnline()) {
                    DataManager.savePlayerData(p, data);
                }
            }else{
                sender.sendMessage(format("&cYou do not have permission to use this command."));
                return true;
            }
        }else if (args[0].equals("undiscoverspell")){
            if (Permission.hasPermission(sender, Permission.COMMAND_UNDISCOVER)){
                if (args.length < 2 || args.length > 3){
                    sender.sendMessage(format("&cThere are too many/few arguments: /alkatraz discoverspell <spell> <player>"));
                    return true;
                }
                Spell spell = SpellRegistry.getSpellFromName(args[1]);
                if (spell == null){
                    sender.sendMessage(format("&cThere is no spell named " + args[1]));
                    return true;
                }
                OfflinePlayer p = args.length == 3 ? Bukkit.getOfflinePlayer(args[2]) : (OfflinePlayer) sender;
                if (p == null){
                    sender.sendMessage(format("&cCouldn't find a player named " + args[2] + ". Make sure they are online."));
                    return true;
                }
                PlayerData data = p.isOnline() ? DataManager.getPlayerData(p) : DataManager.getConfigData(p);
                data.setDiscovered(spell, false);
                if (!p.isOnline()) {
                    DataManager.savePlayerData(p, data);
                }
            }else{
                sender.sendMessage(format("&cYou do not have permission to use this command."));
                return true;
            }
        } else if (args[0].equals("give")) {
            if (Permission.hasPermission(sender, Permission.COMMAND_GIVE)){
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

        } else if (args[0].equals("experience")) {
            if (Permission.hasPermission(sender, Permission.COMMAND_EXPERIENCE)){
                // alkatraz experience set/add
                if (args.length < 3 || args.length > 4){
                    sender.sendMessage(format("&cThere are too many/few arguments: /alkatraz experience set|add <number> <player>"));
                    return true;
                }
                if (args[1].equals("set")){
                    OfflinePlayer p = args.length == 3 ? (OfflinePlayer) sender : Bukkit.getOfflinePlayer(args[3]);
                    PlayerData data = p.isOnline() ? DataManager.getPlayerData(p) : DataManager.getConfigData(p);
                    if (Double.parseDouble(args[2]) > DataManager.requiredExperience(data.getCircle() + 1) && data.getCircle() < 9){
                        sender.sendMessage("&cCannot set beyond the required experience threshold.");
                        return true;
                    }
                    data.setExperience(Double.parseDouble(args[2]));
                    sender.sendMessage(format("&aSet magic experience of " + p.getName() + " to " + args[2]));
                    return true;
                } else if (args[1].equals("add")) {
                    OfflinePlayer p = args.length == 3 ? (OfflinePlayer) sender : Bukkit.getOfflinePlayer(args[3]);
                    PlayerData data = p.isOnline() ? DataManager.getPlayerData(p) : DataManager.getConfigData(p);
                    if (data.getExperience() + Double.parseDouble(args[2]) < 0){
                        sender.sendMessage("&cCannot have negative experience, please change circle level with /alkatraz circle.");
                        return true;
                    }
                    DataManager.addExperience(p, Double.parseDouble(args[2]));
                    sender.sendMessage(format("&aAdded " + args[2] + " to magic experience of " + p.getName() + ". (Total: " + data.getExperience() + ")"));
                    return true;
                }else{
                    sender.sendMessage(format("&cPlease choose a valid operator: set/add."));
                    return true;
                }
            }else{
                sender.sendMessage(format("&cYou do not have permission to use this command."));
                return true;
            }
        } else if (args[0].equals("circle")) {
            if (Permission.hasPermission(sender, Permission.COMMAND_CIRCLE)){
                // alkatraz circle set/add <number> <player>
                if (args.length < 3 || args.length > 4){
                    sender.sendMessage(format("&cThere are too many/few arguments: /alkatraz circle set|add <number> <player>"));
                    return true;
                }
                if (args[1].equals("set")){
                    OfflinePlayer p = args.length == 3 ? (OfflinePlayer) sender : Bukkit.getOfflinePlayer(args[3]);
                    PlayerData data = p.isOnline() ? DataManager.getPlayerData(p) : DataManager.getConfigData(p);
                    if (Integer.parseInt(args[2]) < 0 || Integer.parseInt(args[2]) > 9){
                        sender.sendMessage("&cCannot set beyond the circle threshold (0-9).");
                        return true;
                    }
                    DataManager.addCircle(p.getPlayer(), Integer.parseInt(args[2]) - data.getCircle());
                    sender.sendMessage(format("&aSet circle level of " + p.getName() + " to " + args[2]));
                    if (!p.isOnline()) {
                        DataManager.savePlayerData(p, data);
                    }
                    return true;
                } else if (args[1].equals("add")) {
                    OfflinePlayer p = args.length == 3 ? (OfflinePlayer) sender : Bukkit.getOfflinePlayer(args[3]);
                    PlayerData data = p.isOnline() ? DataManager.getPlayerData(p) : DataManager.getConfigData(p);
                    if (data.getCircle() + Integer.parseInt(args[2]) < 0 || data.getCircle() + Integer.parseInt(args[2]) > 9){
                        sender.sendMessage("&cCannot add beyond the circle threshold. (0-9)");
                        return true;
                    }
                    DataManager.addCircle(p.getPlayer(), Integer.parseInt(args[2]));
                    sender.sendMessage(format("&aAdded " + args[2] + " to circle level of " + p.getName() + ". (New: " + data.getCircle() + ")"));
                    if (!p.isOnline()) {
                        DataManager.savePlayerData(p, data);
                    }
                    return true;
                }else{
                    sender.sendMessage(format("&cPlease choose a valid operator: set/add."));
                    return true;
                }
            }else{
                sender.sendMessage(format("&cYou do not have permission to use this command."));
                return true;
            }
        } else if (args[0].equals("mastery")) {
            if (Permission.hasPermission(sender, Permission.COMMAND_MASTERY)){
                // alkatraz mastery <spell> add|set <number> <player>
                if (args.length < 4 || args.length > 5){
                    sender.sendMessage(format("&cThere are too many/few arguments: /alkatraz mastery <spell> add|set <number> <player>"));
                    return true;
                }
                Spell spell = SpellRegistry.getSpellFromName(args[1]);
                if (spell == null){
                    sender.sendMessage(format("&cThere is no spell named " + args[1]));
                    return true;
                }
                OfflinePlayer p = args.length == 4 ? (OfflinePlayer) sender : Bukkit.getOfflinePlayer(args[3]);
                PlayerData data = p.isOnline() ? DataManager.getPlayerData(p) : DataManager.getConfigData(p);
                double amount = Double.parseDouble(args[3]);
                if (args[2].equals("add")){
                    if (amount + data.getSpellMastery(spell) > spell.getMaxMastery()){
                        data.setSpellMastery(spell, spell.getMaxMastery());
                        sender.sendMessage(format("&a" + p.getName() + "'s spell mastery of " + spell.getDisplayName() + " &ais now " + data.getSpellMastery(spell)));
                        return true;
                    }else{
                        if (amount + data.getSpellMastery(spell) < 0){
                            sender.sendMessage(format("&cSpell Mastery cannot be less than 0."));
                            return true;
                        }
                        DataManager.addSpellMastery(p, spell, (int) amount);
                        sender.sendMessage(format("&aAdded " + amount + " to " + p.getName() + "'s mastery of " + spell.getDisplayName()));
                        return true;
                    }
                } else if (args[2].equals("set")) {
                    if (amount > spell.getMaxMastery()){
                        data.setSpellMastery(spell, spell.getMaxMastery());
                        sender.sendMessage(format("&a" + p.getName() + "'s spell mastery of " + spell.getDisplayName() + " &ais now " + data.getSpellMastery(spell)));
                        return true;
                    }else{
                        if (amount < 0){
                            sender.sendMessage(format("&cSpell Mastery cannot be less than 0."));
                            return true;
                        }
                        data.setSpellMastery(spell, (int) amount);
                        sender.sendMessage(format("&a" + p.getName() + "'s spell mastery of " + spell.getDisplayName() + " &ais now " + data.getSpellMastery(spell)));
                        return true;
                    }
                }
            }

        } else if (args[0].equals("stats")) {
            if (args.length == 1 || args.length == 2){
                Player p = (Player) sender;
                if (args.length == 2){
                    if (Permission.hasPermission(p, Permission.COMMAND_STATS_OTHER)){
                        StatsGUI.createGUI(p, Objects.requireNonNull(Bukkit.getPlayer(args[1])));
                    }else{
                        p.sendMessage(format("&cYou do not have permission to see another player's stats."));
                    }
                }else{
                    StatsGUI.createGUI(p, p);
                }
            }
        } else if (args[0].equals("reload")) {
            if (Permission.hasPermission(sender, Permission.COMMAND_RELOAD)){
                if (args.length == 1){
                    SpellRegistry.reload();
                    sender.sendMessage(format("&aReloaded configs."));
                }
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1){
            List<String> list = new ArrayList<>();
            if (Permission.hasPermission(sender, Permission.COMMAND_DISCOVER)){ list.add("discoverspell"); }
            if (Permission.hasPermission(sender, Permission.COMMAND_UNDISCOVER)){ list.add("undiscoverspell"); }
            if (Permission.hasPermission(sender, Permission.COMMAND_GIVE)){ list.add("give"); }
            if (Permission.hasPermission(sender, Permission.COMMAND_EXPERIENCE)){ list.add("experience"); }
            if (Permission.hasPermission(sender, Permission.COMMAND_CIRCLE)){ list.add("circle"); }
            if (Permission.hasPermission(sender, Permission.COMMAND_MASTERY)){ list.add("mastery"); }
            list.add("stats");

            return list;
        }else if (args.length == 2){
            if (args[0].equals("discoverspell") && Permission.hasPermission(sender, Permission.COMMAND_DISCOVER)){
                List<String> list = new ArrayList<>();
                for (Spell spell : SpellRegistry.getAllSpells().values()){
                    list.add(spell.getId());
                }
                return list;
            } else if (args[0].equals("undiscoverspell") && Permission.hasPermission(sender, Permission.COMMAND_UNDISCOVER)) {
                List<String> list = new ArrayList<>();
                for (Spell spell : SpellRegistry.getAllSpells().values()){
                    list.add(spell.getId());
                }
                return list;
            } else if (args[0].equals("give") && Permission.hasPermission(sender, Permission.COMMAND_GIVE)) {
                List<String> list = new ArrayList<>();
                for (Wand wand : WandRegistry.getAllWands().values()){
                    list.add(format(wand.getName().toLowerCase().replace(" ", "_")));
                }
                return list;
            } else if (args[0].equals("experience") && Permission.hasPermission(sender, Permission.COMMAND_EXPERIENCE)) {
                return List.of("add", "set");
            } else if (args[0].equals("circle") && Permission.hasPermission(sender, Permission.COMMAND_CIRCLE)) {
                return List.of("add", "set");
            } else if (args[0].equals("mastery") && Permission.hasPermission(sender, Permission.COMMAND_MASTERY)) {
                List<String> list = new ArrayList<>();
                for (Spell spell : SpellRegistry.getAllSpells().values()){
                    list.add(spell.getId());
                }
                return list;
            } else if (args[0].equals("stats") && Permission.hasPermission(sender, Permission.COMMAND_STATS_OTHER)) {
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()){
                    list.add(p.getName());
                }
                return list;
            }
        } else if (args.length == 3) {
            if (args[0].equals("discoverspell") && Permission.hasPermission(sender, Permission.COMMAND_DISCOVER)){
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()){
                    list.add(p.getName());
                }
                return list;
            } else if (args[0].equals("undiscoverspell") && Permission.hasPermission(sender, Permission.COMMAND_UNDISCOVER)) {
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()){
                    list.add(p.getName());
                }
                return list;
            } else if (args[0].equals("give") && Permission.hasPermission(sender, Permission.COMMAND_GIVE)) {
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()){
                    list.add(p.getName());
                }
                return list;
            }else if (args[0].equals("experience") && Permission.hasPermission(sender, Permission.COMMAND_EXPERIENCE)){
                return List.of("");
            }else if (args[0].equals("circle") && Permission.hasPermission(sender, Permission.COMMAND_CIRCLE)){
                return List.of("");
            } else if (args[0].equals("mastery") && Permission.hasPermission(sender, Permission.COMMAND_MASTERY)) {
                return List.of("set", "add");
            }
        } else if (args.length == 4) {
            if (args[0].equals("experience") && Permission.hasPermission(sender, Permission.COMMAND_EXPERIENCE)){
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()){
                    list.add(p.getName());
                }
                return list;
            }else if (args[0].equals("circle") && Permission.hasPermission(sender, Permission.COMMAND_CIRCLE)){
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()){
                    list.add(p.getName());
                }
                return list;
            } else if (args[0].equals("mastery") && Permission.hasPermission(sender, Permission.COMMAND_MASTERY)) {
                return List.of();
            }
        } else if (args.length == 5) {
            if (args[0].equals("mastery") && Permission.hasPermission(sender, Permission.COMMAND_MASTERY)){
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
