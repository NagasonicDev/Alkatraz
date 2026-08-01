package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.ProgressionService;
import me.nagasonic.alkatraz.progression.CircleUpAnimation;
import me.nagasonic.alkatraz.progression.circle.CircleDefinition;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CircleUpConfirmationMenu extends Menu {

    private static LangManager lang() {
        return me.nagasonic.alkatraz.Alkatraz.getLangManager();
    }

    private final int targetCircle;

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;

    public CircleUpConfirmationMenu(Player viewer, int targetCircle) {
        super(viewer, lang().get("menu.circle_up_confirm"), 27);
        this.targetCircle = targetCircle;
    }

    @Override
    protected void build() {
        fillAll();

        MagicProfile profile = ProfileManager.getProfile(viewer.getUniqueId(), MagicProfile.class);
        CircleDefinition def = ProgressionService.getCircleDefinition(targetCircle);

        List<String> infoLore = new ArrayList<>();
        infoLore.add(lang().get("circleup.about_to_advance"));
        infoLore.add(ColorFormat.format("&d" + StringUtils.toOrdinal(targetCircle) + " &7circle."));
        infoLore.add(ColorFormat.format(""));
        infoLore.add(lang().get("circleup.rewards_header"));
        if (def != null) {
            if (def.getStatPoints() > 0)
                infoLore.add(lang().get("circleup.reward_stat_points", "amount", String.valueOf(def.getStatPoints())));
            infoLore.add(lang().get("circleup.reward_max_mana", "amount", String.valueOf((int) def.getMaxMana())));
            infoLore.add(lang().get("circleup.reward_mana_regen", "amount", String.valueOf(def.getManaRegeneration())));
        }
        infoLore.add(ColorFormat.format(""));
        infoLore.add(lang().get("circleup.confirm_question"));

        inventory.setItem(13, ItemBuilder.of(Material.NETHER_STAR)
                .name(lang().get("circleup.info_item_name"))
                .rawLore(infoLore)
                .build());

        inventory.setItem(SLOT_CONFIRM, ItemBuilder.of(Material.LIME_WOOL)
                .name(lang().get("circleup.confirm_yes"))
                .lore(lang().get("circleup.confirm_lore", "ordinal", StringUtils.toOrdinal(targetCircle)))
                .build());

        setMenuData(inventory.getItem(SLOT_CONFIRM), "action", "confirm");

        inventory.setItem(SLOT_CANCEL, ItemBuilder.of(Material.RED_WOOL)
                .name(lang().get("circleup.confirm_no"))
                .build());

        setMenuData(inventory.getItem(SLOT_CANCEL), "action", "cancel");
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if ("confirm".equals(action)) {
            if (!ProgressionService.canAdvance(viewer, targetCircle)) {
                viewer.sendMessage(lang().get("circleup.requirements_not_met"));
                new ProgressionMenu(viewer).open();
                return true;
            }
            close();
            CircleUpAnimation.play(viewer, () -> {
                ProgressionService.advance(viewer);
                UnlockManager.refresh(viewer);
                new ProgressionMenu(viewer).open();
            });
            return true;
        }
        if ("cancel".equals(action)) {
            new ProgressionMenu(viewer).open();
            return true;
        }
        return true;
    }
}
