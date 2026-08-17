package me.nagasonic.alkatraz.gui.implementation.engraving;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.lore.LoreFormatter;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks the player to confirm an engraving attempt before it is applied,
 * showing the target item, the rune, the trigger, the mana cost, and the
 * success/failure chances. Confirming delegates to
 * {@link EngravingTableMenu#applyEngraving}.
 */
public class ConfirmEngravingMenu extends Menu {

    private static final int TARGET_SLOT = 22;
    private static final int RUNE_SLOT = 24;
    private static final int MANA_SLOT = 20;
    private static final int SUCCESS_SLOT = 21;
    private static final int FAILURE_SLOT = 23;
    private static final int CONFIRM_SLOT = 31;
    private static final int CANCEL_SLOT = 33;

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public ConfirmEngravingMenu(Player viewer) {
        super(viewer, ColorFormat.format(lang().get("menu.confirm_engraving")), 45);
    }

    @Override
    protected void build() {
        fillAll();

        EngravingSession session = EngravingSession.get(viewer.getUniqueId());
        if (session == null) {
            close();
            return;
        }

        inventory.setItem(TARGET_SLOT, createTargetDisplay(session));
        inventory.setItem(RUNE_SLOT, createRuneDisplay(session));
        inventory.setItem(MANA_SLOT, createManaPanel(session));
        inventory.setItem(SUCCESS_SLOT, createSuccessPanel());
        inventory.setItem(FAILURE_SLOT, createFailurePanel());
        inventory.setItem(CONFIRM_SLOT, createConfirmButton(session));
        inventory.setItem(CANCEL_SLOT, createCancelButton());
    }

    private ItemStack createTargetDisplay(EngravingSession session) {
        ItemStack display = session.targetStack().clone();
        List<String> lore = display.hasItemMeta() && display.getItemMeta().hasLore()
                ? display.getItemMeta().getLore()
                : new ArrayList<>();
        lore.add("");
        lore.add(ColorFormat.format("&7Will receive this engraving."));
        return ItemBuilder.of(display).rawLore(lore).build();
    }

    private ItemStack createRuneDisplay(EngravingSession session) {
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&8Trigger: &f" + triggerName(session.selectedTriggerKey())));

        MagicItemRegistries.ENGRAVING_DEFINITIONS.get(session.selectedEngravingKey()).ifPresent(def ->
                LoreFormatter.engravingBlock(def, session.selectedTriggerKey())
                        .forEach(line -> lore.add(ColorFormat.format(line))));

        ItemStack rune = session.engravingItemStack();
        if (rune != null && rune.getType() != Material.AIR) {
            return ItemBuilder.of(rune.clone()).rawLore(lore).build();
        }

        String engName = MagicItemRegistries.ENGRAVING_DEFINITIONS.get(session.selectedEngravingKey())
                .map(def -> StringUtils.prettifyKey(def.getKey().getKey()))
                .orElse("Unknown");
        return ItemBuilder.of(Material.ENCHANTED_BOOK).name("&6" + engName).rawLore(lore).build();
    }

    private ItemStack createManaPanel(EngravingSession session) {
        int manaCost = EngravingTableMenu.engravingManaCost(session);
        MagicProfile profile = ProfileManager.getProfile(viewer.getUniqueId(), MagicProfile.class);
        int currentMana = profile != null ? (int) profile.getMana() : 0;

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7This attempt costs:"));
        lore.add("");
        lore.add(ColorFormat.format("&8• &b" + manaCost + " &7mana"));
        lore.add("");
        lore.add(ColorFormat.format("&7You have: &b" + currentMana + " &7mana"));
        return ItemBuilder.of(Material.ENDER_PEARL)
                .name("&bMana Cost")
                .rawLore(lore)
                .build();
    }

    private ItemStack createSuccessPanel() {
        int percent = successPercent();
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Chance the engraving is applied:"));
        lore.add("");
        lore.add(ColorFormat.format("&a" + percent + "%"));
        lore.add("");
        lore.add(ColorFormat.format("&7The engraving is added to the item."));
        return ItemBuilder.of(Material.GREEN_DYE)
                .name("&aSuccess Chance")
                .rawLore(lore)
                .build();
    }

    private ItemStack createFailurePanel() {
        int percent = 100 - successPercent();
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Chance the attempt fails:"));
        lore.add("");
        lore.add(ColorFormat.format("&c" + percent + "%"));
        lore.add("");
        lore.add(ColorFormat.format("&7On failure the rune &fand mana &7are lost."));
        return ItemBuilder.of(Material.RED_DYE)
                .name("&cFailure Chance")
                .rawLore(lore)
                .build();
    }

    private ItemStack createConfirmButton(EngravingSession session) {
        int manaCost = EngravingTableMenu.engravingManaCost(session);
        String runeName = runeName(session);

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Consumes:"));
        lore.add(ColorFormat.format("&8• &f1x &6" + runeName));
        lore.add(ColorFormat.format("&8• &b" + manaCost + " &7mana"));
        lore.add("");
        lore.add(ColorFormat.format("&7Success chance: &a" + successPercent() + "%"));
        lore.add(ColorFormat.format("&7Failure: &c" + (100 - successPercent()) + "% &8(rune lost)"));
        return ItemBuilder.of(Material.LIME_CONCRETE)
                .name("&aConfirm Engraving")
                .rawLore(lore)
                .build();
    }

    private ItemStack createCancelButton() {
        return ItemBuilder.of(Material.BARRIER)
                .name("&cCancel")
                .lore("&7Return to the engraving table.")
                .build();
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < size) {
            if (rawSlot == CONFIRM_SLOT) {
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                EngravingSession session = EngravingSession.get(viewer.getUniqueId());
                if (session != null) {
                    EngravingTableMenu.applyEngraving(viewer, session);
                }
                return true;
            }
            if (rawSlot == CANCEL_SLOT) {
                backToTable();
                return true;
            }
            return true;
        }
        return true;
    }

    private void backToTable() {
        EngravingSession session = EngravingSession.get(viewer.getUniqueId());
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        if (session == null) {
            close();
            return;
        }
        new EngravingTableMenu(viewer, session.targetStack(), session.targetInstance(), session.targetDefinition()).open();
    }

    private static int successPercent() {
        return (int) Math.round(EngravingTableMenu.SUCCESS_CHANCE * 100);
    }

    private static String triggerName(NamespacedKey triggerKey) {
        if (triggerKey == null) return "?";
        if (triggerKey.equals(MagicKeys.alkatraz("passive"))) return "Passive";
        return StringUtils.prettifyKey(triggerKey.getKey());
    }

    private static String runeName(EngravingSession session) {
        ItemStack rune = session.engravingItemStack();
        if (rune != null && rune.hasItemMeta() && rune.getItemMeta().hasDisplayName()) {
            return rune.getItemMeta().getDisplayName();
        }
        return MagicItemRegistries.ENGRAVING_DEFINITIONS.get(session.selectedEngravingKey())
                .map(def -> StringUtils.prettifyKey(def.getKey().getKey()))
                .orElse("Unknown");
    }
}
