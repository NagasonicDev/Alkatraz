package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.lang.LangManager;
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
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ProgressionMenu extends Menu {

    private static LangManager lang() {
        return me.nagasonic.alkatraz.Alkatraz.getLangManager();
    }

    public ProgressionMenu(Player viewer) {
        super(viewer, lang().get("menu.progression"), 27);
    }

    @Override
    protected void build() {
        fillAll();

        MagicProfile profile = ProfileManager.getProfile(viewer.getUniqueId(), MagicProfile.class);
        int currentCircle = profile.getCircleLevel();

        inventory.setItem(13, ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(lang().get("progression.title"))
                .lore(lang().get("progression.current_circle", "circle", String.valueOf(currentCircle)),
                      lang().get("progression.arcane_knowledge", "value", String.valueOf((int) profile.getArcaneKnowledge())),
                      "",
                      lang().get("progression.legend_green"),
                      lang().get("progression.legend_yellow"),
                      lang().get("progression.legend_red"))
                .build());

        for (int i = 0; i < 9; i++) {
            int circle = i + 1;
            inventory.setItem(9 + i, createCirclePane(circle, currentCircle, profile));
        }

        inventory.setItem(22, ItemBuilder.of(Material.ARROW)
                .name(lang().get("common.back_white"))
                .build());

        setMenuData(inventory.getItem(22), "action", "back");
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
            statusText = lang().get("progression.status_completed");
        } else if (isNext) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            color = "&e";
            statusText = lang().get("progression.status_working");
        } else {
            material = Material.RED_STAINED_GLASS_PANE;
            color = "&c";
            statusText = lang().get("progression.status_locked");
        }

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format(""));
        lore.add(ColorFormat.format(statusText));

        if (completed) {
            lore.add(lang().get("progression.already_mastered"));
        } else {
            lore.add(ColorFormat.format("&7&m&l-------------------"));
            lore.add(lang().get("progression.requirements_header"));
            if (definition != null) {
                RequirementContext context = new RequirementContext(viewer, profile, circle);
                for (ProgressionRequirement req : definition.getRequirements()) {
                    boolean met = req.isMet(context);
                    String metColor = met ? "&a" : "&c";
                    String desc = getRequirementDisplay(req, context);
                    String checkmark = met ? lang().get("progression.requirement_met") : lang().get("progression.requirement_unmet");
                    lore.add(ColorFormat.format(metColor + "  ") + checkmark + " " + desc);
                }
            }

            lore.add(ColorFormat.format("&7&m&l-------------------"));
            lore.add(lang().get("progression.rewards_header"));
            if (definition != null) {
                if (definition.getStatPoints() > 0)
                    lore.add(lang().get("progression.reward_stat_points", "amount", String.valueOf(definition.getStatPoints())));
                lore.add(lang().get("progression.reward_max_mana", "amount", String.valueOf((int) definition.getMaxMana())));
                lore.add(lang().get("progression.reward_mana_regen", "amount", String.valueOf(definition.getManaRegeneration())));
            }

            lore.add(ColorFormat.format("&7-------------------"));

            if (isNext) {
                if (canAdvance) {
                    lore.add(lang().get("progression.click_to_advance"));
                } else {
                    lore.add(lang().get("progression.click_for_details"));
                }
            } else if (circle > currentCircle + 1) {
                lore.add(lang().get("progression.complete_previous_circle"));
            }
        }

        ItemStack item = ItemBuilder.of(material)
                .name(ColorFormat.format(color + lang().get("progression.circle_name", "circle", StringUtils.toRoman(circle))))
                .rawLore(lore)
                .build();

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
            return lang().get("progression.requirement_arcane_knowledge", "current", String.valueOf((int) current), "needed", String.valueOf((int) needed));
        }
        if (req instanceof SpellMasteryRequirement smReq) {
            Spell spell = SpellRegistry.getSpell(smReq.getSpellId());
            String spellName = spell != null ? spell.getDisplayName() : smReq.getSpellId();
            int current = context.getProfile().getSpellMastery(spell);
            int needed = smReq.getMastery();
            boolean met = req.isMet(context);
            String metColor = met ? "&a" : "&c";
            return ColorFormat.format(metColor + lang().get("progression.requirement_spell_mastery", "spell", spellName, "current", String.valueOf(Math.max(0, current)), "needed", String.valueOf(needed)));
        }
        return req.describe();
    }
}
