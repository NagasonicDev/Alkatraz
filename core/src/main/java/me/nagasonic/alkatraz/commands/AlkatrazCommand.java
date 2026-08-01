package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
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
import me.nagasonic.alkatraz.items.magic.MagicItemBootstrap;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import de.tr7zw.changeme.nbtapi.NBT;
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

    private static LangManager lang() { return Alkatraz.getLangManager(); }
    private static final List<String> CAST_MODES = List.of("code", "hotbar");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(lang().get("commands.main_usage"));
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
            case "test" -> handleTest(sender, args);
            case "gencode" -> sender.sendMessage(Utils.genCode());
        }

        return true;
    }

    private void handleDiscover(CommandSender sender, String[] args, boolean discover) {
        Permission perm = discover ? Permission.COMMAND_DISCOVER : Permission.COMMAND_UNDISCOVER;
        if (!Permission.hasPermission(sender, perm)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(lang().get("commands.discover_usage", "command", args[0]));
            return;
        }
        Spell spell = SpellRegistry.getSpellFromName(args[1]);
        if (spell == null) {
            sender.sendMessage(lang().get("commands.spell_not_found", "name", args[1]));
            return;
        }
        Player p = resolvePlayer(sender, args, 2);
        if (p == null) return;

        ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class).setDiscoveredSpell(spell, discover);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_GIVE)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(lang().get("commands.give_usage"));
            return;
        }

        NamespacedKey parsedKey = MagicKeys.parse(args[1].toLowerCase()).orElse(null);
        if (parsedKey == null) {
            sender.sendMessage(lang().get("commands.item_not_found", "name", args[1]));
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
            sender.sendMessage(lang().get("commands.gave_item", "item", itemDef.getKey().getKey(), "player", p.getName()));
            return;
        }

        // Try engraving definition
        EngravingDefinition engravingDef = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(parsedKey).orElse(null);
        if (engravingDef != null) {
            ItemStack stack = MagicItemStack.createEngravingItem(engravingDef);
            p.getInventory().addItem(stack);
            sender.sendMessage(lang().get("commands.gave_item", "item", engravingDef.getKey().getKey(), "player", p.getName()));
            return;
        }

        sender.sendMessage(lang().get("commands.item_not_found_or_engraving", "name", args[1]));
    }

    private void handleConvert(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_CONVERT)) {
            sender.sendMessage(lang().get("commands.no_permission"));
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
        sender.sendMessage(lang().get("commands.converted_wands_for", "count", String.valueOf(converted), "player", target.getName()));
    }

    private void handleArcaneKnowledge(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_EXPERIENCE)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(lang().get("commands.ak_usage"));
            return;
        }
        Player p = resolvePlayer(sender, args, 3);
        if (p == null) return;

        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        double amount = Double.parseDouble(args[2]);

        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (amount < 0) {
                    sender.sendMessage(lang().get("commands.ak_negative"));
                    return;
                }
                data.setArcaneKnowledge(amount);
                ProgressionService.advanceWhileEligible(p);
                sender.sendMessage(lang().get("commands.ak_set", "player", p.getName(), "amount", String.valueOf(amount)));
            }
            case "add" -> {
                if (data.getArcaneKnowledge() + amount < 0) {
                    sender.sendMessage(lang().get("commands.ak_negative"));
                    return;
                }
                StatUtils.addArcaneKnowledge(p, amount);
                sender.sendMessage(lang().get("commands.ak_add", "player", p.getName(), "amount", String.valueOf(amount)));
            }
            default -> sender.sendMessage(lang().get("commands.ak_invalid_op"));
        }
    }

    private void handleCircle(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_CIRCLE)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(lang().get("commands.circle_usage"));
            return;
        }
        Player p = resolvePlayer(sender, args, 3);
        if (p == null) return;

        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        int amount = Integer.parseInt(args[2]);

        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (amount < 0 || amount > 9) {
                    sender.sendMessage(lang().get("commands.circle_cannot_set"));
                    return;
                }
                StatUtils.addCircle(p, amount - data.getCircleLevel());
                sender.sendMessage(lang().get("commands.circle_set", "player", p.getName(), "amount", String.valueOf(amount)));
            }
            case "add" -> {
                int result = data.getCircleLevel() + amount;
                if (result < 0 || result > 9) {
                    sender.sendMessage(lang().get("commands.circle_cannot_add"));
                    return;
                }
                StatUtils.addCircle(p, amount);
                sender.sendMessage(lang().get("commands.circle_add", "player", p.getName(), "amount", String.valueOf(amount), "new", String.valueOf(data.getCircleLevel())));
            }
            default -> sender.sendMessage(lang().get("commands.ak_invalid_op"));
        }
    }

    private void handleMastery(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_MASTERY)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 4 || args.length > 5) {
            sender.sendMessage(lang().get("commands.mastery_usage"));
            return;
        }
        Spell spell = SpellRegistry.getSpellFromName(args[1]);
        if (spell == null) {
            sender.sendMessage(lang().get("commands.spell_not_found", "name", args[1]));
            return;
        }
        Player p = resolvePlayer(sender, args, 4);
        if (p == null) return;

        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        double amount = Double.parseDouble(args[3]);

        switch (args[2].toLowerCase()) {
            case "add" -> {
                if (data.getSpellMastery(spell) + amount < 0) {
                    sender.sendMessage(lang().get("commands.mastery_negative"));
                    return;
                }
                int clamped = (int) Math.min(amount + data.getSpellMastery(spell), spell.getMaxMastery());
                data.setSpellMastery(spell, clamped);
                sender.sendMessage(lang().get("commands.mastery_added", "player", p.getName(), "amount", String.valueOf(amount), "spell", spell.getDisplayName()));
            }
            case "set" -> {
                if (amount < 0) {
                    sender.sendMessage(lang().get("commands.mastery_negative"));
                    return;
                }
                int clamped = (int) Math.min(amount, spell.getMaxMastery());
                data.setSpellMastery(spell, clamped);
                sender.sendMessage(lang().get("commands.mastery_set", "player", p.getName(), "spell", spell.getDisplayName(), "value", String.valueOf(data.getSpellMastery(spell))));
            }
            default -> sender.sendMessage(lang().get("commands.ak_invalid_op"));
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang().get("commands.player_only"));
            return;
        }
        if (args.length == 2) {
            if (!Permission.hasPermission(p, Permission.COMMAND_STATS_OTHER)) {
                p.sendMessage(lang().get("commands.stats_no_permission"));
                return;
            }
            new StatsMenu(p, Objects.requireNonNull(Bukkit.getPlayer(args[1]))).open();
        } else {
            new StatsMenu(p, p).open();
        }
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_RELOAD)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        ConfigManager.getDefaultConfigs().keySet().forEach(ConfigManager::reloadConfig);
        ConfigManager.reloadConfig("progression.yml");
        SpellRegistry.reload();
        ProgressionService.reload();
        MagicEntities.registerProfiles();
        MagicItemBootstrap.reload();
        sender.sendMessage(lang().get("commands.reload_success"));
    }

    private void handleSpawnMob(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_SPAWN_MOB)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(lang().get("commands.spawnmob_usage"));
            return;
        }
        MagicEntityType type = MagicEntityType.fromId(args[1]).orElse(null);
        if (type == null) {
            sender.sendMessage(lang().get("commands.spawnmob_invalid_type", "type", args[1], "valid", String.join(", ", magicMobIds())));
            return;
        }
        Player target = resolvePlayer(sender, args, 2);
        if (target == null) return;

        MagicEntities.spawn(type, target.getLocation()).ifPresentOrElse(
                spawned -> sender.sendMessage(lang().get("commands.spawnmob_success", "mob", type.getId(), "player", target.getName())),
                () -> sender.sendMessage(lang().get("commands.spawnmob_invalid_type", "type", type.getId(), "valid", String.join(", ", magicMobIds())))
        );
    }

    private void handleCastMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang().get("commands.player_only"));
            return;
        }
        if (args.length != 2 || !CAST_MODES.contains(args[1].toLowerCase())) {
            sender.sendMessage(lang().get("commands.castmode_usage"));
            return;
        }
        String mode = args[1].toLowerCase();
        ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class).setString("castMode", mode);
        sender.sendMessage(lang().get("commands.cast_mode_set", "mode", mode));
    }

    private void handleProfile(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(lang().get("commands.profile_usage"));
            return;
        }

        Player target = resolvePlayer(sender, args, 1);
        if (target == null) return;

        boolean isSelf = sender == target;
        if (!isSelf && !Permission.hasPermission(sender, Permission.COMMAND_PROFILE)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }

        MagicProfile profile = ProfileManager.getProfile(target.getUniqueId(), MagicProfile.class);

        sender.sendMessage(format("&8&m&l---&r &d" + target.getName() + "'s Profile &8&m&l---"));

        // --- Formatted section ---
        sender.sendMessage(format("&7Circle: &f" + profile.getCircleLevel()));
        sender.sendMessage(format("&7Arcane Knowledge: &f" + (int) profile.getArcaneKnowledge()));
        sender.sendMessage(format("&7Research Points: &f" + profile.getResearchPoints()));
        sender.sendMessage(format("&7Stat Points: &f" + profile.getStatPoints() + "  &7Reset Tokens: &f" + profile.getResetTokens()));
        sender.sendMessage(format("&7Cast Mode: &f" + profile.getCastMode()));
        sender.sendMessage("");
        sender.sendMessage(format("&7Mana: &b" + (int) profile.getMana() + "&7/&b" + (int) profile.getMaxMana()));
        sender.sendMessage(format("&7Mana Regen: &b" + String.format("%.1f", profile.getManaRegeneration()) + " &7per second"));

        double spellPower = profile.isDouble("spell_power") ? profile.getDouble("spell_power") : 0.0;
        sender.sendMessage(format("&7Spell Power: &f" + String.format("%.1f", spellPower)));
        sender.sendMessage("");
        sender.sendMessage(format("&7Magic Affinity: &f" + String.format("%.1f", profile.getMagicAffinity())
                + "  &7Magic Resistance: &f" + String.format("%.1f", profile.getMagicResistance())));
        sender.sendMessage(format("&7Affinities: &6Fire: &f" + String.format("%.1f", profile.getFireAffinity())
                + "  &bWater: &f" + String.format("%.1f", profile.getWaterAffinity())
                + "  &fAir: &f" + String.format("%.1f", profile.getAirAffinity())
                + "  &2Earth: &f" + String.format("%.1f", profile.getEarthAffinity())
                + "  &eLight: &f" + String.format("%.1f", profile.getLightAffinity())
                + "  &5Dark: &f" + String.format("%.1f", profile.getDarkAffinity())));
        sender.sendMessage(format("&7Resistances: &6Fire: &f" + String.format("%.1f", profile.getFireResistance())
                + "  &bWater: &f" + String.format("%.1f", profile.getWaterResistance())
                + "  &fAir: &f" + String.format("%.1f", profile.getAirResistance())
                + "  &2Earth: &f" + String.format("%.1f", profile.getEarthResistance())
                + "  &eLight: &f" + String.format("%.1f", profile.getLightResistance())
                + "  &5Dark: &f" + String.format("%.1f", profile.getDarkResistance())));
        if (profile.canCast()) {
            sender.sendMessage("");
            sender.sendMessage(format("&7Element Points: &6" + profile.getFirePoints() + " &b" + profile.getWaterPoints()
                    + " &a" + profile.getAirPoints() + " &2" + profile.getEarthPoints()
                    + " &e" + profile.getLightPoints() + " &5" + profile.getDarkPoints()));
            sender.sendMessage(format("&7Discovered Spells: &f" + profile.getAllDiscoveredSpellTypes().size()));
        }

        // --- All raw stats ---
        sender.sendMessage("");
        sender.sendMessage(format("&8&m&l---&r &7All Stats &8&m&l---"));

        java.util.TreeMap<String, String> allStats = new java.util.TreeMap<>();
        for (String name : profile.getInts()) {
            allStats.put(name, String.valueOf(profile.getInt(name)));
        }
        for (String name : profile.getDoubles()) {
            allStats.put(name, String.format("%.2f", profile.getDouble(name)));
        }
        for (String name : profile.getFloats()) {
            allStats.put(name, String.valueOf(profile.getFloat(name)));
        }
        for (String name : profile.getLongs()) {
            allStats.put(name, String.valueOf(profile.getLong(name)));
        }
        for (String name : profile.getBools()) {
            allStats.put(name, String.valueOf(profile.getBool(name)));
        }
        for (String name : profile.getStrings()) {
            String val = profile.getString(name);
            if (val != null && !val.isEmpty()) {
                allStats.put(name, val);
            }
        }
        for (String name : profile.getStringSets()) {
            java.util.Collection<String> set = profile.getStringSet(name);
            if (set != null && !set.isEmpty()) {
                allStats.put(name, String.join(", ", set));
            }
        }

        for (var entry : allStats.entrySet()) {
            sender.sendMessage(format("&7" + entry.getKey() + ": &f" + entry.getValue()));
        }

        sender.sendMessage(format("&8&m&l---&r &8&m&l---&r"));
    }

    private void handleEditor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang().get("commands.player_only"));
            return;
        }
        if (!Permission.hasPermission(p, Permission.COMMAND_EDITOR)) {
            p.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        new ItemEditorMenu(p).open();
    }

    private void handleEquipment(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang().get("commands.player_only"));
            return;
        }
        if (!Permission.hasPermission(p, Permission.COMMAND_EQUIPMENT)) {
            p.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        new EquipmentMenu(p).open();
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang().get("commands.player_only"));
            return;
        }
        Alkatraz.getNms().spawnGrimoireLectern(p);
    }

    private Player resolvePlayer(CommandSender sender, String[] args, int argIndex) {
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
            case "test" -> tabTest(sender, args);
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
        list.add("test");
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


    private List<String> tabTest(CommandSender sender, String[] args) {
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
