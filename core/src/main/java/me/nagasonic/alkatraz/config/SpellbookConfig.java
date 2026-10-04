package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Centralized config reader for all spellbook-related settings.
 * Non-loot values are read from config.yml, loot values from loot.yml.
 *
 * <p>No-argument getters are served from {@code volatile} fields and the
 * parameterised getters from a {@link ConcurrentHashMap} memo; both are dropped
 * whenever the {@link #REFRESH_INTERVAL_MS} staleness window elapses so live
 * config edits are still picked up within about a second.</p>
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
    // Caching
    // ==========================================

    private static final long REFRESH_INTERVAL_MS = 1000L;

    private static volatile long lastRefresh = Long.MIN_VALUE;
    private static final Map<String, Object> memo = new ConcurrentHashMap<>();

    // no-argument values
    private static volatile boolean villagerTradingEnabled = true;
    private static volatile int villagerChanceTwoTrades = 20;
    private static volatile int villagerChanceOneTrade = 40;
    private static volatile int villagerMaxTrades = 2;
    private static volatile int villagerTradeMaxUses = 3;
    private static volatile double magicStoneBaseChance = 0.75;
    private static volatile double magicStoneLootingBonus = 0.10;
    private static volatile double lootMultiplier = 1.0;
    private static volatile boolean chestLootEnabled = true;
    private static volatile boolean structureLootEnabled = true;
    private static volatile boolean mobDropsEnabled = true;
    private static volatile boolean fishingEnabled = true;
    private static volatile boolean piglinBarteringEnabled = true;
    private static volatile Map<String, Integer> elementCaps = Map.of();

    private static boolean stale() {
        return System.currentTimeMillis() - lastRefresh >= REFRESH_INTERVAL_MS;
    }

    /**
     * Drops every cached spellbook setting. Safe to call at any time; the next
     * access re-reads from the YAML trees.
     */
    public static void reload() {
        doReload();
    }

    private static synchronized void doReload() {
        villagerTradingEnabled = getBoolean("villager_trading.enabled", true);
        villagerChanceTwoTrades = getInt("villager_trading.chance_two_trades", 20);
        // WAS villager_trading.chance_one_trade plus a trailing 's', a key config.yml
        // never defines, so this knob was dead and always returned the hardcoded 40.
        villagerChanceOneTrade = getInt("villager_trading.chance_one_trade", 40);
        villagerMaxTrades = getInt("villager_trading.max_trades_per_villager", 2);
        villagerTradeMaxUses = getInt("villager_trading.trade_max_uses", 3);
        magicStoneBaseChance = getDouble("magic_stone.base_drop_chance", 0.75);
        magicStoneLootingBonus = getDouble("magic_stone.looting_bonus", 0.10);
        lootMultiplier = lootDouble("loot.loot_multiplier", 1.0);
        chestLootEnabled = lootBoolean("loot.chest_loot.enabled", true);
        structureLootEnabled = lootBoolean("loot.structure_loot.enabled", true);
        mobDropsEnabled = lootBoolean("loot.mob_drops.enabled", true);
        fishingEnabled = lootBoolean("loot.fishing.enabled", true);
        piglinBarteringEnabled = lootBoolean("loot.piglin_bartering.enabled", true);
        elementCaps = loadElementCaps();
        memo.clear();
        lastRefresh = System.currentTimeMillis();
    }

    private static void refreshIfStale() {
        if (!stale()) return;
        synchronized (SpellbookConfig.class) {
            if (stale()) doReload();
        }
    }

    private static Integer memoInt(String memoKey, IntSupplier supplier) {
        Object cached = memo.get(memoKey);
        if (cached instanceof Integer value) return value;
        int value = supplier.getAsInt();
        memo.put(memoKey, value);
        return value;
    }

    private static Boolean memoBool(String memoKey, BooleanSupplier supplier) {
        Object cached = memo.get(memoKey);
        if (cached instanceof Boolean value) return value;
        boolean value = supplier.getAsBoolean();
        memo.put(memoKey, value);
        return value;
    }

    @FunctionalInterface
    private interface IntArraySupplier {
        int[] get();
    }

    private static int[] memoIntArray(String memoKey, IntArraySupplier supplier) {
        Object cached = memo.get(memoKey);
        if (cached instanceof int[] value) return value;
        int[] value = supplier.get();
        memo.put(memoKey, value);
        return value;
    }

    private static String memoString(String memoKey, Supplier<String> supplier) {
        Object cached = memo.get(memoKey);
        if (cached instanceof String value) return value;
        String value = supplier.get();
        memo.put(memoKey, value);
        return value;
    }

    // ==========================================
    // SPELLBOOK CIRCLE WEIGHTS
    // ==========================================

    public static int getCircleWeight(int circle) {
        refreshIfStale();
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
        return memoInt("cw:" + circle, () -> getInt("spellbook.circle_weights.circle_" + circle, def));
    }

    // ==========================================
    // ELEMENT SPELLBOOK CIRCLE CAPS
    // ==========================================

    /**
     * Highest circle a random ELEMENT spellbook from {@code sourceKey} may contain.
     *
     * <p>An element book with no cap can roll any spell of that element at any circle,
     * so a blaze's FIRE book could hand over {@code inferno_rite} - circle 9, 600 mana
     * - off a mob whose own signature drop is {@code fire_blast} at circle 2.
     *
     * <p>The rule applied to every one of the 25 call sites is that the cap equals the
     * top circle of that source's own non-element sibling band, so an element book never
     * outranks the random book beside it. A cap of 9 means uncapped.
     *
     * <p>Every cap key in the shipped loot.yml also matches the {@code def} passed by
     * its caller, so a server whose loot.yml predates this block still gets the
     * intended caps - the block is the tuning surface, not the only source of truth.
     *
     * @return the configured cap, or {@code def} when the key is absent.
     */
    public static int getElementMaxCircle(String sourceKey, int def) {
        refreshIfStale();
        Integer cap = elementCaps.get(sourceKey);
        return cap == null ? def : cap;
    }

    /**
     * Flattens {@code loot.element_book_caps} into {@code dotted.key -> circle}.
     *
     * <p>YamlConfiguration turns a dotted YAML key into NESTED sections, so
     * {@code mob.enderman.dark: 4} is read back as {@code mob -> enderman -> dark}.
     * That means {@code getKeys(false)} on the caps section returns the five group
     * names and reading a cap needs the full path, which is exactly the shape of
     * {@code sourceKey}. Group entries resolve to a ConfigurationSection rather than a
     * Number, so they are dropped here instead of being stored as the default cap.
     */
    private static Map<String, Integer> loadElementCaps() {
        Map<String, Integer> caps = new HashMap<>();
        ConfigurationSection section = lootCfg().getConfigurationSection("loot.element_book_caps");
        if (section != null) {
            for (String key : section.getKeys(true)) {
                Object value = section.get(key);
                if (value instanceof Number number) {
                    caps.put(key, number.intValue());
                }
            }
        }
        return Map.copyOf(caps);
    }

    // ==========================================
    // MAGIC STONE DROPS
    // ==========================================

    public static double getMagicStoneBaseChance() {
        refreshIfStale();
        return magicStoneBaseChance;
    }

    public static double getMagicStoneLootingBonus() {
        refreshIfStale();
        return magicStoneLootingBonus;
    }

    // ==========================================
    // VILLAGER TRADING
    // ==========================================

    public static boolean isVillagerTradingEnabled() {
        refreshIfStale();
        return villagerTradingEnabled;
    }

    public static int getVillagerChanceTwoTrades() {
        refreshIfStale();
        return villagerChanceTwoTrades;
    }

    public static int getVillagerChanceOneTrade() {
        refreshIfStale();
        return villagerChanceOneTrade;
    }

    public static int getVillagerMaxTrades() {
        refreshIfStale();
        return villagerMaxTrades;
    }

    public static int getVillagerTradeMaxUses() {
        refreshIfStale();
        return villagerTradeMaxUses;
    }

    /**
     * Returns [emerald_min, emerald_max, stone_min, stone_max] for a given tier.
     * The returned array is a shared cached instance and must not be mutated.
     */
    public static int[] getVillagerTierCosts(int tier) {
        refreshIfStale();
        return memoIntArray("vtc:" + tier, () -> {
            String prefix = "villager_trading.tiers." + tier + ".";
            return new int[]{
                    getInt(prefix + "emerald_min", getDefaultEmeraldMin(tier)),
                    getInt(prefix + "emerald_max", getDefaultEmeraldMax(tier)),
                    getInt(prefix + "stone_min", getDefaultStoneMin(tier)),
                    getInt(prefix + "stone_max", getDefaultStoneMax(tier))
            };
        });
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
    // IMBUING
    // ==========================================

    public static int getImbueStoneCost(int tier) {
        refreshIfStale();
        return memoInt("imbuing:" + tier, () -> getInt("imbuing.stone_costs." + tier, tier));
    }

    // ==========================================
    // LOOT - GLOBAL
    // ==========================================

    public static double getLootMultiplier() {
        refreshIfStale();
        return lootMultiplier;
    }

    // ==========================================
    // LOOT - CHEST LOOT
    // ==========================================

    public static boolean isChestLootEnabled() {
        refreshIfStale();
        return chestLootEnabled;
    }

    public static boolean isChestCategoryEnabled(String category) {
        refreshIfStale();
        return memoBool("chest.cat:" + category, () -> lootBoolean("loot.chest_loot." + category + ".enabled", true));
    }

    public static int getChestInt(String category, String key, int def) {
        refreshIfStale();
        String memoKey = "chest:" + category + ' ' + key + ' ' + def;
        return memoInt(memoKey, () -> lootInt("loot.chest_loot." + category + "." + key, def));
    }

    // ==========================================
    // LOOT - STRUCTURE LOOT
    // ==========================================

    public static boolean isStructureLootEnabled() {
        refreshIfStale();
        return structureLootEnabled;
    }

    public static boolean isStructureCategoryEnabled(String category) {
        refreshIfStale();
        return memoBool("struct.cat:" + category, () -> lootBoolean("loot.structure_loot." + category + ".enabled", true));
    }

    public static int getStructureInt(String category, String key, int def) {
        refreshIfStale();
        String memoKey = "struct:" + category + ' ' + key + ' ' + def;
        return memoInt(memoKey, () -> lootInt("loot.structure_loot." + category + "." + key, def));
    }

    // ==========================================
    // LOOT - MOB DROPS
    // ==========================================

    public static boolean isMobDropsEnabled() {
        refreshIfStale();
        return mobDropsEnabled;
    }

    public static boolean isMobEnabled(String mob) {
        refreshIfStale();
        return memoBool("mob.cat:" + mob, () -> lootBoolean("loot.mob_drops." + mob + ".enabled", true));
    }

    public static int getMobInt(String mob, String key, int def) {
        refreshIfStale();
        String memoKey = "mob:" + mob + ' ' + key + ' ' + def;
        return memoInt(memoKey, () -> lootInt("loot.mob_drops." + mob + "." + key, def));
    }

    /**
     * A string-valued mob loot key, memoised like {@link #getMobInt}.
     *
     * <p>Exists so a specific spellbook's id is data rather than code. It used to be a
     * {@code SpellRegistry.getSpell("fire_blast")} literal inside {@code registerAll()},
     * which NPEs during {@code onEnable} if the id is ever renamed - so renaming a spell
     * took the whole plugin down. Reading it from loot.yml turns a rename into a config
     * edit that logs one warning and skips one loot entry.
     */
    public static String getMobString(String mob, String key, String def) {
        refreshIfStale();
        String memoKey = "mobstr:" + mob + ' ' + key + ' ' + def;
        return memoString(memoKey, () -> lootCfg().getString("loot.mob_drops." + mob + "." + key, def));
    }

    // ==========================================
    // LOOT - FISHING
    // ==========================================

    public static boolean isFishingEnabled() {
        refreshIfStale();
        return fishingEnabled;
    }

    public static int getFishingInt(String key, int def) {
        refreshIfStale();
        String memoKey = "fish:" + key + ' ' + def;
        return memoInt(memoKey, () -> lootInt("loot.fishing." + key, def));
    }

    // ==========================================
    // LOOT - PIGLIN BARTERING
    // ==========================================

    public static boolean isPiglinBarteringEnabled() {
        refreshIfStale();
        return piglinBarteringEnabled;
    }

    public static int getPiglinInt(String key, int def) {
        refreshIfStale();
        String memoKey = "piglin:" + key + ' ' + def;
        return memoInt(memoKey, () -> lootInt("loot.piglin_bartering." + key, def));
    }
}
