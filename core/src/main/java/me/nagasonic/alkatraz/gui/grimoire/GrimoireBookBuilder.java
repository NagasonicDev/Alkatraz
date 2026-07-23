package me.nagasonic.alkatraz.gui.grimoire;

import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

public class GrimoireBookBuilder {

    public static ItemStack buildBook(List<String> pages, String grimoireName) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setTitle("Grimoire");
        meta.setAuthor("Alkatraz");

        List<BaseComponent[]> bookPages = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            bookPages.add(buildPage(i + 1, pages.get(i)));
        }
        meta.spigot().setPages(bookPages.toArray(new BaseComponent[0][]));
        book.setItemMeta(meta);
        return book;
    }

    private static BaseComponent[] buildPage(int pageNumber, String spellId) {
        ComponentBuilder builder = new ComponentBuilder();

        if (spellId == null || spellId.isEmpty()) {
            builder.append("Page " + pageNumber)
                    .bold(true)
                    .color(ChatColor.BLACK)
                    .append("\n\n\nEmpty.\n\nShift+Right-click\nto assign a spell.\n\nClose the book\nto cast the\nselected spell.")
                    .bold(false)
                    .color(ChatColor.BLACK);
            return builder.create();
        }

        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) {
            builder.append("Unknown spell.").color(ChatColor.RED);
            return builder.create();
        }

        Element element = spell.getElement();
        ChatColor elementColor = resolveColor(element.getColor());

        builder.append(stripColor(spell.getDisplayName()))
                .bold(true)
                .color(elementColor)
                .append("\n").reset();

        List<String> description = spell.getDescription();
        if (description != null) {
            for (String line : description) {
                builder.append(stripColor(line))
                        .color(ChatColor.BLACK)
                        .append("\n").reset();
            }
        }

        builder.append("\n").reset();
        builder.append(stripColor(element.getColorlessName()))
                .color(elementColor)
                .append(" | Circle " + spell.getRequiredCircleLevel())
                .color(ChatColor.BLACK)
                .append("\n").reset();
        builder.append("Mana: " + spell.getCost() + " | CD: " + spell.getCooldown() + "s | Cast: " + spell.getCastTime() + "s")
                .color(ChatColor.BLACK);

        return builder.create();
    }

    private static ChatColor resolveColor(String colorCode) {
        if (colorCode == null) return ChatColor.WHITE;
        if (colorCode.length() == 2 && colorCode.charAt(0) == '&') {
            return ChatColor.getByChar(colorCode.charAt(1));
        }
        if (colorCode.startsWith("#")) {
            String h = colorCode.substring(1).toUpperCase();
            if (h.equals("FF8C00")) return ChatColor.GOLD;
            if (h.equals("A0522D")) return ChatColor.DARK_RED;
            if (h.equals("FFFF87")) return ChatColor.YELLOW;
            try { return ChatColor.of(colorCode); } catch (Exception e) { return ChatColor.WHITE; }
        }
        return ChatColor.WHITE;
    }

    private static String stripColor(String text) {
        if (text == null) return "";
        return text.replaceAll("&[0-9a-fk-orA-FK-OR]", "")
                   .replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "")
                   .replaceAll("#[0-9a-fA-F]{6}", "");
    }
}
