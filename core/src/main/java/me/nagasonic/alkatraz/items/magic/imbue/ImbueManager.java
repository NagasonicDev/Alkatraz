package me.nagasonic.alkatraz.items.magic.imbue;

import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.config.SpellbookConfig;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ImbueManager {

    private static final Map<Material, Integer> MATERIAL_TIERS = new ConcurrentHashMap<>();

    private static final NamespacedKey TIER1_KEY = MagicKeys.alkatraz("imbued_tier1");
    private static final NamespacedKey TIER2_KEY = MagicKeys.alkatraz("imbued_tier2");
    private static final NamespacedKey TIER3_KEY = MagicKeys.alkatraz("imbued_tier3");
    private static final NamespacedKey TIER4_KEY = MagicKeys.alkatraz("imbued_tier4");
    private static final NamespacedKey TIER5_KEY = MagicKeys.alkatraz("imbued_tier5");
    private static final NamespacedKey TIER1_WEAPON_KEY = MagicKeys.alkatraz("imbued_tier1_weapon");
    private static final NamespacedKey TIER2_WEAPON_KEY = MagicKeys.alkatraz("imbued_tier2_weapon");
    private static final NamespacedKey TIER3_WEAPON_KEY = MagicKeys.alkatraz("imbued_tier3_weapon");
    private static final NamespacedKey TIER4_WEAPON_KEY = MagicKeys.alkatraz("imbued_tier4_weapon");
    private static final NamespacedKey TIER5_WEAPON_KEY = MagicKeys.alkatraz("imbued_tier5_weapon");
    private static final String IMBUE_SYMBOL = "𖢻";
    private static final String IMBUE_PREFIX_LEGACY = ColorFormat.format("&dImbued &r");

    private ImbueManager() {}

    public static void initialize() {
        for (Material mat : Material.values()) {
            int tier = determineTier(mat);
            if (tier > 0) {
                MATERIAL_TIERS.put(mat, tier);
            }
        }
    }

    public static boolean isImbuable(Material material) {
        return MATERIAL_TIERS.containsKey(material);
    }

    public static Set<Material> getImbuableMaterials() {
        return MATERIAL_TIERS.keySet();
    }

    public static int getTier(Material material) {
        return MATERIAL_TIERS.getOrDefault(material, 0);
    }

    public static NamespacedKey getTierKey(Material material) {
        if (isWeapon(material)) {
            return switch (getTier(material)) {
                case 1 -> TIER1_WEAPON_KEY;
                case 2 -> TIER2_WEAPON_KEY;
                case 3 -> TIER3_WEAPON_KEY;
                case 4 -> TIER4_WEAPON_KEY;
                case 5 -> TIER5_WEAPON_KEY;
                default -> null;
            };
        }
        return switch (getTier(material)) {
            case 1 -> TIER1_KEY;
            case 2 -> TIER2_KEY;
            case 3 -> TIER3_KEY;
            case 4 -> TIER4_KEY;
            case 5 -> TIER5_KEY;
            default -> null;
        };
    }

    public static boolean isWeapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.equals("MACE")
                || name.equals("TRIDENT");
    }

    public static int getStoneCount(Material material) {
        return SpellbookConfig.getImbueStoneCost(getTier(material));
    }

    public static boolean isImbued(ItemStack stack) {
        return MagicItemStack.readDefinitionKey(stack)
                .map(key -> key.getKey().startsWith("imbued_"))
                .orElse(false);
    }

    public static String wrapName(String name) {
        if (name == null) return "";
        String clean = unwrapName(name);
        if (clean.isEmpty()) return "";
        return ColorFormat.format("&d&k" + IMBUE_SYMBOL + " &r&d" + clean + " &d&k" + IMBUE_SYMBOL);
    }

    public static String unwrapName(String displayName) {
        if (displayName == null) return "";
        String s = displayName;
        if (s.startsWith(IMBUE_PREFIX_LEGACY)) {
            s = s.substring(IMBUE_PREFIX_LEGACY.length());
        }
        s = ChatColor.stripColor(s);
        s = s.replace(IMBUE_SYMBOL, "");
        return s.trim();
    }

    public static String getCleanName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return "";
        ItemMeta meta = stack.getItemMeta();
        String clean = meta != null && meta.hasDisplayName() ? unwrapName(meta.getDisplayName()) : "";
        if (clean.isEmpty()) clean = prettifyMaterial(stack.getType());
        return clean;
    }

    public static ItemStack toWrapped(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return copy;
        meta.setDisplayName(wrapName(getCleanName(copy)));
        copy.setItemMeta(meta);
        return copy;
    }

    public static ItemStack toClean(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return copy;
        meta.setDisplayName(getCleanName(copy));
        copy.setItemMeta(meta);
        return copy;
    }

    public static ItemStack imbue(ItemStack input) {
        if (input == null || input.getType().isAir()) return input;
        if (!isImbuable(input.getType())) return input;
        if (isImbued(input)) return input;

        NamespacedKey tierKey = getTierKey(input.getType());
        if (tierKey == null) return input;

        ItemStack result = input.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        String originalName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        List<String> originalLore = meta.hasLore() ? meta.getLore() : null;

        MagicItemInstance instance = MagicItemInstance.createDefault(tierKey);
        result = MagicItemStack.writeInstance(result, instance);

        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta != null) {
            resultMeta.setDisplayName(wrapName(originalName != null ? originalName : prettifyMaterial(input.getType())));

            if (originalLore != null && !originalLore.isEmpty()) {
                List<String> combinedLore = resultMeta.getLore();
                if (combinedLore == null) combinedLore = new ArrayList<>();
                combinedLore.add("");
                combinedLore.addAll(originalLore);
                resultMeta.setLore(combinedLore);
            }

            result.setItemMeta(resultMeta);
        }

        return result;
    }

    private static int determineTier(Material material) {
        String name = material.name();
        if (name.startsWith("NETHERITE_") || name.equals("TURTLE_HELMET") || name.equals("MACE")) return 5;
        if (name.startsWith("DIAMOND_") || name.equals("CROSSBOW") || name.equals("TRIDENT")) return 4;
        if (name.startsWith("IRON_") || name.equals("SHIELD")) return 3;
        if (name.startsWith("STONE_") || name.startsWith("CHAINMAIL_")) return 2;
        if (name.startsWith("WOODEN_") || name.startsWith("LEATHER_") || name.startsWith("GOLDEN_")) return 1;
        if (name.equals("BOW")) return 3;
        return 0;
    }

    private static String prettifyMaterial(Material material) {
        String name = material.name();
        StringBuilder result = new StringBuilder();
        boolean nextUpper = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                result.append(' ');
                nextUpper = true;
            } else {
                result.append(nextUpper ? c : Character.toLowerCase(c));
                nextUpper = false;
            }
        }
        return result.toString();
    }
}
