package me.nagasonic.alkatraz.commands;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.events.SpellDiscoveredEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.mobs.MagicEntities;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.api.mobs.MagicBrainService;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.config.ModifierConfig;
import me.nagasonic.alkatraz.config.ParticleConfig;import me.nagasonic.alkatraz.api.dom.Permission;
import me.nagasonic.alkatraz.gui.GUIItemRegistry;
import me.nagasonic.alkatraz.gui.implementation.EquipmentMenu;
import me.nagasonic.alkatraz.gui.implementation.StatsMenu;
import me.nagasonic.alkatraz.gui.implementation.editor.ItemEditorMenu;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.MagicItemBootstrap;
import me.nagasonic.alkatraz.loot.implementation.SpellbookLoot;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import de.tr7zw.changeme.nbtapi.NBT;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.ProgressionService;
import me.nagasonic.alkatraz.progression.BoosterManager;
import me.nagasonic.alkatraz.progression.ProgressionMultiplierService;import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.effect.ActiveEffect;
import me.nagasonic.alkatraz.spells.effect.EffectKind;
import me.nagasonic.alkatraz.spells.effect.EffectRegistry;
import me.nagasonic.alkatraz.spells.modifier.ModifierInstance;
import me.nagasonic.alkatraz.spells.modifier.ModifierManager;
import me.nagasonic.alkatraz.texturepack.TexturePackManager;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import me.nagasonic.alkatraz.util.WandUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class AlkatrazCommand implements CommandExecutor, TabCompleter {

    private static LangManager lang() { return Alkatraz.getLangManager(); }
    private static final List<String> CAST_MODES = List.of("code", "hotbar");

    private static volatile List<String> spellIdCache;
    private static volatile List<String> magicMobIdCache;
    private static volatile List<String> giveItemIdCache;
    private static volatile List<String> cachedPlayerNames;
    private static volatile long cachedPlayerNamesStamp;

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
            case "summon" -> handleSummon(sender, args);
            case "equipment", "eq" -> handleEquipment(sender, args);
            case "convert" -> handleConvert(sender, args);
            case "profile" -> handleProfile(sender, args);
            case "editor" -> handleEditor(sender, args);
            case "modifier" -> handleModifier(sender, args);
            case "effect", "effects" -> handleEffect(sender, args);            // Debug-only hook; requires the dedicated debug permission.
            case "booster" -> handleBooster(sender, args);
            case "config" -> handleConfig(sender, args);
            case "diagnostics", "diagnose", "diag" -> handleDiagnostics(sender, args);
            case "test" -> {
            if (!Permission.hasPermission(sender, Permission.COMMAND_DEBUG)) {
                sender.sendMessage(lang().get("commands.no_permission"));
                return true;
            }
            handleTest(sender, args);
        }
            case "gencode" -> {
                if (!Permission.hasPermission(sender, Permission.COMMAND_DEBUG)) {
                    sender.sendMessage(lang().get("commands.no_permission"));
                    return true;
                }
                sender.sendMessage(Utils.genCode());
            }
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

        MagicProfile profile = ProfileManager.getOrLoadProfile(p.getUniqueId(), MagicProfile.class);
        profile.setDiscoveredSpell(spell, discover);
        ProfileManager.saveProfile(profile);
        if (discover) {
            Bukkit.getPluginManager().callEvent(new SpellDiscoveredEvent(p, spell));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_GIVE)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 2 || args.length > 4) {
            sender.sendMessage(lang().get("commands.give_usage"));
            return;
        }

        NamespacedKey parsedKey = MagicKeys.parse(args[1].toLowerCase()).orElse(null);
        if (parsedKey == null) {
            sender.sendMessage(lang().get("commands.item_not_found", "name", args[1]));
            return;
        }

        int count = 1;
        int playerIndex = 2;
        if (args.length >= 3) {
            try {
                count = Integer.parseInt(args[2]);
                playerIndex = 3;
            } catch (NumberFormatException e) {
                playerIndex = 2;
                if (args.length >= 4) {
                    try {
                        count = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (count < 1 || count > 4096) {
            sender.sendMessage(lang().get("commands.give_usage"));
            return;
        }

        Player p = resolvePlayer(sender, args, playerIndex);
        if (p == null) return;

        // Try magic item definition first
        ItemDefinition itemDef = MagicItemRegistries.ITEM_DEFINITIONS.get(parsedKey).orElse(null);
        if (itemDef != null) {
            MagicItemInstance instance = MagicItemInstance.createDefault(itemDef.getKey());
            ItemStack stack = MagicItemStack.create(itemDef, instance);
            giveCount(p, stack, count);
            sender.sendMessage(lang().get("commands.gave_item", "item", itemDef.getKey().getKey(), "player", p.getName(), "count", String.valueOf(count)));
            return;
        }

        // Try engraving definition
        EngravingDefinition engravingDef = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(parsedKey).orElse(null);
        if (engravingDef != null) {
            ItemStack stack = MagicItemStack.createEngravingItem(engravingDef);
            giveCount(p, stack, count);
            sender.sendMessage(lang().get("commands.gave_item", "item", engravingDef.getKey().getKey(), "player", p.getName(), "count", String.valueOf(count)));
            return;
        }

        sender.sendMessage(lang().get("commands.item_not_found_or_engraving", "name", args[1]));
    }

    private void giveCount(Player p, ItemStack stack, int count) {
        int max = stack.getMaxStackSize();
        int remaining = count;
        while (remaining > 0) {
            ItemStack part = stack.clone();
            if (MagicItemStack.isNonStackable(part)) {
                MagicItemStack.refreshUniqueUuid(part);
            }
            part.setAmount(Math.min(remaining, max));
            p.getInventory().addItem(part).values()
                    .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            remaining -= part.getAmount();
        }
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

            double magicPower = NBT.get(stack, nbt -> nbt.hasTag("magic_power") ? nbt.getDouble("magic_power") : 0.0);
            int circleLimit = NBT.get(stack, nbt -> nbt.hasTag("circle_limit") ? nbt.getInteger("circle_limit") : 0);
            double castTime = NBT.get(stack, nbt -> nbt.hasTag("casting_time") ? nbt.getDouble("casting_time") : 0.0);

            NamespacedKey defKey;
            if (circleLimit >= 5 && magicPower >= 10) {
                defKey = MagicKeys.alkatraz("reinforced_wand");
            } else {
                defKey = MagicKeys.alkatraz("wooden_wand");
            }
            MagicItemInstance instance = MagicItemInstance.createDefault(defKey);
            instance.putCustomData("mana", NBT.get(stack, nbt -> nbt.hasTag("mana") ? nbt.getDouble("mana") : 0.0));

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

        MagicProfile data = ProfileManager.getOrLoadProfile(p.getUniqueId(), MagicProfile.class);
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(lang().get("commands.ak_usage"));
            return;
        }
        if (!Double.isFinite(amount)) {
            sender.sendMessage(lang().get("commands.ak_usage"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (amount < 0) {
                    sender.sendMessage(lang().get("commands.ak_negative"));
                    return;
                }
                data.setArcaneKnowledge(amount);
                ProgressionService.advanceWhileEligible(p);
                ProfileManager.saveProfile(data);
                sender.sendMessage(lang().get("commands.ak_set", "player", p.getName(), "amount", String.valueOf(amount)));
            }
            case "add" -> {
                if (data.getArcaneKnowledge() + amount < 0) {
                    sender.sendMessage(lang().get("commands.ak_negative"));
                    return;
                }
                StatUtils.addArcaneKnowledge(p, amount);
                ProfileManager.saveProfile(data);
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

        MagicProfile data = ProfileManager.getOrLoadProfile(p.getUniqueId(), MagicProfile.class);
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(lang().get("commands.circle_usage"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (amount < 0 || amount > 9) {
                    sender.sendMessage(lang().get("commands.circle_cannot_set"));
                    return;
                }
                StatUtils.addCircle(p, amount - data.getCircleLevel());
                ProfileManager.saveProfile(data);
                sender.sendMessage(lang().get("commands.circle_set", "player", p.getName(), "amount", String.valueOf(amount)));
            }
            case "add" -> {
                int result = data.getCircleLevel() + amount;
                if (result < 0 || result > 9) {
                    sender.sendMessage(lang().get("commands.circle_cannot_add"));
                    return;
                }
                StatUtils.addCircle(p, amount);
                ProfileManager.saveProfile(data);
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

        MagicProfile data = ProfileManager.getOrLoadProfile(p.getUniqueId(), MagicProfile.class);
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(lang().get("commands.mastery_usage"));
            return;
        }
        if (!Double.isFinite(amount)) {
            sender.sendMessage(lang().get("commands.mastery_usage"));
            return;
        }

        switch (args[2].toLowerCase()) {
            case "add" -> {
                if (data.getSpellMastery(spell) + amount < 0) {
                    sender.sendMessage(lang().get("commands.mastery_negative"));
                    return;
                }
                int clamped = (int) Math.min(amount + data.getSpellMastery(spell), spell.getMaxMastery());
                data.setSpellMastery(spell, clamped);
                ProfileManager.saveProfile(data);
                sender.sendMessage(lang().get("commands.mastery_added", "player", p.getName(), "amount", String.valueOf(amount), "spell", spell.getDisplayName()));
            }
            case "set" -> {
                if (amount < 0) {
                    sender.sendMessage(lang().get("commands.mastery_negative"));
                    return;
                }
                int clamped = (int) Math.min(amount, spell.getMaxMastery());
                data.setSpellMastery(spell, clamped);
                ProfileManager.saveProfile(data);
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
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                p.sendMessage(lang().get("commands.player_not_found", "name", args[1]));
                return;
            }
            new StatsMenu(p, target).open();
        } else {
            new StatsMenu(p, p).open();
        }
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_RELOAD)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        // Language first: SpellRegistry.reload() re-runs Spell.loadCommonConfig, which is
        // where spell names and descriptions are read. A lang edit must land before that.
        Alkatraz.reloadLangManager();
        new ArrayList<>(ConfigManager.getDefaultConfigs().keySet()).forEach(ConfigManager::reloadConfig);
        ConfigManager.reloadConfig("progression.yml");
        ConfigManager.reloadConfig("modifiers.yml");
        me.nagasonic.alkatraz.config.ModifierConfig.reload();
        ConfigManager.reloadConfig("boosters.yml");
        me.nagasonic.alkatraz.config.BoosterConfig.reload();
        // config.yml feeds the static pluginConfig reference and, through it, the particle
        // performance settings. Without this the particles: block stayed at boot-time values.
        Alkatraz.reloadPluginConfig();
        Configs.reload();
        ParticleConfig.reload();
        // texturepack.yml feeds the menu title glyphs, the GUI custom model data and the GUI
        // materials. TexturePackManager has to reload first because GUIItemRegistry caches
        // ItemStacks built from it, so rebuilding them any earlier would reuse the old codes.
        TexturePackManager.reload();
        GUIItemRegistry.reload();
        SpellRegistry.reload();
        ProgressionService.reload();
        me.nagasonic.alkatraz.progression.BoosterManager.onReload();
        MagicEntities.registerProfiles();
        MagicBrainService brainService = MagicBrainService.getInstance();
        if (brainService != null) brainService.reloadBrains();
        MagicItemBootstrap.reload();
        // Spellbook weights are baked into ItemStack NBT at build time, and registerAll()
        // only ran from onEnable, so loot.yml edits did nothing until a restart. Rebuilding
        // here fixes every roll generated from now on. Books already in an inventory or
        // already generated into a chunk keep the weights they were built with, so a
        // spellbook.circle_weights change STILL needs a full restart - see
        // SpellbookLoot.reloadLoot's javadoc.
        SpellbookLoot.reloadLoot();
        me.nagasonic.alkatraz.gui.implementation.research.ResearchGraphMenu.clearCaches();
        refreshStaticCompletions();
        sender.sendMessage(lang().get("commands.reload_success"));
    }

    private void handleSummon(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_SUMMON)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 2 || args.length > 4) {
            sender.sendMessage(lang().get("commands.summon_usage"));
            return;
        }
        MagicEntityType type = MagicEntityType.fromId(args[1]).orElse(null);
        if (type == null) {
            sender.sendMessage(lang().get("commands.summon_invalid_type", "type", args[1], "valid", String.join(", ", magicMobIds())));
            return;
        }

        int count = 1;
        int targetIndex = 2;
        if (args.length >= 3) {
            try {
                count = Integer.parseInt(args[2]);
                targetIndex = 3;
            } catch (NumberFormatException e) {
                targetIndex = 2;
            }
        }
        if (count < 1 || count > 100) {
            sender.sendMessage(lang().get("commands.summon_usage"));
            return;
        }

        SummonTarget target = resolveSummonTarget(sender, args, targetIndex);
        if (target == null) return;

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (MagicEntities.spawn(type, target.location()).isPresent()) spawned++;
        }
        sender.sendMessage(lang().get("commands.summon_success", "count", String.valueOf(spawned), "mob", type.getId(), "target", target.description()));
    }

    private SummonTarget resolveSummonTarget(CommandSender sender, String[] args, int argIndex) {
        if (args.length > argIndex) {
            String raw = args[argIndex];
            Player player = Bukkit.getPlayer(raw);
            if (player != null) return new SummonTarget(player.getLocation(), player.getName());
            String[] coords = raw.split(",");
            if (coords.length == 3) {
                try {
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    double z = Double.parseDouble(coords[2]);
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                        throw new NumberFormatException("non-finite");
                    }
                    if (Math.abs(x) > 30000000 || Math.abs(y) > 30000000 || Math.abs(z) > 30000000) {
                        sender.sendMessage(lang().get("commands.summon_usage"));
                        return null;
                    }
                    World world = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
                    return new SummonTarget(new Location(world, x, y, z), raw);
                } catch (NumberFormatException ignored) {
                }
            }
            sender.sendMessage(lang().get("commands.summon_invalid_location", "target", raw));
            return null;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang().get("commands.summon_require_target"));
            return null;
        }
        return new SummonTarget(p.getLocation(), p.getName());
    }

    private record SummonTarget(Location location, String description) {}

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
        MagicProfile castModeProfile = ProfileManager.getOrLoadProfile(p.getUniqueId(), MagicProfile.class);
        castModeProfile.setString("castMode", mode);
        ProfileManager.saveProfile(castModeProfile);
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

        MagicProfile profile = ProfileManager.getOrLoadProfile(target.getUniqueId(), MagicProfile.class);

        sender.sendMessage(format("&8&m&l---&r &d" + target.getName() + "'s Profile &8&m&l---"));

        // --- Formatted section ---
        sender.sendMessage(format("&7Circle: &f" + profile.getCircleLevel()));
        sender.sendMessage(format("&7Arcane Knowledge: &f" + (int) profile.getArcaneKnowledge()));
        sender.sendMessage(format("&7Research Points: &f" + profile.getResearchPoints()));
        sender.sendMessage(format("&7Stat Points: &f" + profile.getStatPoints() + "  &7Reset Tokens: &f" + profile.getResetTokens()));
        sender.sendMessage(format("&7Cast Mode: &f" + profile.getCastMode()));
        sender.sendMessage("");
        sender.sendMessage(format("&7Mana: &b" + (int) profile.getMana() + "&7/&b" + (int) profile.getMaxMana()));
        sender.sendMessage(format("&7Mana Regen: &b" + decimal(profile.getManaRegeneration()) + " &7per second"));

        double spellPower = profile.isDouble("spell_power") ? profile.getDouble("spell_power") : 0.0;
        sender.sendMessage(format("&7Spell Power: &f" + decimal(spellPower)));
        sender.sendMessage("");
        sender.sendMessage(format("&7Magic Affinity: &f" + decimal(profile.getMagicAffinity())
                + "  &7Magic Resistance: &f" + decimal(profile.getMagicResistance())));
        sender.sendMessage(format("&7Affinities: &6Fire: &f" + decimal(profile.getFireAffinity())
                + "  &bWater: &f" + decimal(profile.getWaterAffinity())
                + "  &fAir: &f" + decimal(profile.getAirAffinity())
                + "  &2Earth: &f" + decimal(profile.getEarthAffinity())
                + "  &eLight: &f" + decimal(profile.getLightAffinity())
                + "  &5Dark: &f" + decimal(profile.getDarkAffinity())));
        sender.sendMessage(format("&7Resistances: &6Fire: &f" + decimal(profile.getFireResistance())
                + "  &bWater: &f" + decimal(profile.getWaterResistance())
                + "  &fAir: &f" + decimal(profile.getAirResistance())
                + "  &2Earth: &f" + decimal(profile.getEarthResistance())
                + "  &eLight: &f" + decimal(profile.getLightResistance())
                + "  &5Dark: &f" + decimal(profile.getDarkResistance())));
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
            allStats.put(name, String.format(Locale.ROOT, "%.2f", profile.getDouble(name)));
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

    private void handleModifier(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_MODIFIER)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(lang().get("commands.modifier_usage"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "revoke" -> handleModifierRevoke(sender, args);
            case "list", "inspect", "show" -> handleModifierList(sender, args);
            default -> sender.sendMessage(lang().get("commands.modifier_usage"));
        }
    }

    private void handleModifierRevoke(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang().get("commands.modifier_usage"));
            return;
        }
        java.util.UUID instanceId;
        try {
            instanceId = java.util.UUID.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(lang().get("commands.modifier_usage"));
            return;
        }
        Player target = resolvePlayer(sender, args, 3);
        if (target == null) return;
        boolean removed = ModifierManager.revoke(target.getUniqueId(), instanceId);
        sender.sendMessage(removed ? lang().get("commands.modifier_revoked")
                : lang().get("commands.modifier_not_found"));
    }

    private void handleModifierList(CommandSender sender, String[] args) {
        Player target = resolvePlayer(sender, args, 2);
        if (target == null) return;
        java.util.List<ModifierInstance> mods = ModifierManager.list(target.getUniqueId());
        if (mods.isEmpty()) {
            sender.sendMessage(lang().get("commands.modifier_list_empty"));
            return;
        }
        sender.sendMessage(format("&8&m&l---&r &d" + target.getName() + "'s Active Modifiers &8&m&l---"));
        long now = System.currentTimeMillis();
        for (ModifierInstance mod : mods) {
            long remaining = mod.expiresAtMillis() == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : Math.max(0L, mod.expiresAtMillis() - now);
            String duration = remaining == Long.MAX_VALUE ? "permanent" : (remaining / 1000L) + "s";
            sender.sendMessage(format("&7source=&f" + mod.source()
                    + " &8| &7target=&f" + mod.statOrAttributeId()
                    + " &8| &7operation=&f" + mod.operation()
                    + " &8| &7amount=&f" + mod.amount()
                    + " &8| &7duration=&f" + duration
                    + " &8| &7type=&f" + mod.type()));
        }
    }

    private void handleEffect(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_DEBUG)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        // Accept both "/alkatraz effects <player>" and "/alkatraz effect list <player>".
        int playerArgIndex = 1;
        if (args.length >= 2 && ("list".equals(args[1].toLowerCase())
                || "inspect".equals(args[1].toLowerCase())
                || "show".equals(args[1].toLowerCase()))) {
            playerArgIndex = 2;
        }
        Player target = resolvePlayer(sender, args, playerArgIndex);
        if (target == null) return;
        java.util.List<ActiveEffect> effects = EffectRegistry.effectsOn(target.getUniqueId());
        if (effects.isEmpty()) {
            sender.sendMessage(lang().get("commands.effect_list_empty"));
            return;
        }
        sender.sendMessage(format("&8&m&l---&r &d" + target.getName() + "'s Active Effects &8&m&l---"));
        long now = System.currentTimeMillis();
        for (ActiveEffect effect : effects) {
            long remaining = effect.expiresAtMillis() == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : Math.max(0L, effect.expiresAtMillis() - now);
            String duration = remaining == Long.MAX_VALUE ? "permanent" : (remaining / 1000L) + "s";
            sender.sendMessage(format("&7source=&f" + effect.sourceSpellId()
                    + " &8| &7kind=&f" + effect.kind()
                    + " &8| &7duration=&f" + duration
                    + " &8| &7circle=&f" + effect.sourceCircle()
                    + " &8| &7element=&f" + effect.element()));
        }
    }

    private void handleBooster(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_BOOSTER)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(lang().get("commands.booster_usage"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "start" -> {
                int idx = 2;
                String scope = "world";
                if (idx < args.length && isBoosterScope(args[idx])) {
                    scope = args[idx++].toLowerCase();
                }
                String target = idx < args.length ? args[idx++] : null;
                double multiplier = 2.0;
                if (idx < args.length) {
                    try {
                        multiplier = Double.parseDouble(args[idx++]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (target == null) {
                    if (sender instanceof Player p && scope.equals("world")) {
                        target = p.getWorld().getName();
                    } else {
                        sender.sendMessage(lang().get("commands.booster_usage"));
                        return;
                    }
                }
                if (!Double.isFinite(multiplier) || multiplier <= 0) {
                    sender.sendMessage(lang().get("commands.booster_usage"));
                    return;
                }
                boolean ok = BoosterManager.start(scope, target, multiplier);
                sender.sendMessage(lang().get(ok ? "commands.booster_started" : "commands.booster_failed",
                        "scope", scope, "target", target, "multiplier", String.valueOf(multiplier)));
            }
            case "stop" -> {
                int idx = 2;
                String scope = "world";
                if (idx < args.length && isBoosterScope(args[idx])) {
                    scope = args[idx++].toLowerCase();
                }
                String target = idx < args.length ? args[idx++] : null;
                if (target == null) {
                    if (sender instanceof Player p && scope.equals("world")) {
                        target = p.getWorld().getName();
                    } else {
                        sender.sendMessage(lang().get("commands.booster_usage"));
                        return;
                    }
                }
                boolean ok = BoosterManager.stop(scope, target);
                sender.sendMessage(lang().get(ok ? "commands.booster_stopped" : "commands.booster_not_found",
                        "scope", scope, "target", target));
            }
            case "list" -> {
                int idx = 2;
                String scopeFilter = null;
                if (idx < args.length && isBoosterScope(args[idx])) {
                    scopeFilter = args[idx++].toLowerCase();
                }
                List<BoosterManager.Booster> boosters = BoosterManager.list(scopeFilter);                if (boosters.isEmpty()) {
                    sender.sendMessage(lang().get("commands.booster_list_empty"));
                    return;
                }
                for (BoosterManager.Booster b : boosters) {                    sender.sendMessage(format("&7" + b.scope() + " &8| &f" + b.target()
                            + " &8| &fx" + String.valueOf(b.multiplier())));
                }
            }
            default -> sender.sendMessage(lang().get("commands.booster_usage"));
        }
    }

    private void handleConfig(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_CONFIG)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(format("&cUsage: /alkatraz config show|validate [file|all]"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "show", "list" -> handleConfigShow(sender, args);
            case "validate" -> handleConfigValidate(sender, args);
            case "diagnose", "diagnostics" -> handleConfigDiagnose(sender, args);
            default -> sender.sendMessage(format("&cUsage: /alkatraz config show|validate [file|all]"));
        }
    }

    private void handleConfigShow(CommandSender sender, String[] args) {
        if (args.length == 2) {
            sender.sendMessage(format("&8&m&l---&r &dConfig files &8&m&l---"));
            List<String> names = new java.util.ArrayList<>(ConfigManager.getConfigs().keySet());
            names.addAll(ConfigManager.getDefaultConfigs().keySet());
            names = names.stream().distinct().sorted().collect(Collectors.toList());
            for (String name : names) sender.sendMessage(format("&7" + name));
            sender.sendMessage(format("&7Use &f/alkatraz config show <file>"));
            return;
        }
        String name = args[2];
        org.bukkit.configuration.file.YamlConfiguration yml;
        try {
            yml = ConfigManager.getConfig(name).get();
        } catch (Exception e) {
            sender.sendMessage(format("&cCould not load &f" + name + "&c: " + e.getMessage()));
            return;
        }
        if (yml == null) {
            sender.sendMessage(format("&cConfig not found: &f" + name));
            return;
        }
        sender.sendMessage(format("&8&m&l---&r &d" + name + " &8&m&l---"));
        sender.sendMessage(format("&7top-level keys: &f" + yml.getKeys(false).size()));
        for (String key : yml.getKeys(false)) {
            String type = yml.isConfigurationSection(key) ? "section" : "value";
            sender.sendMessage(format("&7- &f" + key + " &8(" + type + "&8)"));
        }
    }

    private void handleConfigValidate(CommandSender sender, String[] args) {
        String target = args.length >= 3 ? args[2] : "all";
        List<String> names;
        if ("all".equalsIgnoreCase(target)) {
            java.util.Set<String> collected = new java.util.LinkedHashSet<>();
            collected.addAll(ConfigManager.getConfigs().keySet());
            collected.addAll(ConfigManager.getDefaultConfigs().keySet());
            names = new java.util.ArrayList<>(collected);
        } else {
            names = List.of(target);
        }
        int issueCount = 0;
        sender.sendMessage(format("&8&m&l---&r &dConfig validation &8&m&l---"));
        for (String name : names) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yml = ConfigManager.getConfig(name).reload().get();
                org.bukkit.configuration.file.YamlConfiguration def = ConfigManager.getDefault(name);
                int warnings = 0;
                if (yml.getKeys(false).isEmpty()) warnings++;
                if (def != null) {
                    for (String key : def.getKeys(false)) {
                        if (!yml.isSet(key)) {
                            warnings++;
                            sender.sendMessage(format("&e" + name + "&7: missing key &f" + key));
                        }
                    }
                }
                issueCount += warnings;
                sender.sendMessage((warnings == 0
                        ? format("&a" + name + "&7: OK (" + yml.getKeys(false).size() + " top-level keys)")
                        : format("&e" + name + "&7: " + warnings + " warning(s)")));
            } catch (Exception e) {
                issueCount++;
                sender.sendMessage(format("&c" + name + "&7: ERROR " + e.getMessage()));
            }
        }
        sender.sendMessage(issueCount == 0 ? format("&aValidation complete: no issues found")
                : format("&eValidation complete: " + issueCount + " issue(s) reported"));
    }

    private void handleConfigDiagnose(CommandSender sender, String[] args) {
        handleDiagnostics(sender, args);
    }

    private void handleDiagnostics(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_DIAGNOSE)) {
            sender.sendMessage(lang().get("commands.no_permission"));
            return;
        }
        sender.sendMessage(format("&8&m&l---&r &dAlkatraz diagnostics &8&m&l---"));
        for (String line : me.nagasonic.alkatraz.util.PluginDiagnostics.run()) {
            sender.sendMessage(format(line));
        }
    }

    private boolean isBoosterScope(String token) {
        if (token == null) return false;
        String t = token.toLowerCase();
        return t.equals("world") || t.equals("worlds") || t.equals("group") || t.equals("groups");
    }

    private Player resolvePlayer(CommandSender sender, String[] args, int argIndex) {        if (args.length > argIndex) {            Player p = Bukkit.getPlayer(args[argIndex]);
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
            case "summon" -> tabSummon(sender, args);
            case "equipment", "eq" -> List.of();
            case "profile" -> tabProfile(sender, args);
            case "convert" -> tabConvert(sender, args);
            case "editor" -> List.of();
            case "modifier" -> tabModifier(sender, args);
            case "effect", "effects" -> tabEffect(sender, args);
            case "booster" -> tabBooster(sender, args);            case "config" -> tabConfig(sender, args);            case "diagnostics", "diagnose", "diag" -> tabDiagnostics(sender, args);            case "test" -> tabTest(sender, args);            default -> List.of();
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
        if (Permission.hasPermission(sender, Permission.COMMAND_SUMMON)) list.add("summon");
        if (Permission.hasPermission(sender, Permission.COMMAND_CONVERT)) list.add("convert");
        if (Permission.hasPermission(sender, Permission.COMMAND_EDITOR)) list.add("editor");
        if (Permission.hasPermission(sender, Permission.COMMAND_MODIFIER)) list.add("modifier");
        if (Permission.hasPermission(sender, Permission.COMMAND_BOOSTER)) list.add("booster");
        if (Permission.hasPermission(sender, Permission.COMMAND_CONFIG)) {
            list.add("config");
            list.add("diagnostics");
            list.add("diagnose");
            list.add("diag");
        }
        list.add("stats");        list.add("profile");
        list.add("castmode");
        list.add("mode");
        list.add("equipment");
        list.add("eq");
        if (Permission.hasPermission(sender, Permission.COMMAND_RELOAD)) list.add("test");
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
            case 2 -> giveItemIds();
            case 3, 4 -> playerNames();
            default -> List.of();
        };
    }

    private List<String> giveItemIds() {
        List<String> cached = giveItemIdCache;
        if (cached == null) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            ids.addAll(MagicItemRegistries.ITEM_DEFINITIONS.values().stream().map(def -> def.getKey().getKey()).collect(Collectors.toList()));
            ids.addAll(MagicItemRegistries.ENGRAVING_DEFINITIONS.values().stream().map(def -> def.getKey().getKey()).collect(Collectors.toList()));
            cached = ids;
            giveItemIdCache = cached;
        }
        return cached;
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

    private List<String> tabConfig(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_CONFIG)) return List.of();
        if (args.length == 2) return List.of("show", "validate");
        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("validate") || args[1].equalsIgnoreCase("show")) {
                List<String> names = new java.util.ArrayList<>(ConfigManager.getConfigs().keySet());
                names.addAll(ConfigManager.getDefaultConfigs().keySet());
                return names.stream().distinct().collect(Collectors.toList());
            }
        }
        return List.of();
    }

    private List<String> tabDiagnostics(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_DIAGNOSE)) return List.of();
        return List.of();
    }

    private List<String> tabCastMode(CommandSender sender, String[] args) {
        if (args.length == 2) return CAST_MODES;
        return List.of();
    }

    private List<String> tabModifier(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_MODIFIER)) return List.of();
        return switch (args.length) {
            case 2 -> List.of("revoke", "list", "inspect", "show");
            case 3 -> switch (args[1].toLowerCase()) {
                case "grant" -> modifierIds();
                case "revoke" -> List.of();
                case "list", "inspect", "show" -> playerNames();
                default -> List.of();
            };
            case 4 -> switch (args[1].toLowerCase()) {
                case "grant", "revoke" -> playerNames();
                default -> List.of();
            };
            default -> List.of();
        };
    }

    private List<String> modifierIds() {
        var mods = ModifierConfig.getModifiers();
        if (mods == null) return List.of();
        return new java.util.ArrayList<>(mods.getKeys(false));
    }
    private List<String> tabBooster(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_BOOSTER)) return List.of();
        return switch (args.length) {
            case 2 -> List.of("start", "stop", "list");
            case 3 -> List.of("world", "group");
            case 4 -> {
                String scope = args[2].toLowerCase();
                if (scope.equals("world") || scope.equals("worlds"))
                    yield Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
                if (scope.equals("group") || scope.equals("groups"))
                    yield new java.util.ArrayList<>(ProgressionMultiplierService.getGroupMultipliers().keySet());
                yield List.of();
            }
            default -> List.of();
        };
    }

    private List<String> tabEffect(CommandSender sender, String[] args) {        if (!Permission.hasPermission(sender, Permission.COMMAND_DEBUG)) return List.of();
        if (args.length == 2) return playerNames();
        if (args.length == 3 && ("list".equals(args[1].toLowerCase())
                || "inspect".equals(args[1].toLowerCase())
                || "show".equals(args[1].toLowerCase()))) return playerNames();
        return List.of();
    }
    private List<String> tabSummon(CommandSender sender, String[] args) {
        if (!Permission.hasPermission(sender, Permission.COMMAND_SUMMON)) return List.of();
        return switch (args.length) {
            case 2 -> magicMobIds();
            case 3, 4 -> playerNames();
            default -> List.of();
        };
    }

    private List<String> magicMobIds() {
        List<String> cached = magicMobIdCache;
        if (cached == null) {
            cached = Arrays.stream(MagicEntityType.values()).map(MagicEntityType::getId).collect(Collectors.toList());
            magicMobIdCache = cached;
        }
        return cached;
    }

    private List<String> spellIds() {
        List<String> cached = spellIdCache;
        if (cached == null) {
            cached = SpellRegistry.getAllSpells().values().stream().map(Spell::getId).collect(Collectors.toList());
            spellIdCache = cached;
        }
        return cached;
    }

    private List<String> playerNames() {
        long now = System.currentTimeMillis();
        List<String> cached = cachedPlayerNames;
        if (cached != null && now - cachedPlayerNamesStamp < 1000L) return cached;
        List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        cachedPlayerNames = names;
        cachedPlayerNamesStamp = now;
        return names;
    }

    private static void refreshStaticCompletions() {
        spellIdCache = SpellRegistry.getAllSpells().values().stream().map(Spell::getId).collect(Collectors.toList());
        magicMobIdCache = Arrays.stream(MagicEntityType.values()).map(MagicEntityType::getId).collect(Collectors.toList());
        giveItemIdCache = null;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
