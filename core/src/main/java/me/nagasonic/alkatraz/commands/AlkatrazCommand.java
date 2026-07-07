package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.mobs.MagicEntities;
import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.dom.Permission;
import me.nagasonic.alkatraz.gui.implementation.EquipmentMenu;
import me.nagasonic.alkatraz.gui.implementation.StatsMenu;
import me.nagasonic.alkatraz.gui.implementation.editor.ItemEditorMenu;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import de.tr7zw.nbtapi.NBT;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.ProgressionService;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import me.nagasonic.alkatraz.util.WandUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class AlkatrazCommand implements CommandExecutor, TabCompleter {

    private static final String NO_PERMISSION = "&cYou do not have permission to use this command.";
    private static final List<String> CAST_MODES = List.of("code", "hotbar");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(format("&cPlease add an argument. e.g /alkatraz reload"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "discoverspell" -> handleDiscover(sender, args, true);
            case "undiscoverspell" -> handleDiscover(sender, args, false);
            case "give" -> handleGive(sender, args);
            case "arcaneknowledge", "ak", "experience" -> handleArcaneKnowledge(sender, args);
            case "circle" -> handleCircle(sender, args);
            case "mastery" -> handleMastery(sender, args);
            case "stats" -> handleStats(sender, args);
            case "reload" -> handleReload(sender, args);
            case "castmode", "mode" -> handleCastMode(sender, args);
            case "spawnmob" -> handleSpawnMob(sender, args);
            case "equipment", "eq" -> handleEquipment(sender, args);
            case "convert" -> handleConvert(sender, args);
            case "profile" -> handleProfile(sender, args);
            case "editor" -> handleEditor(sender, args);
            case "gencode" -> sender.sendMessage(Utils.genCode());
        }

        return true;
    }

    private void handleDiscover(CommandSender sender, String[] args, boolean discover) {
        Permission perm = discover ? Permission.COMMAND_DISCOVER : Permission.COMMAND_UNDISCOVER;
        if (!Permission.hasPermission(sender, perm)) {
            sender.sendMessage(format(NO_PERMISSION));
            return;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(format("&cUsage: /alkatraz " + args[0] + " <spell> [player]"));
            return;
        }
        Spell spell = SpellRegistry.getSpellFromName(args[1]);
        if (spell == null) {
            sender.sendMessage(format("&cThere is no spell named " + args[1]));
            return;
        }
        Player p = resolvePlayer(sender, args, 2);
        if (p == null) return;

        ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class).setDiscoveredSpell(spell, discover);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_GIVE)) {
            sender.sendMessage(format(NO_PERMISSION));
            return;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(format("&cUsage: /alkatraz give <item> [player]"));
            return;
        }

        NamespacedKey parsedKey = MagicKeys.parse(args[1].toLowerCase()).orElse(null);
        if (parsedKey == null) {
            sender.sendMessage(format("&cThere is no item named " + args[1]));
            return;
        }

        Player p = resolvePlayer(sender, args, 2);
        if (p == null) return;

        // Try magic item definition first
        ItemDefinition itemDef = MagicItemRegistries.ITEM_DEFINITIONS.get(parsedKey).orElse(null);
        if (itemDef != null) {
            MagicItemInstance instance = MagicItemInstance.createDefault(itemDef.getKey());
            ItemStack stack = MagicItemStack.create(itemDef, instance);
            p.getInventory().addItem(stack);
            sender.sendMessage(format("&aGave " + itemDef.getKey().getKey() + " to " + p.getName()));
            return;
        }

        // Try engraving definition
        EngravingDefinition engravingDef = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(parsedKey).orElse(null);
        if (engravingDef != null) {
            ItemStack stack = MagicItemStack.createEngravingItem(engravingDef);
            p.getInventory().addItem(stack);
            sender.sendMessage(format("&aGave " + engravingDef.getKey().getKey() + " to " + p.getName()));
            return;
        }

        sender.sendMessage(format("&cThere is no item or engraving named " + args[1]));
    }

    private void handleConvert(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_CONVERT)) {
            sender.sendMessage(format(NO_PERMISSION));
            return;
        }
        Player target = resolvePlayer(sender, args, 1);
        if (target == null) return;

        PlayerInventory inv = target.getInventory();
        int converted = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            if (!WandUtils.isWand(stack)) continue;

            double magicPower = NBT.get(stack, nbt -> (Double) nbt.getDouble("magic_power"));
            int circleLimit = NBT.get(stack, nbt -> (Integer) nbt.getInteger("circle_limit"));
            double castTime = NBT.get(stack, nbt -> (Double) nbt.getDouble("casting_time"));

            NamespacedKey defKey;
            if (circleLimit >= 5 && magicPower >= 10) {
                defKey = MagicKeys.alkatraz("reinforced_wand");
            } else {
                defKey = MagicKeys.alkatraz("wooden_wand");
            }
            MagicItemInstance instance = MagicItemInstance.createDefault(defKey);
            instance.putCustomData("mana", NBT.get(stack, nbt -> (Double) nbt.getDouble("mana")));

            MagicItemStack.writeInstance(stack, instance);

            NBT.modify(stack, nbt -> {
                nbt.removeKey("wand");
                nbt.removeKey("cast_code");
                nbt.removeKey("circle_limit");
                nbt.removeKey("magic_power");
                nbt.removeKey("casting_time");
                nbt.removeKey("fire_damage");
                nbt.removeKey("air_damage");
                nbt.removeKey("earth_damage");
                nbt.removeKey("water_damage");
                nbt.removeKey("light_damage");
                nbt.removeKey("dark_damage");
            });

            inv.setItem(i, stack);
            converted++;
        }
        sender.sendMessage(format("&aConverted " + converted + " legacy wand(s) to magic items for " + target.getName() + "."));
    }

    private void handleArcaneKnowledge(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_EXPERIENCE)) {
            sender.sendMessage(format(NO_PERMISSION));
            return;
        }
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(format("&cUsage: /alkatraz arcaneknowledge set|add <number> [player]"));
            return;
        }
        Player p = resolvePlayer(sender, args, 3);
        if (p == null) return;

        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        double amount = Double.parseDouble(args[2]);

        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (amount < 0) {
                    sender.sendMessage(format("&cArcane Knowledge cannot be negative."));
                    return;
                }
                data.setArcaneKnowledge(amount);
                ProgressionService.advanceWhileEligible(p);
                sender.sendMessage(format("&aSet Arcane Knowledge of " + p.getName() + " to " + amount));
            }
            case "add" -> {
                if (data.getArcaneKnowledge() + amount < 0) {
                    sender.sendMessage(format("&cArcane Knowledge cannot be negative."));
                    return;
                }
                StatUtils.addArcaneKnowledge(p, amount);
                sender.sendMessage(format("&aAdded " + amount + " Arcane Knowledge to " + p.getName() + ". (Total: " + data.getArcaneKnowledge() + ")"));
            }
            default -> sender.sendMessage(format("&cPlease choose a valid operator: set/add."));
        }
    }

    private void handleCircle(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_CIRCLE)) {
            sender.sendMessage(format(NO_PERMISSION));
            return;
        }
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(format("&cUsage: /alkatraz circle set|add <number> [player]"));
            return;
        }
        Player p = resolvePlayer(sender, args, 3);
        if (p == null) return;

        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        int amount = Integer.parseInt(args[2]);

        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (amount < 0 || amount > 9) {
                    sender.sendMessage(format("&cCannot set beyond the circle threshold (0-9)."));
                    return;
                }
                StatUtils.addCircle(p, amount - data.getCircleLevel());
                sender.sendMessage(format("&aSet circle level of " + p.getName() + " to " + amount));
            }
            case "add" -> {
                int result = data.getCircleLevel() + amount;
                if (result < 0 || result > 9) {
                    sender.sendMessage(format("&cCannot add beyond the circle threshold (0-9)."));
                    return;
                }
                StatUtils.addCircle(p, amount);
                sender.sendMessage(format("&aAdded " + amount + " to circle level of " + p.getName() + ". (New: " + data.getCircleLevel() + ")"));
            }
            default -> sender.sendMessage(format("&cPlease choose a valid operator: set/add."));
        }
    }

    private void handleMastery(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_MASTERY)) {
            sender.sendMessage(format(NO_PERMISSION));
            return;
        }
        if (args.length < 4 || args.length > 5) {
            sender.sendMessage(format("&cUsage: /alkatraz mastery <spell> set|add <number> [player]"));
            return;
        }
        Spell spell = SpellRegistry.getSpellFromName(args[1]);
        if (spell == null) {
            sender.sendMessage(format("&cThere is no spell named " + args[1]));
            return;
        }
        Player p = resolvePlayer(sender, args, 4);
        if (p == null) return;

        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        double amount = Double.parseDouble(args[3]);

        switch (args[2].toLowerCase()) {
            case "add" -> {
                if (data.getSpellMastery(spell) + amount < 0) {
                    sender.sendMessage(format("&cSpell Mastery cannot be less than 0."));
                    return;
                }
                int clamped = (int) Math.min(amount + data.getSpellMastery(spell), spell.getMaxMastery());
                data.setSpellMastery(spell, clamped);
                sender.sendMessage(format("&aAdded " + amount + " to " + p.getName() + "'s mastery of " + spell.getDisplayName()));
            }
            case "set" -> {
                if (amount < 0) {
                    sender.sendMessage(format("&cSpell Mastery cannot be less than 0."));
                    return;
                }
                int clamped = (int) Math.min(amount, spell.getMaxMastery());
                data.setSpellMastery(spell, clamped);
                sender.sendMessage(format("&a" + p.getName() + "'s spell mastery of " + spell.getDisplayName() + " &ais now " + data.getSpellMastery(spell)));
            }
            default -> sender.sendMessage(format("&cPlease choose a valid operator: set/add."));
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(format("&cThis command can only be used by players."));
            return;
        }
        if (args.length == 2) {
            if (!Permission.hasPermission(p, Permission.COMMAND_STATS_OTHER)) {
                p.sendMessage(format("&cYou do not have permission to see another player's stats."));
                return;
            }
            new StatsMenu(p, Objects.requireNonNull(Bukkit.getPlayer(args[1]))).open();
        } else {
            new StatsMenu(p, p).open();
        }
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_RELOAD)) {
            sender.sendMessage(format(NO_PERMISSION));
            return;
        }
        ConfigManager.getDefaultConfigs().keySet().forEach(ConfigManager::reloadConfig);
        ConfigManager.reloadConfig("progression.yml");
        SpellRegistry.reload();
        ProgressionService.reload();
        MagicEntities.registerProfiles();
        sender.sendMessage(format("&aReloaded configs."));
    }

    private void handleSpawnMob(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_SPAWN_MOB)) {
            sender.sendMessage(format(NO_PERMISSION));
            return;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(format("&cUsage: /alkatraz spawnmob <mob> [player]"));
            return;
        }
        MagicEntityType type = MagicEntityType.fromId(args[1]).orElse(null);
        if (type == null) {
            sender.sendMessage(format("&cUnknown magic mob '&f" + args[1] + "&c'. Valid types: &f"
                    + String.join(", ", magicMobIds())));
            return;
        }
        Player target = resolvePlayer(sender, args, 2);
        if (target == null) return;

        MagicEntities.spawn(type, target.getLocation()).ifPresentOrElse(
                spawned -> sender.sendMessage(format("&aSpawned &f" + type.getId() + "&a at &f" + target.getName() + "&a.")),
                () -> sender.sendMessage(format("&cFailed to spawn &f" + type.getId()
                        + "&c â€” not implemented on this server version."))
        );
    }

    private void handleCastMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(format("&cThis command can only be used by players."));
            return;
        }
        if (args.length != 2 || !CAST_MODES.contains(args[1].toLowerCase())) {
            sender.sendMessage(format("&cUsage: /alkatraz castmode <code|hotbar>"));
            return;
        }
        String mode = args[1].toLowerCase();
        ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class).setString("castMode", mode);
        sender.sendMessage(format("&aCast mode set to &f" + mode + "&a."));
    }

    private void handleProfile(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(format("&cUsage: /alkatraz profile [player]"));
            return;
        }

        Player target = resolvePlayer(sender, args, 1);
        if (target == null) return;

        boolean isSelf = sender == target;
        if (!isSelf && !Permission.hasPermission(sender, Permission.COMMAND_PROFILE)) {
            sender.sendMessage(format(NO_PERMISSION));
            return;
        }

        MagicProfile profile = ProfileManager.getProfile(target.getUniqueId(), MagicProfile.class);

        String affinities = format(String.format("&6Fire: &f%.1f  &bWater: &f%.1f  &aAir: &f%.1f  &2Earth: &f%.1f  &eLight: &f%.1f  &5Dark: &f%.1f",
                profile.getFireAffinity(), profile.getWaterAffinity(), profile.getAirAffinity(),
                profile.getEarthAffinity(), profile.getLightAffinity(), profile.getDarkAffinity()));

        String resistances = format(String.format("&6Fire: &f%.1f  &bWater: &f%.1f  &aAir: &f%.1f  &2Earth: &f%.1f  &eLight: &f%.1f  &5Dark: &f%.1f",
                profile.getFireResistance(), profile.getWaterResistance(), profile.getAirResistance(),
                profile.getEarthResistance(), profile.getLightResistance(), profile.getDarkResistance()));

        sender.sendMessage(format("&8&m&l---&r &d" + target.getName() + "'s Profile &8&m&l---"));
        sender.sendMessage(format("&7Circle: &f" + profile.getCircleLevel()));
        sender.sendMessage(format("&7Arcane Knowledge: &f" + (int) profile.getArcaneKnowledge()));
        sender.sendMessage(format("&7Research Points: &f" + profile.getResearchPoints()));
        sender.sendMessage(format("&7Stat Points: &f" + profile.getStatPoints() + "  &7Reset Tokens: &f" + profile.getResetTokens()));
        sender.sendMessage("");
        sender.sendMessage(format("&7Mana: &b" + (int) profile.getMana() + "&7/&b" + (int) profile.getMaxMana()));
        sender.sendMessage(format("&7Mana Regen: &b" + String.format("%.1f", profile.getManaRegeneration()) + " &7per second"));
        sender.sendMessage("");
        sender.sendMessage(format("&7Affinities:"));
        sender.sendMessage(affinities);
        sender.sendMessage(format("&7Resistances:"));
        sender.sendMessage(resistances);
        if (profile.canCast()) {
            sender.sendMessage("");
            sender.sendMessage(format("&7Element Points: &6" + profile.getFirePoints() + " &b" + profile.getWaterPoints()
                    + " &a" + profile.getAirPoints() + " &2" + profile.getEarthPoints()
                    + " &e" + profile.getLightPoints() + " &5" + profile.getDarkPoints()));
            sender.sendMessage(format("&7Discovered Spells: &f" + profile.getAllDiscoveredSpellTypes().size()));
        }
        sender.sendMessage(format("&8&m&l---&r &8&m&l---&r"));
    }

    private void handleEditor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(format("&cThis command can only be used by players."));
            return;
        }
        if (!Permission.hasPermission(p, Permission.COMMAND_EDITOR)) {
            p.sendMessage(format(NO_PERMISSION));
            return;
        }
        new ItemEditorMenu(p).open();
    }

    private void handleEquipment(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(format("&cThis command can only be used by players."));
            return;
        }
        if (!Permission.hasPermission(p, Permission.COMMAND_EQUIPMENT)) {
            p.sendMessage(format(NO_PERMISSION));
            return;
        }
        new EquipmentMenu(p).open();
    }

    private Player resolvePlayer(CommandSender sender, String[] args, int argIndex) {
        if (args.length > argIndex) {
            Player p = Bukkit.getPlayer(args[argIndex]);
            if (p == null) {
                sender.sendMessage(format("&cCouldn't find a player named " + args[argIndex] + ". Make sure they are online."));
            }
            return p;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(format("&cYou must specify a player when running this from console."));
            return null;
        }
        return p;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return buildSubcommandList(sender);
        }

        return switch (args[0].toLowerCase()) {
            case "discoverspell" -> tabDiscover(sender, args, Permission.COMMAND_DISCOVER);
            case "undiscoverspell" -> tabDiscover(sender, args, Permission.COMMAND_UNDISCOVER);
            case "give" -> tabGive(sender, args);
            case "arcaneknowledge", "ak", "experience" -> tabSetAdd(sender, args, Permission.COMMAND_EXPERIENCE, 4);
            case "circle" -> tabSetAdd(sender, args, Permission.COMMAND_CIRCLE, 4);
            case "mastery" -> tabMastery(sender, args);
            case "stats" -> tabStats(sender, args);
            case "castmode", "mode" -> tabCastMode(sender, args);
            case "spawnmob" -> tabSpawnMob(sender, args);
            case "equipment", "eq" -> List.of();
            case "profile" -> tabProfile(sender, args);
            case "convert" -> tabConvert(sender, args);
            case "editor" -> List.of();
            default -> List.of();
        };
    }

    private List<String> buildSubcommandList(CommandSender sender) {
        var list = new java.util.ArrayList<String>();
        if (Permission.hasPermission(sender, Permission.COMMAND_DISCOVER)) list.add("discoverspell");
        if (Permission.hasPermission(sender, Permission.COMMAND_UNDISCOVER)) list.add("undiscoverspell");
        if (Permission.hasPermission(sender, Permission.COMMAND_GIVE)) list.add("give");
        if (Permission.hasPermission(sender, Permission.COMMAND_EXPERIENCE)) list.add("arcaneknowledge");
        if (Permission.hasPermission(sender, Permission.COMMAND_CIRCLE)) list.add("circle");
        if (Permission.hasPermission(sender, Permission.COMMAND_MASTERY)) list.add("mastery");
        if (Permission.hasPermission(sender, Permission.COMMAND_RELOAD)) list.add("reload");
        if (Permission.hasPermission(sender, Permission.COMMAND_SPAWN_MOB)) list.add("spawnmob");
        if (Permission.hasPermission(sender, Permission.COMMAND_CONVERT)) list.add("convert");
        if (Permission.hasPermission(sender, Permission.COMMAND_EDITOR)) list.add("editor");
        list.add("stats");
        list.add("profile");
        list.add("castmode");
        list.add("mode");
        list.add("equipment");
        list.add("eq");
        return list;
    }

    private List<String> tabDiscover(CommandSender sender, String[] args, Permission perm) {
        if (!Permission.hasPermission(sender, perm)) return List.of();
        return switch (args.length) {
            case 2 -> spellIds();
            case 3 -> playerNames();
            default -> List.of();
        };
    }

    private List<String> tabGive(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_GIVE)) return List.of();
        return switch (args.length) {
            case 2 -> {
                java.util.List<String> ids = new java.util.ArrayList<>();
                ids.addAll(MagicItemRegistries.ITEM_DEFINITIONS.values().stream().map(def -> def.getKey().getKey()).collect(Collectors.toList()));
                ids.addAll(MagicItemRegistries.ENGRAVING_DEFINITIONS.values().stream().map(def -> def.getKey().getKey()).collect(Collectors.toList()));
                yield ids;
            }
            case 3 -> playerNames();
            default -> List.of();
        };
    }

    private List<String> tabConvert(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_CONVERT)) return List.of();
        if (args.length == 2) return playerNames();
        return List.of();
    }

    private List<String> tabSetAdd(CommandSender sender, String[] args, Permission perm, int playerArgIndex) {
        if (!Permission.hasPermission(sender, perm)) return List.of();
        return switch (args.length) {
            case 2 -> List.of("add", "set");
            case 3 -> List.of();
            case 4 -> args.length == playerArgIndex ? playerNames() : List.of();
            default -> List.of();
        };
    }

    private List<String> tabMastery(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_MASTERY)) return List.of();
        return switch (args.length) {
            case 2 -> spellIds();
            case 3 -> List.of("set", "add");
            case 4 -> List.of();
            case 5 -> playerNames();
            default -> List.of();
        };
    }

    private List<String> tabProfile(CommandSender sender, String[] args) {
        if (args.length == 2) return playerNames();
        return List.of();
    }

    private List<String> tabStats(CommandSender sender, String[] args) {
        if (args.length == 2 && Permission.hasPermission(sender, Permission.COMMAND_STATS_OTHER)) return playerNames();
        return List.of();
    }

    private List<String> tabCastMode(CommandSender sender, String[] args) {
        if (args.length == 2) return CAST_MODES;
        return List.of();
    }

    private List<String> tabSpawnMob(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_SPAWN_MOB)) return List.of();
        return switch (args.length) {
            case 2 -> magicMobIds();
            case 3 -> playerNames();
            default -> List.of();
        };
    }

    private List<String> magicMobIds() {
        return Arrays.stream(MagicEntityType.values()).map(MagicEntityType::getId).collect(Collectors.toList());
    }

    private List<String> spellIds() {
        return SpellRegistry.getAllSpells().values().stream().map(Spell::getId).collect(Collectors.toList());
    }

    private List<String> playerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }
}
