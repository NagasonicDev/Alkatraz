package me.nagasonic.alkatraz.gui;

import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating ItemStacks with formatted display names and lore.
 *
 * <p>Usage:
 * <pre>
 *   ItemStack item = ItemBuilder.of(Material.PAPER)
 *       .name("&eTitle")
 *       .lore("&7Line one", "&7Line two")
 *       .glint(true)
 *       .build();
 * </pre>
 */
public class ItemBuilder {
    private final ItemStack item;
    private ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder of(ItemStack existing) {
        ItemBuilder b = new ItemBuilder(existing.getType());
        b.item.setItemMeta(existing.getItemMeta());
        b.meta = b.item.getItemMeta();
        return b;
    }

    /** Set display name with color formatting (& codes and hex). */
    public ItemBuilder name(String name) {
        meta.setDisplayName(ColorFormat.format(name));
        return this;
    }

    /** Set display name without color formatting. */
    public ItemBuilder rawName(String name) {
        meta.setDisplayName(name);
        return this;
    }

    /** Add lore lines with color formatting. */
    public ItemBuilder lore(String... lines) {
        for (String line : lines) {
            lore.add(ColorFormat.format(line));
        }
        return this;
    }

    /** Add a single blank lore line. */
    public ItemBuilder blankLine() {
        lore.add("");
        return this;
    }

    /** Set the full lore, replacing any existing lines. Lines are color-formatted. */
    public ItemBuilder setLore(List<String> lines) {
        lore.clear();
        for (String line : lines) {
            lore.add(ColorFormat.format(line));
        }
        return this;
    }

    /** Set the full lore raw (no color formatting). */
    public ItemBuilder rawLore(List<String> lines) {
        lore.clear();
        lore.addAll(lines);
        return this;
    }

    /** Append raw (already-formatted) lore lines. */
    public ItemBuilder appendLore(List<String> lines) {
        lore.addAll(lines);
        return this;
    }

    /** Get the current lore list (for conditional additions). */
    public List<String> getLore() {
        return lore;
    }

    /** Set item amount. */
    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /** Toggle enchant glint (hidden durability enchant). Useful for "selected" or "locked" states. */
    public ItemBuilder glint(boolean enabled) {
        if (enabled) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /** Hide all attribute modifiers. */
    public ItemBuilder hideAttributes() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return this;
    }

    /** Set custom model data (for texture pack integration). */
    public ItemBuilder customModelData(int cmd) {
        meta.setCustomModelData(cmd);
        return this;
    }

    /** Build the final ItemStack. */
    public ItemStack build() {
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }
}
