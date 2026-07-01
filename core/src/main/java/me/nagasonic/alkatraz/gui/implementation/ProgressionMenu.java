package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.ProgressionService;
import me.nagasonic.alkatraz.progression.circle.CircleDefinition;
import me.nagasonic.alkatraz.progression.requirement.ProgressionRequirement;
import me.nagasonic.alkatraz.progression.requirement.RequirementContext;
import me.nagasonic.alkatraz.progression.requirement.implementation.ArcaneKnowledgeRequirement;
import me.nagasonic.alkatraz.progression.requirement.implementation.SpellMasteryRequirement;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ProgressionMenu extends Menu {

    public ProgressionMenu(Player viewer) {
        super(viewer, ColorFormat.format("&5Progression"), 27);
    }

    @Override
    protected void build() {
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, createPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        MagicProfile profile = ProfileManager.getProfile(viewer.getUniqueId(), MagicProfile.class);
        int currentCircle = profile.getCircleLevel();

        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ColorFormat.format("&dCircle Progression"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ColorFormat.format("&7Current Circle: &f" + currentCircle));
        infoLore.add(ColorFormat.format("&7Arcane Knowledge: &f" + (int) profile.getArcaneKnowledge()));
        infoLore.add(ColorFormat.format(""));
        infoLore.add(ColorFormat.format("&aGreen &7= Completed"));
        infoLore.add(ColorFormat.format("&eYellow &7= Working towards"));
        infoLore.add(ColorFormat.format("&cRed &7= Locked"));
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(13, info);

        for (int i = 0; i < 9; i++) {
            int circle = i + 1;
            inventory.setItem(9 + i, createCirclePane(circle, currentCircle, profile));
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ColorFormat.format("&fBack"));
        back.setItemMeta(backMeta);
        setMenuData(back, "action", "back");
        inventory.setItem(22, back);
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if ("back".equals(action)) {
            new WandTableSelectionMenu(viewer).open();
            return true;
        }

        if ("circle_up".equals(action)) {
            int circle = getIntData(clicked, "circle");
            new CircleUpConfirmationMenu(viewer, circle).open();
            return true;
        }

        return true;
    }

    private ItemStack createCirclePane(int circle, int currentCircle, MagicProfile profile) {
        CircleDefinition definition = ProgressionService.getCircleDefinition(circle);
        boolean completed = circle <= currentCircle;
        boolean isNext = circle == currentCircle + 1;
        boolean canAdvance = isNext && ProgressionService.canAdvance(viewer, circle);

        Material material;
        String color;
        String statusText;

        if (completed) {
            material = Material.LIME_STAINED_GLASS_PANE;
            color = "&a";
            statusText = "&a&lCOMPLETED";
        } else if (isNext) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            color = "&e";
            statusText = "&e&lWORKING TOWARDS";
        } else {
            material = Material.RED_STAINED_GLASS_PANE;
            color = "&c";
            statusText = "&c&lLOCKED";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(color + "Circle " + toRoman(circle)));

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format(""));
        lore.add(ColorFormat.format(statusText));

        if (completed) {
            lore.add(ColorFormat.format("&7You have already mastered this circle."));
        } else {
            lore.add(ColorFormat.format("&7&m&l-------------------"));
            lore.add(ColorFormat.format("&e&lRequirements:"));
            if (definition != null) {
                RequirementContext context = new RequirementContext(viewer, profile, circle);
                for (ProgressionRequirement req : definition.getRequirements()) {
                    boolean met = req.isMet(context);
                    String metColor = met ? "&a" : "&c";
                    String desc = getRequirementDisplay(req, context);
                    lore.add(ColorFormat.format(metColor + "  " + (met ? "✔" : "✘") + " " + desc));
                }
            }

            lore.add(ColorFormat.format("&7&m&l-------------------"));
            lore.add(ColorFormat.format("&e&lRewards:"));
            if (definition != null) {
                if (definition.getStatPoints() > 0)
                    lore.add(ColorFormat.format("&a  +" + definition.getStatPoints() + " Stat Points"));
                lore.add(ColorFormat.format("&a  +" + (int) definition.getMaxMana() + " Max Mana"));
                lore.add(ColorFormat.format("&a  +" + definition.getManaRegeneration() + " Mana Regen/s"));
            }

            lore.add(ColorFormat.format("&7&m&l-------------------"));

            if (isNext) {
                if (canAdvance) {
                    lore.add(ColorFormat.format("&e&lClick to advance!"));
                } else {
                    lore.add(ColorFormat.format("&eClick for details"));
                }
            } else if (circle > currentCircle + 1) {
                lore.add(ColorFormat.format("&cComplete the previous circle first"));
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        if (isNext) {
            setMenuData(item, "action", "circle_up");
            setMenuData(item, "circle", circle);
        }

        return item;
    }

    private String getRequirementDisplay(ProgressionRequirement req, RequirementContext context) {
        if (req instanceof ArcaneKnowledgeRequirement akReq) {
            double current = context.getProfile().getArcaneKnowledge();
            double needed = akReq.getAmount();
            return "Arcane Knowledge: " + (int) current + "/" + (int) needed;
        }
        if (req instanceof SpellMasteryRequirement smReq) {
            Spell spell = SpellRegistry.getSpell(smReq.getSpellId());
            String spellName = spell != null ? spell.getDisplayName() : smReq.getSpellId();
            int current = context.getProfile().getSpellMastery(spell);
            int needed = smReq.getMastery();
            return "Spell Mastery (" + spellName + "): " + Math.max(0, current) + "/" + needed;
        }
        return req.describe();
    }

    private String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> String.valueOf(number);
        };
    }

    private ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(name));
        item.setItemMeta(meta);
        return item;
    }
}
