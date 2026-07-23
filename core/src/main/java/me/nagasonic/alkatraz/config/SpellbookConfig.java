package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Centralized config reader for all spellbook-related settings.
 * Non-loot values are read from config.yml, loot values from loot.yml.
 */
public class SpellbookConfig {

    private static YamlConfiguration cfg() {
        return Alkatraz.getPluginConfig();
    }

    private static YamlConfiguration lootCfg() {
        return Alkatraz.getLootConfig();
    }

    private static int getInt(String path, int def) {
        return cfg().getInt(path, def);
    }

    private static double getDouble(String path, double def) {
        return cfg().getDouble(path, def);
    }

    private static boolean getBoolean(String path, boolean def) {
        return cfg().getBoolean(path, def);
    }

    private static int lootInt(String path, int def) {
        return lootCfg().getInt(path, def);
    }

    private static double lootDouble(String path, double def) {
        return lootCfg().getDouble(path, def);
    }

    private static boolean lootBoolean(String path, boolean def) {
        return lootCfg().getBoolean(path, def);
    }

    // ==========================================
    // SPELLBOOK CIRCLE WEIGHTS
    // ==========================================

    public static int getCircleWeight(int circle) {
        int def = switch (circle) {
            case 1 -> 10000;
            case 2 -> 5000;
            case 3 -> 2500;
            case 4 -> 1250;
            case 5 -> 750;
            case 6 -> 375;
            case 7 -> 150;
            case 8 -> 75;
            case 9 -> 30;
            default -> 0;
        };
        return getInt("spellbook.circle_weights.circle_" + circle, def);
    }

    // ==========================================
    // MAGIC STONE DROPS
    // ==========================================

    public static double getMagicStoneBaseChance() {
        return getDouble("magic_stone.base_drop_chance", 0.75);
    }

    public static double getMagicStoneLootingBonus() {
        return getDouble("magic_stone.looting_bonus", 0.10);
    }

    // ==========================================
    // VILLAGER TRADING
    // ==========================================

    public static boolean isVillagerTradingEnabled() {
        return getBoolean("villager_trading.enabled", true);
    }

    public static int getVillagerChanceTwoTrades() {
        return getInt("villager_trading.chance_two_trades", 20);
    }

    public static int getVillagerChanceOneTrade() {
        return getInt("villager_trading.chance_one_trade", 40);
    }

    public static int getVillagerMaxTrades() {
        return getInt("villager_trading.max_trades_per_villager", 2);
    }

    public static int getVillagerTradeMaxUses() {
        return getInt("villager_trading.trade_max_uses", 3);
    }

    /**
     * Returns [emerald_min, emerald_max, stone_min, stone_max] for a given tier.
     */
    public static int[] getVillagerTierCosts(int tier) {
        String prefix = "villager_trading.tiers." + tier + ".";
        return new int[]{
                getInt(prefix + "emerald_min", getDefaultEmeraldMin(tier)),
                getInt(prefix + "emerald_max", getDefaultEmeraldMax(tier)),
                getInt(prefix + "stone_min", getDefaultStoneMin(tier)),
                getInt(prefix + "stone_max", getDefaultStoneMax(tier))
        };
    }

    private static int getDefaultEmeraldMin(int tier) {
        return switch (tier) {
            case 1 -> 4;
            case 2 -> 8;
            case 3 -> 14;
            case 4 -> 22;
            case 5 -> 32;
            default -> 5;
        };
    }

    private static int getDefaultEmeraldMax(int tier) {
        return switch (tier) {
            case 1 -> 7;
            case 2 -> 13;
            case 3 -> 21;
            case 4 -> 31;
            case 5 -> 47;
            default -> 5;
        };
    }

    private static int getDefaultStoneMin(int tier) {
        return switch (tier) {
            case 1 -> 1;
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            case 5 -> 5;
            default -> 1;
        };
    }

    private static int getDefaultStoneMax(int tier) {
        return switch (tier) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 5;
            case 5 -> 8;
            default -> 1;
        };
    }

    // ==========================================
    // LOOT - GLOBAL
    // ==========================================

    public static double getLootMultiplier() {
        return lootDouble("loot.loot_multiplier", 1.0);
    }

    // ==========================================
    // LOOT - CHEST LOOT
    // ==========================================

    public static boolean isChestLootEnabled() {
        return lootBoolean("loot.chest_loot.enabled", true);
    }

    public static boolean isChestCategoryEnabled(String category) {
        return lootBoolean("loot.chest_loot." + category + ".enabled", true);
    }

    public static int getChestInt(String category, String key, int def) {
        return lootInt("loot.chest_loot." + category + "." + key, def);
    }

    // ==========================================
    // LOOT - STRUCTURE LOOT
    // ==========================================

    public static boolean isStructureLootEnabled() {
        return lootBoolean("loot.structure_loot.enabled", true);
    }

    public static boolean isStructureCategoryEnabled(String category) {
        return lootBoolean("loot.structure_loot." + category + ".enabled", true);
    }

    public static int getStructureInt(String category, String key, int def) {
        return lootInt("loot.structure_loot." + category + "." + key, def);
    }

    // ==========================================
    // LOOT - MOB DROPS
    // ==========================================

    public static boolean isMobDropsEnabled() {
        return lootBoolean("loot.mob_drops.enabled", true);
    }

    public static boolean isMobEnabled(String mob) {
        return lootBoolean("loot.mob_drops." + mob + ".enabled", true);
    }

    public static int getMobInt(String mob, String key, int def) {
        return lootInt("loot.mob_drops." + mob + "." + key, def);
    }

    // ==========================================
    // LOOT - FISHING
    // ==========================================

    public static boolean isFishingEnabled() {
        return lootBoolean("loot.fishing.enabled", true);
    }

    public static int getFishingInt(String key, int def) {
        return lootInt("loot.fishing." + key, def);
    }

    // ==========================================
    // LOOT - PIGLIN BARTERING
    // ==========================================

    public static boolean isPiglinBarteringEnabled() {
        return lootBoolean("loot.piglin_bartering.enabled", true);
    }

    public static int getPiglinInt(String key, int def) {
        return lootInt("loot.piglin_bartering." + key, def);
    }
}
